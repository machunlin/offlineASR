package com.example.asr.service;

import com.example.asr.config.AsrProperties;
import com.example.asr.dto.RecognitionResponse;
import com.example.asr.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** 编排上传校验、解码、单路模型推理与临时文件释放。 */
@Service
public class AsrService {

    private static final Logger log = LoggerFactory.getLogger(AsrService.class);
    private static final String INPUT_FILENAME = "input.audio";
    private static final String PCM_FILENAME = "audio.pcm";
    private static final long BYTES_PER_MB = 1024L * 1024L;

    private final AsrProperties properties;
    private final TemporaryAudioStorage temporaryAudioStorage;
    private final FfmpegAudioDecoder audioDecoder;
    private final AsrEngine asrEngine;
    private final Semaphore inferenceGate = new Semaphore(1);

    public AsrService(AsrProperties properties,
                      TemporaryAudioStorage temporaryAudioStorage,
                      FfmpegAudioDecoder audioDecoder,
                      AsrEngine asrEngine) {
        this.properties = properties;
        this.temporaryAudioStorage = temporaryAudioStorage;
        this.audioDecoder = audioDecoder;
        this.asrEngine = asrEngine;
    }

    public RecognitionResponse recognize(MultipartFile file, String requestId) {
        validateRequest(file);
        long requestStartedAt = System.nanoTime();
        Path requestDirectory = null;
        boolean gateAcquired = false;
        String originalFilename = safeFilename(file.getOriginalFilename());

        log.info("ASR request received requestId={} filename={} contentType={} uploadBytes={}",
                requestId, originalFilename, file.getContentType(), file.getSize());

        try {
            gateAcquired = acquireInferenceGate();
            requestDirectory = temporaryAudioStorage.createRequestDirectory(requestId);
            Path inputPath = requestDirectory.resolve(INPUT_FILENAME);
            Path pcmPath = requestDirectory.resolve(PCM_FILENAME);

            file.transferTo(inputPath);
            log.info("ASR audio saved requestId={} inputPath={} fileBytes={}",
                    requestId, inputPath.toAbsolutePath(), file.getSize());

            DecodedAudio decodedAudio = audioDecoder.decode(inputPath, pcmPath);
            AudioStatistics statistics = analyze(decodedAudio.getSamples());
            log.info("ASR audio decoded requestId={} inputPath={} pcmPath={} sampleRate={} channels=1 samples={} durationSeconds={} rms={} peak={} clippedSamples={} decodeSeconds={}",
                    requestId, inputPath.toAbsolutePath(), decodedAudio.getPcmPath().toAbsolutePath(),
                    decodedAudio.getSampleRate(), decodedAudio.getSamples().length,
                    decodedAudio.getDurationSeconds(), statistics.rms, statistics.peak,
                    statistics.clippedSamples, decodedAudio.getDecodeSeconds());

            long inferenceStartedAt = System.nanoTime();
            String text = asrEngine.recognize(decodedAudio.getSamples(), decodedAudio.getSampleRate());
            double inferenceSeconds = elapsedSeconds(inferenceStartedAt);

            if (text == null || text.trim().isEmpty()) {
                log.warn("ASR inference returned empty text requestId={} audioDurationSeconds={} sampleCount={} inferenceSeconds={}",
                        requestId, decodedAudio.getDurationSeconds(), decodedAudio.getSamples().length,
                        inferenceSeconds);
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "ASR_FAILED", "未获得有效识别结果");
            }

            double totalSeconds = elapsedSeconds(requestStartedAt);
            log.info("ASR completed requestId={} recognizedChars={} audioSeconds={} decodeSeconds={} inferenceSeconds={} totalSeconds={}",
                    requestId, text.length(), decodedAudio.getDurationSeconds(),
                    decodedAudio.getDecodeSeconds(), inferenceSeconds, totalSeconds);
            return new RecognitionResponse(requestId, text, decodedAudio.getDurationSeconds(),
                    decodedAudio.getDecodeSeconds(), inferenceSeconds, totalSeconds);
        } catch (IOException exception) {
            log.error("ASR file operation failed requestId={} filename={}", requestId, originalFilename, exception);
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "上传音频读取失败");
        } catch (RuntimeException exception) {
            log.error("ASR request failed requestId={} filename={} exceptionType={}",
                    requestId, originalFilename, exception.getClass().getName(), exception);
            throw exception;
        } finally {
            deleteTemporaryFiles(requestDirectory, requestId);
            if (gateAcquired) {
                inferenceGate.release();
            }
        }
    }

    private void validateRequest(MultipartFile file) {
        if (!asrEngine.isReady()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_NOT_READY", "识别模型尚未就绪");
        }
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "请上传有效音频文件");
        }
        if (file.getSize() > properties.getMaxUploadMb() * BYTES_PER_MB) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "上传文件超过大小限制");
        }
    }

    private boolean acquireInferenceGate() {
        try {
            boolean acquired = inferenceGate.tryAcquire(properties.getQueueTimeoutSeconds(), TimeUnit.SECONDS);
            if (!acquired) {
                throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "PROCESSING_TIMEOUT", "当前识别任务繁忙，请稍后重试");
            }
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "PROCESSING_TIMEOUT", "识别请求被中断");
        }
    }

    private AudioStatistics analyze(float[] samples) {
        double sumSquares = 0.0;
        double peak = 0.0;
        long clippedSamples = 0;
        for (float sample : samples) {
            if (!Float.isFinite(sample)) {
                continue;
            }
            double absolute = Math.abs(sample);
            sumSquares += (double) sample * sample;
            peak = Math.max(peak, absolute);
            if (absolute >= 0.999) {
                clippedSamples++;
            }
        }
        double rms = samples.length == 0 ? 0.0 : Math.sqrt(sumSquares / samples.length);
        return new AudioStatistics(rms, peak, clippedSamples);
    }

    private String safeFilename(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return "unknown";
        }
        String sanitized = filename.replaceAll("[\\r\\n\\t]", "_");
        return sanitized.length() > 128 ? sanitized.substring(0, 128) : sanitized;
    }

    private void deleteTemporaryFiles(Path requestDirectory, String requestId) {
        if (requestDirectory == null) {
            return;
        }
        try {
            temporaryAudioStorage.deleteRequestDirectory(requestDirectory);
            log.debug("ASR temporary directory removed requestId={} directory={}",
                    requestId, requestDirectory.toAbsolutePath());
        } catch (IOException exception) {
            log.warn("Unable to clean temporary audio requestId={} directory={}",
                    requestId, requestDirectory.toAbsolutePath(), exception);
        }
    }

    private double elapsedSeconds(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000_000.0;
    }

    private static final class AudioStatistics {
        private final double rms;
        private final double peak;
        private final long clippedSamples;

        private AudioStatistics(double rms, double peak, long clippedSamples) {
            this.rms = rms;
            this.peak = peak;
            this.clippedSamples = clippedSamples;
        }
    }
}
