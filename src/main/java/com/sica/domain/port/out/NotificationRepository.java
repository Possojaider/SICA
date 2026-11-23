package com.sica.domain.port.out;
import com.sica.domain.AccessPolicy;
import com.sica.domain.port.out.Store.*;
import java.time.LocalDateTime;
import java.util.List;
public interface NotificationRepository {
    void notifyRequest(long visit, long company, LocalDateTime now);
    void notifyDecision(long visit, String state, LocalDateTime now);
    Data notifications(long user, String search, LocalDateTime from, LocalDateTime until);
    long unreadNotifications(long user);
    boolean markNotificationRead(long user, long id, LocalDateTime now);
}
