// Yona 서버를 확인 도구용으로 띄우고 내리는 도우미이다. 부트 jar를 H2 파일 DB와 전용 데이터 폴더로 실행한다.
// 첫 실행에는 사이트 비밀 키를 정하는 화면(관리자 계정 입력)이 나오므로 한 번 채우고 다시 시작한다.
import { spawn } from 'node:child_process';
import { existsSync, mkdirSync, openSync, readdirSync } from 'node:fs';
import { join, resolve } from 'node:path';

export const ROOT = resolve(new URL('..', import.meta.url).pathname);
const JAVA = process.env.YONA_JAVA || '/usr/lib/jvm/java-25-openjdk-amd64/bin/java';
export const ADMIN = { loginId: 'admin', password: 'pass1234', name: 'Admin', email: 'admin@example.com' };

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

function spawnServer(port, dataDir, jar) {
  mkdirSync(dataDir, { recursive: true });
  const log = openSync(join(dataDir, 'server.log'), 'a');
  const child = spawn(JAVA, ['-Xmx1g', '-jar', jarPath(jar), `--server.port=${port}`, `--yona.data-dir=${dataDir}`, '--yona.git-default-branch=master', '--yona.update.interval=0',
    `--spring.datasource.url=jdbc:h2:file:${join(dataDir, 'db')};DB_CLOSE_DELAY=-1`], { stdio: ['ignore', log, log], detached: false });
  return child;
}

/**
 * 서버를 시작한다. 데이터 폴더가 처음이면 사이트 비밀 키를 정하고 한 번 다시 시작한다.
 * 돌려주는 `stop()`은 서버를 내린다. `base`는 서버 주소이다.
 */
export async function startServer({ port = 19191, dataDir = join(ROOT, '.state', 'server'), jar = null } = {}) {
  const base = `http://localhost:${port}`;
  const first = !existsSync(join(dataDir, 'config', 'secret.properties'));
  let child = spawnServer(port, dataDir, jar);
  await waitReady(base);
  if (first) {
    const body = new URLSearchParams({ loginId: ADMIN.loginId, name: ADMIN.name, email: ADMIN.email, password: ADMIN.password, retypedPassword: ADMIN.password });
    await fetch(base + '/', { method: 'POST', body, redirect: 'manual' });
    child.kill(); await new Promise((r) => child.once('exit', r));
    child = spawnServer(port, dataDir, jar);
    await waitReady(base);
  }
  return { base, dataDir, first, stop: async () => { child.kill(); await new Promise((r) => child.once('exit', r)); } };
}
