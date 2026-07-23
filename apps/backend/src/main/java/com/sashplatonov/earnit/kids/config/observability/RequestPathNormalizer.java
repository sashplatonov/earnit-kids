package com.sashplatonov.earnit.kids.config.observability;

public final class RequestPathNormalizer {
    private RequestPathNormalizer() {
    }

    public static String normalize(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }

        int contentStart = 0;
        while (contentStart < path.length() && path.charAt(contentStart) == '/') {
            contentStart++;
        }
        return contentStart == path.length() ? "/" : "/" + path.substring(contentStart);
    }
}
