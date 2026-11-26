package com.example.asr.service;

import com.example.asr.config.AsrProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.stream.Stream;

/** 管理统一临时目录中的音频文件，并定期清理过期文件。 */
@Component
public class TemporaryAudioStorage {
    private static final Logger log = LoggerFactory.getLogger(TemporaryAudioStorage.class);
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.ROOT);
    private final Path rootDirectory;
    private final long retentionMillis;

    public TemporaryAudioStorage(Path rootDirectory) {
        this(rootDirectory, Duration.ofHours(4));
    }

    /** 生产环境从集中配置读取临时目录和保留时长。 */
    @Autowired
    public TemporaryAudioStorage(AsrProperties properties) {
        this(Path.of(properties.getTempDirectory()), Duration.ofHours(properties.getTempRetentionHours()));
    }

    TemporaryAudioStorage(Path rootDirectory, Duration retention) {
        this.rootDirectory = rootDirectory.toAbsolutePath().normalize();
        this.retentionMillis = retention.toMillis();
    }

    /** 文件名采用年月日时分秒格式；同秒重名时顺延时间戳，并预创建文件以防并发覆盖。 */
    public synchronized Path createFilePath(String extension) throws IOException {
        String suffix = extension != null && extension.matches("[A-Za-z0-9]{1,10}")
                ? extension.toLowerCase(Locale.ROOT) : "audio";
        Files.createDirectories(rootDirectory);
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < 3600; i++) {
            Path file = rootDirectory.resolve(now.plusSeconds(i).format(FORMAT) + "." + suffix).normalize();
            if (file.startsWith(rootDirectory) && Files.notExists(file)) {
                Files.createFile(file);
                return file;
            }
        }
        throw new IOException("无法生成不重复的临时音频文件名");
    }

    /** 后台定期清理创建时间超过保留期限的普通文件，不递归处理子目录。 */
    @Scheduled(fixedDelayString = "${asr.temp-cleanup-interval-ms:600000}")
    public void cleanupExpiredFiles() {
        try {
            cleanupExpiredFiles(System.currentTimeMillis());
        } catch (IOException exception) {
            log.warn("清理临时音频文件失败，目录：{}", rootDirectory, exception);
        }
    }

    void cleanupExpiredFiles(long nowMillis) throws IOException {
        if (Files.notExists(rootDirectory)) return;
        try (Stream<Path> paths = Files.list(rootDirectory)) {
            paths.filter(Files::isRegularFile).forEach(file -> {
                try {
                    BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
                    if (nowMillis - attributes.creationTime().toMillis() > retentionMillis) {
                        Files.deleteIfExists(file);
                        log.info("已清理过期临时音频文件：{}", file.getFileName());
                    }
                } catch (IOException exception) {
                    log.warn("删除临时音频文件失败：{}", file, exception);
                }
            });
        }
    }
}