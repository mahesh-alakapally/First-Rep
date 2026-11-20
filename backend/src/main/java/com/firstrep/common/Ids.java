package com.firstrep.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

public final class Ids {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] CODE = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private Ids() {}

    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    public static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    public static String verificationCode() {
        StringBuilder out = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            out.append(CODE[RANDOM.nextInt(CODE.length)]);
        }
        return out.toString();
    }

    public static String slug(String value) {
        String slug = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (slug.isBlank()) {
            slug = "item";
        }
        return slug.length() > 80 ? slug.substring(0, 80) : slug;
    }
}
