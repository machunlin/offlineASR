import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';

const root = new URL('../', import.meta.url);
const read = path => fs.readFileSync(new URL(path, root), 'utf8');
const sdkSource = read('src/main/resources/static/sdk/offline-asr-sdk.js');
const appSource = read('src/main/resources/static/app.js');
const html = read('src/main/resources/static/index.html');
const microphoneTestHtml = read('src/main/resources/static/microphone-test.html');
const sdkPath = new URL('../src/main/resources/static/sdk/offline-asr-sdk.js', import.meta.url);
const require = createRequire(import.meta.url);

new vm.Script(sdkSource, {filename: 'offline-asr-sdk.js'});
new vm.Script(appSource, {filename: 'app.js'});

const exported = require(fileURLToPath(sdkPath));
assert.equal(exported.VERSION, '1.0.0');
assert.equal(typeof exported.SDK, 'function');

const apiMethods = [
    'init', 'getDevices', 'selectDevice', 'startMonitor', 'stopMonitor',
    'startRecording', 'stopRecording', 'recognize', 'stopRecordingAndRecognize', 'release',
    'on', 'off'
];
const sdk = new exported.SDK({baseUrl: 'http://127.0.0.1:8080/'});
for (const method of apiMethods) assert.equal(typeof sdk[method], 'function', 'SDK 缺少方法：' + method);
assert.equal(sdk._joinUrl('http://127.0.0.1:8080/', '/api/asr'), 'http://127.0.0.1:8080/api/asr');
assert.equal(sdk._defaultFilename('audio/mp4'), 'recording.mp4');
assert.equal(sdk._defaultFilename('audio/webm;codecs=opus'), 'recording.webm');

let requestedConstraints = null;
let temporaryTrackStops = 0;
let activeTrackStops = 0;
let contextCloseCount = 0;
let deviceListenerAdded = false;
let deviceListenerRemoved = false;
const browserListeners = {};
const fakeTrack = () => ({
    label: 'USB 麦克风',
    deviceId: 'mic-2',
    readyState: 'live',
    muted: false,
    stop() { this.readyState = 'ended'; activeTrackStops += 1; },
    addEventListener(name, handler) { browserListeners[name] = handler; },
    getSettings() { return {deviceId: 'mic-2', sampleRate: 48000}; }
});
const fakeStream = () => {
    const track = fakeTrack();
    return {getTracks: () => [track], getAudioTracks: () => [track], track};
};
const fakeMediaDevices = {
    async enumerateDevices() {
        return [
            {kind: 'audioinput', deviceId: 'mic-1', groupId: 'g1', label: '内置麦克风'},
            {kind: 'audioinput', deviceId: 'mic-2', groupId: 'g2', label: 'USB 麦克风'}
        ];
    },
    async getUserMedia(constraints) {
        requestedConstraints = constraints;
        const stream = fakeStream();
        if (constraints.audio === true) {
            stream.track.stop = function () { this.readyState = 'ended'; temporaryTrackStops += 1; };
        }
        return stream;
    },
    addEventListener(name, handler) { if (name === 'devicechange') deviceListenerAdded = true; },
    removeEventListener(name, handler) { if (name === 'devicechange') deviceListenerRemoved = true; }
};
class FakeAnalyser {
    constructor() { this.fftSize = 0; this.smoothingTimeConstant = 0; }
    getFloatTimeDomainData(buffer) { buffer.fill(0.01); }
}
class FakeAudioContext {
    constructor() { this.state = 'running'; }
    createAnalyser() { return new FakeAnalyser(); }
    createMediaStreamSource() { return {connect() {}, disconnect() {}}; }
    async resume() {}
    async close() { contextCloseCount += 1; }
}
class FakeRecorder {
    static isTypeSupported() { return true; }
    constructor(stream, options) {
        this.stream = stream;
        this.mimeType = options && options.mimeType || 'audio/webm';
        this.state = 'inactive';
    }
    start() { this.state = 'recording'; }
    stop() {
        this.state = 'inactive';
        this.ondataavailable({data: new Blob(['test-audio'], {type: this.mimeType})});
        this.onstop();
    }
}
class FakeBrowserFormData {
    constructor() { this.entries = []; }
    append(...args) { this.entries.push(args); }
}
let fakeFetchRequest = null;
const browserModule = {exports: {}};
const browserContext = {
    module: browserModule,
    exports: browserModule.exports,
    navigator: {mediaDevices: fakeMediaDevices},
    window: {
        AudioContext: FakeAudioContext,
        requestAnimationFrame: () => 1,
        cancelAnimationFrame() {}
    },
    MediaRecorder: FakeRecorder,
    Blob,
    FormData: FakeBrowserFormData,
    localStorage: {
        values: new Map(),
        getItem(key) { return this.values.get(key) || null; },
        setItem(key, value) { this.values.set(key, value); },
        removeItem(key) { this.values.delete(key); }
    },
    crypto: {randomUUID: () => 'sdk-runtime-test'},
    fetch: async (url, options) => {
        fakeFetchRequest = {url, options};
        return {
            ok: true,
            async json() {
                return {text: 'SDK 运行时测试成功', requestId: 'sdk-runtime-test', audioDuration: 1};
            }
        };
    },
    setTimeout: () => 1,
    clearTimeout() {}
};
vm.runInNewContext(sdkSource, browserContext, {filename: 'offline-asr-sdk-runtime.js'});
const RuntimeSDK = browserModule.exports.SDK;
const runtimeSdk = new RuntimeSDK({baseUrl: 'http://127.0.0.1:8080'});
const runtimeDevices = await runtimeSdk.init();
assert.equal(runtimeDevices.length, 2);
assert.equal(deviceListenerAdded, true);
const permittedDevices = await runtimeSdk.getDevices({requestPermission: true});
assert.equal(permittedDevices.length, 2);
assert.equal(temporaryTrackStops, 1);
await runtimeSdk.selectDevice('mic-2');
await runtimeSdk.startMonitor();
assert.equal(requestedConstraints.audio.deviceId.exact, 'mic-2');
let volumeEvent = null;
runtimeSdk.on('volumechange', value => { volumeEvent = value; });
runtimeSdk._analyzeVolume();
assert.ok(volumeEvent && volumeEvent.rms > 0);
await runtimeSdk.startRecording();
assert.throws(() => runtimeSdk.stopMonitor(), error => error.code === 'RECORDING_IN_PROGRESS');
await assert.rejects(() => runtimeSdk.stopRecording(), error => error.code === 'AUDIO_SILENCE');
await runtimeSdk.startRecording();
runtimeSdk.hasDetectedSound = true;
const recordingResult = await runtimeSdk.stopRecording();
assert.equal(recordingResult.silent, false);
assert.ok(recordingResult.blob.size > 0);
const recognition = await runtimeSdk.recognize(recordingResult.blob, {filename: 'runtime-test.webm'});
assert.equal(recognition.text, 'SDK 运行时测试成功');
assert.equal(fakeFetchRequest.url, 'http://127.0.0.1:8080/api/asr');
assert.equal(fakeFetchRequest.options.headers['X-Request-Id'], 'sdk-runtime-test');
assert.equal(fakeFetchRequest.options.body.entries[0][0], 'file');
await runtimeSdk.release();
assert.equal(activeTrackStops, 1);
assert.equal(contextCloseCount, 1);
assert.equal(deviceListenerRemoved, true);
assert.throws(() => runtimeSdk._assertAvailable(), error => error.code === 'SDK_DESTROYED');

let eventCount = 0;
const off = sdk.on('contract-test', value => { eventCount += value; });
sdk._emit('contract-test', 2);
off();
sdk._emit('contract-test', 2);
assert.equal(eventCount, 2);
assert.throws(() => sdk.on('invalid', null), error => error.code === 'INVALID_HANDLER');

assert.ok(html.indexOf('sdk/offline-asr-sdk.js') < html.indexOf('app.js'), 'HTML 必须先加载 SDK 再加载 Demo');
assert.match(appSource, /new OfflineASR\.SDK/);
assert.match(appSource, /sdk\.startMonitor\(/);
assert.match(appSource, /sdk\.startRecording\(/);
assert.match(appSource, /sdk\.recognize\(/);
assert.match(appSource, /sdk\.release\(/);
assert.doesNotMatch(appSource, /getUserMedia|new MediaRecorder|createMediaStreamSource|fetch\(/,
    'Demo 不得重新实现 SDK 内部的麦克风、录音和 HTTP 逻辑');
assert.doesNotMatch(sdkSource, /document\.querySelector|document\.getElementById/,
    'SDK 不得依赖 Demo DOM');

const microphoneScriptStart = microphoneTestHtml.indexOf('<script>');
const microphoneScriptEnd = microphoneTestHtml.indexOf('</script>', microphoneScriptStart);
assert.ok(microphoneScriptStart >= 0 && microphoneScriptEnd > microphoneScriptStart);
new vm.Script(microphoneTestHtml.slice(microphoneScriptStart + '<script>'.length, microphoneScriptEnd));
assert.match(microphoneTestHtml, /getUserMedia/);
assert.match(microphoneTestHtml, /createMediaStreamSource/);
assert.match(microphoneTestHtml, /getFloatTimeDomainData/);
assert.match(microphoneTestHtml, /RMS/);
assert.match(microphoneTestHtml, /Peak/);
assert.match(microphoneTestHtml, /MediaRecorder/);
assert.match(microphoneTestHtml, /recording-playback/);
assert.doesNotMatch(microphoneTestHtml, /fetch\(/, '独立麦克风检测页面不得上传音频');

const guide = read('docs/SDK接入文档.md');
for (const method of apiMethods) assert.ok(guide.includes(method), 'SDK 文档缺少接口说明：' + method);
assert.match(guide, /Private Network Access/);
assert.match(guide, /release\(\)/);

console.log('SDK 导出、公开 API、事件机制、Demo 解耦及麦克风检测页面静态契约：PASS');
