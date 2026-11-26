package com.example.asr.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;

import com.example.asr.config.AsrProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 管理项目 temp 目录内单个请求的临时文件，禁止越界删除。 */
@Component
public class TemporaryAudioStorage {

    private final Path rootDirectory;

    public TemporaryAudioStorage(Path rootDirectory) {
        this.rootDirectory = rootDirectory.toAbsolutePath().normalize();
    }

    /** 生产环境固定从集中配置读取项目临时目录；Path 构造器仅供独立测试使用。 */
    @Autowired
    public TemporaryAudioStorage(AsrProperties properties) {
        this(Path.of(properties.getTempDirectory()));
    }

    public Path createRequestDirectory(String requestId) throws IOException {
        if (!requestId.matches("[A-Za-z0-9-]{1,64}")) {
            throw new IllegalArgumentException("请求 ID 格式非法");
        }
        Files.createDirectories(rootDirectory);
        // requestId 是外部可控的关联标识；目录额外追加随机后缀，避免并发复用 ID 时互相覆盖/删除。
        Path requestDirectory = rootDirectory.resolve(requestId + "-" + UUID.randomUUID()).normalize();
        if (!requestDirectory.startsWith(rootDirectory)) {
            throw new IllegalArgumentException("临时目录越界");
        }
        return Files.createDirectory(requestDirectory);
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
