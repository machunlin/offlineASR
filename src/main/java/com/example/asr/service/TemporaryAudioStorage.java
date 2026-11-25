package com.example.asr.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** 管理项目 temp 目录内单个请求的临时文件，禁止越界删除。 */
public class TemporaryAudioStorage {

    private final Path rootDirectory;

    public TemporaryAudioStorage(Path rootDirectory) {
        this.rootDirectory = rootDirectory.toAbsolutePath().normalize();
    }

    public Path createRequestDirectory(String requestId) throws IOException {
        if (!requestId.matches("[A-Za-z0-9-]{1,64}")) {
            throw new IllegalArgumentException("请求 ID 格式非法");
        }
        Files.createDirectories(rootDirectory);
        Path requestDirectory = rootDirectory.resolve(requestId).normalize();
        if (!requestDirectory.startsWith(rootDirectory)) {
            throw new IllegalArgumentException("临时目录越界");
        }
        return Files.createDirectories(requestDirectory);
    }

    public void deleteRequestDirectory(Path requestDirectory) throws IOException {
        Path normalized = requestDirectory.toAbsolutePath().normalize();
        if (!normalized.startsWith(rootDirectory) || normalized.equals(rootDirectory)) {
            throw new IllegalArgumentException("仅允许删除请求临时目录");
        }
        if (!Files.exists(normalized)) {
            return;
        }
        try (var paths = Files.walk(normalized)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            });
        } catch (UncheckedIOException exception) {
            throw exception.getCause();
        }
    }
}
