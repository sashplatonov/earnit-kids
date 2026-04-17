package com.sashplatonov.earnit.kids.util;

import jakarta.enterprise.context.ApplicationScoped;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;

@ApplicationScoped
public class SecureTokenGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    public String generateHexToken(int byteCount) {
        var bytes = new byte[byteCount];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public String generateChildToken() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}