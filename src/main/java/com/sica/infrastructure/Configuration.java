package com.sica.infrastructure;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Map;
import com.sica.domain.SicaException;

public record Configuration(String url, String user, String password, Clock clock) {
    public static Configuration environment() {
        Map<String, String> env = LocalEnvironment.load();
        String user = env.getOrDefault("SICA_DB_USER", "sica");
        String password = env.get("SICA_DB_PASSWORD");
        if (password == null || password.isBlank()) throw new SicaException("Configura SICA_DB_PASSWORD antes de iniciar.");
        return new Configuration(env.getOrDefault("SICA_DB_URL",
                "jdbc:mysql://127.0.0.1:" + env.getOrDefault("SICA_DB_PORT", "3306") + "/sica_acme?connectionTimeZone=America/Bogota&connectTimeout=5000&socketTimeout=15000"),
                user, password, Clock.system(ZoneId.of(env.getOrDefault("SICA_ZONE", "America/Bogota"))));
    }
}
