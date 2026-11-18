package com.sica.application;

import com.sica.domain.Session;
import com.sica.domain.port.in.SicaUseCases;

public sealed interface VisitCommand {
    void execute(SicaUseCases sica, Session session);

    record CheckIn(long visit) implements VisitCommand {
        @Override public void execute(SicaUseCases sica, Session session) { sica.checkIn(session, visit); }
    }
    record CheckOut(long visit) implements VisitCommand {
        @Override public void execute(SicaUseCases sica, Session session) { sica.checkOut(session, visit); }
    }
    record Decide(long visit, boolean approve) implements VisitCommand {
        @Override public void execute(SicaUseCases sica, Session session) { sica.decide(session, visit, approve); }
    }
}
