// 요청 하나가 DB에 보내는 SQL 수를 센다(하이버네이트 SQL 로그). 사용: node probe.mjs <postgres|cubrid|mariadb> <포트>  (컨테이너는 이미 떠 있어야 한다)
import { spawn } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, rmSync, openSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { ADMIN } from '../../e2e/lib/server.mjs';
import { Session } from '../../e2e/lib/http.mjs';
const [DB, DPORT] = [process.argv[2], process.argv[3]];
const ROOT = resolve(new URL('../..', import.meta.url).pathname), PORT = 19610;
const app = { postgres: [`--spring.datasource.url=jdbc:postgresql://localhost:${DPORT}/yona`, '--spring.datasource.username=yona', '--spring.datasource.password=yona'],
  cubrid: [`--spring.datasource.url=jdbc:cubrid:localhost:${DPORT}:yona:dba::?charset=utf-8`, '--spring.datasource.username=dba', '--spring.datasource.password='],
  mariadb: [`--spring.datasource.url=jdbc:mariadb://localhost:${DPORT}/yona`, '--spring.datasource.username=root', '--spring.datasource.password=bench'] }[DB];
const dir = join(ROOT, '.state', `probe-${DB}`); rmSync(dir, { recursive: true, force: true }); mkdirSync(dir, { recursive: true });
const logf = join(dir, 'server.log');
const start = () => spawn(process.env.JAVA_HOME + '/bin/java', ['-Xmx2g', '-jar', join(ROOT, 'build/libs/yona-2.0.0-SNAPSHOT.jar'), `--server.port=${PORT}`, `--yona.data-dir=${dir}`, '--yona.update.interval=0', '--yona.git-default-branch=master', '--logging.level.org.hibernate.SQL=DEBUG', '--spring.jpa.ddl-auto=update', ...app], { stdio: ['ignore', openSync(logf, 'a'), openSync(logf, 'a')] });
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const base = `http://localhost:${PORT}`;
const up = async () => { for (let i = 0; i < 600; i++) { try { const r = await fetch(base + '/', { redirect: 'manual' }); if (r.status < 500) return; } catch {} await sleep(1000); } throw new Error('기동 실패'); };
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
  await s.api('POST', '/user/projects', { name: 'probe', scope: 'PUBLIC', vcs: 'GIT' });
  const auth = { Authorization: `token ${s.token}` }; const API = '/-_-api/v1/owners/admin/projects/probe';
  const post = (p, j) => fetch(base + p, { method: 'POST', headers: { ...auth, 'Content-Type': 'application/json' }, body: JSON.stringify(j) }).then((r) => r.arrayBuffer());
  for (let i = 1; i <= 30; i++) await post(`${API}/issues`, { issues: [{ title: `t${i}`, body: 'b' }] });
  for (let i = 0; i < 5; i++) await post(`${API}/issues/1/comments`, { comment: 'c' });
  const count = () => (readFileSync(logf, 'utf8').match(/org\.hibernate\.SQL/g) || []).length;
  const sqlLines = () => readFileSync(logf, 'utf8').split('\n').filter((l) => l.includes('org.hibernate.SQL')).map((l) => l.replace(/^.*org\.hibernate\.SQL\s*:\s*/, ''));
  const measure = async (name, fn) => {
    await fn(); await sleep(300); const a = sqlLines().length; await fn(); await sleep(500); const mine = sqlLines().slice(a);
    const groups = new Map(); for (const q of mine) { const k = q.slice(0, 160); groups.set(k, (groups.get(k) || 0) + 1); }
    const dup = [...groups].filter(([, n]) => n > 1).sort((x, y) => y[1] - x[1]);
    const dupTotal = dup.reduce((t, [, n]) => t + n - 1, 0);
    console.log(`  ${name.padEnd(26)} SQL ${mine.length}개, 서로 다른 질의 ${groups.size}개, 되풀이로 더 보낸 것 ${dupTotal}개`);
    for (const [k, n] of dup.slice(0, 3)) console.log(`      ${n}회  ${k.replace(/select .*? from /, 'select … from ').slice(0, 110)}`);
  };
  console.log(`[${DB}]`);
  await measure('이슈 단건 조회(API)', () => fetch(base + `${API}/issues/7`, { headers: auth }).then((r) => r.arrayBuffer()));
  await measure('댓글 목록(API)', () => fetch(base + `${API}/issues/1/comments`, { headers: auth }).then((r) => r.arrayBuffer()));
  await measure('이슈 목록 화면(HTML)', () => fetch(base + '/admin/probe/issues').then((r) => r.arrayBuffer()));
  await measure('이슈 상세 화면(HTML)', () => fetch(base + '/admin/probe/issue/7').then((r) => r.arrayBuffer()));
  await measure('이슈 생성(API)', () => post(`${API}/issues`, { issues: [{ title: 'x', body: 'y' }] }));
  await measure('댓글 생성(API)', () => post(`${API}/issues/2/comments`, { comment: 'z' }));
  await measure('검색(API, 낱말)', () => fetch(base + `${API}/search?keyword=t1&searchType=issue`, { headers: auth }).then((r) => r.arrayBuffer()));
} finally { child.kill(); await new Promise((r) => child.once('exit', r));
  if (process.env.SQLDUMP) writeFileSync(process.env.SQLDUMP, readFileSync(logf, 'utf8').split('\n').filter((l) => /lower\(/.test(l)).join('\n'));   // 검색 SQL을 파일로 남긴다(cast가 DB마다 어떻게 나가는지 보려는 용도)
  rmSync(dir, { recursive: true, force: true }); }
