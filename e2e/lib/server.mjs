// Yona 서버를 확인 도구용으로 띄우고 내리는 도우미이다. 부트 jar를 H2 파일 DB와 전용 데이터 폴더로 실행한다.
// 첫 실행에는 사이트 비밀 키를 정하는 화면(관리자 계정 입력)이 나오므로 한 번 채우고 다시 시작한다.
import { spawn } from 'node:child_process';
import { existsSync, mkdirSync, openSync, readdirSync } from 'node:fs';
import { join, resolve } from 'node:path';

export const ROOT = resolve(new URL('..', import.meta.url).pathname);
const JAVA = process.env.YONA_JAVA || '/usr/lib/jvm/java-25-openjdk-amd64/bin/java';
/**
 * 시험용 관리자 계정이다. 비밀번호는 서버의 새 비밀번호 정책(8자 이상, 흔한 비밀번호와 아이디나 이메일과 같은 값 거절, `PasswordPolicy`)을 통과해야
 * 한다. 옛 값 `pass1234`는 흔한 비밀번호라서 환영 화면이 거절한다(HTTP 400).
 */
export const ADMIN = { loginId: 'admin', password: 'Yona-e2e-Admin-4471', name: 'Admin', email: 'admin@example.com' };

function jarPath(jar) {
  if (jar) return jar;
  const dir = resolve(ROOT, '..', 'build', 'libs');
  const found = readdirSync(dir).find((f) => f.endsWith('.jar') && !f.endsWith('-plain.jar'));
  if (!found) throw new Error('부트 jar가 없다. ./gradlew bootJar를 먼저 실행한다');
  return join(dir, found);
}

async function waitReady(base, timeoutMs = 180000) {
  const end = Date.now() + timeoutMs;
  while (Date.now() < end) {
    try { const r = await fetch(base + '/', { redirect: 'manual' }); if (r.status < 500) return; } catch { /* 아직 기동 중 */ }
    await new Promise((r) => setTimeout(r, 1500));
  }
  throw new Error('서버가 제때 뜨지 않았다: ' + base);
}

function spawnServer(port, dataDir, jar, extraArgs = []) {
  mkdirSync(dataDir, { recursive: true });
  const log = openSync(join(dataDir, 'server.log'), 'a');
  const child = spawn(JAVA, ['-Xmx1g', '-jar', jarPath(jar), `--server.port=${port}`, `--yona.data-dir=${dataDir}`, '--yona.git-default-branch=master', '--yona.update.interval=0', ...extraArgs,
    `--spring.datasource.url=jdbc:h2:file:${join(dataDir, 'db')};DB_CLOSE_DELAY=-1`], { stdio: ['ignore', log, log], detached: false });
  return child;
}

/**
 * 서버를 시작한다. 데이터 폴더가 처음이면 사이트 비밀 키를 정하고 한 번 다시 시작한다.
 * 돌려주는 `stop()`은 서버를 내린다. `base`는 서버 주소이다.
 */
export async function startServer({ port = 19191, dataDir = join(ROOT, '.state', 'server'), jar = null, extraArgs = [] } = {}) {
  const base = `http://localhost:${port}`;
  const first = !existsSync(join(dataDir, 'config', 'secret.properties'));
  let child = spawnServer(port, dataDir, jar, extraArgs);
  await waitReady(base);
  if (first) {
    const body = new URLSearchParams({ loginId: ADMIN.loginId, name: ADMIN.name, email: ADMIN.email, password: ADMIN.password, retypedPassword: ADMIN.password });
    // 환영 화면의 POST도 CSRF 보호를 받는다: 먼저 GET으로 `XSRF-TOKEN` 쿠키를 받아 쿠키와 `X-XSRF-TOKEN` 머리글로 되돌려 보낸다.
    // 기본 관리자 계정은 서버가 뜬 직후 초기 데이터를 넣으면서 만들어지므로 그 전의 요청은 실패한다(500이나 400). 성공은 응답 코드가 아니라
    // 서버가 비밀 키 파일을 썼는지로 판단하고, 아직이면 잠시 뒤 다시 시도한다
    const secretFile = join(dataDir, 'config', 'secret.properties');
    let last = 0;
    for (let i = 0; i < 30 && !existsSync(secretFile); i++) {
      const page = await fetch(base + '/', { redirect: 'manual' });
      const xsrf = (page.headers.getSetCookie?.() ?? []).map((c) => c.split(';')[0]).find((kv) => kv.startsWith('XSRF-TOKEN='));
      const headers = xsrf ? { cookie: xsrf, 'X-XSRF-TOKEN': xsrf.slice('XSRF-TOKEN='.length) } : {};
      last = (await fetch(base + '/', { method: 'POST', headers, body, redirect: 'manual' })).status;
      if (!existsSync(secretFile)) await new Promise((r) => setTimeout(r, 1000));
    }
    if (!existsSync(secretFile)) { child.kill(); throw new Error(`환영 화면이 관리자 계정을 받아들이지 않았다(마지막 응답 HTTP ${last}). 비밀번호 정책을 확인한다`); }
    child.kill(); await new Promise((r) => child.once('exit', r));
    child = spawnServer(port, dataDir, jar, extraArgs);
    await waitReady(base);
  }
  return { base, dataDir, first, stop: async () => { child.kill(); await new Promise((r) => child.once('exit', r)); } };
}
