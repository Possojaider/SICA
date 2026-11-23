package com.sica.domain.port.out;
import com.sica.domain.AccessPolicy;
import com.sica.domain.port.out.Store.*;
import java.time.LocalDateTime;
import java.util.List;
public interface ReportingRepository {
    Statistics statistics(Long company);
    Data report(Report report, Long company, String search, LocalDateTime from, LocalDateTime until);
}
