package com.firstrep.common;

import java.util.Locale;
import java.util.Set;

public final class UploadInspector {
    public static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final Set<String> ALLOWED = Set.of("pdf", "png", "jpg", "jpeg", "txt", "md", "csv");

    private UploadInspector() {}

    public static Inspected inspect(String filename, byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw AppException.bad("FILE_REJECTED", "Upload a file of 10 MB or less.");
        }
        String safe = filename == null ? "" : filename.replace('\\', '/');
        int slash = safe.lastIndexOf('/');
        if (slash >= 0) {
            safe = safe.substring(slash + 1);
        }
        if (safe.isBlank() || safe.length() > 180) {
            throw AppException.bad("FILE_REJECTED", "Use a shorter file name.");
        }
        int dot = safe.lastIndexOf('.');
        String ext = dot < 0 ? "" : safe.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED.contains(ext)) {
            throw AppException.bad("FILE_REJECTED", "Allowed files are PDF, PNG, JPEG, text, Markdown, and CSV.");
        }
        if (!magic(ext, bytes)) {
            throw AppException.bad("FILE_REJECTED", "The file contents do not match its extension.");
        }
        return new Inspected(safe, mime(ext));
    }

    private static boolean magic(String ext, byte[] bytes) {
        return switch (ext) {
            case "pdf" -> starts(bytes, new byte[] {'%', 'P', 'D', 'F'});
            case "png" -> starts(bytes, new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47});
            case "jpg", "jpeg" -> starts(bytes, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
            default -> textLike(bytes);
        };
    }

    private static boolean textLike(byte[] bytes) {
        int limit = Math.min(bytes.length, 8192);
        for (int i = 0; i < limit; i++) {
            if (bytes[i] == 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean starts(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static String mime(String ext) {
        return switch (ext) {
            case "pdf" -> "application/pdf";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "csv" -> "text/csv";
            case "md" -> "text/markdown";
            default -> "text/plain";
        };
    }

    public record Inspected(String filename, String contentType) {}
}
