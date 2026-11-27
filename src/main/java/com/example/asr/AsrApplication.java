package com.example.asr;

import com.example.asr.config.AsrProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.nio.file.Path;

@SpringBootApplication
@EnableConfigurationProperties(AsrProperties.class)
@EnableScheduling
public class AsrApplication {

    /** sherpa-onnx Java Wrapper 支持的外部 Native 目录系统属性。 */
    static final String SHERPA_NATIVE_PATH_PROPERTY = "sherpa_onnx.native.path";
    /** 部署脚本和文档统一使用该环境变量指定外部 Native Runtime。 */
    static final String SHERPA_NATIVE_PATH_ENV = "ASR_SHERPA_NATIVE_PATH";

    public static void main(String[] args) {
        configureSherpaNativePath();
        SpringApplication.run(AsrApplication.class, args);
    }

    /**
     * 在 sherpa-onnx 类首次加载前设置外部 Native 目录。
     * 该目录由客户自行准备，项目不下载、不打包、不分发其中任何二进制文件。
     */
    static void configureSherpaNativePath() {
        configureSherpaNativePath(
                System.getProperty(SHERPA_NATIVE_PATH_PROPERTY),
                System.getenv(SHERPA_NATIVE_PATH_ENV));
    }

    static void configureSherpaNativePath(String currentProperty, String configuredPath) {
        if (currentProperty != null && !currentProperty.isBlank()) {
            return;
        }
        if (configuredPath == null || configuredPath.isBlank()) {
            return;
        }
        System.setProperty(
                SHERPA_NATIVE_PATH_PROPERTY,
                Path.of(configuredPath.trim()).toAbsolutePath().normalize().toString());
    }
}
