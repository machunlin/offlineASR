package com.example.asr.service;

import com.example.asr.config.AsrProperties;
import com.k2fsa.sherpa.onnx.OfflineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig;
import com.k2fsa.sherpa.onnx.OfflineStream;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.nio.file.Files;
import java.nio.file.Path;

/** sherpa-onnx SenseVoice 离线识别器的 Spring 生命周期适配器。 */
@Component
public class SherpaOnnxAsrEngine implements AsrEngine {

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

    /**
     * 模型只在 Spring 启动时创建一次；每个请求仅创建轻量 stream，避免重复加载 ONNX 模型。
     */
    private void initializeRecognizer(AsrProperties properties) {
        Path modelDirectory = Path.of(properties.getModelDirectory()).toAbsolutePath().normalize();
        Path modelPath = modelDirectory.resolve(MODEL_FILENAME);
        Path tokensPath = modelDirectory.resolve(TOKENS_FILENAME);

        if (!Files.isRegularFile(modelPath) || !Files.isRegularFile(tokensPath)) {
            unavailableReason = "模型文件缺失";
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
        } catch (RuntimeException exception) {
            // 不能将 JNI/ONNX 的底层异常或路径暴露给 HTTP 客户端。
            unavailableReason = "模型或原生运行库初始化失败";
        }
    }

    @Override
    @PreDestroy
    public void close() {
        if (recognizer != null) {
            recognizer.release();
            recognizer = null;
        }
    }
}
