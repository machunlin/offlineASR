package com.example.asr.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemporaryAudioStorageTest {
    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.ROOT);

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
    void deletesOnlyFilesOlderThanFourHoursAndDoesNotRecurse() throws Exception {
        long now = System.currentTimeMillis();
        TemporaryAudioStorage storage = new TemporaryAudioStorage(tempDirectory);
        Path recent = Files.createFile(tempDirectory.resolve(timestamp(now - 3L * 60 * 60 * 1000) + ".wav"));
        Path expired = Files.createFile(tempDirectory.resolve(timestamp(now - 5L * 60 * 60 * 1000) + ".pcm"));
        Path malformed = Files.createFile(tempDirectory.resolve("keep-me.txt"));
        Path nestedDirectory = Files.createDirectory(tempDirectory.resolve("nested"));
        Path nestedFile = Files.writeString(nestedDirectory.resolve("keep.txt"), "keep");

        storage.cleanupExpiredFiles(now);

        assertTrue(Files.exists(recent));
        assertFalse(Files.exists(expired));
        assertTrue(Files.exists(malformed));
        assertTrue(Files.exists(nestedFile));
        assertTrue(Files.exists(nestedDirectory));
    }

    private String timestamp(long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()).format(FORMAT);
    }
}