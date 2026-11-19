package com.sica.domain;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;
import java.util.HexFormat;

public final class CredentialCodes {
    private static final SecureRandom RANDOM = new SecureRandom();

    private CredentialCodes() {}

    public static String generate() {
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        return "SICA1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    public static String digest(String code) {
        if (code == null || !code.matches("SICA1:[A-Za-z0-9_-]{43}")) {
            throw new SicaException("El código QR no tiene un formato válido.");
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(code.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 no está disponible en este JDK.", failure);
        }
    }
}
