package com.example.asr.controller;
import com.example.asr.dto.RecognitionResponse;
import com.example.asr.service.AsrService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import javax.servlet.http.HttpServletRequest;
import java.util.UUID;
@RestController
@RequestMapping("/api/asr")
public class AsrController {
    private final AsrService service;
    public AsrController(AsrService service){this.service=service;}
    @PostMapping
    public RecognitionResponse recognize(@RequestParam("file") MultipartFile file, @RequestHeader(value="X-Request-Id",required=false) String header, HttpServletRequest request){
        String id=header!=null&&header.matches("[A-Za-z0-9-]{1,64}")?header:UUID.randomUUID().toString(); request.setAttribute("requestId",id); return service.recognize(file,id);
    }
}
