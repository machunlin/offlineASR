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
        Path modelDirectory = Path.of(properties.getModelDirectory()).toAbsolutePath().normalize();
        Path modelPath = modelDirectory.resolve(MODEL_FILENAME);
        Path tokensPath = modelDirectory.resolve(TOKENS_FILENAME);

        if (!Files.isRegularFile(modelPath) || !Files.isRegularFile(tokensPath)) {
            unavailableReason = "模型文件缺失";
            log.error("SenseVoice model files are missing; modelDirectory={}", modelDirectory);
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
            unavailableReason = "模型或原生运行库初始化失败";
            log.error("Unable to initialize sherpa-onnx recognizer; modelDirectory={} errorType={}",
                    modelDirectory, exception.getClass().getName(), exception);
        }
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
