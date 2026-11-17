// 요청 하나가 보내는 SQL을 호출한 앱 코드 위치와 함께 집계한다(하이버네이트 StatementInspector). 사용: node trace.mjs <inspector 클래스 폴더>
import { spawn } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, rmSync, openSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { ADMIN } from '../../e2e/lib/server.mjs';
import { Session } from '../../e2e/lib/http.mjs';
const TRACE_DIR = resolve(process.argv[2]);
const ROOT = resolve(new URL('../..', import.meta.url).pathname), PORT = 19620;
const dir = join(ROOT, '.state', 'trace'); rmSync(dir, { recursive: true, force: true }); mkdirSync(dir, { recursive: true });
const tf = join(dir, 'sqltrace.log'); const logf = join(dir, 'server.log');
const start = () => spawn(process.env.JAVA_HOME + '/bin/java', ['-Xmx1g', `-Dtrace.file=${tf}`, `-Dloader.path=${TRACE_DIR}`, '-cp', join(ROOT, 'build/libs/yona-2.0.0-SNAPSHOT.jar'), 'org.springframework.boot.loader.launch.PropertiesLauncher',
  `--server.port=${PORT}`, `--yona.data-dir=${dir}`, '--yona.update.interval=0', '--yona.git-default-branch=master', `--spring.datasource.url=jdbc:h2:file:${dir}/db;DB_CLOSE_DELAY=-1`,
  '--spring.jpa.properties.hibernate.session_factory.statement_inspector=TraceInspector', ...(process.env.APPARGS || '').split(' ').filter(Boolean)], { stdio: ['ignore', openSync(logf, 'a'), openSync(logf, 'a')] });
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const base = `http://localhost:${PORT}`;
const up = async () => { for (let i = 0; i < 300; i++) { try { const r = await fetch(base + '/', { redirect: 'manual' }); if (r.status < 500) return; } catch {} await sleep(1000); } throw new Error('기동 실패: ' + readFileSync(logf, 'utf8').slice(-600)); };
let child = start(); await up();
const sf = join(dir, 'config', 'secret.properties');
for (let i = 0; i < 60 && !existsSync(sf); i++) {
  const pg = await fetch(base + '/', { redirect: 'manual' }); const x = (pg.headers.getSetCookie?.() ?? []).map((c) => c.split(';')[0]).find((k) => k.startsWith('XSRF-TOKEN='));
  await fetch(base + '/', { method: 'POST', headers: x ? { cookie: x, 'X-XSRF-TOKEN': x.slice(11) } : {}, body: new URLSearchParams({ loginId: 'admin', name: 'A', email: 'a@example.com', password: ADMIN.password, retypedPassword: ADMIN.password }), redirect: 'manual' });
  if (!existsSync(sf)) await sleep(1000);
}
child.kill(); await new Promise((r) => child.once('exit', r)); child = start(); await up();
try {
  const s = new Session(base); await s.login('admin'); await s.deviceToken('repo');
  await s.api('POST', '/user/projects', { name: 'trace', scope: 'PUBLIC', vcs: 'GIT' });
  const auth = { Authorization: `token ${s.token}` }; const API = '/-_-api/v1/owners/admin/projects/trace';
  const post = (p, j) => fetch(base + p, { method: 'POST', headers: { ...auth, 'Content-Type': 'application/json' }, body: JSON.stringify(j) }).then((r) => r.arrayBuffer());
  for (let i = 1; i <= 40; i++) await post(`${API}/issues`, { issues: [{ title: `t${i}`, body: 'b' }] });
  for (let i = 0; i < 5; i++) await post(`${API}/issues/1/comments`, { comment: 'c' });
  const lines = () => readFileSync(tf, 'utf8').split('\n').filter((l) => l.startsWith('SQLTRACE'));
  const report = {};
  const measure = async (name, fn) => { await fn(); await sleep(300); const a = lines().length; await fn(); await sleep(500); report[name] = lines().slice(a).map((l) => { const [, sql, who] = l.split('\t'); return { sql, who }; }); };
  await measure('이슈 목록 화면', () => fetch(base + '/admin/trace/issues').then((r) => r.arrayBuffer()));
  await measure('이슈 상세 화면', () => fetch(base + '/admin/trace/issue/7').then((r) => r.arrayBuffer()));
  await measure('이슈 단건 API', () => fetch(base + `${API}/issues/7`, { headers: auth }).then((r) => r.arrayBuffer()));
  await measure('검색 API', () => fetch(base + `${API}/search?keyword=t1&searchType=issue`, { headers: auth }).then((r) => r.arrayBuffer()));
  writeFileSync(join(process.env.CLAUDE_JOB_DIR, 'tmp/trace/report' + (process.env.TAG || '') + '.json'), JSON.stringify(report, null, 1));
  console.log('저장', Object.entries(report).map(([k, v]) => `${k} ${v.length}개`).join(', '));
} finally { child.kill(); await new Promise((r) => child.once('exit', r)); rmSync(dir, { recursive: true, force: true }); }
