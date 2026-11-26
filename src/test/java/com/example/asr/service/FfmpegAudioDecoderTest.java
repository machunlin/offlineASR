package com.example.asr.service;

import com.example.asr.config.AsrProperties;
import com.example.asr.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FfmpegAudioDecoderTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void buildsBoundedFfmpegCommandAndReturnsFloat32Pcm() throws Exception {
        AsrProperties properties = new AsrProperties();
        CapturingProcessLauncher launcher = new CapturingProcessLauncher(false, 0, 4);
        FfmpegAudioDecoder decoder = new FfmpegAudioDecoder(properties, launcher);
        Path input = Files.write(temporaryDirectory.resolve("input.webm"), new byte[]{1});
        Path output = temporaryDirectory.resolve("audio.pcm");

        DecodedAudio decoded = decoder.decode(input, output);

        assertEquals(List.of(
                "ffmpeg", "-y", "-v", "error", "-i", input.toString(),
                "-t", "601", "-ac", "1", "-ar", "16000", "-f", "f32le", output.toString()),
                launcher.command);
        assertEquals(16_000, decoded.getSampleRate());
        assertEquals(1, decoded.getSamples().length);
    }

    @Test
    void rejectsPcmLargerThanTheConfiguredDurationBeforeReadingIt() throws Exception {
        AsrProperties properties = new AsrProperties();
        CapturingProcessLauncher launcher = new CapturingProcessLauncher(
                false, 0, (601 * 16_000 * Float.BYTES) + 4);
        FfmpegAudioDecoder decoder = new FfmpegAudioDecoder(properties, launcher);
        Path input = Files.write(temporaryDirectory.resolve("long-input.webm"), new byte[]{1});

        ApiException exception = assertThrows(ApiException.class,
                () -> decoder.decode(input, temporaryDirectory.resolve("long-audio.pcm")));

        assertEquals(413, exception.getStatus().value());
        assertEquals("AUDIO_DURATION_EXCEEDED", exception.getCode());
    }

    @Test
    void destroysDecoderProcessWhenItExceedsTimeout() throws Exception {
        AsrProperties properties = new AsrProperties();
        CapturingProcessLauncher launcher = new CapturingProcessLauncher(true, 0, 0);
        FfmpegAudioDecoder decoder = new FfmpegAudioDecoder(properties, launcher);
        Path input = Files.write(temporaryDirectory.resolve("timeout-input.webm"), new byte[]{1});

        ApiException exception = assertThrows(ApiException.class,
                () -> decoder.decode(input, temporaryDirectory.resolve("timeout-audio.pcm")));

        assertEquals(504, exception.getStatus().value());
        assertEquals("PROCESSING_TIMEOUT", exception.getCode());
        assertEquals(true, launcher.process.destroyed);
    }

    private static final class CapturingProcessLauncher implements FfmpegProcessLauncher {
        private final boolean timedOut;
        private final int exitCode;
        private final int outputBytes;
        private List<String> command;
        private FakeProcess process;

        private CapturingProcessLauncher(boolean timedOut, int exitCode, int outputBytes) {
            this.timedOut = timedOut;
            this.exitCode = exitCode;
            this.outputBytes = outputBytes;
        }

        @Override
        public Process start(List<String> command) throws IOException {
            this.command = command;
            Path output = Path.of(command.get(command.size() - 1));
            if (!timedOut) {
                Files.write(output, new byte[outputBytes]);
            }
            process = new FakeProcess(timedOut, exitCode);
            return process;
        }
    }

    private static final class FakeProcess extends Process {
        private final boolean timedOut;
        private final int exitCode;
        private boolean destroyed;

        private FakeProcess(boolean timedOut, int exitCode) {
            this.timedOut = timedOut;
            this.exitCode = exitCode;
        }

        @Override public OutputStream getOutputStream() { return new ByteArrayOutputStream(); }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public InputStream getErrorStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public int waitFor() { return exitCode; }
        @Override public boolean waitFor(long timeout, java.util.concurrent.TimeUnit unit) { return !timedOut; }
        @Override public int exitValue() { return exitCode; }
        @Override public void destroy() { destroyed = true; }
        @Override public Process destroyForcibly() { destroyed = true; return this; }
        @Override public boolean isAlive() { return timedOut && !destroyed; }
    }
}
