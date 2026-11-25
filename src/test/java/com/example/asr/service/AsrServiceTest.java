package com.example.asr.service;

import com.example.asr.config.AsrProperties;
import com.example.asr.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    private MockMultipartFile audioFile(byte[] content) {
        return new MockMultipartFile("file", "recording.webm", "audio/webm", content);
    }
}
