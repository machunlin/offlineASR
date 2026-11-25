package com.example.asr.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemporaryAudioStorageTest {

    @TempDir
    Path projectTempDirectory;

    @Test
    void removesRequestDirectoryAfterProcessingFails() throws Exception {
        TemporaryAudioStorage storage = new TemporaryAudioStorage(projectTempDirectory);
        Path requestDirectory = storage.createRequestDirectory("request-123");
        Path input = requestDirectory.resolve("input.webm");
        Files.write(input, new byte[]{1, 2, 3});

        storage.deleteRequestDirectory(requestDirectory);

        assertFalse(Files.exists(requestDirectory));
        assertTrue(Files.exists(projectTempDirectory));
    }

    @Test
    void rejectsDeletionOutsideTheControlledTemporaryRoot() throws Exception {
        TemporaryAudioStorage storage = new TemporaryAudioStorage(projectTempDirectory);
        Path unrelatedDirectory = Files.createTempDirectory("offline-asr-unrelated-");

        try {
            assertThrows(IllegalArgumentException.class,
                    () -> storage.deleteRequestDirectory(unrelatedDirectory));
            assertTrue(Files.exists(unrelatedDirectory));
        } finally {
            Files.deleteIfExists(unrelatedDirectory);
        }
    }
}
