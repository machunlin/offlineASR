#!/usr/bin/env node
// 中文注释：从规范化后的 CycloneDX SBOM 导出便于人工审核的 Maven 许可证清单。
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const root = dirname(scriptDir);
const label = process.argv[2];
if (!label) {
  console.error('用法：node scripts/export-maven-licenses.mjs <platform-label>');
  process.exit(2);
}

const bomPath = join(root, `deploy/sbom-${label}.cdx.json`);
const outputPath = join(root, `deploy/maven-licenses-${label}.txt`);
if (!existsSync(bomPath)) throw new Error(`缺少 SBOM: ${bomPath}`);
const bom = JSON.parse(readFileSync(bomPath, 'utf8'));
const components = [...(bom.components ?? [])].sort((a, b) =>
  `${a.group ?? ''}:${a.name}:${a.version}`.localeCompare(`${b.group ?? ''}:${b.name}:${b.version}`)
);

const lines = [
  '# OfflineASR Maven dependency license inventory',
  `# Generated from deploy/sbom-${label}.cdx.json`,
  '# Native Runtime, model, FFmpeg and Java Runtime are host-provided and are not listed as bundled Maven components.',
  '# Format: group:artifact:version<TAB>maven-scope<TAB>license',
];
for (const component of components) {
  const scope = (component.properties ?? []).find(property => property.name === 'offlineasr:maven-scope')?.value ?? 'unknown';
  const licenses = (component.licenses ?? []).map(item => item.license?.id ?? item.expression ?? item.license?.name ?? 'UNKNOWN').join(' OR ');
  lines.push(`${component.group ?? ''}:${component.name ?? ''}:${component.version ?? ''}\t${scope}\t${licenses}`);
}
writeFileSync(outputPath, `${lines.join('\n')}\n`);
console.log(`已生成 Maven 许可证清单：${outputPath}`);
