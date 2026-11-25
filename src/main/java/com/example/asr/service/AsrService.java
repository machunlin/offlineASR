package com.example.asr.service;

import com.example.asr.config.AsrProperties;
import com.example.asr.dto.RecognitionResponse;
import com.example.asr.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException; import java.nio.file.Path; import java.util.concurrent.Semaphore; import java.util.concurrent.TimeUnit;

@Service
public class AsrService {
    private final AsrProperties properties; private final TemporaryAudioStorage storage; private final FfmpegAudioDecoder decoder; private final AsrEngine engine; private final Semaphore gate = new Semaphore(1);
    public AsrService(AsrProperties properties, FfmpegAudioDecoder decoder, AsrEngine engine) { this.properties=properties; this.decoder=decoder; this.engine=engine; this.storage=new TemporaryAudioStorage(Path.of(properties.getTempDirectory())); }
    public RecognitionResponse recognize(MultipartFile file, String requestId) {
        if (!engine.isReady()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"SERVICE_NOT_READY","识别模型尚未就绪");
        if (file == null || file.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","请上传有效音频文件");
        if (file.getSize() > properties.getMaxUploadMb()*1024L*1024L) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE,"FILE_TOO_LARGE","上传文件超过大小限制");
        long started=System.nanoTime(); Path dir=null; boolean acquired=false;
        try { acquired=gate.tryAcquire(properties.getQueueTimeoutSeconds(), TimeUnit.SECONDS); if(!acquired) throw new ApiException(HttpStatus.GATEWAY_TIMEOUT,"PROCESSING_TIMEOUT","当前识别任务繁忙，请稍后重试");
            dir=storage.createRequestDirectory(requestId); Path input=dir.resolve("input.audio"); file.transferTo(input); DecodedAudio audio=decoder.decode(input,dir.resolve("audio.pcm"));
            long inferenceStarted=System.nanoTime(); String text=engine.recognize(audio.getSamples(),audio.getSampleRate()); double inference=seconds(inferenceStarted); if(text==null||text.trim().isEmpty()) throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,"ASR_FAILED","未获得有效识别结果");
            return new RecognitionResponse(requestId,text,audio.getDurationSeconds(),audio.getDecodeSeconds(),inference,seconds(started));
        } catch(IOException exception) { throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","上传音频读取失败"); }
        catch(InterruptedException exception) { Thread.currentThread().interrupt(); throw new ApiException(HttpStatus.GATEWAY_TIMEOUT,"PROCESSING_TIMEOUT","识别请求被中断"); }
        finally { if(acquired) gate.release(); if(dir!=null) try { storage.deleteRequestDirectory(dir); } catch(IOException ignored) { } }
    }
    private double seconds(long start){return (System.nanoTime()-start)/1_000_000_000.0;}
}
