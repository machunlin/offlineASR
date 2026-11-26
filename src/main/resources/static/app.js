(() => {
    'use strict';

    const MAX_RECORDING_SECONDS = 600;
    const $ = selector => document.querySelector(selector);
    const elements = {
        status: $('#status'),
        device: $('#microphone-device'),
        refresh: $('#refresh-microphones'),
        microphoneHint: $('#microphone-hint'),
        microphoneState: $('#microphone-state'),
        meter: $('.input-meter'),
        meterFill: $('#input-meter-fill'),
        startMonitor: $('#start-monitor'),
        stopMonitor: $('#stop-monitor'),
        start: $('#start'),
        stop: $('#stop'),
        recognizeRecording: $('#recognize-recording'),
        countdown: $('#countdown'),
        playback: $('#recording-playback'),
        audioFile: $('#audio-file'),
        upload: $('#upload'),
        fileInfo: $('#file-info'),
        result: $('#result'),
        resultMeta: $('#result-meta'),
        clear: $('#clear'),
        localFileHint: $('#local-file-hint')
    };

    const sdk = new OfflineASR.SDK({
        baseUrl: '',
        maxRecordingSeconds: MAX_RECORDING_SECONDS,
        rememberDevice: true
    });

    let lastRecording = null;
    let countdownTimer = null;
    let remainingSeconds = MAX_RECORDING_SECONDS;
    let recording = false;

    function setStatus(message) {
        elements.status.textContent = message;
    }

    function setBusy(busy) {
        elements.start.disabled = busy || recording;
        elements.upload.disabled = busy || !elements.audioFile.files || !elements.audioFile.files.length;
        elements.refresh.disabled = busy || recording;
        elements.device.disabled = busy || recording;
    }

    function showError(error) {
        const message = error && error.message ? error.message : '发生未知错误';
        setStatus((error && error.code ? '[' + error.code + '] ' : '') + message);
    }

    function updateCountdown() {
        elements.countdown.textContent = remainingSeconds + ' 秒';
    }

    function startCountdown() {
        stopCountdown();
        remainingSeconds = MAX_RECORDING_SECONDS;
        updateCountdown();
        countdownTimer = window.setInterval(() => {
            remainingSeconds = Math.max(0, remainingSeconds - 1);
            updateCountdown();
            if (remainingSeconds === 0) stopRecording();
        }, 1000);
    }

    function stopCountdown() {
        if (countdownTimer !== null) window.clearInterval(countdownTimer);
        countdownTimer = null;
        remainingSeconds = MAX_RECORDING_SECONDS;
        updateCountdown();
    }

    function renderDevices(devices) {
        const selectedId = sdk.deviceId;
        elements.device.replaceChildren();
        elements.device.add(new Option('系统默认麦克风', ''));
        devices.forEach(device => {
            elements.device.add(new Option(device.label || '麦克风设备', device.deviceId));
        });
        elements.device.value = selectedId;
        if (devices.length === 0) {
            elements.microphoneHint.textContent = '没有发现麦克风输入设备，请检查系统连接和浏览器权限。';
        } else {
            elements.microphoneHint.textContent = '已发现 ' + devices.length + ' 个麦克风。请选择录音输入设备，再启动检测确认音量。';
        }
    }

    function renderTrackState(state) {
        if (!state || !state.connected) {
            elements.microphoneState.textContent = '尚未连接麦克风';
            return;
        }
        elements.microphoneState.textContent = '设备：' + state.label
            + '；音轨：' + state.readyState
            + '；静音：' + (state.muted ? '是' : '否');
    }

    function renderResult(payload, filename) {
        elements.result.textContent = payload.text || '';
        elements.resultMeta.textContent = [
            filename ? '文件：' + filename : '来源：麦克风录音',
            '音频时长：' + Number(payload.audioDuration || 0).toFixed(2) + ' 秒',
            '解码耗时：' + Number(payload.decodeTime || 0).toFixed(3) + ' 秒',
            '推理耗时：' + Number(payload.inferenceTime || 0).toFixed(3) + ' 秒',
            '总耗时：' + Number(payload.totalTime || 0).toFixed(3) + ' 秒',
            '请求 ID：' + (payload.requestId || '')
        ].join(' | ');
        setStatus('识别完成');
    }

    async function refreshDevices() {
        try {
            setStatus('正在请求麦克风权限并刷新设备');
            const devices = await sdk.getDevices({requestPermission: true});
            renderDevices(devices);
            setStatus('麦克风设备列表已更新');
        } catch (error) {
            showError(error);
        }
    }

    async function startMonitor() {
        try {
            await sdk.startMonitor();
            elements.startMonitor.disabled = true;
            elements.stopMonitor.disabled = false;
            setStatus('麦克风检测已启动，请对着所选设备说话');
        } catch (error) {
            showError(error);
        }
    }

    function stopMonitor() {
        sdk.stopMonitor();
        elements.startMonitor.disabled = false;
        elements.stopMonitor.disabled = true;
        elements.meterFill.style.width = '0%';
        elements.meter.setAttribute('aria-valuenow', '0');
        setStatus('麦克风音量检测已停止');
    }

    async function startRecording() {
        try {
            lastRecording = null;
            elements.recognizeRecording.disabled = true;
            elements.start.disabled = true;
            elements.upload.disabled = true;
            elements.device.disabled = true;
            await sdk.startRecording();
            recording = true;
            elements.startMonitor.disabled = true;
            elements.stopMonitor.disabled = true;
            elements.stop.disabled = false;
            startCountdown();
            setStatus('正在录音，请对着所选麦克风说话');
        } catch (error) {
            recording = false;
            setBusy(false);
            showError(error);
        }
    }

    async function stopRecording() {
        if (!recording) return;
        elements.stop.disabled = true;
        stopCountdown();
        try {
            const result = await sdk.stopRecording();
            lastRecording = result.blob;
            const url = URL.createObjectURL(result.blob);
            elements.playback.src = url;
            elements.playback.hidden = false;
            elements.recognizeRecording.disabled = false;
            setStatus('录音完成，可以回放或提交识别');
        } catch (error) {
            showError(error);
        } finally {
            recording = false;
            elements.stop.disabled = true;
            elements.stopMonitor.disabled = false;
            setBusy(false);
        }
    }

    async function recognizeRecording() {
        if (!lastRecording) return;
        elements.recognizeRecording.disabled = true;
        setBusy(true);
        setStatus('正在识别麦克风录音');
        try {
            const payload = await sdk.recognize(lastRecording, {filename: 'recording.webm'});
            renderResult(payload, '');
        } catch (error) {
            showError(error);
        } finally {
            elements.recognizeRecording.disabled = false;
            setBusy(false);
        }
    }

    async function uploadFile() {
        const file = elements.audioFile.files && elements.audioFile.files[0];
        if (!file) return;
        elements.upload.disabled = true;
        elements.start.disabled = true;
        setStatus('正在上传并识别：' + file.name);
        try {
            const payload = await sdk.recognize(file, {filename: file.name});
            renderResult(payload, file.name);
        } catch (error) {
            showError(error);
        } finally {
            setBusy(false);
        }
    }

    sdk.on('volumechange', volume => {
        elements.meterFill.style.width = volume.level.toFixed(1) + '%';
        elements.meterFill.className = volume.dbfs < -45 ? 'low' : '';
        elements.meter.setAttribute('aria-valuenow', String(Math.round(volume.level)));
    });
    sdk.on('trackstatechange', renderTrackState);
    sdk.on('monitorstart', () => {
        elements.startMonitor.disabled = true;
        elements.stopMonitor.disabled = recording;
    });
    sdk.on('monitorstop', () => {
        elements.startMonitor.disabled = false;
        elements.stopMonitor.disabled = true;
    });
    sdk.on('deviceconnected', renderTrackState);
    sdk.on('deviceschange', event => renderDevices(event.devices));
    sdk.on('recordinglimit', () => setStatus('已达到录音时长上限，正在停止录音'));
    sdk.on('error', showError);

    elements.refresh.onclick = refreshDevices;
    elements.startMonitor.onclick = startMonitor;
    elements.stopMonitor.onclick = stopMonitor;
    elements.start.onclick = startRecording;
    elements.stop.onclick = stopRecording;
    elements.recognizeRecording.onclick = recognizeRecording;
    elements.upload.onclick = uploadFile;
    elements.clear.onclick = () => {
        elements.result.textContent = '';
        elements.resultMeta.textContent = '';
    };
    elements.device.onchange = async () => {
        try {
            await sdk.selectDevice(elements.device.value);
            setStatus('已切换录音输入设备');
        } catch (error) {
            showError(error);
        }
    };
    elements.audioFile.onchange = () => {
        const file = elements.audioFile.files && elements.audioFile.files[0];
        elements.upload.disabled = !file;
        elements.fileInfo.textContent = file
            ? '已选择：' + file.name + '（' + (file.size / 1024 / 1024).toFixed(2) + ' MB）'
            : '尚未选择音频文件';
    };

    async function initialize() {
        if (window.location.protocol === 'file:') {
            elements.localFileHint.hidden = false;
            setStatus('请通过 http://127.0.0.1:8080/ 访问 Demo');
            return;
        }
        try {
            const devices = await sdk.init();
            renderDevices(devices);
            setStatus('SDK 初始化完成，请授权并选择麦克风设备');
        } catch (error) {
            showError(error);
        }
    }

    window.addEventListener('beforeunload', () => {
        sdk.release();
        if (elements.playback.src) URL.revokeObjectURL(elements.playback.src);
    });

    updateCountdown();
    initialize();
})();
