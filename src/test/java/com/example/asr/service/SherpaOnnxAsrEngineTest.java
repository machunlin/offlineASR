package com.example.asr.service;

import com.example.asr.config.AsrProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SherpaOnnxAsrEngineTest {

    private static final String NATIVE_PATH_PROPERTY = "sherpa_onnx.native.path";

    @TempDir
    Path tempDirectory;

    private String previousNativePath;

    @BeforeEach
    void rememberNativePath() {
        previousNativePath = System.getProperty(NATIVE_PATH_PROPERTY);
    }

    @AfterEach
    void restoreNativePath() {
        if (previousNativePath == null) {
            System.clearProperty(NATIVE_PATH_PROPERTY);
        } else {
            System.setProperty(NATIVE_PATH_PROPERTY, previousNativePath);
        }
    }

    @Test
    void remainsNotReadyWhenModelFileIsMissing() {
        AsrProperties properties = propertiesFor(tempDirectory.resolve("models"));

        SherpaOnnxAsrEngine engine = new SherpaOnnxAsrEngine(properties);

        assertFalse(engine.isReady());
        assertTrue(engine.unavailableReason().contains("缺少 model.int8.onnx"));
    }

    @Test
    void rejectsMissingNativeDirectory() {
        Path models = prepareModelDirectory();
        System.setProperty(NATIVE_PATH_PROPERTY, tempDirectory.resolve("missing-native").toString());

        SherpaOnnxAsrEngine engine = new SherpaOnnxAsrEngine(propertiesFor(models));

        assertFalse(engine.isReady());
        assertTrue(engine.unavailableReason().contains("ASR_SHERPA_NATIVE_PATH 不是目录"));
    }

    @Test
    void rejectsNativeDirectoryWithoutBothRuntimeLibraries() throws IOException {
        Path models = prepareModelDirectory();
        Path nativeDirectory = Files.createDirectories(tempDirectory.resolve("native"));
        Files.createFile(nativeDirectory.resolve(System.mapLibraryName("onnxruntime")));
        System.setProperty(NATIVE_PATH_PROPERTY, nativeDirectory.toString());

        SherpaOnnxAsrEngine engine = new SherpaOnnxAsrEngine(propertiesFor(models));

        assertFalse(engine.isReady());
        assertTrue(engine.unavailableReason().contains("Native Runtime 文件缺失"));
    }

    @Test
    void rejectsModelDirectoryWithoutTokensFile() throws IOException {
        Path models = Files.createDirectories(tempDirectory.resolve("models"));
        Files.createFile(models.resolve("model.int8.onnx"));
        Path nativeDirectory = prepareNativeDirectory();
        System.setProperty(NATIVE_PATH_PROPERTY, nativeDirectory.toString());

        SherpaOnnxAsrEngine engine = new SherpaOnnxAsrEngine(propertiesFor(models));

        assertFalse(engine.isReady());
        assertTrue(engine.unavailableReason().contains("缺少 tokens.txt"));
    }

    private AsrProperties propertiesFor(Path modelDirectory) {
        AsrProperties properties = new AsrProperties();
        properties.setModelDirectory(modelDirectory.toString());
        return properties;
    }

    private Path prepareModelDirectory() {
        return tempDirectory.resolve("models");
    }

    private Path prepareNativeDirectory() throws IOException {
        Path nativeDirectory = Files.createDirectories(tempDirectory.resolve("native"));
        Files.createFile(nativeDirectory.resolve(System.mapLibraryName("onnxruntime")));
        Files.createFile(nativeDirectory.resolve(System.mapLibraryName("sherpa-onnx-jni")));
        return nativeDirectory;
    }
}
