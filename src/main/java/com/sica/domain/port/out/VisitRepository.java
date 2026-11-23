package com.sica.domain.port.out;
import com.sica.domain.AccessPolicy;
import com.sica.domain.port.out.Store.*;
import java.time.LocalDateTime;
import java.util.List;
public interface VisitRepository {
    Visit visit(long id, boolean lock);
    Visit inside(long person);
    List<Long> expireDue(LocalDateTime now, Long company);
    long createVisit(long person, long company, AccessPolicy type, String state, LocalDateTime start,
                         LocalDateTime end, long actor, LocalDateTime now, String plate);
    void decide(long visit, String state, long actor, LocalDateTime now, String reason);
    Visit visitByCredential(String hash);
    String credentialHash(long visit);
    void setCredential(long visit, String hash);
    void enter(long visit, LocalDateTime now);
    void exit(long visit, String state, LocalDateTime now);
    long incident(long visit, long reporter, String description, LocalDateTime now);
}
