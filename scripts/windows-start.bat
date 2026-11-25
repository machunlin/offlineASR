@echo off
set ROOT=%~dp0..\
if not exist "%ROOT%app\offline-asr.jar" echo 缺少应用 JAR & exit /b 1
if not exist "%ROOT%models\SenseVoiceSmall\model.int8.onnx" echo 缺少模型文件 & exit /b 1
if not exist "%ROOT%ffmpeg\bin\ffmpeg.exe" echo 缺少 FFmpeg & exit /b 1
"%ROOT%jdk\bin\java.exe" -jar "%ROOT%app\offline-asr.jar" --spring.config.additional-location="%ROOT%config\application.yml"
