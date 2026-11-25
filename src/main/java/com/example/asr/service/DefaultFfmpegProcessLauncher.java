package com.example.asr.service;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/** 生产环境的 FFmpeg 子进程启动器。 */
@Component
public class DefaultFfmpegProcessLauncher implements FfmpegProcessLauncher {

    @Override
    public Process start(List<String> command) throws IOException {
        return new ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
    }
}
