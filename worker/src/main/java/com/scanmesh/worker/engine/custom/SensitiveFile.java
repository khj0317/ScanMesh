package com.scanmesh.worker.engine.custom;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 공개되면 안 되는 파일과 그 파일임을 확인하는 내용 패턴.
 * SPA는 없는 주소에도 200으로 첫 화면을 돌려주므로, 응답 코드만 보지 않고 내용까지 맞아야 발견으로 친다.
 */
record SensitiveFile(String path, String title, String severity, int cweId, Pattern signature) {

    static final List<SensitiveFile> ALL = List.of(
        new SensitiveFile("/.env", "환경 변수 파일(.env) 노출", "CRITICAL", 538, Pattern.compile("(?m)^[A-Z][A-Z0-9_]*=")),
        new SensitiveFile("/.git/HEAD", "Git 저장소(.git) 노출", "HIGH", 527, Pattern.compile("^ref: refs/")),
        new SensitiveFile("/.git/config", "Git 설정(.git/config) 노출", "HIGH", 527, Pattern.compile("(?m)^\\[core]")),
        new SensitiveFile("/.aws/credentials", "AWS 자격 증명 파일 노출", "CRITICAL", 522, Pattern.compile("(?m)^aws_access_key_id\\s*=")),
        new SensitiveFile("/.htpasswd", "비밀번호 파일(.htpasswd) 노출", "HIGH", 538, Pattern.compile("(?m)^[^:\\s<]+:\\$(apr1|2[aby]|5|6)\\$")),
        new SensitiveFile("/backup.sql", "DB 백업 파일 노출", "CRITICAL", 530, Pattern.compile("(?i)(CREATE TABLE|INSERT INTO|-- MySQL dump|-- PostgreSQL database dump)")),
        new SensitiveFile("/dump.sql", "DB 백업 파일 노출", "CRITICAL", 530, Pattern.compile("(?i)(CREATE TABLE|INSERT INTO|-- MySQL dump|-- PostgreSQL database dump)")),
        new SensitiveFile("/phpinfo.php", "phpinfo 페이지 노출", "MEDIUM", 200, Pattern.compile("(?i)<title>\\s*phpinfo\\(\\)|PHP Version")),
        new SensitiveFile("/server-status", "Apache 서버 상태 페이지 노출", "MEDIUM", 200, Pattern.compile("Apache Server Status"))
    );

    /** 첫 화면 대신 돌아온 HTML을 걸러낸다. phpinfo와 서버 상태 페이지는 원래 HTML이다 */
    boolean matches(String body) {
        boolean htmlExpected = path.equals("/phpinfo.php") || path.equals("/server-status");
        String head = body.stripLeading().toLowerCase(Locale.ROOT);
        if (!htmlExpected && (head.startsWith("<!doctype") || head.startsWith("<html"))) {
            return false;
        }
        return signature.matcher(body).find();
    }
}
