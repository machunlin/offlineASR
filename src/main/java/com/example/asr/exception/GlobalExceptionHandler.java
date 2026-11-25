package com.example.asr.exception;

import com.example.asr.dto.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletRequest;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handle(ApiException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.getStatus()).body(new ErrorResponse(requestId(request), exception.getCode(), exception.getMessage()));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(HttpServletRequest request) {
        return ResponseEntity.internalServerError().body(new ErrorResponse(requestId(request), "ASR_FAILED", "语音识别处理失败，请稍后重试"));
    }
    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? UUID.randomUUID().toString() : value.toString();
    }
}
