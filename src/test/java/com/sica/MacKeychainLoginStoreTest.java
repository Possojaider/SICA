package com.sica;

import com.sica.domain.port.out.SavedLoginStore;
import com.sica.infrastructure.MacKeychainLoginStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.MAC)
@EnabledIfEnvironmentVariable(named="SICA_KEYCHAIN_TEST", matches="true")
class MacKeychainLoginStoreTest {
    @Test void keychainSurvivesNewStoreInstancesSupportsUpdatesAndForgetsCredentials() {
        String service = "com.sica.tests.saved-login." + UUID.randomUUID();
        MacKeychainLoginStore store = new MacKeychainLoginStore(service);
        SavedLoginStore.Login first = new SavedLoginStore.Login("cuenta@tests.sica.local", "Clave-de-prueba-2026!");
        try {
            assertTrue(store.load().isEmpty());
            store.save(first);
            SavedLoginStore restored = new MacKeychainLoginStore(service);
            assertEquals(first, restored.load().orElseThrow());
            assertFalse(first.toString().contains(first.password()));
            var replacement = new SavedLoginStore.Login("otra@tests.sica.local", "Otra-clave-de-prueba-2026!");
            restored.save(replacement);
            assertEquals(replacement, store.load().orElseThrow());
            restored.clear();
            assertTrue(store.load().isEmpty());
            assertDoesNotThrow(store::clear);
        } finally {
            store.clear();
        }
    }
}
