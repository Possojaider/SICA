package com.sica.application;
import com.sica.domain.SicaException;
import com.sica.domain.port.out.PasswordHasher;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
final class InputValidation {
    private InputValidation() {}
    static String password(String value, PasswordHasher passwords) {
        if (value == null || value.length() < 12 || value.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new SicaException("Usa al menos 12 caracteres y como máximo 72 bytes para la contraseña.");
        }
        return passwords.hash(value);
    }

    static String email(String value) {
        String normalized = required(value, 100).toLowerCase(Locale.ROOT);
        if (!normalized.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) throw new SicaException("Introduce un correo válido.");
        return normalized;
    }

    static String photo(String value) {
        if (value == null || value.isBlank()) return null;
        String url = required(value, 255);
        try {
            URI uri = URI.create(url);
            if (uri.getHost() != null && uri.getUserInfo() == null && "https".equalsIgnoreCase(uri.getScheme())) return url;
        } catch (IllegalArgumentException invalidUrl) {
            throw new SicaException("Introduce una URL HTTPS válida para la foto.");
        }
        throw new SicaException("Introduce una URL HTTPS válida para la foto.");
    }

    static String optional(String value, int limit) {
        return value == null || value.isBlank() ? null : required(value, limit);
    }

    static String required(String value, int limit) {
        if (value == null || value.isBlank() || value.strip().length() > limit) throw new SicaException("Revisa los campos obligatorios y sus longitudes.");
        return value.strip();
    }
}
