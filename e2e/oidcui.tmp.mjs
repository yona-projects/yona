// X-40 화면 확인용(한 번 쓰고 버리는 스크립트): 사이트 보안 화면의 OIDC 영역과 앱 수정 화면의 연동 주소를 캡처한다.
import { join } from 'node:path';
import { rmSync } from 'node:fs';
import { chromium } from 'playwright';
import { ADMIN, ROOT, startServer } from './lib/server.mjs';
const KEY = join(ROOT, '..', 'src', 'test', 'resources', 'oauth2', 'oidc-test-key.pem');
const run = async (name, extra) => {
  const state = join(ROOT, '.state', name); rmSync(state, { recursive: true, force: true });
  const B = await startServer({ port: name === 'x40on' ? 19400 : 19401, dataDir: state, extraArgs: extra });
  const browser = await chromium.launch(); const page = await (await browser.newContext({ viewport: { width: 1280, height: 1000 } })).newPage();
  try {
    await page.goto(B.base + '/users/loginform'); await page.fill('input[name=loginIdOrEmail]', 'admin'); await page.fill('input[name=password]', ADMIN.password); await page.click('.login-form-wrap button[type=submit]'); await page.waitForTimeout(800);
    await page.goto(B.base + '/sites/security'); await page.locator('#oidc-status').scrollIntoViewIfNeeded(); await page.screenshot({ path: join(ROOT, 'shots', `${name}-security.png`), fullPage: true });
    if (name === 'x40on') {
      await page.goto(B.base + '/user/editform/oauth-apps'); await page.click('#oauth-app-new'); await page.waitForSelector('#oauth-app-form');
      await page.fill('#oauth-name', 'OIDC 앱'); await page.fill('#oauth-redirects', 'https://rp.example/callback'); await page.check('#oauth-scope-openid'); await page.check('#oauth-scope-profile'); await page.check('#oauth-scope-email');
      await page.click('#oauth-app-form button[type=submit]'); await page.waitForSelector('#edit-client-id');
      await page.goto(B.base + '/user/editform/oauth-apps'); await page.click('a[href*="/user/editform/oauth-apps/"]:not([href$="/new"])'); await page.waitForSelector('#oidc-info');
      await page.screenshot({ path: join(ROOT, 'shots', `${name}-app.png`), fullPage: true });
    }
  } finally { await browser.close(); await B.stop(); }
};
await run('x40on', ['--yona.oauth2.oidc.signing-key-file=' + KEY]);
await run('x40off', []);
console.log('done');
