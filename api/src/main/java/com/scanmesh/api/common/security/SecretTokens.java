package com.scanmesh.api.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 리프레시 토큰, 워커 토큰처럼 원문을 한 번만 건네고 DB에는 해시만 남기는 비밀 값.
 * 원문이 충분히 길고 무작위라 BCrypt 같은 느린 해시 없이 SHA-256으로 충분하다.
 */
public final class SecretTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private SecretTokens() {
    }

    /** 256비트 무작위 값을 URL에 써도 되는 Base64로 */
    public static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 16진수 64자 */
    public static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
