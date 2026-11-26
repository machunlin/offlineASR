package com.example.asr.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 离线识别服务的集中配置。 */
@ConfigurationProperties(prefix = "asr")
public class AsrProperties {
    private static final int DEFAULT_THREADS = 2;
    private static final long DEFAULT_MAX_UPLOAD_MB = 50;
    private static final long DEFAULT_MAX_DURATION_SECONDS = 60;
    private static final long DEFAULT_DECODE_TIMEOUT_SECONDS = 60;
    private static final long DEFAULT_QUEUE_TIMEOUT_SECONDS = 5;
    private static final long DEFAULT_TEMP_RETENTION_HOURS = 4;
    private int threads = DEFAULT_THREADS;
    private String modelDirectory = "models/SenseVoiceSmall";
    private String tempDirectory = "temp";
    private String ffmpegCommand = "ffmpeg";
    private long maxUploadMb = DEFAULT_MAX_UPLOAD_MB;
    private long maxDurationSeconds = DEFAULT_MAX_DURATION_SECONDS;
    private long decodeTimeoutSeconds = DEFAULT_DECODE_TIMEOUT_SECONDS;
    private long queueTimeoutSeconds = DEFAULT_QUEUE_TIMEOUT_SECONDS;
    private long tempRetentionHours = DEFAULT_TEMP_RETENTION_HOURS;

    public int getThreads() { return threads; }
    public void setThreads(int value) {
        if (value <= 0) throw new IllegalArgumentException("ASR 推理线程数必须为正整数");
        threads = value;
    }
    public String getModelDirectory() { return modelDirectory; }
    public void setModelDirectory(String value) { modelDirectory = requireText(value, "模型目录"); }
    public String getTempDirectory() { return tempDirectory; }
    public void setTempDirectory(String value) { tempDirectory = requireText(value, "临时目录"); }
    public String getFfmpegCommand() { return ffmpegCommand; }
    public void setFfmpegCommand(String value) { ffmpegCommand = requireText(value, "FFmpeg 命令"); }
    public long getMaxUploadMb() { return maxUploadMb; }
    public void setMaxUploadMb(long value) { maxUploadMb = requirePositive(value, "最大上传大小"); }
    public long getMaxDurationSeconds() { return maxDurationSeconds; }
    public void setMaxDurationSeconds(long value) { maxDurationSeconds = requirePositive(value, "最大录音时长"); }
    public long getDecodeTimeoutSeconds() { return decodeTimeoutSeconds; }
    public void setDecodeTimeoutSeconds(long value) { decodeTimeoutSeconds = requirePositive(value, "解码超时"); }
    public long getQueueTimeoutSeconds() { return queueTimeoutSeconds; }
    public void setQueueTimeoutSeconds(long value) { queueTimeoutSeconds = requirePositive(value, "排队超时"); }
    public long getTempRetentionHours() { return tempRetentionHours; }
    public void setTempRetentionHours(long value) { tempRetentionHours = requirePositive(value, "临时文件保留时长"); }

    private long requirePositive(long value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name + "必须为正整数");
        return value;
    }
    private String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(name + "不能为空");
        return value.trim();
    }
}