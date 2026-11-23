package com.sica.domain.port.out;
import com.sica.domain.AccessPolicy;
import com.sica.domain.port.out.Store.*;
import java.time.LocalDateTime;
import java.util.List;
public interface AuditRepository {
    void audit(Long actor, String action, String table, Long entity, String details);
}
