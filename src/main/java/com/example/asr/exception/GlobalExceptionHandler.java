package com.example.asr.exception;

import com.example.asr.dto.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletRequest;
import java.util.UUID;

/** 将可预期和未预期异常都收敛为统一、安全的 JSON 契约。 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String REQUEST_ID_ATTRIBUTE = "requestId";

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(
            ApiException exception,
            HttpServletRequest request) {
        return ResponseEntity.status(exception.getStatus())
                .body(new ErrorResponse(
                        requestId(request),
                        exception.getCode(),
                        exception.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(HttpServletRequest request) {
        // 不向浏览器返回 Java 异常、JNI 错误或本地文件路径。
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse(
                        requestId(request),
                        "ASR_FAILED",
                        "语音识别处理失败，请稍后重试"));
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(REQUEST_ID_ATTRIBUTE);
        return value == null ? UUID.randomUUID().toString() : value.toString();
    }
}
