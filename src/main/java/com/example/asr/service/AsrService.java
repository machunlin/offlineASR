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

/** 编排上传校验、解码和模型推理；临时文件由定时任务统一清理。 */
@Service
public class AsrService {
    private static final Logger log = LoggerFactory.getLogger(AsrService.class);
    private static final long BYTES_PER_MB = 1024L * 1024L;
    private final AsrProperties properties;
    private final TemporaryAudioStorage temporaryAudioStorage;
    private final FfmpegAudioDecoder audioDecoder;
    private final AsrEngine asrEngine;
    private final Semaphore inferenceGate = new Semaphore(1);

    public AsrService(AsrProperties properties, TemporaryAudioStorage temporaryAudioStorage,
                      FfmpegAudioDecoder audioDecoder, AsrEngine asrEngine) {
        this.properties = properties;
        this.temporaryAudioStorage = temporaryAudioStorage;
        this.audioDecoder = audioDecoder;
        this.asrEngine = asrEngine;
    }

    public RecognitionResponse recognize(MultipartFile file, String requestId) {
        validateRequest(file);
        long startedAt = System.nanoTime();
        boolean gateAcquired = false;
        String filename = safeFilename(file.getOriginalFilename());
        Path inputPath = null;
        Path pcmPath = null;
        log.info("收到语音识别请求，requestId={}，文件名={}，文件类型={}，上传大小={}字节",
                requestId, filename, file.getContentType(), file.getSize());
        try {
            gateAcquired = acquireInferenceGate();
            inputPath = temporaryAudioStorage.createFilePath(getExtension(filename));
            pcmPath = temporaryAudioStorage.createFilePath("pcm");
            file.transferTo(inputPath);
            log.info("音频文件已保存，requestId={}，路径={}，文件大小={}字节",
                    requestId, inputPath.toAbsolutePath(), file.getSize());

            DecodedAudio decoded = audioDecoder.decode(inputPath, pcmPath);
            AudioStatistics statistics = analyze(decoded.getSamples());
            log.info("音频解码完成，requestId={}，原始文件={}，PCM文件={}，采样率={}，采样数={}，时长={}秒，RMS={}，峰值={}，削波样本数={}，解码耗时={}秒",
                    requestId, inputPath.toAbsolutePath(), decoded.getPcmPath().toAbsolutePath(),
                    decoded.getSampleRate(), decoded.getSamples().length, decoded.getDurationSeconds(),
                    statistics.rms, statistics.peak, statistics.clippedSamples, decoded.getDecodeSeconds());

            long inferenceStartedAt = System.nanoTime();
            String text = asrEngine.recognize(decoded.getSamples(), decoded.getSampleRate());
            double inferenceSeconds = elapsedSeconds(inferenceStartedAt);
            if (text == null || text.trim().isEmpty()) {
                log.warn("模型未返回有效识别文本，requestId={}，音频时长={}秒，推理耗时={}秒",
                        requestId, decoded.getDurationSeconds(), inferenceSeconds);
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "ASR_FAILED", "未获得有效识别结果");
            }
            double totalSeconds = elapsedSeconds(startedAt);
            log.info("语音识别完成，requestId={}，识别字符数={}，音频时长={}秒，解码耗时={}秒，推理耗时={}秒，总耗时={}秒，原始音频={}，PCM文件={}",
                    requestId, text.length(), decoded.getDurationSeconds(), decoded.getDecodeSeconds(),
                    inferenceSeconds, totalSeconds, inputPath.toAbsolutePath(), pcmPath.toAbsolutePath());
            return new RecognitionResponse(requestId, text, decoded.getDurationSeconds(),
                    decoded.getDecodeSeconds(), inferenceSeconds, totalSeconds);
        } catch (IOException exception) {
            log.error("处理上传音频文件失败，requestId={}，文件名={}", requestId, filename, exception);
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "上传音频读取失败");
        } catch (RuntimeException exception) {
            log.error("语音识别请求处理失败，requestId={}，文件名={}，异常类型={}",
                    requestId, filename, exception.getClass().getName(), exception);
            throw exception;
        } finally {
            if (gateAcquired) inferenceGate.release();
        }
    }

    private void validateRequest(MultipartFile file) {
        if (!asrEngine.isReady())
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_NOT_READY", "识别模型尚未就绪");
        if (file == null || file.isEmpty())
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "请上传有效音频文件");
        if (file.getSize() > properties.getMaxUploadMb() * BYTES_PER_MB)
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "上传文件超过大小限制");
    }

    private boolean acquireInferenceGate() {
        try {
            boolean acquired = inferenceGate.tryAcquire(properties.getQueueTimeoutSeconds(), TimeUnit.SECONDS);
            if (!acquired)
                throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "PROCESSING_TIMEOUT", "当前识别任务繁忙，请稍后重试");
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "PROCESSING_TIMEOUT", "识别请求被中断");
        }
    }

    private AudioStatistics analyze(float[] samples) {
        double squares = 0.0;
        double peak = 0.0;
        long clipped = 0;
        long valid = 0;
        for (float sample : samples) {
            if (!Float.isFinite(sample)) continue;
            valid++;
            double absolute = Math.abs(sample);
            squares += (double) sample * sample;
            peak = Math.max(peak, absolute);
            if (absolute >= 0.999) clipped++;
        }
        return new AudioStatistics(valid == 0 ? 0.0 : Math.sqrt(squares / valid), peak, clipped);
    }

    private String safeFilename(String filename) {
        if (filename == null || filename.trim().isEmpty()) return "unknown.audio";
        String value = filename.replaceAll("[\\r\\n\\t]", "_");
        return value.length() > 128 ? value.substring(0, 128) : value;
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return "audio";
        String extension = filename.substring(dot + 1);
        return extension.matches("[A-Za-z0-9]{1,10}") ? extension : "audio";
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