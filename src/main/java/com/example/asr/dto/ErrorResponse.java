package com.example.asr.dto;

public class ErrorResponse {
    public final boolean success = false;
    public final String requestId;
    public final String errorCode;
    public final String message;
    public ErrorResponse(String requestId, String errorCode, String message) {
        this.requestId = requestId; this.errorCode = errorCode; this.message = message;
    }
}
