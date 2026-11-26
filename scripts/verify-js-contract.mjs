import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';

const source = fs.readFileSync(new URL('../src/main/resources/static/app.js', import.meta.url), 'utf8');
const html = fs.readFileSync(new URL('../src/main/resources/static/index.html', import.meta.url), 'utf8');
const listeners = {};
const dispatched = [];
const elements = new Map([
  ['#status', {textContent: ''}],
  ['#result', {textContent: ''}],
  ['#result-meta', {textContent: ''}],
  ['#countdown', {textContent: ''}],
  ['#start', {disabled: false, onclick: null}],
  ['#stop', {disabled: true, onclick: null}],
  ['#clear', {onclick: null}],
  ['#local-file-hint', {hidden: true}],
  ['#audio-file', {files: [], onchange: null}],
  ['#upload', {disabled: true, onclick: null}],
  ['#file-info', {textContent: ''}],
]);

let intervalCallback;
let lastFormData;
class FakeMediaRecorder {
  static last;
  static isTypeSupported() { return true; }

  constructor(stream, options) {
    this.stream = stream;
    this.mimeType = options.mimeType;
    this.state = 'inactive';
    FakeMediaRecorder.last = this;
  }

  start() { this.state = 'recording'; }

  stop() {
    this.state = 'inactive';
    if (this.onstop) this.onstopPromise = this.onstop();
  }
}

class FakeFormData {
  constructor() { this.values = []; lastFormData = this; }
  append(...args) { this.values.push(args); }
}

const context = {
  Blob,
  FormData: FakeFormData,
  MediaRecorder: FakeMediaRecorder,
  CustomEvent: class CustomEvent {
    constructor(type, init) { this.type = type; this.detail = init.detail; }
  },
  crypto: {randomUUID: () => 'js-contract-001'},
  document: {querySelector: selector => elements.get(selector)},
  navigator: {mediaDevices: {getUserMedia: async () => ({
    getTracks: () => [{stop() {}}],
  })}},
  window: {
    dispatchEvent: event => dispatched.push(event),
    addEventListener: (type, listener) => {listeners[type] = listener;},
    setInterval: callback => {intervalCallback = callback; return 1;},
    clearInterval: () => {intervalCallback = null;},
  },
};
context.window.window = context.window;
context.window.document = context.document;

vm.runInNewContext(source, context);

let callbackCount = 0;
context.window.onSpeechText = text => {
  callbackCount += 1;
  assert.equal(text, '识别完成');
};
context.fetch = async () => ({
  ok: true,
  json: async () => ({
    success: true,
    requestId: 'js-contract-001',
    text: '识别完成',
    audioDuration: 1,
    decodeTime: 0.1,
    inferenceTime: 0.2,
    totalTime: 0.3,
  }),
});

const result = await context.window.OfflineASR.recognize(new Blob(['audio']), 'sample.wav');
assert.equal(result.text, '识别完成');
assert.equal(callbackCount, 1);
assert.equal(dispatched.length, 1);
assert.equal(dispatched[0].type, 'asr-result');
assert.equal(dispatched[0].detail.requestId, 'js-contract-001');
assert.equal(lastFormData.values[0][0], 'file');
assert.equal(lastFormData.values[0][2], 'sample.wav');

context.fetch = async () => ({ok: false, json: async () => {throw new Error('invalid json');}});
await assert.rejects(
  () => context.window.OfflineASR.recognize(new Blob(['audio'])),
  error => error.code === 'ASR_FAILED' && error.message === '识别失败',
);

context.window.location = {protocol: 'file:'};
await assert.rejects(
  () => context.window.OfflineASR.recognize(new Blob(['audio'])),
  error => error.code === 'LOCAL_FILE_OPEN' && error.message.includes('127.0.0.1:8080'),
);
await elements.get('#start').onclick();
assert.match(elements.get('#status').textContent, /127.0.0.1:8080/);

context.window.location = {protocol: 'http:'};
context.fetch = async () => ({
  ok: true,
  json: async () => ({
    success: true,
    requestId: 'js-contract-001',
    text: '识别完成',
    audioDuration: 1,
    decodeTime: 0.1,
    inferenceTime: 0.2,
    totalTime: 0.3,
  }),
});
await elements.get('#start').onclick();
assert.equal(elements.get('#start').disabled, true);
assert.equal(elements.get('#stop').disabled, false);
assert.equal(elements.get('#countdown').textContent, '60 秒');
for (let second = 0; second < 60; second += 1) intervalCallback();
assert.equal(FakeMediaRecorder.last.state, 'inactive');
await FakeMediaRecorder.last.onstopPromise;
assert.equal(elements.get('#status').textContent, '识别完成');
assert.equal(elements.get('#start').disabled, false);
assert.equal(elements.get('#stop').disabled, true);

const uploadedFile = {name: 'speech.wav', size: 2048};
elements.get('#audio-file').files = [uploadedFile];
elements.get('#audio-file').onchange();
assert.equal(elements.get('#upload').disabled, false);
assert.match(elements.get('#file-info').textContent, /speech.wav/);
await elements.get('#upload').onclick();
assert.equal(elements.get('#status').textContent, '识别完成');
assert.match(elements.get('#result-meta').textContent, /speech.wav/);

assert.match(html, /id="audio-file"/);
assert.match(html, /id="upload"/);
console.log('JavaScript ASR contract, recording timer and audio upload: PASS');

const microphoneTestHtml = fs.readFileSync(
  new URL('../src/main/resources/static/index-test.html', import.meta.url),
  'utf8',
);
const scriptStart = microphoneTestHtml.indexOf('<script>');
const scriptEnd = microphoneTestHtml.indexOf('</script>', scriptStart);
assert.ok(scriptStart >= 0 && scriptEnd > scriptStart, '麦克风检测页面必须包含检测脚本');
new vm.Script(microphoneTestHtml.slice(scriptStart + '<script>'.length, scriptEnd));
assert.ok(microphoneTestHtml.includes('getUserMedia'));
assert.ok(microphoneTestHtml.includes('createMediaStreamSource'));
assert.ok(microphoneTestHtml.includes('getFloatTimeDomainData'));
assert.ok(microphoneTestHtml.includes('RMS'));
assert.ok(microphoneTestHtml.includes('Peak'));
assert.ok(microphoneTestHtml.includes('MediaRecorder'));
assert.ok(microphoneTestHtml.includes('recording-playback'));
assert.equal(microphoneTestHtml.includes('fetch('), false, '独立检测页面不得上传音频或调用识别接口');

console.log('麦克风检测页面脚本语法与音量检测契约：PASS');
