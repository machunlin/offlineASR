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
    const startButton = document.querySelector('#start');
    const stopButton = document.querySelector('#stop');
    const clearButton = document.querySelector('#clear');
    const countdownElement = document.querySelector('#countdown');
    const localFileHint = document.querySelector('#local-file-hint');

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
        if (localFileHint) {
            localFileHint.hidden = false;
        }
        showError(localFileError().message);
    }

    function showError(message) {
        statusElement.textContent = message;
        startButton.disabled = false;
        stopButton.disabled = true;
        stopCountdown();
    }

    function renderCountdown() {
        if (countdownElement) {
            countdownElement.textContent = remainingSeconds + ' 秒';
        }
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
        if (recorder && recorder.state === 'recording') {
            recorder.stop();
        }
    }

    async function parseResponse(response) {
        try {
            return await response.json();
        } catch (error) {
            return {};
        }
    }

    /** Promise、回调和事件观察同一个最终结果，不重复提交音频。 */
    window.OfflineASR = {
        recognize: async audioBlob => {
            if (openedFromLocalFile()) {
                throw localFileError();
            }
            const requestId = crypto.randomUUID();
            const formData = new FormData();
            formData.append('file', audioBlob, 'recording.webm');

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

            if (typeof window.onSpeechText === 'function') {
                window.onSpeechText(payload.text);
            }
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
        try {
            statusElement.textContent = '正在申请麦克风权限';
            mediaStream = await navigator.mediaDevices.getUserMedia({audio: true});
            const options = MediaRecorder.isTypeSupported(PREFERRED_MIME_TYPE)
                ? {mimeType: PREFERRED_MIME_TYPE}
                : undefined;
            recorder = new MediaRecorder(mediaStream, options);
            chunks = [];
            recorder.ondataavailable = event => {
                if (event.data && event.data.size > 0) {
                    chunks.push(event.data);
                }
            };
            recorder.onstop = async () => {
                stopTracks();
                stopCountdown();
                statusElement.textContent = '正在识别';
                try {
                    const response = await window.OfflineASR.recognize(
                        new Blob(chunks, {type: recorder.mimeType || 'audio/webm'}));
                    resultElement.textContent = response.text;
                    statusElement.textContent = '识别完成';
                    startButton.disabled = false;
                } catch (error) {
                    showError(error.message || DEFAULT_ERROR_MESSAGE);
                } finally {
                    stopButton.disabled = true;
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

    clearButton.onclick = () => {
        resultElement.textContent = '';
    };

    renderCountdown();
    if (openedFromLocalFile()) {
        showLocalFileHint();
    }
})();
