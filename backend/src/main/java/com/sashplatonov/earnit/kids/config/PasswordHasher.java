package com.sashplatonov.earnit.kids.config;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import jakarta.enterprise.context.ApplicationScoped;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@ApplicationScoped
public class PasswordHasher {
    private final Argon2 argon2 = Argon2Factory.create();

    public String hash(String password) {
        return argon2.hash(3, 65536, 1, password.toCharArray());
    }

    public boolean verify(String hash, String password) {
        return argon2.verify(hash, password.toCharArray());
    }

    public boolean isArgon2Hash(String value) {
        return value != null && value.startsWith("$argon2");
    }

    public boolean isSha256Hex(String value) {
        return value != null
            && value.length() == 64
            && value.matches("[0-9a-fA-F]{64}");
    }

    public boolean needsArgon2Migration(String value) {
        return value != null && !isArgon2Hash(value) && !isSha256Hex(value);
    }

    public boolean verifyLegacy(String suppliedPassword, String storedPassword) {
        if (storedPassword == null) {
            return false;
        }
        if (storedPassword.equals(suppliedPassword)) {
            return true;
        }
        if (!isSha256Hex(storedPassword)) {
            return false;
        }
        return sha256Hex(suppliedPassword).equalsIgnoreCase(storedPassword);
    }

    private String sha256Hex(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
