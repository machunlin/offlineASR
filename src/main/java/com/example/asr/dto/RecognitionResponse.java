package com.example.asr.dto;
public class RecognitionResponse {
    public final boolean success = true; public final String requestId; public final String text; public final double audioDuration; public final double decodeTime; public final double inferenceTime; public final double totalTime;
    public RecognitionResponse(String requestId, String text, double audioDuration, double decodeTime, double inferenceTime, double totalTime) { this.requestId=requestId;this.text=text;this.audioDuration=audioDuration;this.decodeTime=decodeTime;this.inferenceTime=inferenceTime;this.totalTime=totalTime; }
}
