package com.example.asr.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

    @Test
    void repeatedRequestIdsUseIsolatedDirectories() throws Exception {
        TemporaryAudioStorage storage = new TemporaryAudioStorage(projectTempDirectory);

        Path first = storage.createRequestDirectory("same-request-id");
        Path second = storage.createRequestDirectory("same-request-id");

        assertNotEquals(first, second);
        Files.writeString(first.resolve("first.tmp"), "first");
        Files.writeString(second.resolve("second.tmp"), "second");

        storage.deleteRequestDirectory(first);

        assertTrue(Files.exists(second.resolve("second.tmp")));
        assertTrue(Files.notExists(first));
    }
}
