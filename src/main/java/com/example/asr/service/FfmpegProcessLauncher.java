package com.example.asr.service;

import java.io.IOException;
import java.util.List;

/** 隔离 FFmpeg 进程创建，使命令契约与超时逻辑可独立测试。 */
public interface FfmpegProcessLauncher {

    Process start(List<String> command) throws IOException;
}
