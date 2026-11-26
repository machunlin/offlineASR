package com.example.asr.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AsrPropertiesTest {

    @Test
    void usesTwoInferenceThreadsByDefault() {
        AsrProperties properties = new AsrProperties();

        assertEquals(2, properties.getThreads());
    }

    @Test
    void usesTenMinutesAsTheDefaultRecordingLimit() {
        AsrProperties properties = new AsrProperties();

        assertEquals(600, properties.getMaxDurationSeconds());
    }

    @Test
    void rejectsNonPositiveInferenceThreadCount() {
        AsrProperties properties = new AsrProperties();

        assertThrows(IllegalArgumentException.class, () -> properties.setThreads(0));
    }

    @Test
    void rejectsBlankModelAndTemporaryDirectories() {
        AsrProperties properties = new AsrProperties();

        assertThrows(IllegalArgumentException.class, () -> properties.setModelDirectory("  "));
        assertThrows(IllegalArgumentException.class, () -> properties.setTempDirectory(""));
    }
}
