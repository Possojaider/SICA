package com.sica.domain.port.out;

import com.sica.domain.AccessPolicy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public interface Store {
    record Account(long id, String hash, boolean active, String role, Long company, Set<String> permissions) {}
    record Person(String type, Long company, String state) {}
    record Visit(long id, long person, long company, String state, LocalDateTime start, LocalDateTime expires,
                 LocalDateTime entry) {}
    record Company(String taxId, String name, boolean active, String contact) {
        public Company(String taxId, String name, boolean active) { this(taxId, name, active, null); }
    }
    record PersonForm(String document, String name, String type, Long company, String photo, String state) {}
    record UserForm(String email, String name, String hash, boolean active, String role, Long company) {}
    enum Entity { EMPRESAS, PERSONAS, USUARIOS }
    enum Report { NOTIFICACIONES, EMPRESAS, PERSONAS, USUARIOS, VISITAS, SOLICITUDES, DENTRO, HISTORIAL, DECISIONES, INCIDENTES, AUDITORIA }
    record Data(List<String> columns, List<List<String>> rows) {}
    record Option(long id, String label) {
        @Override public String toString() { return label + " · " + id; }
    }
    record Credential(long visit, String code, LocalDateTime expires) {}
    record Statistics(long inside, long pending, long approved, long incidents) {}

    <T> T transaction(Function<Unit, T> operation);

    /** Capacidades del mismo contexto transaccional; los servicios usan puertos específicos. */
    interface Unit extends AuthenticationRepository, DirectoryRepository, VisitRepository, NotificationRepository, ReportingRepository, AuditRepository {}
}
