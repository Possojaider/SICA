package com.sica.application;
import com.sica.domain.*;
import com.sica.domain.port.out.*;
import com.sica.domain.port.out.Store.*;
import java.util.*;
import java.time.*;
import static com.sica.application.InputValidation.*;
import static com.sica.domain.CompanyScope.*;
final class VisitService {
    private final VisitRepository tx;
    private final DirectoryRepository directory;
    private final NotificationRepository notices;
    private final AuditRepository audit;
    private final Clock clock;
    VisitService(VisitRepository visits, DirectoryRepository directory, NotificationRepository notices, AuditRepository audit, Clock clock) {
        this.tx=visits; this.directory=directory; this.notices=notices; this.audit=audit; this.clock=clock;
    }
    private LocalDateTime currentTime() { return LocalDateTime.now(clock); }
    public long request(Account actor, long person, long company, AccessPolicy type,
                    LocalDateTime start, LocalDateTime end, String plate) {
        if (type == null || start == null || end == null) throw new SicaException("Selecciona el escenario y el horario de visita.");
        scope(actor, company);
        Person subject = directory.lockPerson(person);
        eligible(subject, company);
        LocalDateTime now = LocalDateTime.now(clock);
        type.validate(subject.type(), subject.company(), company, start, end, now);
        String state = type == AccessPolicy.PREVIA ? "APROBADA" : "PENDIENTE";
        long id = tx.createVisit(person, company, type, state, start, end, actor.id(), now, optional(plate, 10));
        audit.audit(actor.id(), "CREAR", "visitas", id, type + " / " + state);
        if (type == AccessPolicy.PREVIA) {
            audit.audit(actor.id(), "APROBAR", "visitas", id, "Registro anticipado autorizado");
        } else {
            notices.notifyRequest(id, company, now);
        }
        return id;
    }

    public void decide(Account actor, long id, boolean approve, String reason) {
        String explanation = required(reason, 1000);
        Visit visit = load(id, true);
        scope(actor, visit.company());
        if (!"PENDIENTE".equals(visit.state())) throw new SicaException("La solicitud ya fue resuelta.");
        LocalDateTime now = LocalDateTime.now(clock);
        if (!now.isBefore(visit.expires())) throw new SicaException("La solicitud expiró.");
        String state = approve ? "APROBADA" : "RECHAZADA";
        tx.decide(id, state, actor.id(), now, explanation);
        audit.audit(actor.id(), approve ? "APROBAR" : "RECHAZAR", "visitas", id, explanation);
        notices.notifyDecision(id, state, now);
    }

    public Credential issueCredential(Account actor, long id) {
        Visit visit = load(id, true);
        scope(actor, visit.company());
        if (!"APROBADA".equals(visit.state()) || !currentTime().isBefore(visit.expires())) {
            throw new SicaException("Solo se puede emitir un QR para una visita aprobada y vigente.");
        }
        String code = CredentialCodes.generate();
        tx.setCredential(id, CredentialCodes.digest(code));
        audit.audit(actor.id(), "EMITIR_QR", "visitas", id, "Credencial temporal emitida o renovada");
        return new Credential(id, code, visit.expires());
    }

    public void checkIn(Account actor, long id) {
        registerEntry(actor, load(id, false), null);
    }

    public void checkInWithCredential(Account actor, String code) {
        String hash = CredentialCodes.digest(code);
        Visit visit = tx.visitByCredential(hash);
        if (visit == null) throw new SicaException("El QR no está disponible. Puede haber sido utilizado o renovado.");
        registerEntry(actor, visit, hash);
    }

    public void checkOut(Account actor, long id) {
        Visit visit = load(id, true);
        scope(actor, visit.company());
        if (!"DENTRO".equals(visit.state())) throw new SicaException("La visita no tiene un ingreso abierto.");
        tx.exit(id, "CERRADA", LocalDateTime.now(clock));
        audit.audit(actor.id(), "CHECK_OUT", "visitas", id, "Salida registrada");
    }

    public long incident(Account actor, long visitId, String description) {
        Visit visit = load(visitId, false);
        scope(actor, visit.company());
        long id = tx.incident(visitId, actor.id(), required(description, 2000), LocalDateTime.now(clock));
        audit.audit(actor.id(), "INCIDENTE", "incidentes", id, "Asociado a visita " + visitId);
        return id;
    }

    private void registerEntry(Account actor, Visit initial, String expectedHash) {
        Person person = directory.lockPerson(initial.person());
        Visit visit = load(initial.id(), true);
        scope(actor, visit.company());
        if (expectedHash != null && !expectedHash.equals(tx.credentialHash(visit.id()))) {
            throw new SicaException("El QR fue utilizado o renovado. Consulta la visita.");
        }
        eligible(person, visit.company());
        LocalDateTime now = currentTime();
        AccessPolicy.authorize(visit.state(), visit.start(), visit.expires(), now);
        Visit previous = tx.inside(visit.person());
        if (previous != null) {
            tx.exit(previous.id(), "SALIDA_OLVIDADA", now);
            audit.audit(actor.id(), "SALIDA_OLVIDADA", "visitas", previous.id(),
                    "Regularizada al ingresar en visita " + visit.id());
        }
        tx.enter(visit.id(), now);
        audit.audit(actor.id(), "CHECK_IN", "visitas", visit.id(),
                expectedHash == null ? "Ingreso registrado" : "Ingreso mediante credencial QR");
    }

    private Visit load(long id, boolean lock) {
        Visit visit = tx.visit(id, lock);
        if (visit == null) throw new SicaException("La visita no existe.");
        return visit;
    }

    private void eligible(Person person, long company) {
        if (person == null) throw new SicaException("La persona no existe.");
        if (!"HABILITADO".equals(person.state())) throw new SicaException("La persona tiene restringido el acceso.");
        if (!directory.activeCompany(company) || (person.company() != null && !directory.activeCompany(person.company()))) {
            throw new SicaException("La empresa debe estar activa.");
        }
    }
}
