package com.sica.domain.port.in;

import com.sica.domain.*;
import com.sica.domain.port.out.Store.*;
import java.time.LocalDateTime;
import java.util.*;

public interface SicaUseCases {
    LocalDateTime currentTime();
    Session login(String email, String password);
    void logout(Session session);
    Set<String> permissions(Session session);
    List<Option> companies(Session session);
    Statistics statistics(Session session);
    long saveCompany(Session session, Long id, Company form);
    long savePerson(Session session, Long id, PersonForm form);
    long saveUser(Session session, Long id, UserForm form);
    void archive(Session session, Entity entity, long id);
    long request(Session session, long person, long company, AccessPolicy type, LocalDateTime start, LocalDateTime end);
    void decide(Session session, long id, boolean approve);
    void checkIn(Session session, long id);
    void checkOut(Session session, long id);
    long incident(Session session, long visit, String description);
    Data report(Session session, Report report, String search, LocalDateTime from, LocalDateTime until);
}
