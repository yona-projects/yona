// 두 순회 결과(shots/<이름>/report.json)를 비교한다(로드맵 X-38a). 사용: node compare.mjs <기준 이름> <현재 이름>
// 결과: shots/compare-<기준>-<현재>/index.html. 화면마다 기준과 현재 캡처를 나란히 놓고, 새로 생긴 오류와 사라진 화면, 상태 변화를 앞에 모아 보인다.
// 모양이 의도적으로 바뀌므로 픽셀 차이로 통과와 실패를 가르지 않고, 사람이 훑을 자료로 쓴다. 종료 코드는 "새 문제가 있으면 1"이다.
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { ROOT } from './lib/server.mjs';

const [a, b] = process.argv.slice(2);
if (!a || !b) { console.error('사용: node compare.mjs <기준 이름> <현재 이름>'); process.exit(2); }
const load = (n) => JSON.parse(readFileSync(join(ROOT, 'shots', n, 'report.json'), 'utf8')).results;
const A = load(a), B = load(b);
const key = (r) => `${r.id}.${r.role}`;
const mapA = new Map(A.map((r) => [key(r), r])), mapB = new Map(B.map((r) => [key(r), r]));
const problemsOf = (r) => [...r.console, ...r.pageerrors, ...r.failed, ...r.http, ...(r.error ? [r.error] : []), ...(r.interact ? r.interact.failed.map((f) => `[${f.type}] ${f.text}: ${f.reason}`) : [])];

const newProblems = [], fixed = [], missing = [], added = [], statusChanged = [];
for (const [k, rb] of mapB) {
  const ra = mapA.get(k);
  if (!ra) { added.push(k); continue; }
  if (ra.status !== rb.status) statusChanged.push(`${k}: ${ra.status} → ${rb.status}`);
  const pa = new Set(problemsOf(ra));
  for (const p of problemsOf(rb)) if (!pa.has(p)) newProblems.push(`${k}: ${p}`);
  const pb = new Set(problemsOf(rb));
  for (const p of problemsOf(ra)) if (!pb.has(p)) fixed.push(`${k}: ${p}`);
}
for (const k of mapA.keys()) if (!mapB.has(k)) missing.push(k);

const out = join(ROOT, 'shots', `compare-${a}-${b}`); mkdirSync(out, { recursive: true });
const esc = (s) => String(s).replace(/[&<>]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[c]));
const list = (title, items) => items.length ? `<h4>${title} (${items.length})</h4><ul>${items.map((i) => `<li>${esc(i)}</li>`).join('')}</ul>` : '';
const rows = [...mapB.keys()].map((k) => `<tr><td>${esc(k)}<br><small>${esc(mapB.get(k).path)}</small></td><td>${existsSync(join(ROOT, 'shots', a, `${k}.png`)) ? `<img loading="lazy" src="../${a}/${k}.png">` : '-'}</td><td><img loading="lazy" src="../${b}/${k}.png"></td></tr>`).join('');
writeFileSync(join(out, 'index.html'), `<!doctype html><meta charset="utf-8"><title>${a} vs ${b}</title><style>body{font:13px sans-serif;margin:8px}td{vertical-align:top;border-top:1px solid #ccc;padding:4px}img{width:560px;max-height:420px;object-fit:cover;object-position:top}</style><h3>${a} → ${b}</h3>${list('새 문제', newProblems)}${list('고친 문제', fixed)}${list('상태 변화', statusChanged)}${list('사라진 화면', missing)}${list('새 화면', added)}<table><tr><th>화면</th><th>${a}</th><th>${b}</th></tr>${rows}</table>`);
console.log(`${a} → ${b}: 새 문제 ${newProblems.length}, 고친 문제 ${fixed.length}, 상태 변화 ${statusChanged.length}, 사라진 화면 ${missing.length}, 새 화면 ${added.length}`);
for (const p of newProblems.slice(0, 30)) console.log('  새 문제:', p);
console.log('모음:', join(out, 'index.html'));
process.exit(newProblems.length || missing.length ? 1 : 0);
