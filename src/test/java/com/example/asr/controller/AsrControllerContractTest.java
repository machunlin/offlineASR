package com.example.asr.controller;

import com.example.asr.dto.RecognitionResponse;
import com.example.asr.exception.ApiException;
import com.example.asr.exception.GlobalExceptionHandler;
import com.example.asr.service.AsrService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AsrControllerContractTest {

    @Test
    void returnsStableSuccessJsonAndPreservesValidRequestId() throws Exception {
        AsrService service = mock(AsrService.class);
        when(service.recognize(any(), eq("contract-001"))).thenReturn(
                new RecognitionResponse("contract-001", "测试文本", 1.2, 0.1, 0.2, 0.3));

        mvc(service).perform(multipart("/api/asr")
                        .file(audioFile())
                        .header("X-Request-Id", "contract-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.requestId").value("contract-001"))
                .andExpect(jsonPath("$.text").value("测试文本"))
                .andExpect(jsonPath("$.audioDuration").value(1.2))
                .andExpect(jsonPath("$.decodeTime").value(0.1))
                .andExpect(jsonPath("$.inferenceTime").value(0.2))
                .andExpect(jsonPath("$.totalTime").value(0.3));
    }

    @Test
    void mapsBusinessFailureToSafeErrorJson() throws Exception {
        AsrService service = mock(AsrService.class);
        doThrow(new ApiException(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "FILE_TOO_LARGE",
                "上传文件超过大小限制"))
                .when(service).recognize(any(), any());

        mvc(service).perform(multipart("/api/asr").file(audioFile()))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("FILE_TOO_LARGE"))
                .andExpect(jsonPath("$.message").value("上传文件超过大小限制"));
    }

    private MockMvc mvc(AsrService service) {
        return MockMvcBuilders.standaloneSetup(new AsrController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private MockMultipartFile audioFile() {
        return new MockMultipartFile(
                "file",
                "recording.webm",
                MediaType.valueOf("audio/webm").toString(),
                new byte[]{1});
    }
}
