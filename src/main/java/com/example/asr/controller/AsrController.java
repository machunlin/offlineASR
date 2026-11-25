package com.example.asr.controller;

import com.example.asr.dto.RecognitionResponse;
import com.example.asr.service.AsrService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.UUID;

/** 接收浏览器录音并返回一次性最终识别结果。 */
@RestController
@RequestMapping("/api/asr")
public class AsrController {

    private static final String REQUEST_ID_ATTRIBUTE = "requestId";
    private static final String REQUEST_ID_PATTERN = "[A-Za-z0-9-]{1,64}";

    private final AsrService asrService;

    public AsrController(AsrService asrService) {
        this.asrService = asrService;
    }

    @PostMapping
    public RecognitionResponse recognize(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-Request-Id", required = false) String requestIdHeader,
            HttpServletRequest request) {
        String requestId = resolveRequestId(requestIdHeader);
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        return asrService.recognize(file, requestId);
    }

    private String resolveRequestId(String requestIdHeader) {
        if (requestIdHeader != null && requestIdHeader.matches(REQUEST_ID_PATTERN)) {
            return requestIdHeader;
        }
        return UUID.randomUUID().toString();
    }
}
