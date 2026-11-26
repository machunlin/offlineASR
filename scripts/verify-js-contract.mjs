import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';

const source = fs.readFileSync(new URL('../src/main/resources/static/app.js', import.meta.url), 'utf8');
const listeners = {};
const dispatched = [];
const elements = new Map([
  ['#status', { textContent: '' }],
  ['#result', { textContent: '' }],
  ['#start', { disabled: false, onclick: null }],
  ['#stop', { disabled: true, onclick: null }],
  ['#clear', { onclick: null }],
]);

const context = {
  Blob,
  FormData,
  CustomEvent: class CustomEvent {
    constructor(type, init) { this.type = type; this.detail = init.detail; }
  },
  crypto: { randomUUID: () => 'js-contract-001' },
  document: { querySelector: selector => elements.get(selector) },
  navigator: { mediaDevices: { getUserMedia: async () => { throw new Error('not used'); } } },
  window: {
    dispatchEvent: event => dispatched.push(event),
    addEventListener: (type, listener) => { listeners[type] = listener; },
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
    inferenceTime: 0.2,
  }),
});

const result = await context.window.OfflineASR.recognize(new Blob(['audio']));
assert.equal(result.text, '识别完成');
assert.equal(callbackCount, 1);
assert.equal(dispatched.length, 1);
assert.equal(dispatched[0].type, 'asr-result');
assert.equal(dispatched[0].detail.requestId, 'js-contract-001');

context.fetch = async () => ({ ok: false, json: async () => { throw new Error('invalid json'); } });
await assert.rejects(
  () => context.window.OfflineASR.recognize(new Blob(['audio'])),
  error => error.code === 'ASR_FAILED' && error.message === '识别失败',
);

context.window.location = {protocol: 'file:'};
await assert.rejects(
    () => context.window.OfflineASR.recognize(new Blob(['audio'])),
    error => error.code === 'LOCAL_FILE_OPEN' && error.message.includes('127.0.0.1:8080'),
);
await context.window.document.querySelector('#start').onclick();
assert.match(elements.get('#status').textContent, /127.0.0.1:8080/);

console.log('JavaScript ASR contract: PASS');
