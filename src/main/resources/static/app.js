(() => {
    const PREFERRED_MIME_TYPE = 'audio/webm;codecs=opus';
    const DEFAULT_ERROR_CODE = 'ASR_FAILED';
    const DEFAULT_ERROR_MESSAGE = '识别失败';
    const MAX_RECORDING_SECONDS = 60;

    let recorder;
    let mediaStream;
    let chunks = [];
    let countdownTimer;
    let remainingSeconds = MAX_RECORDING_SECONDS;

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

    function stopTracks() {
        if (mediaStream) {
            mediaStream.getTracks().forEach(track => track.stop());
            mediaStream = null;
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
        try {
            statusElement.textContent = '正在申请麦克风权限';
            mediaStream = await navigator.mediaDevices.getUserMedia({audio: true});
            const options = MediaRecorder.isTypeSupported(PREFERRED_MIME_TYPE)
                ? {mimeType: PREFERRED_MIME_TYPE}
                : undefined;
            recorder = new MediaRecorder(mediaStream, options);
            chunks = [];
            recorder.ondataavailable = event => {
                if (event.data && event.data.size > 0) chunks.push(event.data);
            };
            recorder.onstop = async () => {
                stopTracks();
                stopCountdown();
                statusElement.textContent = '正在识别录音';
                try {
                    const response = await window.OfflineASR.recognize(
                        new Blob(chunks, {type: recorder.mimeType || 'audio/webm'}),
                        'recording.webm');
                    renderResult(response, '');
                } catch (error) {
                    showError(error.message || DEFAULT_ERROR_MESSAGE);
                } finally {
                    startButton.disabled = false;
                    stopButton.disabled = true;
                    uploadButton.disabled = !audioFileInput.files || audioFileInput.files.length === 0;
                }
            };
            recorder.onerror = event => {
                stopTracks();
                showError('录音失败：' + (event.error ? event.error.message : '未知错误'));
            };
            recorder.start();
            stopButton.disabled = false;
            statusElement.textContent = '录音中';
            startCountdown();
        } catch (error) {
            stopTracks();
            showError('无法使用麦克风：' + error.message);
        }
    };

    stopButton.onclick = stopRecording;

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
    if (openedFromLocalFile()) showLocalFileHint();
})();
