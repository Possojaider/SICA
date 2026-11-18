package com.sica.infrastructure;

import com.sica.application.Sica;

public final class ApplicationFactory {
    private ApplicationFactory() {}

    public static Sica create(Configuration configuration) {
        return new Sica(new JdbcStore(configuration.url(), configuration.user(), configuration.password()),
                new BcryptHasher(), configuration.clock());
    }
}
