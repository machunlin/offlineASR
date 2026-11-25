package com.example.asr.controller;
import com.example.asr.config.AsrProperties;
import com.example.asr.service.AsrEngine;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.LinkedHashMap; import java.util.Map;
@RestController
public class AsrHealthController {
 private final AsrEngine engine; private final AsrProperties properties; private final long started=System.nanoTime();
 public AsrHealthController(AsrEngine engine,AsrProperties properties){this.engine=engine;this.properties=properties;}
 @GetMapping("/api/asr/health") public ResponseEntity<Map<String,Object>> health(){Map<String,Object> body=new LinkedHashMap<>();boolean ready=engine.isReady();body.put("success",ready);body.put("applicationStatus",ready?"READY":"NOT_READY");body.put("modelLoaded",ready);body.put("modelName","SenseVoiceSmall INT8");body.put("inferenceDevice","cpu");body.put("inferenceThreads",properties.getThreads());body.put("ffmpegAvailable",true);body.put("uptime",(System.nanoTime()-started)/1_000_000_000.0);return ResponseEntity.status(ready?200:503).body(body);}
}
