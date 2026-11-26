/**
 * 离线 ASR 浏览器 SDK
 * 负责麦克风设备管理、音量检测、录音和 ASR HTTP 请求。
 * 不依赖任何页面 DOM，可用于第三方浏览器页面。
 */
(function (root, factory) {
    if (typeof module === 'object' && module.exports) {
        module.exports = factory();
    } else {
        root.OfflineASR = root.OfflineASR || {};
        root.OfflineASR.SDK = factory().SDK;
        root.OfflineASR.SDK_VERSION = factory().VERSION;
    }
}(typeof globalThis !== 'undefined' ? globalThis : this, function () {
    'use strict';

    const VERSION = '1.0.0';
    const DEFAULTS = {
        baseUrl: '',
        recognizePath: '/api/asr',
        maxRecordingSeconds: 600,
        preferredMimeTypes: ['audio/webm;codecs=opus', 'audio/webm', 'audio/mp4'],
        storageKey: 'offline-asr-microphone-device',
        rememberDevice: true,
        silenceRmsThreshold: 0.0003,
        silencePeakThreshold: 0.001
    };

    class SDKError extends Error {
        constructor(code, message, cause) {
            super(message);
            this.name = 'OfflineASRError';
            this.code = code;
            if (cause !== undefined) this.cause = cause;
        }
    }

    class SDK {
        constructor(options) {
            this.options = Object.assign({}, DEFAULTS, options || {});
            this.listeners = new Map();
            this.deviceId = '';
            this.devices = [];
            this.stream = null;
            this.audioContext = null;
            this.analyser = null;
            this.sourceNode = null;
            this.sampleBuffer = null;
            this.animationFrameId = null;
            this.recorder = null;
            this.recordingChunks = [];
            this.recordingPromise = null;
            this.recordingResolve = null;
            this.recordingReject = null;
            this.recordingStartedAt = 0;
            this.recordingTimer = null;
            this.lastVolume = null;
            this.hasDetectedSound = false;
            this.initialized = false;
            this.destroyed = false;
            this._deviceChangeHandler = () => this.getDevices().catch(error => this._emit('error', error));
        }

        on(eventName, handler) {
            if (typeof handler !== 'function') throw new SDKError('INVALID_HANDLER', '事件回调必须是函数');
            if (!this.listeners.has(eventName)) this.listeners.set(eventName, new Set());
            this.listeners.get(eventName).add(handler);
            return () => this.off(eventName, handler);
        }

        off(eventName, handler) {
            const handlers = this.listeners.get(eventName);
            if (handlers) {
                handlers.delete(handler);
                if (!handlers.size) this.listeners.delete(eventName);
            }
        }

        _emit(eventName, detail) {
            const handlers = this.listeners.get(eventName);
            if (handlers) {
                handlers.forEach(handler => {
                    try { handler(detail); } catch (error) {
                        // 单个业务回调异常不能中断 SDK 内部状态流转。
                        setTimeout(() => { throw error; }, 0);
                    }
                });
            }
        }

        _assertAvailable() {
            if (this.destroyed) throw new SDKError('SDK_DESTROYED', 'SDK 已释放，不能继续使用');
            if (!navigator.mediaDevices) throw new SDKError('MEDIA_DEVICES_UNSUPPORTED', '当前浏览器不支持麦克风设备访问');
        }

        async init() {
            this._assertAvailable();
            if (this.initialized) return this.getDevices();
            if (!navigator.mediaDevices.getUserMedia || !navigator.mediaDevices.enumerateDevices) {
                throw new SDKError('MEDIA_DEVICES_UNSUPPORTED', '当前浏览器不支持麦克风采集或设备枚举');
            }
            this.initialized = true;
            if (this.options.rememberDevice) {
                try { this.deviceId = localStorage.getItem(this.options.storageKey) || ''; } catch (error) { /* 浏览器可能禁用本地存储 */ }
            }
            if (navigator.mediaDevices.addEventListener) {
                navigator.mediaDevices.addEventListener('devicechange', this._deviceChangeHandler);
            }
            const devices = await this.getDevices();
            this._emit('ready', {devices});
            return devices;
        }

        async getDevices(options) {
            this._assertAvailable();
            const settings = options || {};
            let temporaryStream = null;
            try {
                if (settings.requestPermission) {
                    temporaryStream = await navigator.mediaDevices.getUserMedia({audio: true});
                }
                const allDevices = await navigator.mediaDevices.enumerateDevices();
                this.devices = allDevices
                    .filter(device => device.kind === 'audioinput')
                    .map((device, index) => ({
                        deviceId: device.deviceId,
                        groupId: device.groupId,
                        label: device.label || '麦克风设备 ' + (index + 1),
                        kind: device.kind
                    }));
                this._emit('deviceschange', {devices: this.devices.slice()});
                return this.devices.slice();
            } catch (error) {
                throw this._normalizeError(error, 'DEVICE_ENUMERATION_FAILED', '获取麦克风设备列表失败');
            } finally {
                if (temporaryStream) temporaryStream.getTracks().forEach(track => track.stop());
            }
        }

        async selectDevice(deviceId) {
            this._assertAvailable();
            this.deviceId = deviceId || '';
            if (this.options.rememberDevice) {
                try {
                    if (this.deviceId) localStorage.setItem(this.options.storageKey, this.deviceId);
                    else localStorage.removeItem(this.options.storageKey);
                } catch (error) { /* 本地存储不可用时仍允许本次选择 */ }
            }
            if (this.stream) {
                const wasMonitoring = !!this.analyser;
                this._stopMonitorGraph();
                this._stopStream();
                if (wasMonitoring) await this.startMonitor();
            }
            this._emit('devicechange', {deviceId: this.deviceId});
            return this.deviceId;
        }

        async _ensureStream() {
            this._assertAvailable();
            if (this.stream && this.stream.getAudioTracks().some(track => track.readyState === 'live')) return this.stream;
            const audio = this.deviceId ? {deviceId: {exact: this.deviceId}} : true;
            try {
                this.stream = await navigator.mediaDevices.getUserMedia({audio});
                const track = this.stream.getAudioTracks()[0];
                if (!track) throw new SDKError('NO_AUDIO_TRACK', '浏览器没有返回麦克风音轨');
                track.addEventListener('mute', () => this._emit('trackstatechange', this._getTrackState()));
                track.addEventListener('unmute', () => this._emit('trackstatechange', this._getTrackState()));
                track.addEventListener('ended', () => {
                    this._emit('trackstatechange', this._getTrackState());
                    if (this.recorder && this.recorder.state === 'recording') this.stopRecording().catch(() => {});
                });
                this._emit('deviceconnected', this._getTrackState());
                return this.stream;
            } catch (error) {
                throw this._normalizeError(error, 'MICROPHONE_ACCESS_DENIED', '无法访问所选麦克风，请检查浏览器权限和设备连接');
            }
        }

        _getTrackState() {
            const track = this.stream && this.stream.getAudioTracks()[0];
            if (!track) return {connected: false};
            return {
                connected: track.readyState === 'live',
                label: track.label || '麦克风',
                readyState: track.readyState,
                muted: !!track.muted,
                settings: track.getSettings ? track.getSettings() : {}
            };
        }

        async startMonitor() {
            const stream = await this._ensureStream();
            if (this.analyser) return this._getTrackState();
            const AudioContextClass = window.AudioContext || window.webkitAudioContext;
            if (!AudioContextClass || typeof window.requestAnimationFrame !== 'function') {
                throw new SDKError('AUDIO_MONITOR_UNSUPPORTED', '当前浏览器不支持实时音量检测');
            }
            try {
                this.audioContext = new AudioContextClass();
                if (this.audioContext.state === 'suspended') await this.audioContext.resume();
                this.analyser = this.audioContext.createAnalyser();
                this.analyser.fftSize = 2048;
                this.analyser.smoothingTimeConstant = 0.15;
                this.sourceNode = this.audioContext.createMediaStreamSource(stream);
                this.sourceNode.connect(this.analyser);
                this.sampleBuffer = new Float32Array(this.analyser.fftSize);
                this._analyzeVolume();
                const state = this._getTrackState();
                this._emit('monitorstart', state);
                return state;
            } catch (error) {
                this._stopMonitorGraph();
                this._stopStream();
                throw this._normalizeError(error, 'AUDIO_MONITOR_FAILED', '启动麦克风音量检测失败');
            }
        }

        _analyzeVolume() {
            if (!this.analyser || !this.sampleBuffer) return;
            this.analyser.getFloatTimeDomainData(this.sampleBuffer);
            let squareSum = 0;
            let peak = 0;
            for (let index = 0; index < this.sampleBuffer.length; index += 1) {
                const value = this.sampleBuffer[index];
                squareSum += value * value;
                peak = Math.max(peak, Math.abs(value));
            }
            const rms = Math.sqrt(squareSum / this.sampleBuffer.length);
            const dbfs = rms > 0 ? 20 * Math.log10(rms) : -Infinity;
            const level = Number.isFinite(dbfs) ? Math.max(0, Math.min(100, (dbfs + 60) / 60 * 100)) : 0;
            this.lastVolume = {rms, peak, dbfs, level, timestamp: Date.now()};
            if (rms >= this.options.silenceRmsThreshold || peak >= this.options.silencePeakThreshold) {
                this.hasDetectedSound = true;
            }
            this._emit('volumechange', this.lastVolume);
            this._emit('trackstatechange', this._getTrackState());
            this.animationFrameId = window.requestAnimationFrame(() => this._analyzeVolume());
        }

        _stopMonitorGraph() {
            if (this.animationFrameId !== null) window.cancelAnimationFrame(this.animationFrameId);
            this.animationFrameId = null;
            if (this.sourceNode) {
                try { this.sourceNode.disconnect(); } catch (error) { /* 已断开 */ }
            }
            this.sourceNode = null;
            this.analyser = null;
            this.sampleBuffer = null;
            if (this.audioContext) {
                const context = this.audioContext;
                this.audioContext = null;
                context.close().catch(() => {});
            }
            this._emit('monitorstop', {});
        }

        stopMonitor(options) {
            if (this.recorder && this.recorder.state === 'recording') {
                throw new SDKError('RECORDING_IN_PROGRESS', '录音期间不能停止音量检测，以免静音检测失效');
            }
            this._stopMonitorGraph();
            if (!(options && options.keepStream === false)) return;
            this._stopStream();
        }

        async startRecording(options) {
            this._assertAvailable();
            if (this.recorder && this.recorder.state === 'recording') {
                throw new SDKError('RECORDING_IN_PROGRESS', '当前已经处于录音状态');
            }
            const settings = options || {};
            const stream = await this._ensureStream();
            if (!this.analyser) await this.startMonitor();
            const mimeType = this._selectMimeType();
            try {
                this.recorder = mimeType ? new MediaRecorder(stream, {mimeType}) : new MediaRecorder(stream);
            } catch (error) {
                throw this._normalizeError(error, 'RECORDER_UNSUPPORTED', '当前浏览器无法创建录音器');
            }
            this.recordingChunks = [];
            this.hasDetectedSound = false;
            this.recordingStartedAt = Date.now();
            this.recordingPromise = new Promise((resolve, reject) => {
                this.recordingResolve = resolve;
                this.recordingReject = reject;
            });
            this.recorder.ondataavailable = event => {
                if (event.data && event.data.size > 0) this.recordingChunks.push(event.data);
            };
            this.recorder.onerror = event => {
                const error = this._normalizeError(event.error, 'RECORDING_FAILED', '录音过程发生错误');
                if (this.recordingReject) this.recordingReject(error);
                this._clearRecordingState();
                this._emit('error', error);
            };
            this.recorder.onstop = () => {
                const durationMs = Date.now() - this.recordingStartedAt;
                const blob = new Blob(this.recordingChunks, {type: this.recorder.mimeType || mimeType || 'application/octet-stream'});
                const result = {
                    blob,
                    durationSeconds: durationMs / 1000,
                    mimeType: blob.type,
                    silent: !this.hasDetectedSound
                };
                this._clearRecordingState();
                this._emit('recordingstop', result);
                if (result.silent && settings.rejectSilence !== false) {
                    const error = new SDKError('AUDIO_SILENCE', '未检测到有效声音，录音未提交识别');
                    error.recording = result;
                    if (this.recordingReject) this.recordingReject(error);
                } else if (this.recordingResolve) {
                    this.recordingResolve(result);
                }
                this.recordingResolve = null;
                this.recordingReject = null;
            };
            try {
                this.recorder.start();
                const maxSeconds = Number(settings.maxRecordingSeconds || this.options.maxRecordingSeconds);
                this.recordingTimer = setTimeout(() => {
                    this._emit('recordinglimit', {maxRecordingSeconds: maxSeconds});
                    this.stopRecording().catch(() => {});
                }, maxSeconds * 1000);
                this._emit('recordingstart', {mimeType: this.recorder.mimeType, startedAt: this.recordingStartedAt});
                return {started: true, mimeType: this.recorder.mimeType};
            } catch (error) {
                this._clearRecordingState();
                throw this._normalizeError(error, 'RECORDING_START_FAILED', '启动录音失败');
            }
        }

        _selectMimeType() {
            if (typeof MediaRecorder === 'undefined') return '';
            return this.options.preferredMimeTypes.find(type => MediaRecorder.isTypeSupported(type)) || '';
        }

        stopRecording() {
            if (!this.recorder || this.recorder.state !== 'recording') {
                return Promise.reject(new SDKError('NOT_RECORDING', '当前没有正在进行的录音'));
            }
            const resultPromise = this.recordingPromise;
            try {
                this.recorder.stop();
            } catch (error) {
                return Promise.reject(this._normalizeError(error, 'RECORDING_STOP_FAILED', '停止录音失败'));
            }
            return resultPromise;
        }

        async recognize(blob, options) {
            this._assertAvailable();
            if (!(blob instanceof Blob) || blob.size === 0) throw new SDKError('INVALID_AUDIO', '待识别音频不能为空');
            const settings = options || {};
            const requestId = settings.requestId || this._createRequestId();
            const formData = new FormData();
            formData.append('file', blob, settings.filename || this._defaultFilename(blob.type));
            let response;
            try {
                response = await fetch(this._joinUrl(this.options.baseUrl, this.options.recognizePath), {
                    method: 'POST',
                    headers: {'X-Request-Id': requestId},
                    body: formData,
                    signal: settings.signal
                });
            } catch (error) {
                throw this._normalizeError(error, 'NETWORK_ERROR', '无法连接离线语音识别服务');
            }
            let payload = {};
            try { payload = await response.json(); } catch (error) { /* 由统一错误结构兜底 */ }
            if (!response.ok) {
                throw new SDKError(payload.errorCode || 'ASR_FAILED', payload.message || '语音识别失败');
            }
            const result = Object.assign({}, payload, {requestId: payload.requestId || requestId});
            this._emit('result', result);
            return result;
        }

        async stopRecordingAndRecognize(options) {
            const recording = await this.stopRecording();
            const settings = options || {};
            const result = await this.recognize(recording.blob, settings);
            return Object.assign({recording}, result);
        }

        async release() {
            if (this.destroyed) return;
            if (this.recorder && this.recorder.state === 'recording') {
                try { await this.stopRecording(); } catch (error) { /* 释放时不上传录音 */ }
            }
            this.stopMonitor({keepStream: false});
            this._stopStream();
            if (navigator.mediaDevices && navigator.mediaDevices.removeEventListener) {
                navigator.mediaDevices.removeEventListener('devicechange', this._deviceChangeHandler);
            }
            this.listeners.clear();
            this.initialized = false;
            this.destroyed = true;
        }

        _stopStream() {
            if (this.stream) {
                this.stream.getTracks().forEach(track => track.stop());
                this.stream = null;
            }
            this._emit('deviceconnected', {connected: false});
        }

        _clearRecordingState() {
            if (this.recordingTimer !== null) clearTimeout(this.recordingTimer);
            this.recordingTimer = null;
            this.recordingChunks = [];
            this.recordingPromise = null;
        }

        _defaultFilename(mimeType) {
            if (mimeType && mimeType.indexOf('mp4') >= 0) return 'recording.mp4';
            if (mimeType && mimeType.indexOf('ogg') >= 0) return 'recording.ogg';
            return 'recording.webm';
        }

        _createRequestId() {
            if (typeof crypto !== 'undefined' && crypto.randomUUID) return crypto.randomUUID();
            return 'asr-' + Date.now() + '-' + Math.random().toString(16).slice(2);
        }

        _joinUrl(base, path) {
            return (base || '').replace(/\/$/, '') + '/' + String(path || '').replace(/^\//, '');
        }

        _normalizeError(error, fallbackCode, fallbackMessage) {
            if (error instanceof SDKError) return error;
            if (error && error.name === 'NotAllowedError') return new SDKError('MICROPHONE_PERMISSION_DENIED', '麦克风权限被拒绝，请在浏览器站点设置中允许访问', error);
            if (error && error.name === 'NotFoundError') return new SDKError('MICROPHONE_NOT_FOUND', '没有找到可用的麦克风设备', error);
            if (error && error.name === 'NotReadableError') return new SDKError('MICROPHONE_NOT_READABLE', '麦克风被其他程序占用或系统禁止访问', error);
            return new SDKError(fallbackCode, (error && error.message) || fallbackMessage, error);
        }
    }

    return {SDK, VERSION, SDKError};
}));
