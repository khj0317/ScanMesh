package com.scanmesh.api.target.service;

import com.scanmesh.api.common.net.BlockedAddressException;
import com.scanmesh.api.common.net.DnsTxtLookup;
import com.scanmesh.api.common.net.RedirectNotAllowedException;
import com.scanmesh.api.common.net.WebFetcher;
import com.scanmesh.api.target.entity.VerificationMethod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.util.List;

/** 사용자가 올린 인증 토큰을 DNS나 사이트에서 실제로 찾아 도메인 소유를 확인한다 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OwnershipVerifier {

    public static final String DNS_RECORD_PREFIX = "_scanmesh-verification.";
    public static final String FILE_PATH = "/.well-known/scanmesh-verification.txt";

    private final DnsTxtLookup dnsTxtLookup;
    private final WebFetcher webFetcher;

    /** @param detail 실패했을 때 사용자가 무엇을 고치면 되는지 알려 주는 문구 */
    public record Outcome(boolean verified, String detail) {

        static Outcome success() {
            return new Outcome(true, "소유 인증이 완료되었습니다.");
        }

        static Outcome failure(String detail) {
            return new Outcome(false, detail);
        }
    }

    public static String dnsRecordName(String host) {
        return DNS_RECORD_PREFIX + host;
    }

    public static String fileUrl(String origin) {
        return origin + FILE_PATH;
    }

    public Outcome verify(VerificationMethod method, String host, String origin, String token) {
        return switch (method) {
            case DNS_TXT -> verifyDns(host, token);
            case WELL_KNOWN_FILE -> verifyFile(origin, token);
        };
    }

    private Outcome verifyDns(String host, String token) {
        String name = dnsRecordName(host);
        List<String> records;
        try {
            records = dnsTxtLookup.lookup(name);
        } catch (IOException e) {
            log.info("소유 인증 DNS 조회 실패: {}", name, e);
            return Outcome.failure("DNS 조회에 실패했습니다. 잠시 후 다시 시도해주세요.");
        }
        if (records.isEmpty()) {
            return Outcome.failure(name + " 의 TXT 레코드를 찾지 못했습니다. DNS 변경이 반영되기까지 시간이 걸릴 수 있습니다.");
        }
        if (records.stream().map(String::strip).noneMatch(token::equals)) {
            return Outcome.failure(name + " 의 TXT 레코드 값이 인증 토큰과 다릅니다.");
        }
        return Outcome.success();
    }

    private Outcome verifyFile(String origin, String token) {
        String url = fileUrl(origin);
        WebFetcher.FetchResult result;
        try {
            result = webFetcher.get(URI.create(url));
        } catch (BlockedAddressException e) {
            log.warn("소유 인증 파일 요청 차단: {}", e.getMessage());
            return Outcome.failure("도메인이 내부망이나 예약된 IP 주소를 가리켜서 확인하지 않았습니다.");
        } catch (RedirectNotAllowedException e) {
            return Outcome.failure("다른 도메인으로 리다이렉트되어 확인하지 않았습니다. " + url + " 이 리다이렉트 없이 응답하게 해주세요.");
        } catch (IOException e) {
            log.info("소유 인증 파일 요청 실패: {}", url, e);
            return Outcome.failure(url + " 에 연결하지 못했습니다.");
        }
        if (result.status() != 200) {
            return Outcome.failure(url + " 의 응답 코드가 " + result.status() + " 입니다. 200으로 응답해야 합니다.");
        }
        if (result.body().lines().map(String::strip).noneMatch(token::equals)) {
            return Outcome.failure(url + " 의 내용이 인증 토큰과 다릅니다.");
        }
        return Outcome.success();
    }
}
