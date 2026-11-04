// src/test/js/*.test.mjs 를 모두 실행한다. 각 파일은 `export const tests = [{ name, run }]`을 내보낸다. 하나라도 실패하면 종료 코드가 1이다.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { close } from './harness.mjs';

const dir = path.dirname(fileURLToPath(import.meta.url));
const only = process.argv[2];
let failed = 0, passed = 0;
for (const file of fs.readdirSync(dir).filter((f) => f.endsWith('.test.mjs')).sort()) {
  if (only && !file.includes(only)) continue;
  const { tests } = await import(pathToFileURL(path.join(dir, file)));
  for (const t of tests) {
    try { await t.run(); passed++; console.log(`PASS ${file} :: ${t.name}`); }
    catch (e) { failed++; console.log(`FAIL ${file} :: ${t.name}\n     ${String(e.message || e).split('\n').join('\n     ')}`); }
  }
}
await close();
console.log(`\n${passed} passed, ${failed} failed`);
process.exit(failed ? 1 : 0);
