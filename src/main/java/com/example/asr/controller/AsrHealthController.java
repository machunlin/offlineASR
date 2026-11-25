package com.example.asr.controller;

import com.example.asr.config.AsrProperties;
import com.example.asr.service.AsrEngine;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** 暴露不含敏感路径和模型内容的本机诊断信息。 */
@RestController
public class AsrHealthController {

    private static final String MODEL_NAME = "SenseVoiceSmall INT8";
    private static final String INFERENCE_DEVICE = "cpu";

    private final AsrEngine asrEngine;
    private final AsrProperties properties;
    private final long startedAt = System.nanoTime();

    public AsrHealthController(AsrEngine asrEngine, AsrProperties properties) {
        this.asrEngine = asrEngine;
        this.properties = properties;
    }

    @GetMapping("/api/asr/health")
    public ResponseEntity<Map<String, Object>> health() {
        boolean modelLoaded = asrEngine.isReady();
        boolean ffmpegAvailable = isFfmpegAvailable();
        boolean ready = modelLoaded && ffmpegAvailable;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", ready);
        response.put("applicationStatus", ready ? "READY" : "NOT_READY");
        response.put("modelLoaded", modelLoaded);
        response.put("modelName", MODEL_NAME);
        response.put("inferenceDevice", INFERENCE_DEVICE);
        response.put("inferenceThreads", properties.getThreads());
        response.put("ffmpegAvailable", ffmpegAvailable);
        response.put("uptime", elapsedSeconds());

        return ResponseEntity.status(ready ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(response);
    }

    private boolean isFfmpegAvailable() {
        try {
            Process process = new ProcessBuilder(properties.getFfmpegCommand(), "-version")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private double elapsedSeconds() {
        return (System.nanoTime() - startedAt) / 1_000_000_000.0;
    }
}
