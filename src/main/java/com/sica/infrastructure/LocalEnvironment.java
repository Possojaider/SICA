package com.sica.infrastructure;

import com.sica.domain.SicaException;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class LocalEnvironment {
    private LocalEnvironment() {}

    public static Map<String, String> load() {
        Map<String, String> values = new HashMap<>();
        Path file = Path.of(System.getProperty("sica.env.file", ".env"));
        if (Files.exists(file)) {
            try {
                for (String line : Files.readAllLines(file)) {
                    String stripped = line.strip();
                    if (stripped.isEmpty() || stripped.startsWith("#")) continue;
                    int separator = line.indexOf('=');
                    if (separator <= 0) throw new SicaException("Revisa el formato CLAVE=valor del archivo de configuración.");
                    values.put(line.substring(0, separator).strip(), line.substring(separator + 1));
                }
            } catch (IOException failure) {
                throw new SicaException("No se pudo leer el archivo de configuración local.");
            }
        }
        values.putAll(System.getenv());
        return Map.copyOf(values);
    }
}
