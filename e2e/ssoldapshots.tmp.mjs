// D-82 화면 확인용(한 번 쓰고 버리는 스크립트): 사내 로그인 화면의 일반 탭과 LDAP 탭을 캡처한다.
import { join } from 'node:path';
import { rmSync } from 'node:fs';
import { chromium } from 'playwright';
import { ADMIN, ROOT, startServer } from './lib/server.mjs';
const state = join(ROOT, '.state', 'ssoldap'); rmSync(state, { recursive: true, force: true });
const B = await startServer({ port: 19412, dataDir: state });
const browser = await chromium.launch(); const page = await (await browser.newContext({ viewport: { width: 1280, height: 900 } })).newPage();
const shot = async (name) => { await page.waitForTimeout(500); await page.screenshot({ path: join(ROOT, 'shots', `login-${name}.png`), fullPage: true }); console.log('shot', name); };
try {
  await page.goto(B.base + '/users/loginform'); await page.fill('input[name=loginIdOrEmail]', ADMIN.loginId); await page.fill('input[name=password]', ADMIN.password); await page.click('.login-form-wrap button[type=submit]'); await page.waitForLoadState('networkidle');
  await page.goto(B.base + '/sites/sso'); await shot('general-off');
  await page.goto(B.base + '/sites/sso?tab=ldap'); await shot('ldap-from-file');
  await page.check('#ldap-enabled'); await page.fill('#ldap-host', 'ldap.corp.example'); await page.fill('#ldap-port', '389'); await page.fill('#ldap-base-dn', 'ou=people,dc=corp,dc=example');
  await page.fill('#ldap-postfix', 'ou=people,dc=corp,dc=example'); await page.fill('#ldap-login-prop', 'uid'); await page.fill('#ldap-user-prop', 'uid'); await page.fill('#ldap-email-prop', 'mail');
  await page.click('#ldap-form button[type=submit]'); await page.waitForLoadState('networkidle'); await shot('ldap-saved');
  await page.goto(B.base + '/sites/sso?tab=general'); await shot('general-ldap-on');
} finally { await browser.close(); await B.stop(); }
