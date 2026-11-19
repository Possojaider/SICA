package com.sica;

import com.sica.domain.*;
import com.sica.infrastructure.QrCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class QrCodecTest {
    @TempDir Path temp;

    @Test void generatedCodesAreRandomAndOnlyTheirDigestIsStored() {
        var codes = new HashSet<String>();
        for (int i = 0; i < 200; i++) {
            String code = CredentialCodes.generate();
            assertTrue(codes.add(code));
            assertEquals(64, CredentialCodes.digest(code).length());
            assertFalse(CredentialCodes.digest(code).contains(code));
        }
        assertThrows(SicaException.class, () -> CredentialCodes.digest(null));
        assertThrows(SicaException.class, () -> CredentialCodes.digest("https://example.com/visita/1"));
    }

    @Test void exportedQrCanBeScannedFromAnImage() throws Exception {
        String code = CredentialCodes.generate();
        byte[] png = QrCodec.encode(code);
        Path image = temp.resolve("visita.png");
        Files.write(image, png);
        assertEquals(code, QrCodec.read(image));
    }

    @Test void corruptedAndOversizedImagesAreRejected() throws Exception {
        Path invalid = temp.resolve("invalid.png");
        Files.writeString(invalid, "esto no es una imagen");
        assertThrows(SicaException.class, () -> QrCodec.read(invalid));
        Path large = temp.resolve("large.png");
        try (var file = new java.io.RandomAccessFile(large.toFile(), "rw")) {
            file.setLength(10 * 1024 * 1024 + 1);
        }
        assertThrows(SicaException.class, () -> QrCodec.read(large));
    }
}
