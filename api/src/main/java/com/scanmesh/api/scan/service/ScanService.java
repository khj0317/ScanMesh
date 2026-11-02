package com.scanmesh.api.scan.service;

import com.scanmesh.api.common.exception.BusinessException;
import com.scanmesh.api.common.exception.ErrorCode;
import com.scanmesh.api.member.repository.MemberRepository;
import com.scanmesh.api.scan.ScanProperties;
import com.scanmesh.api.scan.dto.ScanResponse;
import com.scanmesh.api.scan.entity.Scan;
import com.scanmesh.api.scan.entity.ScanTask;
import com.scanmesh.api.scan.repository.ScanRepository;
import com.scanmesh.api.scan.repository.ScanTaskRepository;
import com.scanmesh.api.target.entity.Target;
import com.scanmesh.api.target.repository.TargetRepository;
import com.scanmesh.common.scan.Engine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** 사용자의 점검 요청, 조회, 취소 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScanService {

    private final ScanRepository scanRepository;
    private final ScanTaskRepository scanTaskRepository;
    private final TargetRepository targetRepository;
    private final MemberRepository memberRepository;
    private final ScanProperties properties;
    private final Clock clock;

    @Transactional
    public ScanResponse request(Long memberId, Long targetId, Set<Engine> engines) {
        Target target = targetRepository.findByIdAndMemberIdForUpdate(targetId, memberId)
            .orElseThrow(() -> new BusinessException(ErrorCode.TARGET_NOT_FOUND));
        if (!target.isVerified()) {
            throw new BusinessException(ErrorCode.TARGET_NOT_VERIFIED);
        }
        // 0주차 실험에서 제한 없이 점검하자 대상 앱이 죽었다. 한 대상에 점검이 겹치지 않게 한다
        if (scanRepository.existsActiveByTargetId(targetId)) {
            throw new BusinessException(ErrorCode.SCAN_ALREADY_RUNNING);
        }
        Collection<Engine> selected = engines == null || engines.isEmpty() ? EnumSet.allOf(Engine.class) : EnumSet.copyOf(engines);
        Scan scan = new Scan(target, memberRepository.getReferenceById(memberId), selected, properties.maxAttempts(), clock.instant());
        return ScanResponse.from(scanRepository.save(scan));
    }

    public ScanResponse get(Long memberId, Long scanId) {
        return ScanResponse.from(find(memberId, scanId));
    }

    public List<ScanResponse> listByTarget(Long memberId, Long targetId) {
        targetRepository.findByIdAndMemberId(targetId, memberId)
            .orElseThrow(() -> new BusinessException(ErrorCode.TARGET_NOT_FOUND));
        return scanRepository.findRecentByTargetId(targetId).stream()
            .map(ScanResponse::from)
            .toList();
    }

    /** 대기 중인 작업은 바로 취소하고, 실행 중인 작업은 워커가 다음 하트비트에서 확인해 멈춘다 */
    @Transactional
    public ScanResponse cancel(Long memberId, Long scanId) {
        Scan scan = find(memberId, scanId);
        Instant now = clock.instant();
        // 워커가 같은 작업을 가져가거나 끝내는 중일 수 있으므로 작업 행을 잠그고 바꾼다
        for (ScanTask task : scanTaskRepository.findAllByScanIdForUpdate(scanId)) {
            task.requestCancel(now);
        }
        return ScanResponse.from(scan);
    }

    private Scan find(Long memberId, Long scanId) {
        return scanRepository.findByIdAndMemberId(scanId, memberId)
            .orElseThrow(() -> new BusinessException(ErrorCode.SCAN_NOT_FOUND));
    }
}
