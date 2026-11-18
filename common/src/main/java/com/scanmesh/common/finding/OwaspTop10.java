package com.scanmesh.common.finding;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 발견 항목을 OWASP Top 10 (2021) 분류로 바꾼다. 기본은 OWASP가 분류마다 공개한 CWE 목록 중 웹 점검에서
 * 자주 나오는 것을 옮긴 표이고, 공식 목록에 없지만 OWASP 설명에 해당하는 경우는 보충 규칙으로 따로 둔다.
 * 어느 쪽에도 없으면 분류하지 않는다(null).
 */
public final class OwaspTop10 {

    private static final String SECURITY_MISCONFIGURATION = "A05:2021 보안 설정 오류";
    private static final Map<Integer, String> BY_CWE = new HashMap<>();

    /**
     * 보충 규칙: 보안 헤더가 없거나 잘못된 문제. A05의 설명이 "서버가 보안 헤더나 지시문을 보내지 않거나 안전한 값으로 설정하지 않음"을
     * 직접 예로 들지만, 엔진들이 붙이는 CWE-693(보호 장치 실패)은 공식 CWE 목록에 없어서 이것으로 채운다.
     * HSTS는 CWE-319라 공식 표로는 A02(암호화 실패)지만, 헤더 설정 문제이므로 다른 헤더와 같이 A05로 본다.
     */
    private static final Map<String, String> BY_CHECK = Map.of(
        "missing-csp", SECURITY_MISCONFIGURATION,
        "missing-hsts", SECURITY_MISCONFIGURATION,
        "missing-x-content-type-options", SECURITY_MISCONFIGURATION,
        "missing-frame-protection", SECURITY_MISCONFIGURATION
    );
    private static final int PROTECTION_MECHANISM_FAILURE = 693;

    static {
        put("A01:2021 접근 통제 취약점", 22, 23, 35, 59, 200, 201, 219, 264, 275, 276, 284, 285, 352, 359, 377, 402, 425,
            441, 497, 538, 540, 548, 552, 566, 601, 639, 651, 668, 706, 862, 863, 913, 922, 1275);
        put("A02:2021 암호화 실패", 261, 296, 310, 319, 321, 322, 323, 324, 325, 326, 327, 328, 329, 330, 331, 335,
            336, 337, 338, 340, 347, 523, 720, 757, 759, 760, 780, 818, 916);
        put("A03:2021 인젝션", 20, 74, 75, 77, 78, 79, 80, 83, 87, 88, 89, 90, 91, 93, 94, 95, 96, 97, 98, 99, 113,
            116, 138, 184, 470, 471, 564, 610, 643, 644, 652, 917);
        put("A04:2021 안전하지 않은 설계", 73, 183, 209, 213, 235, 256, 257, 266, 269, 280, 311, 312, 313, 316, 419,
            430, 434, 444, 451, 472, 501, 522, 525, 539, 579, 598, 602, 642, 646, 650, 653, 656, 657, 799, 807, 840,
            841, 927, 1021, 1173);
        put("A05:2021 보안 설정 오류", 2, 11, 13, 15, 16, 260, 315, 520, 526, 537, 541, 547, 611, 614, 756, 776, 942,
            1004, 1032, 1174);
        put("A06:2021 취약하고 오래된 구성 요소", 937, 1035, 1104);
        put("A07:2021 식별 및 인증 실패", 255, 259, 287, 288, 290, 294, 295, 297, 300, 302, 304, 306, 307, 346, 384,
            521, 613, 620, 640, 798, 940, 1216);
        put("A08:2021 소프트웨어 및 데이터 무결성 실패", 345, 353, 426, 494, 502, 565, 784, 829, 830, 915);
        put("A09:2021 보안 로깅 및 모니터링 실패", 117, 223, 532, 778);
        put("A10:2021 서버 측 요청 위조(SSRF)", 918);
    }

    private OwaspTop10() {
    }

    /** @param check FindingFingerprint.checkName의 검사 종류 */
    public static String categoryOf(String check, Integer cweId) {
        String byCheck = check == null ? null : BY_CHECK.get(check);
        if (byCheck != null) {
            return byCheck;
        }
        // ZAP은 교차 출처 정책, 권한 정책 같은 다른 보안 헤더 문제에도 CWE-693을 붙인다
        if (cweId != null && cweId == PROTECTION_MECHANISM_FAILURE) {
            return SECURITY_MISCONFIGURATION;
        }
        return categoryOf(cweId);
    }

    /** OWASP 공식 CWE 목록만으로 분류한다 */
    public static String categoryOf(Integer cweId) {
        return cweId == null ? null : BY_CWE.get(cweId);
    }

    private static void put(String category, Integer... cweIds) {
        for (Integer cweId : List.of(cweIds)) {
            BY_CWE.put(cweId, category);
        }
    }
}
