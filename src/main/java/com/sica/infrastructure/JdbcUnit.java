package com.sica.infrastructure;
import com.sica.domain.*;
import com.sica.domain.port.out.*;
import com.sica.domain.port.out.Store.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
final class JdbcUnit implements Store.Unit {
    private final JdbcDirectory directory;
    private final JdbcVisits visits;
    private final JdbcNotifications notifications;
    private final JdbcReports reports;
    private final JdbcAudit audit;
    JdbcUnit(Connection connection) {
        directory=new JdbcDirectory(connection);
        visits=new JdbcVisits(connection);
        notifications=new JdbcNotifications(connection);
        reports=new JdbcReports(connection);
        audit=new JdbcAudit(connection);
    }
    @Override public List<Option> companies(Long company) { return directory.companies(company); }
    @Override public boolean activeCompany(long id) { return directory.activeCompany(id); }
    @Override public Person lockPerson(long id) { return directory.lockPerson(id); }
    @Override public long company(Long id, Company form) { return directory.company(id, form); }
    @Override public long person(Long id, PersonForm form) { return directory.person(id, form); }
    @Override public long user(Long id, UserForm form) { return directory.user(id, form); }
    @Override public void archive(Entity entity, long id) { directory.archive(entity, id); }
    @Override public Account byEmail(String email) { return directory.byEmail(email); }
    @Override public Account account(long id) { return directory.account(id); }
    @Override public Visit visit(long id, boolean lock) { return visits.visit(id, lock); }
    @Override public Visit inside(long person) { return visits.inside(person); }
    @Override public List<Long> expireDue(LocalDateTime now, Long company) { return visits.expireDue(now, company); }
    @Override public long createVisit(long person, long company, AccessPolicy type, String state, LocalDateTime start,
                                LocalDateTime end, long actor, LocalDateTime now, String plate) { return visits.createVisit(person, company, type, state, start, end, actor, now, plate); }
    @Override public void decide(long visit, String state, long actor, LocalDateTime now, String reason) { visits.decide(visit, state, actor, now, reason); }
    @Override public Visit visitByCredential(String hash) { return visits.visitByCredential(hash); }
    @Override public String credentialHash(long visit) { return visits.credentialHash(visit); }
    @Override public void setCredential(long visit, String hash) { visits.setCredential(visit, hash); }
    @Override public void enter(long visit, LocalDateTime now) { visits.enter(visit, now); }
    @Override public void exit(long visit, String state, LocalDateTime now) { visits.exit(visit, state, now); }
    @Override public long incident(long visit, long reporter, String description, LocalDateTime now) { return visits.incident(visit, reporter, description, now); }
    @Override public void notifyRequest(long visit, long company, LocalDateTime now) { notifications.notifyRequest(visit, company, now); }
    @Override public void notifyDecision(long visit, String state, LocalDateTime now) { notifications.notifyDecision(visit, state, now); }
    @Override public Data notifications(long user, String search, LocalDateTime from, LocalDateTime until) { return notifications.notifications(user, search, from, until); }
    @Override public long unreadNotifications(long user) { return notifications.unreadNotifications(user); }
    @Override public boolean markNotificationRead(long user, long id, LocalDateTime now) { return notifications.markNotificationRead(user, id, now); }
    @Override public Statistics statistics(Long company) { return reports.statistics(company); }
    @Override public Data report(Report report, Long company, String search, LocalDateTime from, LocalDateTime until) { return reports.report(report, company, search, from, until); }
    @Override public void audit(Long actor, String action, String table, Long entity, String details) { audit.audit(actor, action, table, entity, details); }
}
