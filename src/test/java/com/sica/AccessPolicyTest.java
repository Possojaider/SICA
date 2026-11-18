package com.sica;

import com.sica.domain.*;
import com.sica.domain.port.out.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class AccessPolicyTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 18, 10, 0);

    @Test void approvedWindowIncludesStartAndExcludesExpiration() {
        assertDoesNotThrow(() -> AccessPolicy.authorize("APROBADA", now, now.plusHours(1), now));
        assertThrows(SicaException.class, () -> AccessPolicy.authorize("APROBADA", now, now.plusHours(1), now.plusHours(1)));
        assertThrows(SicaException.class, () -> AccessPolicy.authorize("APROBADA", now, now.plusHours(1), now.minusSeconds(1)));
    }

    @Test void pendingAndRejectedCannotEnter() {
        for (String state : new String[] {"PENDIENTE", "RECHAZADA", "DENTRO", "CERRADA", "SALIDA_OLVIDADA"}) {
            assertThrows(SicaException.class, () -> AccessPolicy.authorize(state, now, now.plusHours(1), now));
        }
    }

    @Test void noBadgeRequiresMatchingWorkerCompanyAndCurrentDay() {
        assertDoesNotThrow(() -> AccessPolicy.SIN_CARNET.validate("TRABAJADOR", 1L, 1, now, now.toLocalDate().plusDays(1).atStartOfDay(), now));
        assertThrows(SicaException.class, () -> AccessPolicy.SIN_CARNET.validate("INVITADO", 1L, 1, now, now.plusHours(1), now));
        assertThrows(SicaException.class, () -> AccessPolicy.SIN_CARNET.validate("TRABAJADOR", 2L, 1, now, now.plusHours(1), now));
        assertThrows(SicaException.class, () -> AccessPolicy.SIN_CARNET.validate("TRABAJADOR", 1L, 1, now, now.plusDays(1), now));
        assertThrows(SicaException.class, () -> AccessPolicy.SIN_CARNET.validate("TRABAJADOR", 1L, 1, now.plusDays(1), now.plusDays(1).plusHours(1), now));
    }

    @Test void guestVisitAndInvalidPeriodsAreValidated() {
        assertThrows(SicaException.class, () -> AccessPolicy.PREVIA.validate("TRABAJADOR", 1L, 1, now, now.plusHours(1), now));
        assertThrows(SicaException.class, () -> AccessPolicy.NO_ANUNCIADA.validate("INVITADO", null, 1, now, now, now));
        assertThrows(SicaException.class, () -> AccessPolicy.PREVIA.validate("INVITADO", null, 1, now.minusDays(1), now.minusHours(1), now));
    }
}
