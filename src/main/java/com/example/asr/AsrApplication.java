package com.example.asr;

import com.example.asr.config.AsrProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AsrProperties.class)
public class AsrApplication {

    public static void main(String[] args) {
        SpringApplication.run(AsrApplication.class, args);
    }
}
