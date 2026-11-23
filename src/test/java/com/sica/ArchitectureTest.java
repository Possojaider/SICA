package com.sica;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ArchitectureTest {
    @Test void transactionScopedServicesDependOnCapabilitySpecificPorts() throws Exception {
        for (String name : List.of("DirectoryService", "VisitService")) {
            Class<?> type = Class.forName("com.sica.application." + name);
            for (var field : type.getDeclaredFields()) {
                assertFalse(field.getType().getName().startsWith("com.sica.infrastructure."));
                assertNotEquals(com.sica.domain.port.out.Store.Unit.class, field.getType());
            }
        }
        assertTrue(com.sica.domain.port.out.DirectoryRepository.class.isInterface());
        assertTrue(com.sica.domain.port.out.VisitRepository.class.isInterface());
    }

    @Test void domainIsIndependentOfPersistenceAndPresentationTechnologies() throws Exception {
        try (var files = Files.walk(Path.of("src/main/java/com/sica/domain"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                for (String forbidden : List.of("javafx.", "java.sql.", "com.mysql.", "com.sica.infrastructure.", "com.sica.presentation.", "com.sica.application.")) {
                    assertFalse(source.contains(forbidden), file + " depende de " + forbidden);
                }
            }
        }
    }
}
