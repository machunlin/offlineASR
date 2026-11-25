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

@Component
public class SherpaOnnxAsrEngine implements AsrEngine {
    private OfflineRecognizer recognizer;
    private String unavailableReason;
    public SherpaOnnxAsrEngine(AsrProperties properties) {
        Path directory = Path.of(properties.getModelDirectory()).toAbsolutePath().normalize();
        Path model = directory.resolve("model.int8.onnx"); Path tokens = directory.resolve("tokens.txt");
        if (!Files.isRegularFile(model) || !Files.isRegularFile(tokens)) { unavailableReason = "模型文件缺失"; return; }
        try {
            OfflineSenseVoiceModelConfig senseVoice = OfflineSenseVoiceModelConfig.builder().setModel(model.toString()).build();
            OfflineModelConfig modelConfig = OfflineModelConfig.builder().setSenseVoice(senseVoice).setTokens(tokens.toString()).setNumThreads(properties.getThreads()).setDebug(false).build();
            recognizer = new OfflineRecognizer(OfflineRecognizerConfig.builder().setOfflineModelConfig(modelConfig).setDecodingMethod("greedy_search").build());
        } catch (RuntimeException exception) { unavailableReason = "模型或原生运行库初始化失败"; }
    }
    public boolean isReady() { return recognizer != null; }
    public String unavailableReason() { return unavailableReason; }
    public String recognize(float[] samples, int sampleRate) {
        if (!isReady()) throw new IllegalStateException(unavailableReason);
        OfflineStream stream = recognizer.createStream();
        try { stream.acceptWaveform(samples, sampleRate); recognizer.decode(stream); return recognizer.getResult(stream).getText(); }
        finally { stream.release(); }
    }
    @PreDestroy public void close() { if (recognizer != null) { recognizer.release(); recognizer = null; } }
}
