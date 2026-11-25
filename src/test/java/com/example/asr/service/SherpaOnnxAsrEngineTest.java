package com.example.asr.service;

import com.example.asr.config.AsrProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SherpaOnnxAsrEngineTest {

    @TempDir
    Path emptyModelDirectory;

    @Test
    void remainsNotReadyWhenRequiredModelFilesAreMissing() {
        AsrProperties properties = new AsrProperties();
        properties.setModelDirectory(emptyModelDirectory.toString());

        SherpaOnnxAsrEngine engine = new SherpaOnnxAsrEngine(properties);

        assertFalse(engine.isReady());
        assertEquals("模型文件缺失", engine.unavailableReason());
    }
}
