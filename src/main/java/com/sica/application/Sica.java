package com.sica.application;

import com.sica.domain.AccessPolicy;
import com.sica.domain.CredentialCodes;
import com.sica.domain.port.out.PasswordHasher;
import com.sica.domain.SicaException;
import com.sica.domain.Session;
import com.sica.domain.port.out.Store;
import com.sica.domain.port.out.Store.*;
import java.net.URI;
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
        return protectedOperation(session, "VISITAS_LEER", (tx, actor) ->
                tx.statistics("FUNCIONARIO".equals(actor.role()) ? actor.company() : null));
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
        String hash = password(password);
        store.transaction(tx -> {
            if (tx.byEmail(normalized) != null) throw new SicaException("El correo ya está registrado.");
            long id = tx.user(null, new UserForm(normalized, "Administrador SICA", hash, true, "SUPER", null));
            tx.audit(id, "CREAR", "usuarios", id, "Alta inicial desde la consola");
            return null;
        });
    }

    public long saveCompany(Session session, Long id, Company form) {
        return protectedOperation(session, "EMPRESAS", (tx, actor) -> {
            long saved = tx.company(id, new Company(required(form.taxId(), 32), required(form.name(), 150), form.active()));
            change(tx, actor, id, "empresas", saved, "Activo: " + form.active());
            return saved;
        });
    }

    public long savePerson(Session session, Long id, PersonForm form) {
        return protectedOperation(session, id == null ? "PERSONAS_CREAR" : "PERSONAS", (tx, actor) -> {
            if (!actor.permissions().contains("PERSONAS") && !"INVITADO".equals(form.type())) {
                throw new SicaException("Tu rol puede registrar invitados.");
            }
            if ("FUNCIONARIO".equals(actor.role())) {
                if (form.company() == null) throw new SicaException("Asocia el invitado a tu empresa.");
                scope(actor, form.company());
            }
            if (!Set.of("TRABAJADOR", "INVITADO").contains(Objects.toString(form.type(), "")) ||
                    !Set.of("HABILITADO", "RESTRINGIDO").contains(Objects.toString(form.state(), ""))) {
                throw new SicaException("Selecciona un tipo y un estado de acceso válidos.");
            }
            if (("TRABAJADOR".equals(form.type()) && form.company() == null) ||
                    (form.company() != null && !tx.activeCompany(form.company()))) {
                throw new SicaException("Selecciona una empresa activa.");
            }
            Person before = id == null ? null : tx.lockPerson(id);
            long saved = tx.person(id, new PersonForm(required(form.document(), 40), required(form.name(), 150),
                    form.type(), form.company(), photo(form.photo()), form.state()));
            change(tx, actor, id, "personas", saved, "Estado: " + form.state());
            if (before != null && !before.state().equals(form.state())) {
                tx.audit(actor.id(), "CAMBIAR_ESTADO", "personas", saved, before.state() + " → " + form.state());
            }
            return saved;
        });
    }

    public long saveUser(Session session, Long id, UserForm form) {
        return protectedOperation(session, "USUARIOS", (tx, actor) -> {
            if (!Set.of("SUPER", "SUPERVISOR", "GUARDA", "FUNCIONARIO").contains(Objects.toString(form.role(), ""))) {
                throw new SicaException("Selecciona un rol válido.");
            }
            if (("FUNCIONARIO".equals(form.role()) && form.company() == null) ||
                    (form.company() != null && !tx.activeCompany(form.company()))) {
                throw new SicaException("Selecciona una empresa activa.");
            }
            if (id != null && id == actor.id() && (!form.active() || !actor.role().equals(form.role()))) {
                throw new SicaException("Tu usuario debe conservar su rol y permanecer activo.");
            }
            String hash = id != null && (form.hash() == null || form.hash().isEmpty()) ? null : password(form.hash());
            long saved = tx.user(id, new UserForm(email(form.email()), required(form.name(), 150), hash,
                    form.active(), form.role(), form.company()));
            change(tx, actor, id, "usuarios", saved, "Rol: " + form.role() + ", activo: " + form.active());
            return saved;
        });
    }

    public void archive(Session session, Entity entity, long id) {
        protectedOperation(session, entity.name(), (tx, actor) -> {
            if (entity == Entity.USUARIOS && id == actor.id()) throw new SicaException("Tu usuario debe permanecer activo.");
            tx.archive(entity, id);
            tx.audit(actor.id(), "ELIMINAR", entity.name().toLowerCase(Locale.ROOT), id, "Baja lógica");
            return null;
        });
    }

    public long request(Session session, long person, long company, AccessPolicy type,
                        LocalDateTime start, LocalDateTime end) {
        return protectedOperation(session, type == AccessPolicy.PREVIA ? "PRE_REGISTRAR" : "SOLICITAR", (tx, actor) -> {
            if (type == null || start == null || end == null) throw new SicaException("Selecciona el escenario y el horario de visita.");
            scope(actor, company);
            Person subject = tx.lockPerson(person);
            eligible(tx, subject, company);
            LocalDateTime now = LocalDateTime.now(clock);
            type.validate(subject.type(), subject.company(), company, start, end, now);
            String state = type == AccessPolicy.PREVIA ? "APROBADA" : "PENDIENTE";
            long id = tx.createVisit(person, company, type, state, start, end, actor.id(), now);
            tx.audit(actor.id(), "CREAR", "visitas", id, type + " / " + state);
            if (type == AccessPolicy.PREVIA) {
                tx.audit(actor.id(), "APROBAR", "visitas", id, "Registro anticipado autorizado");
            } else {
                tx.notifyRequest(id, company, now);
            }
            return id;
        });
    }

    public void decide(Session session, long id, boolean approve, String reason) {
        protectedOperation(session, "DECIDIR", (tx, actor) -> {
            String explanation = required(reason, 1000);
            Visit visit = load(tx, id, true);
            scope(actor, visit.company());
            if (!"PENDIENTE".equals(visit.state())) throw new SicaException("La solicitud ya fue resuelta.");
            LocalDateTime now = LocalDateTime.now(clock);
            if (!now.isBefore(visit.expires())) throw new SicaException("La solicitud expiró.");
            String state = approve ? "APROBADA" : "RECHAZADA";
            tx.decide(id, state, actor.id(), now, explanation);
            tx.audit(actor.id(), approve ? "APROBAR" : "RECHAZAR", "visitas", id, explanation);
            tx.notifyDecision(id, state, now);
            return null;
        });
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
        return protectedOperation(session, "QR_EMITIR", (tx, actor) -> {
            Visit visit = load(tx, id, true);
            scope(actor, visit.company());
            if (!"APROBADA".equals(visit.state()) || !currentTime().isBefore(visit.expires())) {
                throw new SicaException("Solo se puede emitir un QR para una visita aprobada y vigente.");
            }
            String code = CredentialCodes.generate();
            tx.setCredential(id, CredentialCodes.digest(code));
            tx.audit(actor.id(), "EMITIR_QR", "visitas", id, "Credencial temporal emitida o renovada");
            return new Credential(id, code, visit.expires());
        });
    }

    public void checkIn(Session session, long id) {
        protectedOperation(session, "ACCESO", (tx, actor) -> {
            registerEntry(tx, actor, load(tx, id, false), null);
            return null;
        });
    }

    public void checkInWithCredential(Session session, String code) {
        String hash = CredentialCodes.digest(code);
        protectedOperation(session, "ACCESO", (tx, actor) -> {
            Visit visit = tx.visitByCredential(hash);
            if (visit == null) throw new SicaException("El QR no está disponible. Puede haber sido utilizado o renovado.");
            registerEntry(tx, actor, visit, hash);
            return null;
        });
    }

    private void registerEntry(Unit tx, Account actor, Visit initial, String expectedHash) {
        Person person = tx.lockPerson(initial.person());
        Visit visit = load(tx, initial.id(), true);
        scope(actor, visit.company());
        if (expectedHash != null && !expectedHash.equals(tx.credentialHash(visit.id()))) {
            throw new SicaException("El QR fue utilizado o renovado. Consulta la visita.");
        }
        eligible(tx, person, visit.company());
        LocalDateTime now = currentTime();
        AccessPolicy.authorize(visit.state(), visit.start(), visit.expires(), now);
        Visit previous = tx.inside(visit.person());
        if (previous != null) {
            tx.exit(previous.id(), "SALIDA_OLVIDADA", now);
            tx.audit(actor.id(), "SALIDA_OLVIDADA", "visitas", previous.id(),
                    "Regularizada al ingresar en visita " + visit.id());
        }
        tx.enter(visit.id(), now);
        tx.audit(actor.id(), "CHECK_IN", "visitas", visit.id(),
                expectedHash == null ? "Ingreso registrado" : "Ingreso mediante credencial QR");
    }

    public void checkOut(Session session, long id) {
        protectedOperation(session, "ACCESO", (tx, actor) -> {
            Visit visit = load(tx, id, true);
            scope(actor, visit.company());
            if (!"DENTRO".equals(visit.state())) throw new SicaException("La visita no tiene un ingreso abierto.");
            tx.exit(id, "CERRADA", LocalDateTime.now(clock));
            tx.audit(actor.id(), "CHECK_OUT", "visitas", id, "Salida registrada");
            return null;
        });
    }

    public long incident(Session session, long visitId, String description) {
        return protectedOperation(session, "INCIDENTES", (tx, actor) -> {
            Visit visit = load(tx, visitId, false);
            scope(actor, visit.company());
            long id = tx.incident(visitId, actor.id(), required(description, 2000), LocalDateTime.now(clock));
            tx.audit(actor.id(), "INCIDENTE", "incidentes", id, "Asociado a visita " + visitId);
            return id;
        });
    }

    public Data report(Session session, Report report, String search, LocalDateTime from, LocalDateTime until) {
        String permission = switch (report) {
            case NOTIFICACIONES -> null;
            case EMPRESAS, USUARIOS, INCIDENTES, AUDITORIA -> report.name();
            case PERSONAS -> "PERSONAS_LEER";
            case VISITAS, SOLICITUDES -> "VISITAS_LEER";
            default -> "REPORTES";
        };
        if (from != null && until != null && !until.isAfter(from)) throw new SicaException("El período del filtro no es válido.");
        return protectedOperation(session, permission, (tx, actor) -> {
            if (report == Report.NOTIFICACIONES) return tx.notifications(actor.id(), Objects.toString(search, ""), from, until);
            return tx.report(report, "FUNCIONARIO".equals(actor.role()) ? actor.company() : null,
                        Objects.toString(search, "").strip(), from, until);
        });
    }

    private <T> T protectedOperation(Session session, String permission, BiFunction<Unit, Account, T> action) {
        if (session == null || session.token() == null ||
                !Long.valueOf(session.userId()).equals(activeSessions.get(session.token()))) {
            throw new SicaException("Inicia sesión para continuar.");
        }
        return store.transaction(tx -> {
            Account actor = tx.account(session.userId());
            if (actor == null || !actor.active()) throw new SicaException("La sesión ya no está autorizada.");
            if (permission != null && !actor.permissions().contains(permission)) {
                throw new SicaException("Tu rol no tiene permiso para esta operación.");
            }
            return action.apply(tx, actor);
        });
    }

    private static Visit load(Unit tx, long id, boolean lock) {
        Visit visit = tx.visit(id, lock);
        if (visit == null) throw new SicaException("La visita no existe.");
        return visit;
    }

    private static void scope(Account actor, long company) {
        if ("FUNCIONARIO".equals(actor.role()) && !Long.valueOf(company).equals(actor.company())) {
            throw new SicaException("La visita pertenece a otra empresa.");
        }
    }

    private static void eligible(Unit tx, Person person, long company) {
        if (person == null) throw new SicaException("La persona no existe.");
        if (!"HABILITADO".equals(person.state())) throw new SicaException("La persona tiene restringido el acceso.");
        if (!tx.activeCompany(company) || (person.company() != null && !tx.activeCompany(person.company()))) {
            throw new SicaException("La empresa debe estar activa.");
        }
    }

    private static void change(Unit tx, Account actor, Long id, String table, long saved, String details) {
        tx.audit(actor.id(), id == null ? "CREAR" : "ACTUALIZAR", table, saved, details);
    }

    private String password(String value) {
        if (value == null || value.length() < 12 || value.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new SicaException("Usa al menos 12 caracteres y como máximo 72 bytes para la contraseña.");
        }
        return passwords.hash(value);
    }

    private static String email(String value) {
        String normalized = required(value, 254).toLowerCase(Locale.ROOT);
        if (!normalized.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) throw new SicaException("Introduce un correo válido.");
        return normalized;
    }

    private static String photo(String value) {
        if (value == null || value.isBlank()) return null;
        String url = required(value, 2048);
        try {
            URI uri = URI.create(url);
            if (uri.getHost() != null && uri.getUserInfo() == null && "https".equalsIgnoreCase(uri.getScheme())) return url;
        } catch (IllegalArgumentException invalidUrl) {
            throw new SicaException("Introduce una URL HTTPS válida para la foto.");
        }
        throw new SicaException("Introduce una URL HTTPS válida para la foto.");
    }

    private static String required(String value, int limit) {
        if (value == null || value.isBlank() || value.strip().length() > limit) throw new SicaException("Revisa los campos obligatorios y sus longitudes.");
        return value.strip();
    }
}
