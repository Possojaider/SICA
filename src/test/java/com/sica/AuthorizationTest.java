package com.sica;

import com.sica.domain.Session;

import com.sica.application.*;
import com.sica.domain.*;
import com.sica.domain.port.out.*;
import com.sica.domain.port.out.Store.*;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;

class AuthorizationTest {
    private Account actor;
    private final List<String> audit = new ArrayList<>();
    private final PasswordHasher hasher = new PasswordHasher() {
        public boolean matches(String password, String hash) { return hash.equals("test:" + password); }
        public String hash(String password) { return "test:" + password; }
    };
    // El puerto de prueba solo acepta los métodos usados en estos escenarios.
    private final Store store = new Store() {
        public <T> T transaction(Function<Unit, T> operation) {
            int previous = audit.size();
            Unit unit = (Unit) java.lang.reflect.Proxy.newProxyInstance(Unit.class.getClassLoader(), new Class<?>[] {Unit.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "account" -> actor;
                        case "byEmail" -> "user@acme.co".equals(arguments[0]) ? actor : null;
                        case "audit" -> { audit.add((String) arguments[1]); yield null; }
                        default -> throw new AssertionError("El permiso debe validarse antes de acceder a " + method.getName());
                    });
            try {
                return operation.apply(unit);
            } catch (RuntimeException failure) {
                audit.subList(previous, audit.size()).clear();
                throw failure;
            }
        }
    };
    private final Sica sica = new Sica(store, hasher, Clock.fixed(Instant.parse("2026-11-18T15:00:00Z"), ZoneId.of("America/Bogota")));
    private Session session;

    @BeforeEach void prepare() {
        actor = new Account(1, "test:correct-password", true, "GUARDA", null, Set.of());
        session = sica.login("user@acme.co", "correct-password");
        audit.clear();
    }

    @Test void authenticationAuditsBothKnownAndUnknownFailures() {
        assertEquals(session.userId(), sica.login("USER@ACME.CO", "correct-password").userId());
        assertThrows(SicaException.class, () -> sica.login("user@acme.co", "wrong"));
        assertThrows(SicaException.class, () -> sica.login("unknown@acme.co", "wrong"));
        assertEquals(List.of("LOGIN_OK", "LOGIN_FALLIDO", "LOGIN_FALLIDO"), audit);
    }

    @Test void inactiveAccountCannotLoginOrUseAnExistingSession() {
        actor = new Account(1, actor.hash(), false, "GUARDA", null, Set.of("ACCESO"));
        assertThrows(SicaException.class, () -> sica.login("user@acme.co", "correct-password"));
        assertThrows(SicaException.class, () -> sica.checkIn(session, 1));
    }

    @Test void allProtectedOperationsRejectMissingPermissionBeforePersistence() {
        List<org.junit.jupiter.api.function.Executable> operations = List.of(
                () -> sica.saveCompany(session, null, new Company("1", "Acme", true)),
                () -> sica.savePerson(session, null, new PersonForm("1", "Persona", "INVITADO", null, "", "HABILITADO")),
                () -> sica.saveUser(session, null, new UserForm("a@b.co", "Nombre", "password12345", true, "GUARDA", null)),
                () -> sica.request(session, 1, 1, AccessPolicy.PREVIA, sica.currentTime(), sica.currentTime().plusHours(1)),
                () -> sica.request(session, 1, 1, AccessPolicy.NO_ANUNCIADA, sica.currentTime(), sica.currentTime().plusHours(1)),
                () -> sica.decide(session, 1, true, "Identidad y motivo de visita verificados"), () -> sica.decide(session, 1, false, "Identidad y motivo de visita verificados"),
                () -> sica.checkIn(session, 1), () -> sica.checkOut(session, 1),
                () -> sica.issueCredential(session, 1),
                () -> sica.checkInWithCredential(session, CredentialCodes.generate()),
                () -> sica.incident(session, 1, "Descripción"), () -> sica.archive(session, Entity.PERSONAS, 1),
                () -> sica.report(session, Report.AUDITORIA, "", null, null),
                () -> sica.report(session, Report.DENTRO, "", null, null));
        for (var operation : operations) assertThrows(SicaException.class, operation);
        assertTrue(audit.isEmpty());
    }

    @Test void revokedPermissionIsReadForEachOperationAndNoSessionIsRejected() {
        assertEquals(Set.of(), sica.permissions(session));
        actor = new Account(1, actor.hash(), true, "GUARDA", null, Set.of("ACCESO"));
        assertEquals(Set.of("ACCESO"), sica.permissions(session));
        actor = new Account(1, actor.hash(), true, "GUARDA", null, Set.of());
        assertThrows(SicaException.class, () -> sica.checkIn(session, 1));
        assertThrows(SicaException.class, () -> sica.permissions(null));
    }

    @Test void logoutRevokesTheSessionAndForgedSessionsAreRejected() {
        sica.logout(session);
        assertThrows(SicaException.class, () -> sica.permissions(session));
        assertThrows(SicaException.class, () -> sica.permissions(new Session(UUID.randomUUID(), 1)));
        assertEquals(List.of("LOGOUT"), audit);
    }

    @Test void inactiveUsersCanStillCloseTheirSessions() {
        actor = new Account(1, actor.hash(), false, "GUARDA", null, Set.of());
        assertDoesNotThrow(() -> sica.logout(session));
        assertThrows(SicaException.class, () -> sica.permissions(session));
    }

    @Test void passwordLengthAndEmailAreValidatedBeforeUserPersistence() {
        actor = new Account(1, actor.hash(), true, "SUPER", null, Set.of("USUARIOS"));
        assertThrows(SicaException.class, () -> sica.saveUser(session, null,
                new UserForm("bad-email", "Usuario", "long-password123", true, "GUARDA", null)));
        assertThrows(SicaException.class, () -> sica.saveUser(session, null,
                new UserForm("a@b.co", "Usuario", "short", true, "GUARDA", null)));
    }
}
