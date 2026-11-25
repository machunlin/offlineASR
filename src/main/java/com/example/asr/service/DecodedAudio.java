package com.example.asr.service;

import java.nio.file.Path;

public class DecodedAudio {
    private final Path pcmPath; private final float[] samples; private final int sampleRate; private final double durationSeconds; private final double decodeSeconds;
    public DecodedAudio(Path pcmPath, float[] samples, int sampleRate, double durationSeconds, double decodeSeconds) {
        this.pcmPath = pcmPath; this.samples = samples; this.sampleRate = sampleRate; this.durationSeconds = durationSeconds; this.decodeSeconds = decodeSeconds;
    }
    public Path getPcmPath() { return pcmPath; }
    public float[] getSamples() { return samples; }
    public int getSampleRate() { return sampleRate; }
    public double getDurationSeconds() { return durationSeconds; }
    public double getDecodeSeconds() { return decodeSeconds; }
}
