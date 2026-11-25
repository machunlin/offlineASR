package com.example.asr.dto;

/** 成功识别后的稳定 REST 响应。所有耗时字段均以秒为单位。 */
public class RecognitionResponse {

    private final boolean success = true;
    private final String requestId;
    private final String text;
    private final double audioDuration;
    private final double decodeTime;
    private final double inferenceTime;
    private final double totalTime;

    public RecognitionResponse(String requestId, String text, double audioDuration,
                               double decodeTime, double inferenceTime, double totalTime) {
        this.requestId = requestId;
        this.text = text;
        this.audioDuration = audioDuration;
        this.decodeTime = decodeTime;
        this.inferenceTime = inferenceTime;
        this.totalTime = totalTime;
    }

    public boolean isSuccess() { return success; }
    public String getRequestId() { return requestId; }
    public String getText() { return text; }
    public double getAudioDuration() { return audioDuration; }
    public double getDecodeTime() { return decodeTime; }
    public double getInferenceTime() { return inferenceTime; }
    public double getTotalTime() { return totalTime; }
}
