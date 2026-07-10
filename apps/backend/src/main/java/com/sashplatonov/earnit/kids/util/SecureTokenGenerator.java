package com.sashplatonov.earnit.kids.util;

import jakarta.enterprise.context.ApplicationScoped;

import java.security.SecureRandom;
import java.util.HexFormat;

@ApplicationScoped
public class SecureTokenGenerator {
    private final SecureRandom random = new SecureRandom();

    public String generateHexToken(int byteCount) {
        var bytes = new byte[byteCount];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public String generateChildToken() {
        return generateHexToken(8);
    }
}
