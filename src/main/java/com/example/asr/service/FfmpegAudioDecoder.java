package com.example.asr.service;

import com.example.asr.config.AsrProperties;
import com.example.asr.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** 使用 FFmpeg 将浏览器录音统一转换为模型需要的 Float32 PCM。 */
@Component
public class FfmpegAudioDecoder {

    private static final Logger log = LoggerFactory.getLogger(FfmpegAudioDecoder.class);
    private static final int TARGET_SAMPLE_RATE = 16_000;
    private static final String PCM_FORMAT = "f32le";
    // 多解码 1 秒用于识别并拒绝超长音频，同时限制 FFmpeg 的最大输出量。
    private static final long DURATION_DETECTION_MARGIN_SECONDS = 1L;
    private static final long BYTES_PER_SAMPLE = Float.BYTES;

    private final AsrProperties properties;
    private final FfmpegProcessLauncher processLauncher;

    public FfmpegAudioDecoder(AsrProperties properties, FfmpegProcessLauncher processLauncher) {
        this.properties = properties;
        this.processLauncher = processLauncher;
    }

    public DecodedAudio decode(Path inputPath, Path outputPath) {
        long decodeStartedAt = System.nanoTime();
        Process process = startDecoder(inputPath, outputPath);

        waitForDecoder(process);
        validatePcmFile(outputPath);

        try {
            byte[] pcmBytes = Files.readAllBytes(outputPath);
            float[] samples = toFloatSamples(pcmBytes);
            double durationSeconds = (double) samples.length / TARGET_SAMPLE_RATE;
            validateDuration(durationSeconds);

            return new DecodedAudio(
                    outputPath,
                    samples,
                    TARGET_SAMPLE_RATE,
                    durationSeconds,
                    elapsedSeconds(decodeStartedAt));
        } catch (IOException exception) {
            throw new ApiException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "UNSUPPORTED_MEDIA_TYPE",
                    "不支持或无法识别该音频格式");
        }
    }

    private Process startDecoder(Path inputPath, Path outputPath) {
        List<String> command = List.of(
                properties.getFfmpegCommand(),
                "-y",
                "-v", "error",
                "-i", inputPath.toString(),
                "-t", String.valueOf(properties.getMaxDurationSeconds()
                        + DURATION_DETECTION_MARGIN_SECONDS),
                "-ac", "1",
                "-ar", String.valueOf(TARGET_SAMPLE_RATE),
                "-f", PCM_FORMAT,
                outputPath.toString());
        try {
            return processLauncher.start(command);
        } catch (IOException exception) {
            log.error("Unable to start FFmpeg decoder errorType={}", exception.getClass().getName(), exception);
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "SERVICE_NOT_READY",
                    "FFmpeg 不可用，请检查部署包");
        }
    }

    private void waitForDecoder(Process process) {
        try {
            if (!process.waitFor(properties.getDecodeTimeoutSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                log.warn("FFmpeg decoding timed out after {} seconds", properties.getDecodeTimeoutSeconds());
                throw new ApiException(
                        HttpStatus.GATEWAY_TIMEOUT,
                        "PROCESSING_TIMEOUT",
                        "音频解码超时");
            }
            if (process.exitValue() != 0) {
                log.warn("FFmpeg decoding failed exitCode={}", process.exitValue());
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "AUDIO_DECODE_FAILED",
                        "音频解码失败，请使用有效录音重试");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "PROCESSING_TIMEOUT",
                    "音频解码被中断");
        }
    }

    private void validatePcmFile(Path outputPath) {
        try {
            long maxPcmBytes = (properties.getMaxDurationSeconds()
                    + DURATION_DETECTION_MARGIN_SECONDS)
                    * TARGET_SAMPLE_RATE * BYTES_PER_SAMPLE;
            if (!Files.isRegularFile(outputPath) || Files.size(outputPath) == 0L) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "AUDIO_DECODE_FAILED",
                        "音频解码失败，请使用有效录音重试");
            }
            if (Files.size(outputPath) > maxPcmBytes) {
                throw new ApiException(
                        HttpStatus.PAYLOAD_TOO_LARGE,
                        "AUDIO_DURATION_EXCEEDED",
                        "录音时长超过限制");
            }
        } catch (IOException exception) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "AUDIO_DECODE_FAILED",
                    "音频解码失败，请使用有效录音重试");
        }
    }

    private float[] toFloatSamples(byte[] pcmBytes) {
        if (pcmBytes.length % Float.BYTES != 0) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "AUDIO_DECODE_FAILED",
                    "音频数据无效");
        }

        float[] samples = new float[pcmBytes.length / Float.BYTES];
        ByteBuffer.wrap(pcmBytes)
                .order(ByteOrder.LITTLE_ENDIAN)
                .asFloatBuffer()
                .get(samples);
        return samples;
    }

    private void validateDuration(double durationSeconds) {
        if (durationSeconds <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_AUDIO", "录音没有有效音频");
        }
        if (durationSeconds > properties.getMaxDurationSeconds()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "AUDIO_DURATION_EXCEEDED",
                    "录音时长超过限制");
        }
    }

    private double elapsedSeconds(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000_000.0;
    }
}
