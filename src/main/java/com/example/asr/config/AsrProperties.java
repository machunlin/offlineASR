package com.example.asr.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "asr")
public class AsrProperties {

    private int threads = 2;
    private String modelDirectory = "models/SenseVoiceSmall";
    private String tempDirectory = "temp";
    private String ffmpegCommand = "ffmpeg";
    private long maxUploadMb = 50;
    private long maxDurationSeconds = 600;
    private long decodeTimeoutSeconds = 60;
    private long inferenceTimeoutSeconds = 120;
    private long queueTimeoutSeconds = 5;

    public int getThreads() {
        return threads;
    }

    public void setThreads(int threads) {
        if (threads <= 0) {
            throw new IllegalArgumentException("ASR 推理线程数必须为正整数");
        }
        this.threads = threads;
    }

    public String getModelDirectory() { return modelDirectory; }
    public void setModelDirectory(String value) { this.modelDirectory = value; }
    public String getTempDirectory() { return tempDirectory; }
    public void setTempDirectory(String value) { this.tempDirectory = value; }
    public String getFfmpegCommand() { return ffmpegCommand; }
    public void setFfmpegCommand(String value) { this.ffmpegCommand = value; }
    public long getMaxUploadMb() { return maxUploadMb; }
    public void setMaxUploadMb(long value) { this.maxUploadMb = positive(value, "最大上传大小"); }
    public long getMaxDurationSeconds() { return maxDurationSeconds; }
    public void setMaxDurationSeconds(long value) { this.maxDurationSeconds = positive(value, "最大录音时长"); }
    public long getDecodeTimeoutSeconds() { return decodeTimeoutSeconds; }
    public void setDecodeTimeoutSeconds(long value) { this.decodeTimeoutSeconds = positive(value, "解码超时"); }
    public long getInferenceTimeoutSeconds() { return inferenceTimeoutSeconds; }
    public void setInferenceTimeoutSeconds(long value) { this.inferenceTimeoutSeconds = positive(value, "推理超时"); }
    public long getQueueTimeoutSeconds() { return queueTimeoutSeconds; }
    public void setQueueTimeoutSeconds(long value) { this.queueTimeoutSeconds = positive(value, "排队超时"); }

    private long positive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + "必须为正整数");
        }
        return value;
    }
}
