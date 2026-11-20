package com.firstrep.entitlement;

import com.firstrep.common.AppException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class StripeSignatures {
    private StripeSignatures() {}

    public static void verify(String payload, String header, String secret, Duration tolerance) {
        if (secret == null || secret.isBlank()) {
            throw AppException.bad("STRIPE_NOT_CONFIGURED", "Stripe webhook secret is not configured.");
        }
        if (header == null || header.isBlank() || payload == null) {
            throw AppException.bad("STRIPE_SIGNATURE", "Missing Stripe signature.");
        }
        String timestamp = null;
        String signature = null;
        for (String part : header.split(",")) {
            String[] bits = part.split("=", 2);
            if (bits.length != 2) {
                continue;
            }
            if ("t".equals(bits[0])) {
                timestamp = bits[1];
            } else if ("v1".equals(bits[0]) && signature == null) {
                signature = bits[1];
            }
        }
        if (timestamp == null || signature == null) {
            throw AppException.bad("STRIPE_SIGNATURE", "Stripe signature header is not valid.");
        }
        long seconds;
        try {
            seconds = Long.parseLong(timestamp);
        } catch (NumberFormatException ex) {
            throw AppException.bad("STRIPE_SIGNATURE", "Stripe signature timestamp is not valid.");
        }
        long skew = Math.abs(Instant.now().getEpochSecond() - seconds);
        if (skew > tolerance.getSeconds()) {
            throw AppException.bad("STRIPE_SIGNATURE", "Stripe signature timestamp is outside the allowed window.");
        }
        String expected = hmac(secret, timestamp + "." + payload);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
            throw AppException.bad("STRIPE_SIGNATURE", "Stripe signature did not match.");
        }
    }

    public static String hmac(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC unavailable", ex);
        }
    }
}
