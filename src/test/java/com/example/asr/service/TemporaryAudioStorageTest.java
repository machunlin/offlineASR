package com.example.asr.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemporaryAudioStorageTest {
    @TempDir
    Path tempDirectory;

    @Test
    void createsTimestampNamedFilesInTheSameDirectory() throws Exception {
        TemporaryAudioStorage storage = new TemporaryAudioStorage(tempDirectory);
        Path audio = storage.createFilePath("wav");
        Path pcm = storage.createFilePath("pcm");
        assertTrue(audio.getParent().equals(tempDirectory));
        assertTrue(pcm.getParent().equals(tempDirectory));
        assertTrue(audio.getFileName().toString().matches("\\d{14}\\.wav"));
        assertTrue(pcm.getFileName().toString().matches("\\d{14}\\.pcm"));
        assertTrue(Files.exists(audio));
        assertTrue(Files.exists(pcm));
    }

    @Test
    void doesNotOverwriteFilesCreatedWithinTheSameSecond() throws Exception {
        TemporaryAudioStorage storage = new TemporaryAudioStorage(tempDirectory);
        Path first = storage.createFilePath("wav");
        Path second = storage.createFilePath("wav");
        assertFalse(first.equals(second));
        assertTrue(Files.exists(first));
        assertTrue(Files.exists(second));
    }

    @Test
    void cleanupDoesNotDeleteRecentFilesOrRecurseIntoSubdirectories() throws Exception {
        TemporaryAudioStorage storage = new TemporaryAudioStorage(tempDirectory);
        Path recent = storage.createFilePath("wav");
        Path nestedDirectory = Files.createDirectory(tempDirectory.resolve("nested"));
        Path nestedFile = Files.writeString(nestedDirectory.resolve("keep.txt"), "keep");
        storage.cleanupExpiredFiles(System.currentTimeMillis());
        assertTrue(Files.exists(recent));
        assertTrue(Files.exists(nestedFile));
        assertTrue(Files.exists(nestedDirectory));
    }
}