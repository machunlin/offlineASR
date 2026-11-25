package com.example.asr.exception;

import org.springframework.http.HttpStatus;

/** 可预期的业务失败，统一映射为不泄露底层细节的 HTTP 响应。 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
