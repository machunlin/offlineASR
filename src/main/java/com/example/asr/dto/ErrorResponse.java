package com.example.asr.dto;

/** 对浏览器安全暴露的错误响应，不包含异常栈、绝对路径或音频内容。 */
public class ErrorResponse {

    private final boolean success = false;
    private final String requestId;
    private final String errorCode;
    private final String message;

    public ErrorResponse(String requestId, String errorCode, String message) {
        this.requestId = requestId;
        this.errorCode = errorCode;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public String getRequestId() { return requestId; }
    public String getErrorCode() { return errorCode; }
    public String getMessage() { return message; }
}
