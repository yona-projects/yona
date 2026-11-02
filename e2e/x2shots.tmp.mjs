// X-2 화면 확인용(한 번 쓰고 버리는 스크립트): 인증 앱, 보안 키(가상 인증기), 로그인 2단계, 팝업, 강제 설정을 캡처한다.
import { join } from 'node:path';
import { createHmac } from 'node:crypto';
import { rmSync } from 'node:fs';
import { chromium } from 'playwright';
import { ADMIN, ROOT, startServer } from './lib/server.mjs';

const OUT = join(ROOT, 'shots');
const state = join(ROOT, '.state', 'x2');
rmSync(state, { recursive: true, force: true });
const B = await startServer({ port: 19394, dataDir: state });

const b32 = (s) => { const A = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'; let bits = 0, v = 0; const out = []; for (const c of s.replace(/\s/g, '').toUpperCase()) { v = (v << 5) | A.indexOf(c); bits += 5; if (bits >= 8) { out.push((v >> (bits - 8)) & 255); bits -= 8; } } return Buffer.from(out); };
const totp = (secret, offset = 0) => { const step = Math.floor(Date.now() / 30000) + offset; const msg = Buffer.alloc(8); msg.writeBigUInt64BE(BigInt(step)); const h = createHmac('sha1', b32(secret)).update(msg).digest(); const o = h[19] & 15; return String(((h.readUInt32BE(o) & 0x7fffffff) % 1000000)).padStart(6, '0'); };

const browser = await chromium.launch();
const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
const page = await ctx.newPage();
const shot = async (name) => { await page.waitForTimeout(500); await page.screenshot({ path: join(OUT, `x2-${name}.png`), fullPage: true }); console.log('shot', name); };
page.on('console', (m) => console.log('console:', m.type(), m.text())); page.on('pageerror', (e) => console.log('pageerror:', e.message)); page.on('response', (r) => { if (r.url().includes('webauthn')) console.log('resp', r.status(), r.url()); });
const cdp = await ctx.newCDPSession(page);
await cdp.send('WebAuthn.enable');
await cdp.send('WebAuthn.addVirtualAuthenticator', { options: { protocol: 'ctap2', transport: 'usb', hasResidentKey: false, hasUserVerification: true, isUserVerified: true, automaticPresenceSimulation: true } });

const login = async (id, pw = ADMIN.password) => {
  await page.goto(B.base + '/users/loginform');
  await page.fill('input[name=loginIdOrEmail]', id); await page.fill('input[name=password]', pw);
  await page.click('.login-form-wrap button[type=submit]');
};
let status = 'ok';
try {
  await login(ADMIN.loginId);
  await page.goto(B.base + '/user/editform/two-factor'); await shot('01-list-off');
  await page.click('form[action$="/user/two-factor/begin"] button'); await page.waitForSelector('#two-factor-secret'); await shot('02-setup-qr');
  const secret = (await page.textContent('#two-factor-secret')).trim();
  await page.fill('#two-factor-code', totp(secret)); await page.click('#two-factor-confirm button[type=submit]');
  await page.waitForSelector('#backup-codes'); await shot('03-backup-codes');
  const backup = await page.$$eval('#backup-codes li', (l) => l.map((x) => x.textContent.trim()));
  await page.goto(B.base + '/user/editform/two-factor/security-key'); await shot('03b-key-add');
  await page.fill('#security-key-name', 'YubiKey 테스트'); await page.click('#security-key-add');
  await page.waitForTimeout(3000); console.log('error box:', await page.textContent('#security-key-error').catch(() => null), 'url', page.url());
  await page.goto(B.base + '/user/editform/two-factor'); await shot('04-list-on');
  const rows = await page.$$eval('tr.method-row', (r) => r.map((x) => x.dataset.type)); console.log('methods', rows);
  if (!rows.includes('WEBAUTHN')) status = 'webauthn-registration-failed';

  // 로그아웃 뒤 보안 키로 로그인
  await page.goto(B.base + '/logout');
  await login(ADMIN.loginId); await page.waitForURL('**/users/login/2fa'); await shot('05-challenge');
  await page.click('#security-key-use'); await page.waitForURL((u) => !u.pathname.includes('/users/login'), { timeout: 15000 });
  console.log('after security key login', page.url());
  if (page.url().includes('/users/login')) status = 'webauthn-login-failed';

  // 틀린 코드, 백업 코드
  await page.goto(B.base + '/logout'); await login(ADMIN.loginId); await page.waitForURL('**/users/login/2fa');
  await page.fill('#two-factor-code', '000000'); await page.click('#two-factor-form button[type=submit]'); await page.waitForSelector('#two-factor-code'); await shot('06-wrong-code');
  await page.fill('#two-factor-code', backup[0]); await page.check('#remember-device'); await page.click('#two-factor-form button[type=submit]');
  await page.waitForURL((u) => !u.pathname.includes('/users/login'), { timeout: 15000 }); console.log('after backup code', page.url());

  // 알려진 기기: 다시 로그인하면 묻지 않는다
  await page.goto(B.base + '/logout'); await login(ADMIN.loginId); await page.waitForTimeout(800);
  console.log('known device login lands', page.url()); if (page.url().includes('/users/login')) status = 'known-device-failed';
  await page.goto(B.base + '/user/editform/two-factor'); await shot('07-list-devices');

  // 팝업 로그인
  await page.goto(B.base + '/logout'); await page.goto(B.base + '/');
  const ctx2 = await browser.newContext({ viewport: { width: 1280, height: 900 } }); const p2 = await ctx2.newPage();
  const c2 = await ctx2.newCDPSession(p2); await c2.send('WebAuthn.enable');
  await p2.goto(B.base + '/'); await p2.evaluate(() => { const m = document.querySelector('#loginDialog'); window.bootstrap.Modal.getOrCreateInstance(m).show(); }); await p2.waitForTimeout(600);
  await p2.fill('#loginDialog input[name=loginIdOrEmail]', ADMIN.loginId); await p2.fill('#loginDialog input[name=password]', ADMIN.password);
  await p2.click('#loginDialog .login-step button[type=submit]'); await p2.waitForSelector('#loginDialog .two-factor-step', { state: 'visible', timeout: 10000 }); await p2.waitForTimeout(500);
  await p2.screenshot({ path: join(OUT, 'x2-08-popup-step.png') }); console.log('shot popup');
  await p2.fill('#loginDialog input[name=code]', backup[1]); await p2.click('#loginDialog .two-factor-step button[type=submit]'); await p2.waitForTimeout(1500);
  const logged = await p2.evaluate(() => document.body.innerText.includes('Log out') || document.body.innerText.includes('로그아웃')); console.log('popup login ok', logged); if (!logged) status = 'popup-failed';

  // 관리자 보안 설정과 강제 이동
  await page.goto(B.base + '/logout'); await login(ADMIN.loginId, ADMIN.password); await page.fill('#two-factor-code', totp(secret, 1)).catch(() => {});
  await page.goto(B.base + '/sites/security').catch(() => {});
} catch (e) { console.log('ERROR', e.message); status = 'error'; await page.screenshot({ path: join(OUT, 'x2-error.png') }); }
console.log('STATUS', status);
await browser.close(); await B.stop();
