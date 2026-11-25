package com.example.asr.service;

import com.example.asr.config.AsrProperties;
import com.example.asr.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class FfmpegAudioDecoder {
    private static final int SAMPLE_RATE = 16000;
    private final AsrProperties properties;
    public FfmpegAudioDecoder(AsrProperties properties) { this.properties = properties; }

    public DecodedAudio decode(Path input, Path output) {
        long started = System.nanoTime();
        List<String> command = List.of(properties.getFfmpegCommand(), "-y", "-v", "error", "-i", input.toString(), "-ac", "1", "-ar", "16000", "-f", "f32le", output.toString());
        try {
            Process process = new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(properties.getDecodeTimeoutSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "PROCESSING_TIMEOUT", "音频解码超时");
            }
            if (process.exitValue() != 0 || !Files.isRegularFile(output) || Files.size(output) == 0) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "AUDIO_DECODE_FAILED", "音频解码失败，请使用有效录音重试");
            }
            byte[] bytes = Files.readAllBytes(output);
            if (bytes.length % Float.BYTES != 0) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "AUDIO_DECODE_FAILED", "音频数据无效");
            float[] samples = new float[bytes.length / Float.BYTES];
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(samples);
            double duration = (double) samples.length / SAMPLE_RATE;
            if (duration <= 0) throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_AUDIO", "录音没有有效音频");
            if (duration > properties.getMaxDurationSeconds()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "AUDIO_DURATION_EXCEEDED", "录音时长超过限制");
            return new DecodedAudio(output, samples, SAMPLE_RATE, duration, seconds(started));
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "不支持或无法识别该音频格式");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "PROCESSING_TIMEOUT", "音频解码被中断");
        }
    }
    private double seconds(long start) { return (System.nanoTime() - start) / 1_000_000_000.0; }
}
