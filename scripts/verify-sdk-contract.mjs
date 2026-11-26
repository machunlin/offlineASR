import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import {createRequire} from 'node:module';

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

const exported = require(sdkPath);
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
