package com.example.asr.service;

import com.example.asr.config.AsrProperties;
import com.example.asr.dto.RecognitionResponse;
import com.example.asr.exception.ApiException;
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

    private static final String INPUT_FILENAME = "input.audio";
    private static final String PCM_FILENAME = "audio.pcm";
    private static final long BYTES_PER_MB = 1024L * 1024L;

    private final AsrProperties properties;
    private final TemporaryAudioStorage temporaryAudioStorage;
    private final FfmpegAudioDecoder audioDecoder;
    private final AsrEngine asrEngine;

    /**
     * SenseVoice 识别器在首期以单路方式复用，避免并发访问引起不可预测的 JNI 资源竞争。
     */
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
        try {
            gateAcquired = acquireInferenceGate();
            requestDirectory = temporaryAudioStorage.createRequestDirectory(requestId);

            Path inputPath = requestDirectory.resolve(INPUT_FILENAME);
            file.transferTo(inputPath);

            DecodedAudio decodedAudio = audioDecoder.decode(
                    inputPath,
                    requestDirectory.resolve(PCM_FILENAME));

            long inferenceStartedAt = System.nanoTime();
            String text = asrEngine.recognize(
                    decodedAudio.getSamples(),
                    decodedAudio.getSampleRate());
            double inferenceSeconds = elapsedSeconds(inferenceStartedAt);

            if (text == null || text.trim().isEmpty()) {
                throw new ApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "ASR_FAILED",
                        "未获得有效识别结果");
            }

            return new RecognitionResponse(
                    requestId,
                    text,
                    decodedAudio.getDurationSeconds(),
                    decodedAudio.getDecodeSeconds(),
                    inferenceSeconds,
                    elapsedSeconds(requestStartedAt));
        } catch (IOException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REQUEST",
                    "上传音频读取失败");
        } finally {
            // 仅释放当前请求实际取得的许可，不能让等待超时的请求破坏单路限制。
            if (gateAcquired) {
                inferenceGate.release();
            }
            deleteTemporaryFiles(requestDirectory);
        }
    }

    private void validateRequest(MultipartFile file) {
        if (!asrEngine.isReady()) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "SERVICE_NOT_READY",
                    "识别模型尚未就绪");
        }
        if (file == null || file.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REQUEST",
                    "请上传有效音频文件");
        }
        if (file.getSize() > properties.getMaxUploadMb() * BYTES_PER_MB) {
            throw new ApiException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "FILE_TOO_LARGE",
                    "上传文件超过大小限制");
        }
    }

    private boolean acquireInferenceGate() {
        try {
            boolean acquired = inferenceGate.tryAcquire(
                    properties.getQueueTimeoutSeconds(),
                    TimeUnit.SECONDS);
            if (!acquired) {
                throw new ApiException(
                        HttpStatus.GATEWAY_TIMEOUT,
                        "PROCESSING_TIMEOUT",
                        "当前识别任务繁忙，请稍后重试");
            }
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "PROCESSING_TIMEOUT",
                    "识别请求被中断");
        }
    }

    private void deleteTemporaryFiles(Path requestDirectory) {
        if (requestDirectory == null) {
            return;
        }
        try {
            temporaryAudioStorage.deleteRequestDirectory(requestDirectory);
        } catch (IOException ignored) {
            // 清理失败不覆盖原始识别错误；该目录始终限制在项目 temp 根目录内。
        }
    }

    private double elapsedSeconds(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000_000.0;
    }
}
