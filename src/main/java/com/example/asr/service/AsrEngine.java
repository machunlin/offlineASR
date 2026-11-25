package com.example.asr.service;

public interface AsrEngine extends AutoCloseable {
    boolean isReady();
    String unavailableReason();
    String recognize(float[] samples, int sampleRate);
    @Override void close();
}
