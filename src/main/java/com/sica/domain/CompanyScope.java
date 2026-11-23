package com.sica.domain;
import com.sica.domain.port.out.Store.Account;
import com.sica.domain.port.out.Store.Person;
import java.util.Objects;
public final class CompanyScope {
    private CompanyScope() {}
    public static void scope(Account actor, long company) {
        if ("FUNCIONARIO".equals(actor.role()) && !Long.valueOf(company).equals(actor.company())) {
            throw new SicaException("La visita pertenece a otra empresa.");
        }
    }

    public static void personScope(Account actor, Person person) {
        if ("FUNCIONARIO".equals(actor.role()) && !Objects.equals(actor.company(), person.company())) {
            throw new SicaException("La persona pertenece a otra empresa.");
        }
    }
}
