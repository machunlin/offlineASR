#!/usr/bin/env node
// 中文注释：补齐本地 system scope sherpa-onnx Java Wrapper 的许可证与交付属性。
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const root = dirname(scriptDir);
const label = process.argv[2];
if (!label) {
  console.error('用法：node scripts/normalize-maven-bom.mjs <platform-label>');
  process.exit(2);
}

const bomPath = join(root, `deploy/sbom-${label}.cdx.json`);
const testTreePath = join(root, `deploy/maven-test-dependencies-${label}.txt`);
for (const path of [bomPath, testTreePath]) {
  if (!existsSync(path)) throw new Error(`缺少文件: ${path}`);
}

const bom = JSON.parse(readFileSync(bomPath, 'utf8'));
const treeLines = readFileSync(testTreePath, 'utf8').split(/\r?\n/);
const scopeByGa = new Map();
for (const line of treeLines) {
  const match = line.match(/(?:^[|+\- ]*)?([^: ]+):([^: ]+):(?:[^: ]+:)?([^: ]+):([^: ]+)$/);
  if (!match) continue;
  const [, group, name, , scope] = match;
  scopeByGa.set(`${group}:${name}`, scope);
}

for (const component of bom.components ?? []) {
  const ga = `${component.group ?? ''}:${component.name ?? ''}`;
  const scope = scopeByGa.get(ga) ?? 'runtime';
  component.properties ??= [];
  const propertyNames = new Set(component.properties.map(property => property.name));
  if (!propertyNames.has('offlineasr:maven-scope')) {
    component.properties.push({ name: 'offlineasr:maven-scope', value: scope });
  }

  if (ga === 'com.k2fsa.sherpa.onnx:sherpa-onnx-jvm') {
    if (!component.licenses || component.licenses.length === 0) {
      component.licenses = [{
        license: {
          id: 'Apache-2.0',
          url: 'https://github.com/k2-fsa/sherpa-onnx/blob/v2.0.3/LICENSE'
        }
      }];
    }
    component.properties.push(
      { name: 'offlineasr:delivery-mode', value: 'bundled-java-wrapper' },
      { name: 'offlineasr:native-runtime', value: 'not-bundled; host-provided' }
    );
  }
}

bom.metadata ??= {};
bom.metadata.properties ??= [];
const existingMetadataNames = new Set(bom.metadata.properties.map(property => property.name));
for (const property of [
  { name: 'offlineasr:platform-label', value: label },
  { name: 'offlineasr:java-runtime-policy', value: 'host-provided Java 11; no JRE redistributed' },
  { name: 'offlineasr:native-runtime-policy', value: 'sherpa-onnx JNI and ONNX Runtime are host-provided; not bundled' },
  { name: 'offlineasr:model-policy', value: 'SenseVoiceSmall model is host-provided; not bundled' },
  { name: 'offlineasr:ffmpeg-policy', value: 'FFmpeg is host-provided; not bundled' }
]) {
  if (!existingMetadataNames.has(property.name)) bom.metadata.properties.push(property);
}

writeFileSync(bomPath, `${JSON.stringify(bom, null, 2)}\n`);
console.log(`已补齐 CycloneDX 元数据：${bomPath}`);
