package com.example.asr.service;

import com.example.asr.config.AsrProperties;
import com.k2fsa.sherpa.onnx.OfflineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig;
import com.k2fsa.sherpa.onnx.OfflineStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.nio.file.Files;
import java.nio.file.Path;

/** sherpa-onnx SenseVoice 离线识别器的 Spring 生命周期适配器。 */
@Component
public class SherpaOnnxAsrEngine implements AsrEngine {

    private static final Logger log = LoggerFactory.getLogger(SherpaOnnxAsrEngine.class);
    private static final String MODEL_FILENAME = "model.int8.onnx";
    private static final String TOKENS_FILENAME = "tokens.txt";
    private static final String DECODING_METHOD = "greedy_search";

    private OfflineRecognizer recognizer;
    private String unavailableReason;

    public SherpaOnnxAsrEngine(AsrProperties properties) {
        initializeRecognizer(properties);
    }

    @Override
    public boolean isReady() {
        return recognizer != null;
    }

    @Override
    public String unavailableReason() {
        return unavailableReason;
    }

    @Override
    public String recognize(float[] samples, int sampleRate) {
        if (!isReady()) {
            throw new IllegalStateException(unavailableReason);
        }

        OfflineStream stream = recognizer.createStream();
        try {
            stream.acceptWaveform(samples, sampleRate);
            recognizer.decode(stream);
            return recognizer.getResult(stream).getText();
        } finally {
            stream.release();
        }
    }

    /** 模型只在 Spring 启动时创建一次；每个请求仅创建轻量 stream。 */
    private void initializeRecognizer(AsrProperties properties) {
        if (!validateExternalNativeRuntime()) {
            return;
        }

        Path modelDirectory = Path.of(properties.getModelDirectory()).toAbsolutePath().normalize();
        Path modelPath = modelDirectory.resolve(MODEL_FILENAME);
        Path tokensPath = modelDirectory.resolve(TOKENS_FILENAME);

        if (!Files.isRegularFile(modelPath)) {
            unavailableReason = "缺少 model.int8.onnx；请按 docs/08-第三方组件与许可证清单.md 下载 "
                    + "SenseVoiceSmall INT8 2025-09-09 并设置 ASR_MODEL_DIR";
            log.error("SenseVoice model file is missing; modelDirectory={}", modelDirectory);
            return;
        }
        if (!Files.isRegularFile(tokensPath)) {
            unavailableReason = "缺少 tokens.txt；请解压 SenseVoiceSmall INT8 模型包并检查 ASR_MODEL_DIR";
            log.error("SenseVoice tokens file is missing; modelDirectory={}", modelDirectory);
            return;
        }

        try {
            OfflineSenseVoiceModelConfig senseVoiceConfig = OfflineSenseVoiceModelConfig.builder()
                    .setModel(modelPath.toString())
                    .build();
            OfflineModelConfig modelConfig = OfflineModelConfig.builder()
                    .setSenseVoice(senseVoiceConfig)
                    .setTokens(tokensPath.toString())
                    .setNumThreads(properties.getThreads())
                    .setDebug(false)
                    .build();
            OfflineRecognizerConfig recognizerConfig = OfflineRecognizerConfig.builder()
                    .setOfflineModelConfig(modelConfig)
                    .setDecodingMethod(DECODING_METHOD)
                    .build();
            recognizer = new OfflineRecognizer(recognizerConfig);
            log.info("SenseVoice recognizer initialized modelDirectory={} threads={}",
                    modelDirectory, properties.getThreads());
        } catch (RuntimeException | LinkageError exception) {
            // JNI 加载失败通常是 LinkageError，不是 RuntimeException。
            unavailableReason = "sherpa-onnx Native Runtime 或模型初始化失败；"
                    + "请检查 ASR_SHERPA_NATIVE_PATH、文件架构与模型文件";
            log.error("Unable to initialize sherpa-onnx recognizer; modelDirectory={} errorType={}",
                    modelDirectory, exception.getClass().getName(), exception);
        }
    }

    private boolean validateExternalNativeRuntime() {
        String configuredPath = System.getProperty("sherpa_onnx.native.path");
        if (configuredPath == null || configuredPath.isBlank()) {
            // 开发环境可以由外部 classpath 提供 Native JAR；应用包不含 Native Runtime。
            return true;
        }

        Path nativeDirectory = Path.of(configuredPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(nativeDirectory)) {
            unavailableReason = "ASR_SHERPA_NATIVE_PATH 不是目录：" + nativeDirectory;
            return false;
        }

        Path onnxRuntime = nativeDirectory.resolve(System.mapLibraryName("onnxruntime"));
        Path sherpaJni = nativeDirectory.resolve(System.mapLibraryName("sherpa-onnx-jni"));
        if (!Files.isRegularFile(onnxRuntime) || !Files.isRegularFile(sherpaJni)) {
            unavailableReason = "Native Runtime 文件缺失；目录必须同时包含 "
                    + onnxRuntime.getFileName() + " 和 " + sherpaJni.getFileName();
            return false;
        }
        return true;
    }

    @Override
    @PreDestroy
    public void close() {
        if (recognizer != null) {
            recognizer.release();
            recognizer = null;
            log.info("SenseVoice recognizer released");
        }
    }
}
