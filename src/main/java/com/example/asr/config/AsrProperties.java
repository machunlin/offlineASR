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

    private int threads = DEFAULT_THREADS;
    private String modelDirectory = "models/SenseVoiceSmall";
    private String tempDirectory = "temp";
    private String ffmpegCommand = "ffmpeg";
    private long maxUploadMb = DEFAULT_MAX_UPLOAD_MB;
    private long maxDurationSeconds = DEFAULT_MAX_DURATION_SECONDS;
    private long decodeTimeoutSeconds = DEFAULT_DECODE_TIMEOUT_SECONDS;
    private long queueTimeoutSeconds = DEFAULT_QUEUE_TIMEOUT_SECONDS;

    public int getThreads() { return threads; }

    public void setThreads(int threads) {
        if (threads <= 0) throw new IllegalArgumentException("ASR 推理线程数必须为正整数");
        this.threads = threads;
    }

    public String getModelDirectory() { return modelDirectory; }

    public void setModelDirectory(String modelDirectory) {
        this.modelDirectory = requireText(modelDirectory, "模型目录");
    }

    public String getTempDirectory() { return tempDirectory; }

    public void setTempDirectory(String tempDirectory) {
        this.tempDirectory = requireText(tempDirectory, "临时目录");
    }

    public String getFfmpegCommand() { return ffmpegCommand; }

    public void setFfmpegCommand(String ffmpegCommand) {
        this.ffmpegCommand = requireText(ffmpegCommand, "FFmpeg 命令");
    }

    public long getMaxUploadMb() { return maxUploadMb; }

    public void setMaxUploadMb(long maxUploadMb) {
        this.maxUploadMb = requirePositive(maxUploadMb, "最大上传大小");
    }

    public long getMaxDurationSeconds() { return maxDurationSeconds; }

    public void setMaxDurationSeconds(long maxDurationSeconds) {
        this.maxDurationSeconds = requirePositive(maxDurationSeconds, "最大录音时长");
    }

    public long getDecodeTimeoutSeconds() { return decodeTimeoutSeconds; }

    public void setDecodeTimeoutSeconds(long decodeTimeoutSeconds) {
        this.decodeTimeoutSeconds = requirePositive(decodeTimeoutSeconds, "解码超时");
    }

    public long getQueueTimeoutSeconds() { return queueTimeoutSeconds; }

    public void setQueueTimeoutSeconds(long queueTimeoutSeconds) {
        this.queueTimeoutSeconds = requirePositive(queueTimeoutSeconds, "排队超时");
    }

    private long requirePositive(long value, String propertyName) {
        if (value <= 0) throw new IllegalArgumentException(propertyName + "必须为正整数");
        return value;
    }

    private String requireText(String value, String propertyName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(propertyName + "不能为空");
        }
        return value.trim();
    }
}
