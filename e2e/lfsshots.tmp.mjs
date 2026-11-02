// X-8 화면 확인용(한 번 쓰고 버리는 스크립트): 코드 보기의 LFS 안내, 프로젝트 LFS 탭, 사이트 LFS 설정을 캡처한다.
import { join } from 'node:path';
import { rmSync, mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { chromium } from 'playwright';
import { ADMIN, ROOT, startServer } from './lib/server.mjs';
import { Session } from './lib/http.mjs';
const state = join(ROOT, '.state', 'lfs'); rmSync(state, { recursive: true, force: true });
const B = await startServer({ port: 19404, dataDir: state });
const browser = await chromium.launch(); const page = await (await browser.newContext({ viewport: { width: 1280, height: 900 } })).newPage();
const shot = async (name) => { await page.waitForTimeout(400); await page.screenshot({ path: join(ROOT, 'shots', `lfs-${name}.png`), fullPage: true }); console.log('shot', name); };
try {
  const s = new Session(B.base); await s.login(ADMIN.loginId); const pat = await s.deviceToken('repo');
  await s.api('POST', '/user/projects', { name: 'assets', description: 'LFS 시험', scope: 'PUBLIC', vcs: 'GIT' }, { ok: [200, 201, 204, 409] });
  const auth = 'Basic ' + Buffer.from(`admin:${s.token ?? pat}`).toString('base64');
  const lfs = `${B.base}/admin/assets.git/info/lfs`;
  const data = Buffer.from('디자인 시안 바이너리'.repeat(50)); const oid = createHash('sha256').update(data).digest('hex');
  let r = await fetch(`${lfs}/objects/batch`, { method: 'POST', headers: { Authorization: auth, 'Content-Type': 'application/vnd.git-lfs+json', Accept: 'application/vnd.git-lfs+json' }, body: JSON.stringify({ operation: 'upload', transfers: ['basic'], objects: [{ oid, size: data.length }] }) });
  console.log('batch', r.status, (await r.text()).slice(0, 120));
  r = await fetch(`${lfs}/objects/${oid}`, { method: 'PUT', headers: { Authorization: auth, 'Content-Type': 'application/octet-stream' }, body: data }); console.log('put', r.status);
  for (const p of ['design/logo.psd', 'design/banner.psd']) { r = await fetch(`${lfs}/locks`, { method: 'POST', headers: { Authorization: auth, 'Content-Type': 'application/vnd.git-lfs+json' }, body: JSON.stringify({ path: p }) }); console.log('lock', r.status); }
  // 포인터 파일을 푸시한다
  const dir = mkdtempSync(join(tmpdir(), 'lfs-')); const run = (...a) => execFileSync('git', a, { cwd: dir, stdio: 'pipe' });
  run('init', '-q', '-b', 'master'); run('config', 'user.email', 'a@example.com'); run('config', 'user.name', 'admin');
  writeFileSync(join(dir, 'logo.psd'), `version https://git-lfs.github.com/spec/v1\noid sha256:${oid}\nsize ${data.length}\n`); writeFileSync(join(dir, 'missing.psd'), `version https://git-lfs.github.com/spec/v1\noid sha256:${'e'.repeat(64)}\nsize 123456789\n`);
  run('add', '.'); run('commit', '-q', '-m', 'pointers');
  const url = B.base.replace('http://', `http://admin:${s.token ?? pat}@`) + '/admin/assets';
  try { run('push', '-q', url, 'master'); } catch (e) { console.log('push failed', String(e.stderr).slice(0, 200)); }
  await page.goto(B.base + '/users/loginform'); await page.fill('input[name=loginIdOrEmail]', 'admin'); await page.fill('input[name=password]', ADMIN.password); await page.click('.login-form-wrap button[type=submit]'); await page.waitForTimeout(800);
  await page.goto(B.base + '/admin/assets/code/master/logo.psd'); await shot('code-pointer');
  await page.goto(B.base + '/admin/assets/code/master/missing.psd'); await shot('code-missing');
  await page.goto(B.base + '/admin/assets/setting/lfs'); await shot('project-setting');
  await page.goto(B.base + '/sites/lfs'); await shot('site-setting');
} finally { await browser.close(); await B.stop(); }
