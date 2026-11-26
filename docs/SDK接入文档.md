# 浏览器端离线 ASR JavaScript SDK 接入文档

版本：1.0.0  
适用服务：Java 11 + Spring Boot 离线 ASR  
默认识别接口：`POST /api/asr`  
文档目标：第三方前端工程师可独立完成浏览器麦克风采集、录音和离线语音识别接入。

## 1. SDK 定位与架构

SDK 是独立的浏览器端 JavaScript 库，负责：

- 麦克风设备枚举、设备选择及设备变化通知。
- 麦克风权限申请、实时音量检测和音轨状态监控。
- 浏览器录音、静音检测、录音时长限制和音频资源释放。
- 将音频以 multipart/form-data 上传到 ASR 服务。
- 通过事件和 Promise 返回识别结果及错误信息。

SDK 不依赖本项目的 `index.html`、`app.js` 或任何页面 DOM。第三方系统只需引入 SDK 文件并调用公开 API。

整体调用关系：

```text
第三方业务页面
    |
    | JavaScript SDK API
    v
offline-asr-sdk.js
    |-- 浏览器 MediaDevices / MediaStream
    |-- Web Audio API / AudioContext
    |-- MediaRecorder
    |
    | POST /api/asr (multipart/form-data)
    v
Spring Boot ASR 服务
    |
    v
FFmpeg -> sherpa-onnx -> SenseVoiceSmall
    |
    v
识别文本 JSON
```

## 2. 文件与运行环境

SDK 文件位于：

```text
/sdk/offline-asr-sdk.js
```

在本项目中可通过以下地址访问：

`http://127.0.0.1:8080/sdk/offline-asr-sdk.js`

建议第三方项目将 SDK 文件复制到自身静态资源目录，并由自身 Web 服务提供；不要把 Demo 的 `app.js` 当作 SDK 使用。

### 2.1 浏览器要求

- 建议使用当前受支持版本的 Chrome 或 Edge。
- 麦克风采集依赖 `navigator.mediaDevices.getUserMedia`。
- 实时音量检测依赖 Web Audio API（`AudioContext`）。
- 录音依赖 `MediaRecorder`，SDK 优先选择 WebM/Opus，随后尝试 WebM 或 MP4。
- 浏览器必须处于安全上下文：HTTPS 或 localhost。普通 HTTP 远程域名通常无法调用麦克风。
- 必须由用户明确授权麦克风访问。SDK 不能绕过浏览器权限，也不能在用户拒绝后静默开启设备。

### 2.2 本机部署和远程网页的区别

**推荐部署方式：业务页面和 ASR 服务运行在同一台客户 Windows 主机。**

例如客户主机本地启动 ASR 服务，浏览器访问 `http://127.0.0.1:8080/`。该地址属于 localhost 安全上下文。

如果业务页面部署在远程服务器，而 ASR 服务仍运行在客户电脑的 localhost，则需要额外处理：

1. 远程页面访问本机服务会涉及 CORS。
2. HTTPS 页面访问 localhost 可能触发浏览器 Private Network Access（私有网络访问）限制或预检请求。
3. 客户端安全软件、防火墙和浏览器策略可能限制网页访问本机服务。
4. 不能将 `baseUrl` 改为任意地址就认为跨域问题已解决。

本项目服务默认只监听 `127.0.0.1`。不要为了让远程页面接入而擅自改为 `0.0.0.0`；如需远程接入，应单独评审网络边界、身份认证、CORS、访问控制和 TLS。

## 3. 引入 SDK

### 3.1 浏览器 script 引入

```html
<script src="/sdk/offline-asr-sdk.js"></script>
<script>
    const asr = new OfflineASR.SDK({
        baseUrl: 'http://127.0.0.1:8080'
    });
</script>
```

SDK 加载后提供全局对象：

- `OfflineASR.SDK`：SDK 构造函数。
- `OfflineASR.SDK_VERSION`：SDK 版本号。

SDK 同时保留 CommonJS 导出形式，便于 Node.js 测试工具加载；浏览器接入以 script 方式为准。

### 3.2 初始化参数

```javascript
const asr = new OfflineASR.SDK({
    baseUrl: 'http://127.0.0.1:8080',
    recognizePath: '/api/asr',
    maxRecordingSeconds: 600,
    rememberDevice: true,
    storageKey: 'offline-asr-microphone-device',
    silenceRmsThreshold: 0.0003,
    silencePeakThreshold: 0.001
});
```

| 参数 | 默认值 | 说明 |
|---|---|---|
| `baseUrl` | 空字符串 | ASR 服务地址；同源部署可留空 |
| `recognizePath` | `/api/asr` | 识别接口路径 |
| `maxRecordingSeconds` | 600 | SDK 单次录音最长时长，单位秒 |
| `rememberDevice` | true | 是否记忆上次选择的设备 |
| `storageKey` | `offline-asr-microphone-device` | localStorage 设备 ID 键 |
| `silenceRmsThreshold` | 0.0003 | 静音判断 RMS 阈值 |
| `silencePeakThreshold` | 0.001 | 静音判断 Peak 阈值 |

录音时长默认按项目需求使用 600 秒。若业务确实需要更短限制，可在实例化时传入更小值；不要仅修改页面倒计时而忽略 SDK 限制。

## 4. 初始化和设备管理

`init()` 不主动弹出权限窗口，只初始化 SDK、读取可见设备列表并恢复已记忆的设备 ID。

```javascript
await asr.init();
const devices = await asr.getDevices();
console.log(devices);
```

设备对象结构：

```javascript
{
    deviceId: '浏览器提供的设备标识',
    groupId: '设备分组标识',
    label: '麦克风名称',
    kind: 'audioinput'
}
```

浏览器在用户授权前可能隐藏设备名称或返回空设备标识，这是浏览器隐私保护行为，不代表没有麦克风。

### 4.1 申请权限并刷新设备

应由用户点击按钮触发：

```javascript
const devices = await asr.getDevices({ requestPermission: true });
```

该方法会短暂申请麦克风权限以便浏览器返回设备名称，随后释放临时音频流。正式检测或录音时 SDK 会重新打开所选设备。

### 4.2 选择麦克风

```javascript
await asr.selectDevice(deviceId);
```

传入空字符串表示系统默认麦克风。设备 ID 由浏览器提供，可能因权限、设备变化或浏览器策略而变化，不能将其作为永久硬件编号使用。

如果当前已启动音量检测，切换设备会释放旧音频流并重新打开新设备。录音期间不允许切换设备。

## 5. 麦克风实时检测

### 5.1 启动和停止检测

```javascript
await asr.startMonitor();

// 停止音量分析，但默认保留麦克风流，便于随后直接录音。
asr.stopMonitor();

// 停止检测并同时释放麦克风流。录音过程中不允许停止音量检测，以免静音保护失效。
asr.stopMonitor({ keepStream: false });
```

音量检测会持续产生 `volumechange` 事件。建议业务页面只更新音量条和必要的状态，不要在每次回调中执行大量 DOM 重绘或业务请求。

### 5.2 音量事件数据

```javascript
asr.on('volumechange', volume => {
    console.log(volume.rms, volume.peak, volume.dbfs, volume.level);
});
```

| 字段 | 含义 |
|---|---|
| `rms` | 均方根音量，反映采样窗口内平均能量 |
| `peak` | 峰值振幅 |
| `dbfs` | 相对满刻度分贝值；数字静音时为负无穷 |
| `level` | SDK 映射后的 0～100 显示值 |
| `timestamp` | 本地采样时间戳（毫秒） |

RMS/Peak 用于检测数字音频信号，不等于语音识别质量判断。环境噪声、键盘声也可能产生音量；最终是否为有效语音仍需业务侧结合场景判断。

### 5.3 音轨状态

```javascript
asr.on('trackstatechange', state => {
    console.log(state.connected, state.label, state.readyState, state.muted);
});
```

重点关注：

- `readyState === 'live'`：音轨仍处于活动状态。
- `muted === true`：浏览器暂时没有从该音轨提供媒体数据。
- `readyState === 'ended'`：音轨已经结束，通常需要重新授权或重新选择设备。

## 6. 录音

### 6.1 开始录音

```javascript
await asr.startRecording();
```

SDK 会复用已打开的麦克风流；如果尚未启动检测，会先启动检测。录音上限默认 600 秒，到时 SDK 自动停止录音并触发 `recordinglimit` 事件。

### 6.2 停止录音

```javascript
try {
    const recording = await asr.stopRecording();
    console.log(recording.blob);
    console.log(recording.durationSeconds);
    console.log(recording.mimeType);
} catch (error) {
    if (error.code === 'AUDIO_SILENCE') {
        console.warn('没有检测到有效声音', error.recording);
    }
}
```

成功返回对象：

| 字段 | 说明 |
|---|---|
| `blob` | 浏览器录制的音频 Blob |
| `durationSeconds` | 实际录音时长 |
| `mimeType` | 浏览器录制格式 |
| `silent` | 是否被 SDK 判定为静音 |

默认情况下，如果没有检测到超过阈值的音频信号，`stopRecording()` 会以 `AUDIO_SILENCE` 拒绝 Promise，错误对象的 `recording` 字段保留本次录音信息。若业务确实需要保留静音文件，可在开始录音时传入 `{ rejectSilence: false }`。

### 6.3 录音时长配置

```javascript
const asr = new OfflineASR.SDK({ maxRecordingSeconds: 300 });
```

也可以单次覆盖：

```javascript
await asr.startRecording({ maxRecordingSeconds: 120 });
```

## 7. 语音识别

录音与识别是两个独立步骤，便于第三方决定何时上传、是否先试听、是否保存音频。

```javascript
const recording = await asr.stopRecording();
const result = await asr.recognize(recording.blob, {
    filename: 'meeting-recording.webm'
});
console.log(result.text);
```

也支持将“停止录音 + 上传识别”合并为一次调用：

```javascript
const result = await asr.stopRecordingAndRecognize({filename: 'recording.webm'});
console.log(result.text);
```

该方法内部先执行 `stopRecording()`，再执行 `recognize()`；静音检测失败时不会上传。

也支持直接识别文件：

```javascript
const file = document.querySelector('#audio-file').files[0];
const result = await asr.recognize(file, { filename: file.name });
```

### 7.1 HTTP 请求

SDK 调用：

- 方法：`POST`
- 路径：默认 `/api/asr`
- Content-Type：由浏览器自动生成 multipart/form-data boundary
- 表单字段：`file`
- 请求头：`X-Request-Id`

不要手动设置 multipart 的 Content-Type，否则可能丢失浏览器自动生成的 boundary。

### 7.2 返回数据

SDK 原样返回后端 JSON，并在缺少 requestId 时补充本次请求 ID。当前服务响应字段包括：

| 字段 | 说明 |
|---|---|
| `text` | 识别文本 |
| `requestId` | 请求追踪 ID |
| `audioDuration` | 音频时长（秒） |
| `decodeTime` | FFmpeg 解码耗时（秒） |
| `inferenceTime` | 模型推理耗时（秒） |
| `totalTime` | 总处理耗时（秒） |

最终以实际后端响应契约为准。SDK 不对识别文本进行业务加工。

## 8. 事件机制

通过 `on(eventName, handler)` 注册事件，返回取消订阅函数：

```javascript
const off = asr.on('result', result => {
    console.log('识别结果：', result.text);
});

// 不再需要时取消订阅
off();
```

| 事件 | 触发时机 | 事件数据 |
|---|---|---|
| `ready` | SDK 初始化完成 | `{ devices }` |
| `deviceschange` | 设备列表刷新或系统设备变化 | `{ devices }` |
| `devicechange` | SDK 当前选中的设备 ID 变化 | `{ deviceId }` |
| `deviceconnected` | 音频流连接或释放 | 音轨状态对象 |
| `trackstatechange` | 音轨 mute/unmute/ended | 音轨状态对象 |
| `monitorstart` | 音量检测启动 | 音轨状态对象 |
| `monitorstop` | 音量检测停止 | 空对象 |
| `volumechange` | 每次音量采样 | 音量数据对象 |
| `recordingstart` | 开始录音 | MIME 类型、开始时间 |
| `recordingstop` | 录音结束 | Blob、时长、格式、静音标识 |
| `recordinglimit` | 达到录音上限 | 最大录音时长 |
| `result` | 识别成功 | 后端识别结果 |
| `error` | 录音器内部异常等 | Error 对象 |

设备列表变化事件与当前选择变化事件不是同一概念，不要混用。

## 9. 错误码

| 错误码 | 含义 | 建议处理 |
|---|---|---|
| `SDK_DESTROYED` | SDK 已释放 | 重新创建 SDK 实例 |
| `MEDIA_DEVICES_UNSUPPORTED` | 浏览器不支持媒体设备 API | 更换受支持浏览器或安全上下文 |
| `MICROPHONE_PERMISSION_DENIED` | 用户拒绝麦克风权限 | 指引用户到浏览器站点设置授权 |
| `MICROPHONE_NOT_FOUND` | 未找到麦克风 | 检查设备连接并刷新列表 |
| `MICROPHONE_NOT_READABLE` | 设备不可读或被占用 | 检查系统隐私权限、静音及设备占用 |
| `MICROPHONE_ACCESS_DENIED` | 麦克风打开失败 | 检查权限和设备选择 |
| `AUDIO_MONITOR_UNSUPPORTED` | 不支持实时音量检测 | 更换浏览器 |
| `RECORDING_IN_PROGRESS` | 已在录音或录音期间切换设备 | 先停止当前录音 |
| `NOT_RECORDING` | 当前没有录音 | 检查业务状态 |
| `AUDIO_SILENCE` | 未检测到有效音频信号 | 检查输入设备、系统静音和麦克风权限 |
| `INVALID_AUDIO` | 上传音频为空或无效 | 检查文件内容 |
| `NETWORK_ERROR` | 无法连接 ASR 服务 | 检查服务状态、地址、CORS 和浏览器网络策略 |
| `ASR_FAILED` | 后端识别失败 | 根据错误消息和 requestId 查看服务日志 |

不要仅根据 `AUDIO_SILENCE` 就断定麦克风硬件损坏；还需要检查操作系统麦克风隐私权限、系统静音、浏览器输入设备选择和音轨状态。

## 10. 资源释放和页面生命周期

页面离开或业务模块不再使用 SDK 时必须释放资源：

```javascript
await asr.release();
```

释放操作会停止录音（不上传）、停止音量检测、关闭 AudioContext、停止 MediaStream 音轨、移除设备变化监听并清空事件订阅。

`release()` 后当前实例不可再次使用。如需重新接入，应创建新的 SDK 实例。

推荐在页面卸载时触发释放。若页面使用 SPA 路由，应在组件销毁时释放，而不是只依赖 `beforeunload`。

## 11. 完整接入示例

以下示例可作为第三方页面的最小起点：

```html
<select id="microphones"></select>
<button id="refresh">授权并刷新设备</button>
<button id="monitor">启动麦克风检测</button>
<button id="record">开始录音</button>
<button id="stop">停止录音</button>
<button id="recognize">识别录音</button>
<pre id="text"></pre>

<script src="/sdk/offline-asr-sdk.js"></script>
<script>
const asr = new OfflineASR.SDK({
    baseUrl: 'http://127.0.0.1:8080',
    maxRecordingSeconds: 600
});
let audioBlob = null;

asr.on('volumechange', data => {
    console.log('实时音量', data.level, data.rms, data.peak);
});
asr.on('trackstatechange', state => {
    console.log('音轨状态', state);
});
asr.on('result', result => {
    document.querySelector('#text').textContent = result.text || '';
});
asr.on('error', error => {
    console.error(error.code, error.message);
});

async function initialize() {
    await asr.init();
    await refreshDevices();
}

async function refreshDevices() {
    const devices = await asr.getDevices({requestPermission: true});
    const select = document.querySelector('#microphones');
    select.replaceChildren(new Option('系统默认麦克风', ''));
    devices.forEach(device => select.add(new Option(device.label, device.deviceId)));
}

document.querySelector('#refresh').onclick = refreshDevices;
document.querySelector('#microphones').onchange = event => asr.selectDevice(event.target.value);
document.querySelector('#monitor').onclick = () => asr.startMonitor();
document.querySelector('#record').onclick = () => asr.startRecording();

document.querySelector('#stop').onclick = async () => {
    try {
        const result = await asr.stopRecording();
        audioBlob = result.blob;
    } catch (error) {
        console.error(error.code, error.message);
    }
};

document.querySelector('#recognize').onclick = async () => {
    if (!audioBlob) return;
    const result = await asr.recognize(audioBlob, {filename: 'recording.webm'});
    document.querySelector('#text').textContent = result.text || '';
};

initialize().catch(error => console.error(error.code, error.message));
window.addEventListener('pagehide', () => asr.release());
</script>
```

生产项目还应补充按钮禁用状态、加载状态、录音回放、文件大小检查和业务异常提示。

## 12. 接入注意事项

1. 麦克风权限必须由用户在浏览器弹窗中授权；不要在页面加载时自动弹出权限窗口。
2. 先调用 `getDevices({requestPermission:true})` 可获得更完整的设备名称；也可直接启动检测，由浏览器在用户操作时申请权限。
3. 蓝牙耳机连接、断开或切换输入模式后，重新刷新设备列表并确认当前选中的输入设备。
4. 设备 ID 由浏览器管理，不能作为跨浏览器或跨操作系统的固定标识。
5. 音量条有变化不等于录音内容一定是有效语音；建议录音后试听并结合服务端解码 RMS/Peak 日志排查。
6. SDK 静音检测是客户端防护，不替代服务端音频校验。
7. 不要手动设置 multipart Content-Type。
8. 远程 HTTPS 页面调用本机 HTTP 服务时，需要单独验证 CORS、Private Network Access 和浏览器安全策略。
9. 当前 ASR 服务默认只监听本机回环地址，不应为了跨域而直接开放局域网访问。
10. 页面退出、组件销毁或切换业务模块时必须调用 `release()`。
11. SDK 当前不实现用户身份认证、访问令牌、流式识别和 WebSocket；如业务需要，应作为独立需求设计。
12. 不要把音频 Blob、设备 ID 或识别文本写入不必要的长期存储；遵循业务系统自身的数据保护要求。

## 13. Demo 与 SDK 的边界

- `index.html` 和 `app.js`：本项目功能演示，所有录音和识别能力通过 SDK 调用。
- `sdk/offline-asr-sdk.js`：第三方复用的核心浏览器库。
- `microphone-test.html`：独立麦克风排障工具，不上传音频、不调用 ASR。
- `docs/SDK接入文档.md`：第三方接入说明。

Demo 页面可以调整布局和提示文案，但不得把麦克风采集、录音、静音检测和 HTTP 识别逻辑重新写回 Demo。
