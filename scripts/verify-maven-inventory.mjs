#!/usr/bin/env node
// 中文注释：校验 Maven 依赖清单、SBOM 和外部 Native Runtime 隔离边界。
import { existsSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const root = dirname(scriptDir);
const label = process.argv[2];
if (!label) {
  console.error('用法：node scripts/verify-maven-inventory.mjs <platform-label>');
  process.exit(2);
}

function fail(message) {
  console.error(`Maven 依赖清单校验失败：${message}`);
  process.exit(1);
}

const paths = {
  runtime: join(root, `deploy/maven-runtime-dependencies-${label}.txt`),
  test: join(root, `deploy/maven-test-dependencies-${label}.txt`),
  sbom: join(root, `deploy/sbom-${label}.cdx.json`)
};
for (const [kind, path] of Object.entries(paths)) {
  if (!existsSync(path)) fail(`缺少 ${kind} 文件: ${path}`);
}
const runtimeLines = readFileSync(paths.runtime, 'utf8').split(/\r?\n/).filter(line => line.trim());
const testLines = readFileSync(paths.test, 'utf8').split(/\r?\n/).filter(line => line.trim());
if (runtimeLines.length === 0) fail('运行时依赖树为空');
if (testLines.length <= runtimeLines.length) fail('测试依赖树未包含额外测试依赖');

const bom = JSON.parse(readFileSync(paths.sbom, 'utf8'));
if (bom.bomFormat !== 'CycloneDX') fail('SBOM 不是 CycloneDX 格式');
if (!Array.isArray(bom.components) || bom.components.length === 0) fail('SBOM components 为空');
if (!bom.metadata?.component) fail('SBOM 缺少 metadata.component');

const components = bom.components.map(component => ({
  ga: `${component.group ?? ''}:${component.name ?? ''}`,
  version: component.version ?? '',
  licenses: component.licenses ?? [],
  properties: component.properties ?? []
}));

const missingLicenses = components.filter(component => component.licenses.length === 0);
if (missingLicenses.length > 0) {
  fail(`SBOM 中有 ${missingLicenses.length} 个构件未提供许可证元数据：${missingLicenses.map(component => `${component.ga}:${component.version}`).join(', ')}`);
}

const wrapper = components.find(component => component.ga === 'com.k2fsa.sherpa.onnx:sherpa-onnx-jvm');
if (!wrapper) fail('SBOM 未记录 sherpa-onnx Java Wrapper');
const wrapperScope = wrapper.properties.find(property => property.name === 'offlineasr:maven-scope')?.value;
if (wrapperScope !== 'system') fail(`sherpa-onnx Java Wrapper 的 Maven scope 异常：${wrapperScope ?? 'missing'}`);

const forbiddenNative = components.filter(component => component.ga.includes('sherpa-onnx-native-lib-'));
if (forbiddenNative.length > 0) {
  fail(`SBOM 不应记录随应用分发的 Native Runtime：${forbiddenNative.map(component => component.ga).join(', ')}`);
}

console.log(`Maven 依赖清单校验通过：运行时 ${runtimeLines.length} 行，测试 ${testLines.length} 行，SBOM ${components.length} 个构件；Native Runtime 未进入应用依赖。`);
