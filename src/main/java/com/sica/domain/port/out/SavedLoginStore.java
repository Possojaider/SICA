package com.sica.domain.port.out;

import java.util.Optional;

public interface SavedLoginStore {
    record Login(String email, String password) {
        @Override
        public String toString() {
            return "SavedLogin[credenciales protegidas]";
        }
    }

    boolean available();
    Optional<Login> load();
    void save(Login login);
    void clear();

    static SavedLoginStore unavailable() {
        return new SavedLoginStore() {
            public boolean available() { return false; }
            public Optional<Login> load() { return Optional.empty(); }
            public void save(Login login) { throw new IllegalStateException("Almacenamiento seguro no disponible."); }
            public void clear() { }
        };
    }
}
