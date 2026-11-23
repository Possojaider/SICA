package com.sica.application;
import com.sica.domain.*;
import com.sica.domain.port.out.*;
import com.sica.domain.port.out.Store.*;
import java.util.*;
import java.time.*;
import static com.sica.application.InputValidation.*;
import static com.sica.domain.CompanyScope.*;
final class DirectoryService {
    private final DirectoryRepository tx;
    private final AuthenticationRepository accounts;
    private final AuditRepository audit;
    private final PasswordHasher passwords;
    DirectoryService(DirectoryRepository directory, AuthenticationRepository accounts, AuditRepository audit, PasswordHasher passwords) {
        this.tx=directory; this.accounts=accounts; this.audit=audit; this.passwords=passwords;
    }
    private String password(String value) { return InputValidation.password(value, passwords); }
    public long saveCompany(Account actor, Long id, Company form) {
        long saved = tx.company(id, new Company(optional(form.taxId(), 32), required(form.name(), 100), form.active(), optional(form.contact(), 100)));
        change(actor, id, "empresas", saved, "Activo: " + form.active());
        return saved;
    }

    public long savePerson(Account actor, Long id, PersonForm form) {
        if (id == null && !actor.permissions().contains("TRABAJADORES_CREAR") && !"INVITADO".equals(form.type())) {
            throw new SicaException("Tu rol puede registrar invitados.");
        }
        if ("FUNCIONARIO".equals(actor.role())) {
            if (form.company() == null) throw new SicaException("Asocia el invitado a tu empresa.");
            scope(actor, form.company());
        }
        if (!Set.of("TRABAJADOR", "INVITADO").contains(Objects.toString(form.type(), "")) ||
                !Set.of("HABILITADO", "RESTRINGIDO", "BAJA").contains(Objects.toString(form.state(), ""))) {
            throw new SicaException("Selecciona un tipo y un estado de acceso válidos.");
        }
        Person before = id == null ? null : tx.lockPerson(id);
        if (id != null && before == null) throw new SicaException("La persona no existe.");
        if (before != null) personScope(actor, before);
        if ("BAJA".equals(form.state()) && (before == null || !"BAJA".equals(before.state()))) {
            throw new SicaException("Utiliza la operación Dar de baja para desactivar una persona.");
        }
        if (("TRABAJADOR".equals(form.type()) && form.company() == null) ||
                (form.company() != null && !tx.activeCompany(form.company()) &&
                        (before == null || !Objects.equals(before.company(), form.company())))) {
            throw new SicaException("Selecciona una empresa activa.");
        }
        long saved = tx.person(id, new PersonForm(required(form.document(), 20), required(form.name(), 100),
                form.type(), form.company(), photo(form.photo()), form.state()));
        change(actor, id, "personas", saved, "Estado: " + form.state());
        if (before != null && !before.state().equals(form.state())) {
            audit.audit(actor.id(), "CAMBIAR_ESTADO", "personas", saved, before.state() + " → " + form.state());
        }
        return saved;
    }

    public long saveUser(Account actor, Long id, UserForm form) {
        if (!Set.of("SUPER", "SUPERVISOR", "GUARDA", "FUNCIONARIO").contains(Objects.toString(form.role(), ""))) {
            throw new SicaException("Selecciona un rol válido.");
        }
        Account before = id == null ? null : accounts.account(id);
        if (id != null && before == null) throw new SicaException("El usuario no existe.");
        if (("FUNCIONARIO".equals(form.role()) && form.company() == null) ||
                (form.company() != null && !tx.activeCompany(form.company()) &&
                        (before == null || !Objects.equals(before.company(), form.company())))) {
            throw new SicaException("Selecciona una empresa activa.");
        }
        if (id != null && id == actor.id() && (!form.active() || !actor.role().equals(form.role()))) {
            throw new SicaException("Tu usuario debe conservar su rol y permanecer activo.");
        }
        String hash = id != null && (form.hash() == null || form.hash().isEmpty()) ? null : password(form.hash());
        long saved = tx.user(id, new UserForm(email(form.email()), required(form.name(), 100), hash,
                form.active(), form.role(), form.company()));
        change(actor, id, "usuarios", saved, "Rol: " + form.role() + ", activo: " + form.active());
        return saved;
    }

    public void archive(Account actor, Entity entity, long id) {
        if (entity == Entity.USUARIOS && id == actor.id()) throw new SicaException("Tu usuario debe permanecer activo.");
        if (entity == Entity.PERSONAS) {
            Person person = tx.lockPerson(id);
            if (person == null) throw new SicaException("La persona no existe.");
            personScope(actor, person);
        }
        tx.archive(entity, id);
        audit.audit(actor.id(), "ELIMINAR", entity.name().toLowerCase(Locale.ROOT), id, "Baja lógica");
    }
    private void change( Account actor, Long id, String table, long saved, String details) {
        audit.audit(actor.id(), id == null ? "CREAR" : "ACTUALIZAR", table, saved, details);
    }
}
