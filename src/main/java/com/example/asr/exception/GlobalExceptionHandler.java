package com.example.asr.exception;

import com.example.asr.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletRequest;
import java.util.UUID;

/** 将可预期和未预期异常都收敛为统一、安全的 JSON 契约。 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String REQUEST_ID_ATTRIBUTE = "requestId";

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(
            ApiException exception,
            HttpServletRequest request) {
        log.warn("ASR request rejected requestId={} code={} status={}",
                requestId(request), exception.getCode(), exception.getStatus().value());
        return ResponseEntity.status(exception.getStatus())
                .body(new ErrorResponse(
                        requestId(request),
                        exception.getCode(),
                        exception.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request) {
        String requestId = requestId(request);
        // 完整异常仅写入本机日志，不向浏览器返回 Java/JNI 异常或本地路径。
        log.error("Unexpected ASR request failure requestId={} exceptionType={}",
                requestId, exception.getClass().getName(), exception);
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse(
                        requestId,
                        "ASR_FAILED",
                        "语音识别处理失败，请稍后重试"));
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(REQUEST_ID_ATTRIBUTE);
        return value == null ? UUID.randomUUID().toString() : value.toString();
    }
}
