(() => {
    const PREFERRED_MIME_TYPE = 'audio/webm;codecs=opus';
    const DEFAULT_ERROR_CODE = 'ASR_FAILED';
    const DEFAULT_ERROR_MESSAGE = '识别失败';
    const MAX_RECORDING_SECONDS = 60;
    const DEVICE_STORAGE_KEY = 'offline-asr-microphone-device';

    let recorder;
    let mediaStream;
    let chunks = [];
    let countdownTimer;
    let remainingSeconds = MAX_RECORDING_SECONDS;
    let audioContext;
    let analyser;
    let sampleBuffer;
    let animationFrameId;
    let hasDetectedSound = false;
    let recordingInProgress = false;

    const statusElement = document.querySelector('#status');
    const resultElement = document.querySelector('#result');
    const resultMetaElement = document.querySelector('#result-meta');
    const startButton = document.querySelector('#start');
    const stopButton = document.querySelector('#stop');
    const clearButton = document.querySelector('#clear');
    const countdownElement = document.querySelector('#countdown');
    const localFileHint = document.querySelector('#local-file-hint');
    const audioFileInput = document.querySelector('#audio-file');
    const uploadButton = document.querySelector('#upload');
    const fileInfoElement = document.querySelector('#file-info');
    const deviceSelect = document.querySelector('#microphone-device');
    const refreshDevicesButton = document.querySelector('#refresh-microphones');
    const microphoneHint = document.querySelector('#microphone-hint');
    const inputMeterFill = document.querySelector('#input-meter-fill');
    const microphoneState = document.querySelector('#microphone-state');

    function openedFromLocalFile() {
        return window.location && window.location.protocol === 'file:';
    }

    function localFileError() {
        return {
            code: 'LOCAL_FILE_OPEN',
            message: '请先启动识别服务，再通过 http://127.0.0.1:8080/ 打开页面',
        };
    }

    function showLocalFileHint() {
        if (localFileHint) localFileHint.hidden = false;
        showError(localFileError().message);
    }

    function showError(message) {
        statusElement.textContent = message;
        startButton.disabled = false;
        stopButton.disabled = true;
        uploadButton.disabled = !audioFileInput.files || audioFileInput.files.length === 0;
        stopCountdown();
    }

    function renderCountdown() {
        if (countdownElement) countdownElement.textContent = remainingSeconds + ' 秒';
    }

    function stopCountdown() {
        if (countdownTimer) {
            window.clearInterval(countdownTimer);
            countdownTimer = null;
        }
        remainingSeconds = MAX_RECORDING_SECONDS;
        renderCountdown();
    }

    function startCountdown() {
        remainingSeconds = MAX_RECORDING_SECONDS;
        renderCountdown();
        countdownTimer = window.setInterval(() => {
            remainingSeconds = Math.max(0, remainingSeconds - 1);
            renderCountdown();
            if (remainingSeconds === 0) {
                statusElement.textContent = '已达到 60 秒上限，正在停止录音';
                stopRecording();
            }
        }, 1000);
    }

    function stopVolumeMonitor() {
        if (animationFrameId !== undefined && animationFrameId !== null
            && typeof window.cancelAnimationFrame === 'function') {
            window.cancelAnimationFrame(animationFrameId);
        }
        animationFrameId = null;
        if (audioContext) {
            audioContext.close().catch(() => {});
            audioContext = null;
        }
        analyser = null;
        sampleBuffer = null;
        if (inputMeterFill) inputMeterFill.style.width = '0%';
    }

    function stopTracks() {
        stopVolumeMonitor();
        if (mediaStream) {
            mediaStream.getTracks().forEach(track => track.stop());
            mediaStream = null;
        }
        if (microphoneState) microphoneState.textContent = '尚未连接麦克风';
    }

    function startVolumeMonitor(stream) {
        const AudioContextClass = window.AudioContext || window.webkitAudioContext;
        if (!AudioContextClass || typeof window.requestAnimationFrame !== 'function') {
            if (microphoneHint) microphoneHint.textContent = '当前浏览器不支持实时音量检测，但仍可进行录音。';
            return;
        }

        audioContext = new AudioContextClass();
        const source = audioContext.createMediaStreamSource(stream);
        analyser = audioContext.createAnalyser();
        analyser.fftSize = 2048;
        analyser.smoothingTimeConstant = 0.15;
        source.connect(analyser);
        sampleBuffer = new Float32Array(analyser.fftSize);

        const track = stream.getAudioTracks()[0];
        const updateTrackState = () => {
            if (!microphoneState || !track) return;
            microphoneState.textContent = '设备：' + (track.label || '麦克风')
                + '；状态：' + track.readyState
                + '；静音：' + (track.muted ? '是' : '否');
        };
        updateTrackState();
        if (track && track.addEventListener) {
            track.addEventListener('mute', updateTrackState);
            track.addEventListener('unmute', updateTrackState);
            track.addEventListener('ended', () => {
                updateTrackState();
                if (recordingInProgress) stopRecording();
                statusElement.textContent = '麦克风连接已中断，请重新选择设备';
            });
        }

        const analyze = () => {
            if (!analyser || !sampleBuffer) return;
            analyser.getFloatTimeDomainData(sampleBuffer);
            let squareSum = 0;
            let peak = 0;
            for (let i = 0; i < sampleBuffer.length; i += 1) {
                const value = sampleBuffer[i];
                squareSum += value * value;
                peak = Math.max(peak, Math.abs(value));
            }
            const rms = Math.sqrt(squareSum / sampleBuffer.length);
            const db = rms > 0 ? 20 * Math.log10(rms) : -Infinity;
            const level = Number.isFinite(db) ? Math.max(0, Math.min(100, (db + 60) / 60 * 100)) : 0;
            if (inputMeterFill) {
                inputMeterFill.style.width = level.toFixed(1) + '%';
                inputMeterFill.className = db < -45 ? 'low' : '';
            }
            if (recordingInProgress && peak > 0.001) hasDetectedSound = true;
            if (microphoneState && track) {
                microphoneState.textContent = '设备：' + (track.label || '麦克风')
                    + '；RMS：' + rms.toFixed(4)
                    + '；Peak：' + peak.toFixed(4)
                    + '；音轨：' + track.readyState
                    + '；静音：' + (track.muted ? '是' : '否');
            }
            animationFrameId = window.requestAnimationFrame(analyze);
        };
        analyze();
    }

    async function refreshMicrophoneDevices(requestPermission) {
        if (!navigator.mediaDevices || !navigator.mediaDevices.enumerateDevices || !deviceSelect) return;
        let temporaryStream;
        try {
            if (requestPermission && navigator.mediaDevices.getUserMedia) {
                temporaryStream = await navigator.mediaDevices.getUserMedia({audio: true});
            }
            const devices = await navigator.mediaDevices.enumerateDevices();
            const audioInputs = devices.filter(device => device.kind === 'audioinput');
            const selectedId = deviceSelect.value
                || (window.localStorage && window.localStorage.getItem(DEVICE_STORAGE_KEY))
                || '';
            deviceSelect.replaceChildren();
            deviceSelect.add(new Option('系统默认麦克风', ''));
            audioInputs.forEach((device, index) => {
                const label = device.label || ('麦克风设备 ' + (index + 1) + '（授权后显示名称）');
                deviceSelect.add(new Option(label, device.deviceId));
            });
            if (audioInputs.some(device => device.deviceId === selectedId)) {
                deviceSelect.value = selectedId;
            } else if (selectedId && window.localStorage) {
                window.localStorage.removeItem(DEVICE_STORAGE_KEY);
            }
            if (microphoneHint) {
                microphoneHint.textContent = audioInputs.length
                    ? '已发现 ' + audioInputs.length + ' 个输入设备。请选择 MacBook 内置麦克风或蓝牙耳机麦克风；选择结果会保存在当前浏览器。'
                    : '未发现麦克风设备，请检查系统连接和浏览器权限。';
            }
        } catch (error) {
            if (microphoneHint) microphoneHint.textContent = '读取麦克风设备失败：' + error.message;
        } finally {
            if (temporaryStream) temporaryStream.getTracks().forEach(track => track.stop());
        }
    }

    function stopRecording() {
        stopCountdown();
        stopButton.disabled = true;
        if (recorder && recorder.state === 'recording') recorder.stop();
    }

    async function parseResponse(response) {
        try {
            return await response.json();
        } catch (error) {
            return {};
        }
    }

    function renderResult(payload, filename) {
        resultElement.textContent = payload.text || '';
        const details = [
            filename ? '文件：' + filename : '来源：麦克风录音',
            '音频时长：' + Number(payload.audioDuration || 0).toFixed(2) + ' 秒',
            '解码耗时：' + Number(payload.decodeTime || 0).toFixed(3) + ' 秒',
            '推理耗时：' + Number(payload.inferenceTime || 0).toFixed(3) + ' 秒',
            '总耗时：' + Number(payload.totalTime || 0).toFixed(3) + ' 秒',
            '请求 ID：' + (payload.requestId || ''),
        ];
        resultMetaElement.textContent = details.join(' | ');
        statusElement.textContent = '识别完成';
    }

    /** 麦克风录音和文件上传共用同一个后端接口。 */
    window.OfflineASR = {
        recognize: async (audioBlob, filename) => {
            if (openedFromLocalFile()) throw localFileError();
            const requestId = crypto.randomUUID();
            const formData = new FormData();
            formData.append('file', audioBlob, filename || 'recording.webm');

            let response;
            try {
                response = await fetch('/api/asr', {
                    method: 'POST',
                    headers: {'X-Request-Id': requestId},
                    body: formData,
                });
            } catch (error) {
                throw {code: 'NETWORK_ERROR', message: '无法连接本机识别服务'};
            }

            const payload = await parseResponse(response);
            if (!response.ok) {
                throw {
                    code: payload.errorCode || DEFAULT_ERROR_CODE,
                    message: payload.message || DEFAULT_ERROR_MESSAGE,
                };
            }

            if (typeof window.onSpeechText === 'function') window.onSpeechText(payload.text);
            window.dispatchEvent(new CustomEvent('asr-result', {
                detail: {
                    text: payload.text,
                    requestId: payload.requestId,
                    durationSeconds: payload.audioDuration,
                    inferenceSeconds: payload.inferenceTime,
                },
            }));
            return payload;
        },
    };

    startButton.onclick = async () => {
        if (openedFromLocalFile()) {
            showLocalFileHint();
            return;
        }
        startButton.disabled = true;
        uploadButton.disabled = true;
        if (deviceSelect) deviceSelect.disabled = true;
        if (refreshDevicesButton) refreshDevicesButton.disabled = true;
        try {
            statusElement.textContent = '正在连接所选麦克风';
            const selectedDeviceId = deviceSelect ? deviceSelect.value : '';
            const audioConstraints = selectedDeviceId
                ? {deviceId: {exact: selectedDeviceId}}
                : true;
            mediaStream = await navigator.mediaDevices.getUserMedia({audio: audioConstraints});
            const track = mediaStream.getAudioTracks()[0];
            if (!track) throw new Error('浏览器没有返回音频轨道');
            if (microphoneState) {
                microphoneState.textContent = '已连接：' + (track.label || '麦克风')
                    + '；音轨状态：' + track.readyState;
            }
            startVolumeMonitor(mediaStream);
            const options = MediaRecorder.isTypeSupported(PREFERRED_MIME_TYPE)
                ? {mimeType: PREFERRED_MIME_TYPE}
                : undefined;
            recorder = new MediaRecorder(mediaStream, options);
            chunks = [];
            recorder.ondataavailable = event => {
                if (event.data && event.data.size > 0) chunks.push(event.data);
            };
            recorder.onstop = async () => {
                const recordedStream = mediaStream;
                stopTracks();
                recordingInProgress = false;
                stopCountdown();
                if (recordedStream) {
                    const blob = new Blob(chunks, {type: recorder.mimeType || 'audio/webm'});
                    if (!hasDetectedSound) {
                        statusElement.textContent = '录音未上传：没有检测到有效声音';
                        if (microphoneHint) {
                            microphoneHint.textContent = '本次录音检测为静音。请检查所选设备，或切换到另一个麦克风后重试。';
                        }
                        startButton.disabled = false;
                        stopButton.disabled = true;
                        uploadButton.disabled = !audioFileInput.files || audioFileInput.files.length === 0;
                        if (deviceSelect) deviceSelect.disabled = false;
                        if (refreshDevicesButton) refreshDevicesButton.disabled = false;
                        return;
                    }
                    statusElement.textContent = '正在识别录音';
                    try {
                        const response = await window.OfflineASR.recognize(blob, 'recording.webm');
                        renderResult(response, '');
                    } catch (error) {
                        showError(error.message || DEFAULT_ERROR_MESSAGE);
                    }
                }
                startButton.disabled = false;
                stopButton.disabled = true;
                uploadButton.disabled = !audioFileInput.files || audioFileInput.files.length === 0;
                if (deviceSelect) deviceSelect.disabled = false;
                if (refreshDevicesButton) refreshDevicesButton.disabled = false;
            };
            recorder.onerror = event => {
                recordingInProgress = false;
                stopTracks();
                showError('录音失败：' + (event.error ? event.error.message : '未知错误'));
                if (deviceSelect) deviceSelect.disabled = false;
                if (refreshDevicesButton) refreshDevicesButton.disabled = false;
            };
            recorder.start();
            recordingInProgress = true;
            hasDetectedSound = false;
            stopButton.disabled = false;
            statusElement.textContent = '录音中，请对着所选麦克风说话';
            startCountdown();
        } catch (error) {
            stopTracks();
            showError('无法使用所选麦克风：' + error.message + '。请刷新设备列表或选择其他麦克风。');
            if (deviceSelect) deviceSelect.disabled = false;
            if (refreshDevicesButton) refreshDevicesButton.disabled = false;
        }
    };

    stopButton.onclick = stopRecording;

    if (refreshDevicesButton) {
        refreshDevicesButton.onclick = () => refreshMicrophoneDevices(true);
    }
    if (navigator.mediaDevices && navigator.mediaDevices.addEventListener) {
        navigator.mediaDevices.addEventListener('devicechange', () => refreshMicrophoneDevices(false));
    }
    if (deviceSelect) {
        deviceSelect.onchange = () => {
            if (window.localStorage) {
                if (deviceSelect.value) window.localStorage.setItem(DEVICE_STORAGE_KEY, deviceSelect.value);
                else window.localStorage.removeItem(DEVICE_STORAGE_KEY);
            }
        };
    }

    audioFileInput.onchange = () => {
        const file = audioFileInput.files && audioFileInput.files[0];
        uploadButton.disabled = !file;
        fileInfoElement.textContent = file
            ? '已选择：' + file.name + '（' + (file.size / 1024 / 1024).toFixed(2) + ' MB）'
            : '尚未选择音频文件';
    };

    uploadButton.onclick = async () => {
        if (openedFromLocalFile()) {
            showLocalFileHint();
            return;
        }
        const file = audioFileInput.files && audioFileInput.files[0];
        if (!file) {
            showError('请先选择音频文件');
            return;
        }
        uploadButton.disabled = true;
        startButton.disabled = true;
        statusElement.textContent = '正在上传并识别：' + file.name;
        try {
            const response = await window.OfflineASR.recognize(file, file.name);
            renderResult(response, file.name);
        } catch (error) {
            showError(error.message || DEFAULT_ERROR_MESSAGE);
        } finally {
            startButton.disabled = false;
            uploadButton.disabled = !audioFileInput.files || audioFileInput.files.length === 0;
        }
    };

    clearButton.onclick = () => {
        resultElement.textContent = '';
        resultMetaElement.textContent = '';
    };

    renderCountdown();
    refreshMicrophoneDevices(false);
    if (openedFromLocalFile()) showLocalFileHint();
})();
