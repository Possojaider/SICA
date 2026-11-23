package com.sica.application;

import com.sica.domain.AccessPolicy;
import com.sica.domain.Permissions;
import com.sica.domain.CredentialCodes;
import com.sica.domain.port.out.PasswordHasher;
import com.sica.domain.SicaException;
import com.sica.domain.Session;
import com.sica.domain.port.out.Store;
import com.sica.domain.port.out.Store.*;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.Set;
import java.util.function.BiFunction;
import static com.sica.application.InputValidation.*;

public final class Sica implements com.sica.domain.port.in.SicaUseCases {
    private final Store store;
    private final PasswordHasher passwords;
    private final Clock clock;
    private final String dummyHash;
    private final ConcurrentMap<UUID, Long> activeSessions = new ConcurrentHashMap<>();

    public Sica(Store store, PasswordHasher passwords, Clock clock) {
        this.store = store;
        this.passwords = passwords;
        this.clock = clock;
        dummyHash = passwords.hash("Sica-verificación-interna");
    }

    public LocalDateTime currentTime() {
        return LocalDateTime.now(clock);
    }

    public Session login(String email, String password) {
        Account account = store.transaction(tx -> {
            Account found = tx.byEmail(Objects.toString(email, "").strip().toLowerCase(Locale.ROOT));
            String secret = Objects.toString(password, "");
            boolean correct = secret.getBytes(StandardCharsets.UTF_8).length <= 72 &&
                    passwords.matches(secret, found == null ? dummyHash : found.hash());
            boolean allowed = found != null && found.active() && correct;
            tx.audit(found == null ? null : found.id(), allowed ? "LOGIN_OK" : "LOGIN_FALLIDO",
                    "usuarios", found == null ? null : found.id(), allowed ? "Sesión iniciada" : "Acceso denegado");
            return allowed ? found : null;
        });
        if (account == null) throw new SicaException("Credenciales incorrectas o usuario inactivo.");
        Session session = new Session(UUID.randomUUID(), account.id());
        activeSessions.put(session.token(), session.userId());
        return session;
    }

    public List<Option> companies(Session session) {
        return protectedOperation(session, null, (tx, actor) ->
                tx.companies("FUNCIONARIO".equals(actor.role()) ? actor.company() : null));
    }

    public Statistics statistics(Session session) {
        return protectedOperation(session, "VISITAS_LEER", (tx, actor) -> {
            expire(tx, actor);
            return tx.statistics("FUNCIONARIO".equals(actor.role()) ? actor.company() : null);
        });
    }

    public void logout(Session session) {
        if (session == null || session.token() == null ||
                !Long.valueOf(session.userId()).equals(activeSessions.remove(session.token()))) {
            throw new SicaException("La sesión ya está cerrada.");
        }
        try {
            store.transaction(tx -> {
                tx.audit(session.userId(), "LOGOUT", "usuarios", session.userId(), "Sesión finalizada");
                return null;
            });
        } catch (RuntimeException failure) {
            org.slf4j.LoggerFactory.getLogger(Sica.class).warn(
                    "La sesión se cerró localmente y no se pudo registrar LOGOUT.", failure);
        }
    }

    public Set<String> permissions(Session session) {
        return protectedOperation(session, null, (tx, actor) -> actor.permissions());
    }

    public void bootstrap(String email, String password) {
        String normalized = email(email);
        String hash = InputValidation.password(password, passwords);
        store.transaction(tx -> {
            if (tx.byEmail(normalized) != null) throw new SicaException("El correo ya está registrado.");
            long id = tx.user(null, new UserForm(normalized, "Administrador SICA", hash, true, "SUPER", null));
            tx.audit(id, "CREAR", "usuarios", id, "Alta inicial desde la consola");
            return null;
        });
    }

    public long saveCompany(Session session, Long id, Company form) {
        return protectedOperation(session, id == null ? Permissions.create(Entity.EMPRESAS) : Permissions.edit(Entity.EMPRESAS), (tx, actor) -> new DirectoryService(tx, tx, tx, passwords).saveCompany(actor, id, form));
    }

    public long savePerson(Session session, Long id, PersonForm form) {
        return protectedOperation(session, id == null ? "PERSONAS_CREAR" : Permissions.edit(Entity.PERSONAS), (tx, actor) -> new DirectoryService(tx, tx, tx, passwords).savePerson(actor, id, form));
    }

    public long saveUser(Session session, Long id, UserForm form) {
        return protectedOperation(session, id == null ? Permissions.create(Entity.USUARIOS) : Permissions.edit(Entity.USUARIOS), (tx, actor) -> new DirectoryService(tx, tx, tx, passwords).saveUser(actor, id, form));
    }

    public void archive(Session session, Entity entity, long id) {
        protectedOperation(session, Permissions.archive(entity), (tx, actor) -> { new DirectoryService(tx, tx, tx, passwords).archive(actor, entity, id); return null; });
    }

    public long request(Session session, long person, long company, AccessPolicy type,
                        LocalDateTime start, LocalDateTime end) {
        return request(session, person, company, type, start, end, null);
    }

    public long request(Session session, long person, long company, AccessPolicy type,
                        LocalDateTime start, LocalDateTime end, String plate) {
        return protectedOperation(session, type == AccessPolicy.PREVIA ? "PRE_REGISTRAR" : "SOLICITAR", (tx, actor) -> new VisitService(tx, tx, tx, tx, clock).request(actor, person, company, type, start, end, plate));
    }

    public void decide(Session session, long id, boolean approve, String reason) {
        protectedOperation(session, "DECIDIR", (tx, actor) -> { new VisitService(tx, tx, tx, tx, clock).decide(actor, id, approve, reason); return null; });
    }

    public long unreadNotifications(Session session) {
        return protectedOperation(session, null, (tx, actor) -> tx.unreadNotifications(actor.id()));
    }

    public void markNotificationRead(Session session, long id) {
        protectedOperation(session, null, (tx, actor) -> {
            if (tx.markNotificationRead(actor.id(), id, currentTime())) {
                tx.audit(actor.id(), "LEER_NOTIFICACION", "notificaciones", id, "Notificación leída");
            }
            return null;
        });
    }

    public Credential issueCredential(Session session, long id) {
        return protectedOperation(session, "QR_EMITIR", (tx, actor) -> new VisitService(tx, tx, tx, tx, clock).issueCredential(actor, id));
    }

    public void checkIn(Session session, long id) {
        protectedOperation(session, "ACCESO", (tx, actor) -> { new VisitService(tx, tx, tx, tx, clock).checkIn(actor, id); return null; });
    }

    public void checkInWithCredential(Session session, String code) {
        protectedOperation(session, "ACCESO", (tx, actor) -> { new VisitService(tx, tx, tx, tx, clock).checkInWithCredential(actor, code); return null; });
    }


    public void checkOut(Session session, long id) {
        protectedOperation(session, "ACCESO", (tx, actor) -> { new VisitService(tx, tx, tx, tx, clock).checkOut(actor, id); return null; });
    }

    public long incident(Session session, long visitId, String description) {
        return protectedOperation(session, "INCIDENTES", (tx, actor) -> new VisitService(tx, tx, tx, tx, clock).incident(actor, visitId, description));
    }

    public Data report(Session session, Report report, String search, LocalDateTime from, LocalDateTime until) {
        String permission = Permissions.read(report);
        if (from != null && until != null && !until.isAfter(from)) throw new SicaException("El período del filtro no es válido.");
        return protectedOperation(session, permission, (tx, actor) -> {
            expire(tx, actor);
            if (report == Report.NOTIFICACIONES) return tx.notifications(actor.id(), Objects.toString(search, ""), from, until);
            return tx.report(report, "FUNCIONARIO".equals(actor.role()) ? actor.company() : null,
                        Objects.toString(search, "").strip(), from, until);
        });
    }

    private void expire(Unit tx, Account actor) {
        Long company = "FUNCIONARIO".equals(actor.role()) ? actor.company() : null;
        for (long id : tx.expireDue(currentTime(), company)) {
            tx.audit(null, "EXPIRAR", "visitas", id, "Autorización vencida. QR invalidado; historial conservado.");
        }
    }

    private <T> T protectedOperation(Session session, String permission, BiFunction<Unit, Account, T> action) {
        if (session == null || session.token() == null ||
                !Long.valueOf(session.userId()).equals(activeSessions.get(session.token()))) {
            throw new SicaException("Inicia sesión para continuar.");
        }
        return store.transaction(tx -> {
            Account actor = tx.account(session.userId());
            if (actor == null || !actor.active()) throw new SicaException("La sesión ya no está autorizada.");
            if ("FUNCIONARIO".equals(actor.role()) && actor.company() == null) {
                throw new SicaException("Asigna una empresa al funcionario antes de operar.");
            }
            if (permission != null && !actor.permissions().contains(permission)) {
                throw new SicaException("Tu rol no tiene permiso para esta operación.");
            }
            return action.apply(tx, actor);
        });
    }










}
