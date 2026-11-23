package com.sica;

import com.sica.application.Sica;
import com.sica.domain.Session;
import com.sica.domain.port.out.Store.Report;
import com.sica.infrastructure.BcryptHasher;
import com.sica.infrastructure.JdbcStore;
import java.time.*;

/** Cliente de aceptación ejecutado en otra JVM, contra una base temporal de pruebas. */
public final class ProcessFlowClient {
    public static void main(String[] args) throws Exception {
        var env = System.getenv();
        Sica sica = new Sica(new JdbcStore(env.get("SICA_PROCESS_URL"), env.get("SICA_PROCESS_USER"),
                env.get("SICA_PROCESS_DB_PASSWORD")), new BcryptHasher(),
                Clock.fixed(Instant.parse(env.get("SICA_PROCESS_NOW")), ZoneId.of("America/Bogota")));
        Session session = sica.login(env.get("SICA_PROCESS_EMAIL"), env.get("SICA_PROCESS_PASSWORD"));
        long visit = Long.parseLong(args[1]);
        try {
            if (args[0].equals("approve")) {
                var notifications = sica.report(session, Report.NOTIFICACIONES, "", null, null);
                var notification = notifications.rows().stream()
                        .filter(row -> row.get(1).equals(args[1])).findFirst().orElseThrow();
                sica.markNotificationRead(session, Long.parseLong(notification.getFirst()));
                sica.decide(session, visit, true, "Aprobación desde un proceso independiente.");
            } else {
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
                boolean approved = false;
                while (System.nanoTime() < deadline) {
                    var visits = sica.report(session, Report.VISITAS, args[1], null, null);
                    if (visits.rows().stream().anyMatch(row -> row.getFirst().equals(args[1]) && row.get(6).equals("APROBADA"))) {
                        approved = true;
                        break;
                    }
                    Thread.sleep(100);
                }
                if (!approved) throw new IllegalStateException("No se recibió la aprobación del otro proceso.");
                sica.checkIn(session, visit);
                sica.incident(session, visit, "Incidente de aceptación entre procesos independientes.");
                sica.checkOut(session, visit);
            }
        } finally {
            sica.logout(session);
        }
        System.out.println("FLOW_OK");
    }
}
