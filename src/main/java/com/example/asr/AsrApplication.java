package com.example.asr;

import com.example.asr.config.AsrProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(AsrProperties.class)
@EnableScheduling
public class AsrApplication {
    public static void main(String[] args) {
        SpringApplication.run(AsrApplication.class, args);
    }
}