// 관측성 확인용 트래픽: OTLP로 Alloy에 보내는 서버를 띄우고 웹, API, git, 접근 거부를 만든다.
import { join } from 'node:path';
import { execFileSync } from 'node:child_process';
import { mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { ADMIN, ROOT, startServer } from './lib/server.mjs';
import { Session } from './lib/http.mjs';
import { seed } from './seed.mjs';
process.env.OTEL_EXPORTER_OTLP_ENDPOINT = 'http://127.0.0.1:24318';
process.env.OTEL_RESOURCE_ATTRIBUTES = 'deployment.environment=dev';
process.env.MANAGEMENT_TRACING_SAMPLING_PROBABILITY = '1.0';   // 시연용: 모든 요청을 추적한다(기본은 10%)
const fx = await seed({ port: 19191 });
const B = await startServer({ port: 19393, dataDir: join(ROOT, '.state', 'server') });
try {
  const s = new Session(B.base); await s.login(ADMIN.loginId); await s.deviceToken('repo read:org write:org admin:org');
  await s.api('POST', '/user/projects', { name: 'obsdemo', description: '관측성 확인', scope: 'PUBLIC', vcs: 'GIT' }, { ok: [200, 201, 204, 409] });
  const cookie = s.playwrightCookies().map((c) => `${c.name}=${c.value}`).join('; ');
  const page = (p) => fetch(B.base + p, { headers: { cookie } }).catch(() => {});
  const sh = (cmd, args, cwd) => execFileSync(cmd, args, { cwd, stdio: 'pipe', env: { ...process.env, GIT_TERMINAL_PROMPT: '0' } }).toString();
  const work = mkdtempSync(join(tmpdir(), 'obs-'));
  const url = `http://${ADMIN.loginId}:${encodeURIComponent(ADMIN.password)}@127.0.0.1:19393/admin/obsdemo`;
  for (let round = 0; round < 6; round++) {
    if (round === 0) { const seedDir = join(work, 'seed'); sh('git', ['init', '-q', '-b', 'main', seedDir]); writeFileSync(join(seedDir, 'README.md'), 'obs'); sh('git', ['-C', seedDir, 'add', '.']); sh('git', ['-C', seedDir, '-c', 'user.name=t', '-c', 'user.email=t@e.com', 'commit', '-qm', 'init']); sh('git', ['-C', seedDir, 'push', '-q', url, '+HEAD:refs/heads/main']); }
    sh('git', ['clone', '-q', url, `c${round}`], work);
    writeFileSync(join(work, `c${round}`, `f${round}.txt`), `round ${round}`);
    sh('git', ['-C', join(work, `c${round}`), 'add', '.']);
    sh('git', ['-C', join(work, `c${round}`), '-c', 'user.name=t', '-c', 'user.email=t@e.com', 'commit', '-qm', `r${round}`]);
    sh('git', ['-C', join(work, `c${round}`), 'push', '-q', 'origin', `+HEAD:refs/heads/obs-${round}`]);
    for (const p of ['/admin/obsdemo', '/admin/obsdemo/issues', '/admin/obsdemo/commits', '/-_-api/v1/user']) await page(p);
    await fetch(`${B.base}/${fx.project.private.owner}/${fx.project.private.name}/info/refs?service=git-upload-pack`).catch(() => {});
    await fetch(`${B.base}/nosuchuser/nosuchproject/issues`).catch(() => {});
    await new Promise((r) => setTimeout(r, 4000));
  }
  await new Promise((r) => setTimeout(r, 75000));   // 지표 내보내기(60초 주기)를 기다린다
  console.log('done');
} finally { await B.stop(); }
