// DB 성능 비교 측정(애플리케이션 수준). 같은 jar, 같은 자원 제한, 같은 데이터와 요청 순서로 한 DB씩 돌린다.
// 사용: node tools/db-bench/bench.mjs <mariadb|postgres|cubrid> [출력 폴더]   (환경 변수 SCALE=0.1 이면 규모를 줄여 예행한다)
// 컨테이너는 이 스크립트가 만들고(이름 yona-bench-<db>), 끝나면 지운다. 다른 컨테이너는 건드리지 않는다.
import { spawn, spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { performance } from 'node:perf_hooks';
import { ADMIN } from '../../e2e/lib/server.mjs';
import { Session } from '../../e2e/lib/http.mjs';

const DB = process.argv[2];
// 변형 실험용 입력: DBCMD(컨테이너 안에서 실행할 셸 명령, 설정 변경 뒤 재시작 포함), APPARGS(앱에 더할 인자), TAG(결과 파일 이름 꼬리)
const DBCMD = process.env.DBCMD || '', APPARGS = (process.env.APPARGS || '').split(' ').filter(Boolean), TAG = process.env.TAG ? `-${process.env.TAG}` : '';
const OUT = resolve(process.argv[3] || '.');
const SCALE = Number(process.env.SCALE || 1);
const ROOT = resolve(new URL('../..', import.meta.url).pathname);
const JAR = join(ROOT, 'build/libs/yona-2.0.0-SNAPSHOT.jar');
const JAVA = (process.env.JAVA_HOME || '') + '/bin/java';
const PORT = 19600, NAME = `yona-bench-${DB}`, CPUS = process.env.CPUS || '4', MEM = '4g';
const N_SEQ = Math.round(1000 * SCALE), N_PAR = Math.round(4000 * SCALE), N_COMMENT = Math.round(4000 * SCALE);

const DBS = {
  mariadb: { image: 'mariadb:10.11', port: 36001, inner: 3306, data: '/var/lib/mysql',
    env: ['-e', 'MARIADB_ROOT_PASSWORD=bench', '-e', 'MARIADB_DATABASE=yona'], cmd: ['--character-set-server=utf8mb4', '--collation-server=utf8mb4_bin'],
    app: ['--spring.datasource.url=jdbc:mariadb://localhost:36001/yona', '--spring.datasource.username=root', '--spring.datasource.password=bench'] },
  postgres: { image: 'postgres:16', port: 36002, inner: 5432, data: '/var/lib/postgresql/data',
    env: ['-e', 'POSTGRES_DB=yona', '-e', 'POSTGRES_USER=yona', '-e', 'POSTGRES_PASSWORD=yona'], cmd: [],
    app: ['--spring.datasource.url=jdbc:postgresql://localhost:36002/yona', '--spring.datasource.username=yona', '--spring.datasource.password=yona'] },
  cubrid: { image: 'cubrid/cubrid:11.4', port: 36003, inner: 33000, data: '/home/cubrid/CUBRID/databases',
    env: ['-e', 'CUBRID_DB=yona', '-e', 'CUBRID_LOCALE=en_US.utf8'], cmd: [],
    app: ['--spring.datasource.url=jdbc:cubrid:localhost:36003:yona:dba::?charset=utf-8', '--spring.datasource.username=dba', '--spring.datasource.password='] },
}[DB];
if (process.env.DBURL) DBS.app[0] = `--spring.datasource.url=${process.env.DBURL}`;   // 접속 주소 변형 실험용
if (!DBS) { console.error('사용: bench.mjs <mariadb|postgres|cubrid>'); process.exit(2); }

const sh = (cmd, args, opt = {}) => { const r = spawnSync(cmd, args, { encoding: 'utf8', ...opt }); return { code: r.status, out: (r.stdout || '') + (r.stderr || '') }; };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const log = (...a) => console.log(`[${DB}]`, ...a);

// 난수는 DB와 무관하게 같은 순서가 나오도록 고정한다
function rng(seed) { return () => { seed |= 0; seed = (seed + 0x6D2B79F5) | 0; let t = Math.imul(seed ^ (seed >>> 15), 1 | seed); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; }; }
const rand = rng(20261117);
const WORDS = ['로그인', '배포', '알림', '검색', '첨부', '권한', '메일', '성능', '한글', '저장소', '브랜치', '리뷰', '마일스톤', '라벨', '댓글', '캐시', '인증', '토큰', '백업', '이관'];
const SENT = ['화면이 느리게 열린다는 보고가 있다.', '재현 절차는 아래와 같다.', '로그에는 오류가 남지 않는다.', 'Chrome과 Firefox에서 모두 같다.', '이전 버전에서는 정상이었다.', '운영 서버에서만 나타난다.', '임시로 우회할 수 있다.'];
const pick = (a) => a[Math.floor(rand() * a.length)];
const issueBody = (i) => Array.from({ length: 6 }, () => pick(SENT)).join(' ') + ` (${i})`;
const issueTitle = (i) => (i % 700 === 0 ? `희귀어${i / 700} 관련 문제 ${i}` : `${pick(WORDS)} 관련 문제 ${i}`);

async function waitHttp(base, ms = 600000) {
  const end = Date.now() + ms;
  while (Date.now() < end) { try { const r = await fetch(base + '/', { redirect: 'manual' }); if (r.status < 500) return; } catch { /* 기동 중 */ } await sleep(1000); }
  throw new Error('서버가 뜨지 않았다');
}

function startApp(dataDir) {
  mkdirSync(dataDir, { recursive: true });
  const logFile = join(dataDir, 'server.log');
  const child = spawn(JAVA, ['-Xmx2g', '-jar', JAR, `--server.port=${PORT}`, `--yona.data-dir=${dataDir}`, '--yona.update.interval=0', '--yona.git-default-branch=master',
    '--spring.datasource.hikari.maximum-pool-size=20', ...DBS.app, ...APPARGS], { stdio: ['ignore', require_fd(logFile), require_fd(logFile)] });
  return child;
}
import { openSync } from 'node:fs';
function require_fd(f) { return openSync(f, 'a'); }

async function stat(fn, n, c) {
  const lat = []; let errors = 0, next = 0;
  const t0 = performance.now();
  await Promise.all(Array.from({ length: c }, async () => {
    while (true) {
      const i = next++; if (i >= n) return;
      const s = performance.now();
      try { const ok = await fn(i); if (!ok) errors++; } catch { errors++; }
      lat.push(performance.now() - s);
    }
  }));
  const sec = (performance.now() - t0) / 1000;
  lat.sort((a, b) => a - b);
  const q = (p) => lat[Math.min(lat.length - 1, Math.floor(lat.length * p))];
  return { n, c, errors, sec: +sec.toFixed(2), rps: +(n / sec).toFixed(1), p50: +q(0.5).toFixed(1), p95: +q(0.95).toFixed(1), p99: +q(0.99).toFixed(1), max: +lat[lat.length - 1].toFixed(1) };
}

const result = { db: DB, image: DBS.image, scale: SCALE, cpus: CPUS, memory: MEM, jvm: '-Xmx2g', pool: 20, started: new Date().toISOString() };
let child = null;
const dataDir = join(ROOT, '.state', `bench-${DB}`);
rmSync(dataDir, { recursive: true, force: true });
sh('docker', ['rm', '-fv', NAME]);
try {
  // 1) 컨테이너
  log('컨테이너 시작');
  const run = sh('docker', ['run', '-d', '--name', NAME, '--cpus', CPUS, '--memory', MEM, '-p', `${DBS.port}:${DBS.inner}`, ...DBS.env, DBS.image, ...DBS.cmd]);
  if (run.code !== 0) throw new Error(run.out);
  const t0 = performance.now();
  for (let i = 0; i < 180; i++) { // 연결이 되고 첫 질의가 통할 때까지
    const probe = { mariadb: ['mariadb', '-uroot', '-pbench', '-e', 'select 1'], postgres: ['pg_isready', '-U', 'yona'], cubrid: ['bash', '-c', 'gosu cubrid /home/cubrid/CUBRID/bin/cubrid server status | grep -q yona'] }[DB];
    if (sh('docker', ['exec', NAME, ...probe]).code === 0) break; await sleep(1000);
  }
  if (DB === 'cubrid') { // 시험과 같이 브로커 동시 연결 한도를 올린다
    sh('docker', ['exec', NAME, 'bash', '-c', "gosu cubrid sed -i -e 's/^MAX_NUM_APPL_SERVER .*/MAX_NUM_APPL_SERVER     =300/' /home/cubrid/CUBRID/conf/cubrid_broker.conf && gosu cubrid /home/cubrid/CUBRID/bin/cubrid broker restart > /dev/null 2>&1 < /dev/null"]);
    await sleep(3000);
  }
  if (DBCMD) { const r = sh('docker', ['exec', NAME, 'bash', '-c', DBCMD]); result.dbcmd = DBCMD; result.dbcmdOut = r.out.slice(0, 300); await sleep(5000); }
  result.tag = TAG; result.appargs = APPARGS;
  result.dbReadySec = +((performance.now() - t0) / 1000).toFixed(1);
  const version = { mariadb: ['mariadb', '--version'], postgres: ['postgres', '--version'], cubrid: ['bash', '-c', 'gosu cubrid /home/cubrid/CUBRID/bin/cubrid_rel | head -1'] }[DB];
  result.version = sh('docker', ['exec', NAME, ...version]).out.trim().split('\n')[0];

  // 2) 첫 기동(스키마 생성)과 환영 화면
  const base = `http://localhost:${PORT}`;
  let s0 = performance.now();
  child = startApp(dataDir);
  await waitHttp(base);
  result.firstStartSec = +((performance.now() - s0) / 1000).toFixed(1);
  log('첫 기동(스키마 생성 포함)', result.firstStartSec, '초');
  const secretFile = join(dataDir, 'config', 'secret.properties');
  const body = new URLSearchParams({ loginId: ADMIN.loginId, name: ADMIN.name, email: ADMIN.email, password: ADMIN.password, retypedPassword: ADMIN.password });
  for (let i = 0; i < 60 && !existsSync(secretFile); i++) {
    const page = await fetch(base + '/', { redirect: 'manual' });
    const xsrf = (page.headers.getSetCookie?.() ?? []).map((c) => c.split(';')[0]).find((kv) => kv.startsWith('XSRF-TOKEN='));
    await fetch(base + '/', { method: 'POST', headers: xsrf ? { cookie: xsrf, 'X-XSRF-TOKEN': xsrf.slice(11) } : {}, body, redirect: 'manual' });
    if (!existsSync(secretFile)) await sleep(1000);
  }
  if (!existsSync(secretFile)) throw new Error('관리자 계정 설정 실패');
  child.kill(); await new Promise((r) => child.once('exit', r));
  // 3) 재시작(스키마가 이미 있는 두 번째 기동)
  s0 = performance.now();
  child = startApp(dataDir);
  await waitHttp(base);
  result.secondStartSec = +((performance.now() - s0) / 1000).toFixed(1);
  log('두 번째 기동', result.secondStartSec, '초');

  const admin = new Session(base); await admin.login(ADMIN.loginId); await admin.deviceToken('repo');
  await admin.api('POST', '/user/projects', { name: 'bench', description: '성능 측정', scope: 'PUBLIC', vcs: 'GIT' });
  const auth = { Authorization: `token ${admin.token}` };
  const API = '/-_-api/v1/owners/admin/projects/bench';
  const post = (path, json) => fetch(base + path, { method: 'POST', headers: { ...auth, 'Content-Type': 'application/json' }, body: JSON.stringify(json) }).then(async (r) => { await r.arrayBuffer(); return r.status === 201; });
  const get = (path, headers = {}) => fetch(base + path, { headers, redirect: 'manual' }).then(async (r) => { await r.arrayBuffer(); return r.status === 200; });

  // 4) 쓰기: 이슈 만들기(순차, 병렬), 댓글 달기(병렬)
  result.write = {};
  let seq = 0; const nextIssue = () => ++seq;
  result.write.issue_seq = await stat(() => { const i = nextIssue(); return post(`${API}/issues`, { issues: [{ title: issueTitle(i), body: issueBody(i) }] }); }, N_SEQ, 1);
  log('이슈 순차', JSON.stringify(result.write.issue_seq));
  result.write.issue_c8 = await stat(() => { const i = nextIssue(); return post(`${API}/issues`, { issues: [{ title: issueTitle(i), body: issueBody(i) }] }); }, N_PAR, 8);
  log('이슈 병렬 8', JSON.stringify(result.write.issue_c8));
  const total = seq;
  result.write.comment_c8 = await stat((i) => post(`${API}/issues/${(i % Math.max(1, Math.floor(total / 2))) + 1}/comments`, { comment: `댓글 ${i}: ${pick(SENT)} ${pick(WORDS)}` }), N_COMMENT, 8);
  log('댓글 병렬 8', JSON.stringify(result.write.comment_c8));
  result.rows = { issues: total, comments: N_COMMENT };

  // 5) 읽기
  const rn = rng(77); const num = () => Math.floor(rn() * total) + 1;
  const scenarios = {
    api_issue: () => get(`${API}/issues/${num()}`, auth),
    api_comments: () => get(`${API}/issues/${num()}/comments`, auth),
    html_issue_list: () => get('/admin/bench/issues'),
    html_issue_list_deep: () => get(`/admin/bench/issues?pageNum=${Math.max(2, Math.floor(total / 40))}`),
    html_issue_detail: () => get(`/admin/bench/issue/${num()}`),
    search_common: () => get(`${API}/search?keyword=${encodeURIComponent('로그인')}&searchType=issue`, auth),
    search_rare: () => get(`${API}/search?keyword=${encodeURIComponent('희귀어2')}&searchType=issue`, auth),
  };
  for (const f of Object.values(scenarios)) for (let i = 0; i < 10; i++) await f(); // 예열
  await stat(() => scenarios.html_issue_detail(), Math.round(300 * SCALE) + 20, 4);
  result.read = {};
  for (const [name, fn] of Object.entries(scenarios)) {
    const slow = name.startsWith('search') || name === 'html_issue_list_deep';
    const c1 = await stat(fn, Math.round((slow ? 60 : 200) * Math.max(SCALE, 0.25)), 1);
    const c16 = await stat(fn, Math.round((slow ? 200 : 1600) * Math.max(SCALE, 0.25)), 16);
    result.read[name] = { c1, c16 };
    log(name, 'c1 p50', c1.p50, 'p95', c1.p95, '| c16 rps', c16.rps, 'p95', c16.p95, 'err', c1.errors + c16.errors);
  }

  // 6) 용량과 메모리
  result.diskMb = Number(sh('docker', ['exec', NAME, 'bash', '-c', `du -sm ${DBS.data} | cut -f1`]).out.trim()) || null;
  const mem = sh('docker', ['stats', '--no-stream', '--format', '{{.MemUsage}}', NAME]).out.trim();
  result.dbMemory = mem;
  result.finished = new Date().toISOString();
} catch (e) {
  result.error = String(e?.stack || e); log('실패', result.error);
} finally {
  if (child) { child.kill(); await new Promise((r) => child.once('exit', r)); }
  if (!process.env.KEEP) sh('docker', ['rm', '-fv', NAME]);   // KEEP=1이면 데이터가 든 DB 컨테이너를 남긴다(질의를 직접 시험할 때)
  mkdirSync(OUT, { recursive: true });
  writeFileSync(join(OUT, `result-${DB}${TAG}.json`), JSON.stringify(result, null, 2));
  rmSync(dataDir, { recursive: true, force: true });
  log('결과 저장', join(OUT, `result-${DB}${TAG}.json`));
}
