package com.scanmesh.api.target.service;

import com.scanmesh.api.common.exception.BusinessException;
import com.scanmesh.api.common.exception.ErrorCode;
import com.scanmesh.api.common.net.HostResolver;
import com.scanmesh.api.member.repository.MemberRepository;
import com.scanmesh.api.target.DevAllowedHosts;
import com.scanmesh.api.target.dto.TargetCreateRequest;
import com.scanmesh.api.target.dto.TargetResponse;
import com.scanmesh.api.target.dto.VerificationResponse;
import com.scanmesh.api.target.entity.Target;
import com.scanmesh.api.target.entity.VerificationMethod;
import com.scanmesh.api.target.repository.TargetRepository;
import com.scanmesh.common.net.PublicAddressPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

/**
 * 점검 대상 등록과 소유 인증.
 * 등록과 인증은 DNS 조회나 외부 HTTP 요청처럼 몇 초씩 걸릴 수 있는 일을 하므로, 그동안 DB 연결을 붙잡지 않게
 * 트랜잭션을 메서드 전체가 아니라 DB를 읽고 쓰는 부분에만 건다.
 */
@Service
public class TargetService {

    public static final int MAX_TARGETS_PER_MEMBER = 20;
    public static final Duration VERIFICATION_COOLDOWN = Duration.ofSeconds(10);

    private final TargetRepository targetRepository;
    private final MemberRepository memberRepository;
    private final HostResolver hostResolver;
    private final OwnershipVerifier ownershipVerifier;
    private final DevAllowedHosts devAllowedHosts;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public TargetService(
        TargetRepository targetRepository,
        MemberRepository memberRepository,
        HostResolver hostResolver,
        OwnershipVerifier ownershipVerifier,
        DevAllowedHosts devAllowedHosts,
        PlatformTransactionManager transactionManager,
        Clock clock
    ) {
        this.targetRepository = targetRepository;
        this.memberRepository = memberRepository;
        this.hostResolver = hostResolver;
        this.ownershipVerifier = ownershipVerifier;
        this.devAllowedHosts = devAllowedHosts;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public TargetResponse create(Long memberId, TargetCreateRequest request) {
        TargetUrl url = TargetUrl.parse(request.url(), devAllowedHosts.hosts());
        boolean devTarget = devAllowedHosts.contains(url.host());
        if (!devTarget) {
            checkResolvesToPublicAddress(url.host());
        }

        return transaction.execute(status -> {
            if (targetRepository.existsByMemberIdAndHost(memberId, url.host())) {
                throw new BusinessException(ErrorCode.DUPLICATE_TARGET);
            }
            if (targetRepository.countByMemberId(memberId) >= MAX_TARGETS_PER_MEMBER) {
                throw new BusinessException(ErrorCode.TARGET_LIMIT_EXCEEDED,
                    "점검 대상은 " + MAX_TARGETS_PER_MEMBER + "개까지 등록할 수 있습니다. 쓰지 않는 대상을 삭제해주세요.");
            }
            Target target = new Target(
                memberRepository.getReferenceById(memberId),
                url.baseUrl(),
                url.host(),
                request.verificationMethod(),
                newVerificationToken()
            );
            if (devTarget) {
                // 연습용 대상은 DNS나 인증 파일을 둘 수 없으므로 바로 인증된 것으로 본다
                target.markVerified(clock.instant());
            }
            return TargetResponse.from(targetRepository.save(target));
        });
    }

    @Transactional(readOnly = true)
    public List<TargetResponse> list(Long memberId) {
        return targetRepository.findAllByMemberIdOrderByIdDesc(memberId).stream()
            .map(TargetResponse::from)
            .toList();
    }

    @Transactional
    public void delete(Long memberId, Long targetId) {
        targetRepository.delete(find(memberId, targetId));
    }

    public VerificationResponse verify(Long memberId, Long targetId) {
        Instant now = clock.instant();
        Attempt attempt = transaction.execute(status -> {
            Target target = find(memberId, targetId);
            if (target.isVerified()) {
                return null;
            }
            if (targetRepository.recordVerificationAttempt(targetId, now, now.minus(VERIFICATION_COOLDOWN)) == 0) {
                throw new BusinessException(ErrorCode.VERIFICATION_TOO_FREQUENT,
                    "소유 인증 확인은 " + VERIFICATION_COOLDOWN.toSeconds() + "초에 한 번만 할 수 있습니다. 잠시 후 다시 시도해주세요.");
            }
            return new Attempt(target.getVerificationMethod(), target.getHost(), target.origin(), target.getVerificationToken());
        });
        if (attempt == null) {
            return new VerificationResponse(true, "이미 소유 인증이 완료된 대상입니다.", get(memberId, targetId));
        }

        // 외부 확인은 트랜잭션 밖에서 한다
        OwnershipVerifier.Outcome outcome = ownershipVerifier.verify(attempt.method(), attempt.host(), attempt.origin(), attempt.token());

        TargetResponse target = transaction.execute(status -> {
            // 확인하는 사이에 삭제됐으면 여기서 404가 난다
            Target found = find(memberId, targetId);
            if (outcome.verified()) {
                found.markVerified(clock.instant());
            }
            return TargetResponse.from(found);
        });
        return new VerificationResponse(outcome.verified(), outcome.detail(), target);
    }

    private TargetResponse get(Long memberId, Long targetId) {
        return transaction.execute(status -> TargetResponse.from(find(memberId, targetId)));
    }

    private Target find(Long memberId, Long targetId) {
        return targetRepository.findByIdAndMemberId(targetId, memberId)
            .orElseThrow(() -> new BusinessException(ErrorCode.TARGET_NOT_FOUND));
    }

    /**
     * 등록할 때 한 번 걸러서 사용자에게 바로 알려 준다. DNS 응답은 나중에 바뀔 수 있으므로
     * 인증 요청과 점검 때도 연결 직전에 다시 검사한다.
     */
    private void checkResolvesToPublicAddress(String host) {
        InetAddress[] addresses;
        try {
            addresses = hostResolver.resolve(host);
        } catch (UnknownHostException e) {
            throw new BusinessException(ErrorCode.TARGET_HOST_NOT_FOUND);
        }
        for (InetAddress address : addresses) {
            if (!PublicAddressPolicy.isPublic(address)) {
                throw new BusinessException(ErrorCode.TARGET_ADDRESS_NOT_ALLOWED);
            }
        }
    }

    private String newVerificationToken() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return "scanmesh-" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private record Attempt(VerificationMethod method, String host, String origin, String token) {
    }
}
