package com.sica.infrastructure;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.sica.domain.port.out.PasswordHasher;

public final class BcryptHasher implements PasswordHasher {
    @Override
    public boolean matches(String password, String hash) {
        return BCrypt.verifyer().verify(password.toCharArray(), hash).verified;
    }

    @Override
    public String hash(String password) {
        return BCrypt.withDefaults().hashToString(12, password.toCharArray());
    }
}
