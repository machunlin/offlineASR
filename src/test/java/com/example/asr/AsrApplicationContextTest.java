package com.example.asr;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 防止配置类、临时目录存储等多构造器组件再次出现 Spring 无法装配的问题。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AsrApplicationContextTest {

    @Test
    void applicationContextStartsWithoutModelFiles() {
        // 开发/CI 环境没有模型时允许应用上下文启动，健康检查将报告 NOT_READY。
    }
}
