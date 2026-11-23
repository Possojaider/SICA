package com.sica;

import com.sica.infrastructure.TutorialResources;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TutorialResourcesTest {
    @Test void videoAndChaptersAreBundledAndAvailableWithoutTheDeliveryFolder() throws Exception {
        var content = TutorialResources.prepare();
        assertTrue(Files.size(content.video()) > 1_000_000);
        assertEquals(25, content.chapters().size());
        assertEquals(0, content.chapters().getFirst().seconds());
        double previous = -1;
        for (var chapter : content.chapters()) {
            assertTrue(chapter.seconds() > previous);
            assertFalse(chapter.title().isBlank());
            previous = chapter.seconds();
        }
        assertSame(content, TutorialResources.prepare());
    }
}
