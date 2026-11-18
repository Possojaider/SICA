package com.sica.domain.port.out;

public interface PasswordHasher {
    boolean matches(String password, String hash);
    String hash(String password);
}
