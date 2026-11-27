package com.example.asr;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AsrApplicationTest {

    @TempDir
    Path tempDirectory;

    @AfterEach
    void clearNativePathProperty() {
        System.clearProperty(AsrApplication.SHERPA_NATIVE_PATH_PROPERTY);
    }

    @Test
    void mapsExternalNativePathEnvironmentSettingToSherpaProperty() {
        AsrApplication.configureSherpaNativePath(null, tempDirectory.toString());

        assertEquals(
                tempDirectory.toAbsolutePath().normalize().toString(),
                System.getProperty(AsrApplication.SHERPA_NATIVE_PATH_PROPERTY));
    }

    @Test
    void doesNotOverrideExplicitSherpaNativePathProperty() {
        System.setProperty(AsrApplication.SHERPA_NATIVE_PATH_PROPERTY, "/explicit/native/path");

        AsrApplication.configureSherpaNativePath(
                System.getProperty(AsrApplication.SHERPA_NATIVE_PATH_PROPERTY),
                tempDirectory.toString());

        assertEquals(
                "/explicit/native/path",
                System.getProperty(AsrApplication.SHERPA_NATIVE_PATH_PROPERTY));
    }

    @Test
    void ignoresBlankExternalNativePath() {
        AsrApplication.configureSherpaNativePath(null, "  ");

        assertEquals(null, System.getProperty(AsrApplication.SHERPA_NATIVE_PATH_PROPERTY));
    }
}
