package com.example.asr.service;

import com.example.asr.config.AsrProperties;
import com.example.asr.dto.RecognitionResponse;
import com.example.asr.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AsrServiceTest {

    @TempDir
    Path temporaryRoot;

    @Test
    void rejectsRecognitionWhenTheEngineIsNotReady() {
        AsrProperties properties = new AsrProperties();
        AsrEngine engine = mock(AsrEngine.class);
        when(engine.isReady()).thenReturn(false);

        AsrService service = new AsrService(
                properties,
                new TemporaryAudioStorage(temporaryRoot),
                mock(FfmpegAudioDecoder.class),
                engine);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.recognize(audioFile(new byte[]{1}), "request-001"));

        assertEquals(503, exception.getStatus().value());
        assertEquals("SERVICE_NOT_READY", exception.getCode());
    }

    @Test
    void rejectsUploadLargerThanConfiguredLimitBeforeWritingTemporaryFiles() {
        AsrProperties properties = new AsrProperties();
        properties.setMaxUploadMb(1);
        AsrEngine engine = mock(AsrEngine.class);
        when(engine.isReady()).thenReturn(true);

        AsrService service = new AsrService(
                properties,
                new TemporaryAudioStorage(temporaryRoot),
                mock(FfmpegAudioDecoder.class),
                engine);

        MockMultipartFile largeFile = audioFile(new byte[1_048_577]);
        ApiException exception = assertThrows(ApiException.class,
                () -> service.recognize(largeFile, "request-002"));

        assertEquals(413, exception.getStatus().value());
        assertEquals("FILE_TOO_LARGE", exception.getCode());
    }

    /**
     * 真实端到端测试：读取仓库中的中文 WAV，执行 FFmpeg 解码和 SenseVoice JNI 推理，
     * 在 IDEA 测试控制台输出实际识别文本。运行前需准备模型、原生库和 FFmpeg。
     */
    @Test
    @EnabledIfSystemProperty(named = "asr.accuracy.enabled", matches = "true")
    void recognizesBundledChineseAudioAndPrintsRecognizedText() throws Exception {
        AsrProperties properties = new AsrProperties();
        properties.setModelDirectory(System.getenv().getOrDefault("ASR_MODEL_DIR", "models/SenseVoiceSmall"));
        properties.setFfmpegCommand(System.getenv().getOrDefault("ASR_FFMPEG", "ffmpeg"));
        properties.setMaxDurationSeconds(60);
        configureNativePathFromEnvironment();

        SherpaOnnxAsrEngine engine = new SherpaOnnxAsrEngine(properties);
        try {
            assertTrue(engine.isReady(),
                    "SenseVoice 模型或 Native Runtime 未就绪，请检查 ASR_MODEL_DIR 和 ASR_SHERPA_NATIVE_PATH");

            FfmpegAudioDecoder decoder = new FfmpegAudioDecoder(
                    properties,
                    command -> new ProcessBuilder(command).start());
            AsrService service = new AsrService(
                    properties,
                    new TemporaryAudioStorage(temporaryRoot.resolve("asr-temp")),
                    decoder,
                    engine);

            recognizeFixture(service, "hello_zh.wav", "chinese-short-001");
            recognizeFixture(service, "example_zh.wav", "chinese-sample-001");
        } finally {
            engine.close();
        }
    }

    private void configureNativePathFromEnvironment() {
        String nativePath = System.getenv("ASR_SHERPA_NATIVE_PATH");
        if (nativePath != null && !nativePath.isBlank()) {
            System.setProperty("sherpa_onnx.native.path",
                    Path.of(nativePath).toAbsolutePath().normalize().toString());
        }
    }

    private void recognizeFixture(AsrService service, String resourceName, String requestId) throws IOException {
        byte[] audioBytes;
        try (InputStream input = AsrServiceTest.class.getResourceAsStream(
                "/test-data/" + resourceName)) {
            assertNotNull(input, "测试音频资源不存在: " + resourceName);
            audioBytes = input.readAllBytes();
        }

        MockMultipartFile audio = new MockMultipartFile(
                "file", resourceName, "audio/wav", audioBytes);
        RecognitionResponse response = service.recognize(audio, requestId);

        assertNotNull(response);
        assertFalse(response.getText() == null || response.getText().trim().isEmpty(),
                "识别结果为空: " + resourceName);
        assertTrue(response.getAudioDuration() > 0, "音频时长无效: " + resourceName);

        System.out.printf(
                "%n========== ASR TEST RESULT ==========%n音频文件: %s%n音频大小: %d bytes%n音频时长: %.2f s%n识别结果: %s%n解码耗时: %.3f s%n推理耗时: %.3f s%n总耗时: %.3f s%n=====================================%n",
                resourceName,
                audioBytes.length,
                response.getAudioDuration(),
                response.getText(),
                response.getDecodeTime(),
                response.getInferenceTime(),
                response.getTotalTime());
    }

    private MockMultipartFile audioFile(byte[] content) {
        return new MockMultipartFile("file", "recording.webm", "audio/webm", content);
    }
}
