package com.firstrep.common;

import com.firstrep.common.Kinds.LinkKind;
import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;
import java.util.Set;

public final class UrlSafety {
    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "localhost", "localhost.localdomain", "metadata.google.internal", "metadata.internal");

    private UrlSafety() {}

    public static Checked check(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > 1000) {
            throw AppException.bad("UNSAFE_URL", "Enter a public https URL.");
        }
        URI uri;
        try {
            uri = URI.create(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw AppException.bad("UNSAFE_URL", "That URL could not be read.");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
            throw AppException.bad("UNSAFE_URL", "Only public https URLs are accepted.");
        }
        if (uri.getPort() != -1 && uri.getPort() != 443) {
            throw AppException.bad("UNSAFE_URL", "Only the standard https port is accepted.");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (BLOCKED_HOSTS.contains(host) || host.endsWith(".local") || host.endsWith(".localhost") || host.endsWith(".internal")) {
            throw AppException.bad("UNSAFE_URL", "That host is not allowed.");
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                    throw AppException.bad("UNSAFE_URL", "Private or local addresses are not allowed.");
                }
            }
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            throw AppException.bad("UNSAFE_URL", "That host could not be resolved.");
        }
        String path = uri.getPath() == null ? "" : uri.getPath();
        boolean github = "github.com".equals(host);
        if (github) {
            String[] parts = path.split("/");
            if (parts.length < 3 || parts[1].isBlank() || parts[2].isBlank()) {
                throw AppException.bad("UNSAFE_URL", "Use a GitHub repository URL such as https://github.com/org/repo.");
            }
            return new Checked(LinkKind.GITHUB, uri.toString());
        }
        return new Checked(LinkKind.LIVE, uri.toString());
    }

    public record Checked(LinkKind kind, String url) {}
}
