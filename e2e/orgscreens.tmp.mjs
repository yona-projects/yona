// X-39 범위 확인용(한 번 쓰고 버리는 스크립트): 지금 Yona의 조직 화면을 캡처한다.
import { join } from 'node:path';
import { rmSync } from 'node:fs';
import { chromium } from 'playwright';
import { ADMIN, ROOT, startServer } from './lib/server.mjs';
import { Session } from './lib/http.mjs';
const state = join(ROOT, '.state', 'orgshots'); rmSync(state, { recursive: true, force: true });
const B = await startServer({ port: 19402, dataDir: state });
const browser = await chromium.launch(); const page = await (await browser.newContext({ viewport: { width: 1280, height: 800 } })).newPage();
try {
  const s = new Session(B.base); await s.login(ADMIN.loginId); await s.deviceToken('repo admin:org read:org write:org');
  await s.api('POST', '/orgs', { name: 'acme', description: 'Acme 조직' }, { ok: [200, 201, 409] });
  for (const p of ['alpha', 'beta']) await s.api('POST', '/orgs/acme/projects', { name: p, description: `${p} 프로젝트`, scope: 'PUBLIC', vcs: 'GIT' }, { ok: [200, 201, 204, 409] });
  await page.goto(B.base + '/users/loginform'); await page.fill('input[name=loginIdOrEmail]', 'admin'); await page.fill('input[name=password]', ADMIN.password); await page.click('.login-form-wrap button[type=submit]'); await page.waitForTimeout(800);
  const shots = [['home', '/organizations/acme'], ['issues', '/organizations/acme/issues'], ['setting', '/organizations/acme/settingform'], ['members', '/organizations/acme/members'], ['audit', '/organizations/acme/audit'], ['delete', '/organizations/acme/deleteForm']];
  for (const [n, p] of shots) { await page.goto(B.base + p); await page.waitForTimeout(500); await page.screenshot({ path: join(ROOT, 'shots', `org-${n}.png`), fullPage: true }); console.log('shot', n, page.url()); }
} finally { await browser.close(); await B.stop(); }
