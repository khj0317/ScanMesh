package com.scanmesh.common.finding;

import java.util.Locale;

/** 위험도. 선언 순서가 높은 것부터이므로 정렬과 비교에 그대로 쓴다 */
public enum Severity {

    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
    INFO;

    public boolean isHigherThan(Severity other) {
        return compareTo(other) < 0;
    }

    /** Nuclei의 critical/high/medium/low/info. 모르는 값(unknown 등)은 INFO로 본다 */
    public static Severity fromNuclei(String value) {
        if (value == null) {
            return INFO;
        }
        return switch (value.strip().toLowerCase(Locale.ROOT)) {
            case "critical" -> CRITICAL;
            case "high" -> HIGH;
            case "medium" -> MEDIUM;
            case "low" -> LOW;
            default -> INFO;
        };
    }

    /** ZAP의 riskcode: 3 High, 2 Medium, 1 Low, 0 Informational. ZAP에는 CRITICAL이 없다 */
    public static Severity fromZapRiskCode(String riskCode) {
        return switch (riskCode == null ? "" : riskCode.strip()) {
            case "3" -> HIGH;
            case "2" -> MEDIUM;
            case "1" -> LOW;
            default -> INFO;
        };
    }
}
