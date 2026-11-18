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
    record Company(String taxId, String name, boolean active) {}
    record PersonForm(String document, String name, String type, Long company, String photo, String state) {}
    record UserForm(String email, String name, String hash, boolean active, String role, Long company) {}
    enum Entity { EMPRESAS, PERSONAS, USUARIOS }
    enum Report { EMPRESAS, PERSONAS, USUARIOS, VISITAS, SOLICITUDES, DENTRO, HISTORIAL, DECISIONES, INCIDENTES, AUDITORIA }
    record Data(List<String> columns, List<List<String>> rows) {}
    record Option(long id, String label) {
        @Override public String toString() { return label + " · " + id; }
    }
    record Statistics(long inside, long pending, long approved, long incidents) {}

    <T> T transaction(Function<Unit, T> operation);

    interface Unit {
        Account byEmail(String email);
        List<Option> companies(Long company);
        Statistics statistics(Long company);
        Account account(long id);
        boolean activeCompany(long id);
        Person lockPerson(long id);
        Visit visit(long id, boolean lock);
        Visit inside(long person);
        long createVisit(long person, long company, AccessPolicy type, String state, LocalDateTime start,
                         LocalDateTime end, long actor, LocalDateTime now);
        void decide(long visit, String state, long actor, LocalDateTime now);
        void enter(long visit, LocalDateTime now);
        void exit(long visit, String state, LocalDateTime now);
        long company(Long id, Company form);
        long person(Long id, PersonForm form);
        long user(Long id, UserForm form);
        void archive(Entity entity, long id);
        long incident(long visit, long reporter, String description, LocalDateTime now);
        Data report(Report report, Long company, String search, LocalDateTime from, LocalDateTime until);
        void audit(Long actor, String action, String table, Long entity, String details);
    }
}
