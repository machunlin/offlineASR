package com.example.asr.service;

import com.example.asr.config.AsrProperties;
import com.example.asr.dto.RecognitionResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 基于固定测试集执行真实语音识别，并按字符编辑距离计算 CER。
 */
class AsrAccuracyTest {

    private static final Path MANIFEST_PATH =
            Path.of("src/test/resources/test-data/manifest.tsv");

    @TempDir
    Path temporaryRoot;

    @Test
    @EnabledIfSystemProperty(named = "asr.accuracy.enabled", matches = "true")
    void evaluatesAllAudioSamplesAndCalculatesCer() throws Exception {
        List<TestSample> samples = readManifest();
        assertEquals(15, samples.size(), "测试集必须包含 15 条样本");

        AsrProperties properties = new AsrProperties();
        properties.setMaxDurationSeconds(60);

        SherpaOnnxAsrEngine engine = new SherpaOnnxAsrEngine(properties);
        try {
            assertTrue(engine.isReady(),
                    "SenseVoice 模型或 sherpa-onnx 原生库未就绪，请检查模型目录和当前平台 native JAR");

            FfmpegAudioDecoder decoder = new FfmpegAudioDecoder(
                    properties,
                    command -> new ProcessBuilder(command).start());
            AsrService service = new AsrService(
                    properties,
                    new TemporaryAudioStorage(temporaryRoot.resolve("asr-temp")),
                    decoder,
                    engine);

            long totalEdits = 0;
            long totalReferenceCharacters = 0;

            for (TestSample sample : samples) {
                byte[] audioBytes = Files.readAllBytes(sample.audioPath);
                assertTrue(audioBytes.length > 0, "音频文件为空：" + sample.fileName);
                assertEquals(sample.sha256, sha256(audioBytes),
                        "音频 SHA-256 与清单不一致：" + sample.fileName);

                MockMultipartFile audio = new MockMultipartFile(
                        "file",
                        sample.fileName,
                        contentType(sample.fileName),
                        audioBytes);
                RecognitionResponse response = service.recognize(audio, "accuracy-" + sample.id);

                assertNotNull(response, "识别响应为空：" + sample.fileName);
                assertFalse(response.getText() == null || response.getText().trim().isEmpty(),
                        "识别结果为空：" + sample.fileName);
                assertTrue(response.getAudioDuration() > 0,
                        "识别返回的音频时长无效：" + sample.fileName);
                assertTrue(Math.abs(response.getAudioDuration() - sample.duration) < 1.0,
                        "识别音频时长与清单差异过大：" + sample.fileName);

                int[] reference = normalize(sample.reference);
                int[] hypothesis = normalize(response.getText());
                assertTrue(reference.length > 0, "参考文本为空：" + sample.fileName);

                int edits = editDistance(reference, hypothesis);
                double cer = edits / (double) reference.length;
                totalEdits += edits;
                totalReferenceCharacters += reference.length;

                System.out.printf(
                        "%n========== 语音准确率测试 ==========%n样本：%s%n文件：%s%n采样率：%d Hz%n音频时长：%.3f 秒%n参考文本：%s%n识别文本：%s%n编辑距离：%d%nCER：%.2f%%%n解码耗时：%.3f 秒%n推理耗时：%.3f 秒%n总耗时：%.3f 秒%n====================================%n",
                        sample.id,
                        sample.fileName,
                        sample.sampleRate,
                        response.getAudioDuration(),
                        sample.reference,
                        response.getText(),
                        edits,
                        cer * 100.0,
                        response.getDecodeTime(),
                        response.getInferenceTime(),
                        response.getTotalTime());
            }

            double corpusCer = totalEdits / (double) totalReferenceCharacters;
            System.out.printf(
                    "%n========== 测试集汇总 ==========%n样本数量：%d%n参考字符数：%d%n总编辑距离：%d%n整体 CER：%.2f%%%n==============================%n",
                    samples.size(),
                    totalReferenceCharacters,
                    totalEdits,
                    corpusCer * 100.0);
        } finally {
            engine.close();
        }
    }

    private List<TestSample> readManifest() throws IOException {
        assertTrue(Files.isRegularFile(MANIFEST_PATH),
                "测试集清单不存在，请先执行 scripts/download-test-data.sh 或 scripts/download-test-data.ps1");

        List<String> lines = Files.readAllLines(MANIFEST_PATH, StandardCharsets.UTF_8);
        assertTrue(lines.size() > 1, "测试集清单没有样本数据");

        List<TestSample> samples = new ArrayList<>();
        for (int lineNumber = 1; lineNumber < lines.size(); lineNumber++) {
            String line = lines.get(lineNumber);
            if (line.trim().isEmpty()) {
                continue;
            }

            String[] fields = line.split("\t", -1);
            assertEquals(13, fields.length, "清单列数错误，行号：" + (lineNumber + 1));

            String id = fields[0];
            String fileName = fields[1];
            String reference = fields[2];
            int sampleRate = Integer.parseInt(fields[10]);
            double duration = Double.parseDouble(fields[11]);
            String sha256 = fields[12];

            assertTrue(sampleRate > 0, "采样率无效：" + fileName);
            assertTrue(duration > 0, "音频时长无效：" + fileName);
            assertTrue(sha256.matches("[0-9a-f]{64}"), "SHA-256 格式无效：" + fileName);
            assertFalse(reference.trim().isEmpty(), "参考文本为空：" + fileName);

            Path audioPath = MANIFEST_PATH.getParent().resolve(fileName);
            assertTrue(Files.isRegularFile(audioPath), "音频文件不存在：" + audioPath);
            samples.add(new TestSample(
                    id, fileName, reference, sampleRate, duration, sha256, audioPath));
        }
        return samples;
    }

    private String contentType(String fileName) {
        return fileName.toLowerCase(Locale.ROOT).endsWith(".mp3")
                ? "audio/mpeg"
                : "audio/wav";
    }

    private String sha256(byte[] content) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
        StringBuilder result = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }
        return result.toString();
    }

    private int[] normalize(String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        return normalized.codePoints()
                .filter(codePoint -> !Character.isWhitespace(codePoint))
                .filter(codePoint -> !isPunctuation(codePoint))
                .toArray();
    }

    private boolean isPunctuation(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.CONNECTOR_PUNCTUATION
                || type == Character.DASH_PUNCTUATION
                || type == Character.START_PUNCTUATION
                || type == Character.END_PUNCTUATION
                || type == Character.INITIAL_QUOTE_PUNCTUATION
                || type == Character.FINAL_QUOTE_PUNCTUATION
                || type == Character.OTHER_PUNCTUATION;
    }

    private int editDistance(int[] reference, int[] hypothesis) {
        int[] previous = new int[hypothesis.length + 1];
        int[] current = new int[hypothesis.length + 1];

        for (int column = 0; column <= hypothesis.length; column++) {
            previous[column] = column;
        }

        for (int row = 1; row <= reference.length; row++) {
            current[0] = row;
            for (int column = 1; column <= hypothesis.length; column++) {
                int substitutionCost = reference[row - 1] == hypothesis[column - 1] ? 0 : 1;
                current[column] = Math.min(
                        Math.min(current[column - 1] + 1, previous[column] + 1),
                        previous[column - 1] + substitutionCost);
            }
            int[] temporary = previous;
            previous = current;
            current = temporary;
        }
        return previous[hypothesis.length];
    }

    private static final class TestSample {
        private final String id;
        private final String fileName;
        private final String reference;
        private final int sampleRate;
        private final double duration;
        private final String sha256;
        private final Path audioPath;

        private TestSample(String id, String fileName, String reference,
                           int sampleRate, double duration, String sha256, Path audioPath) {
            this.id = id;
            this.fileName = fileName;
            this.reference = reference;
            this.sampleRate = sampleRate;
            this.duration = duration;
            this.sha256 = sha256;
            this.audioPath = audioPath;
        }
    }
}
