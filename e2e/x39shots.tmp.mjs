// X-39 화면 확인용(한 번 쓰고 버리는 스크립트): 조직 OAuth 앱, 소유자 이전, 정책, 승인 화면의 경고를 캡처한다.
import { join } from 'node:path';
import { rmSync } from 'node:fs';
import { chromium } from 'playwright';
import { ADMIN, ROOT, startServer } from './lib/server.mjs';
import { Session } from './lib/http.mjs';
const state = join(ROOT, '.state', 'x39'); rmSync(state, { recursive: true, force: true });
const B = await startServer({ port: 19403, dataDir: state });
const browser = await chromium.launch();
const shot = async (page, name) => { await page.waitForTimeout(400); await page.screenshot({ path: join(ROOT, 'shots', `x39-${name}.png`), fullPage: true }); console.log('shot', name); };
const login = async (id, pw) => { const p = await (await browser.newContext({ viewport: { width: 1280, height: 900 } })).newPage(); await p.goto(B.base + '/users/loginform'); await p.fill('input[name=loginIdOrEmail]', id); await p.fill('input[name=password]', pw); await p.click('.login-form-wrap button[type=submit]'); await p.waitForTimeout(800); return p; };
try {
  const s = new Session(B.base); await s.login(ADMIN.loginId); await s.deviceToken('repo admin:org read:org write:org');
  await s.signup('mem', 'Mem', 'mem@example.com');
  await s.api('POST', '/orgs', { name: 'acme', description: 'Acme 조직' }, { ok: [200, 201, 409] });
  await s.api('POST', '/orgs', { name: 'beta', description: 'Beta 조직' }, { ok: [200, 201, 409] });
  await s.api('POST', '/orgs/acme/members', { loginId: 'mem' }, { ok: [200, 201, 204, 409] });
  const admin = await login('admin', ADMIN.password); const mem = await login('mem', ADMIN.password);
  // 구성원이 만든 외부 앱
  await mem.goto(B.base + '/user/editform/oauth-apps/new'); await mem.fill('#oauth-name', '외부 에이전트'); await mem.fill('#oauth-redirects', 'http://localhost:1/cb'); await mem.check('#oauth-scope-read\\:user'); await mem.check('#oauth-scope-repo');
  await mem.click('#oauth-app-form button[type=submit]'); await mem.waitForSelector('#edit-client-id'); const clientId = (await mem.textContent('#edit-client-id')).trim();
  // 조직 앱 만들기(관리자)
  await admin.goto(B.base + '/organizations/acme/oauth-apps'); await shot(admin, 'org-apps-empty');
  await admin.click('#oauth-app-new'); await admin.fill('#oauth-name', 'Acme 빌드 봇'); await admin.fill('#oauth-redirects', 'https://bot.acme.example/callback'); await admin.check('#oauth-scope-read\\:user');
  await shot(admin, 'org-app-new');
  await admin.click('#oauth-app-form button[type=submit]'); await admin.waitForSelector('#edit-client-id');
  await shot(admin, 'org-app-edit');
  await admin.goto(B.base + '/organizations/acme/oauth-apps'); await shot(admin, 'org-apps');
  // 승인 화면(제한 전)과 정책
  const url = `/oauth2/authorize?response_type=code&client_id=${clientId}&scope=${encodeURIComponent('read:user repo')}&state=s&redirect_uri=${encodeURIComponent('http://localhost:1/cb')}&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=S256`;
  await admin.goto(B.base + '/organizations/acme/oauth-policy'); await admin.check('#policy-true'); await admin.click('#oauth-policy-form button[type=submit]');
  await mem.goto(B.base + url); await mem.waitForTimeout(600); await shot(mem, 'consent-warning');
  await mem.click('#consent-allow').catch(() => {}); await mem.waitForTimeout(800);
  await admin.goto(B.base + '/organizations/acme/oauth-policy'); await shot(admin, 'policy-pending');
  await admin.click('tr.policy-app-row button[value=APPROVED]'); await admin.waitForTimeout(500); await shot(admin, 'policy-approved');
  // 소유자 이전 화면
  await admin.goto(B.base + '/organizations/acme/oauth-apps'); await admin.click('tr.app-row a'); await admin.waitForSelector('#oauth-transfer'); await admin.locator('#oauth-transfer').scrollIntoViewIfNeeded(); await shot(admin, 'transfer');
  // 조직 앱 승인 화면(소유자 표시)과 사이트 관리자 목록
  await admin.goto(B.base + '/user/editform/oauth-apps'); await shot(admin, 'user-apps');
  await admin.goto(B.base + '/sites/oauth-apps'); await shot(admin, 'site-apps');
  await admin.goto(B.base + '/sites/security'); await shot(admin, 'site-security');
  await mem.goto(B.base + '/user/editform/oauth-apps'); await shot(mem, 'member-authorized');
} finally { await browser.close(); await B.stop(); }
