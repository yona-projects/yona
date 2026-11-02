// X-40 확인용(한 번 쓰고 버리는 스크립트): 공개된 OIDC 클라이언트 라이브러리(openid-client)가 실제 jar에서 발견 문서, PKCE, nonce, ID 토큰 서명 검증, 사용자 정보까지 통과하는지 확인한다.
import { join } from 'node:path';
import { rmSync } from 'node:fs';
import { chromium } from 'playwright';
import { ADMIN, ROOT, startServer } from './lib/server.mjs';
const client = await import('/tmp/claude-1000/-home-jiho-yona-convert-yona/572b44fd-d452-40b9-ac89-25d34813d6ac/scratchpad/mcpclient/node_modules/openid-client/build/index.js');
const state = join(ROOT, '.state', 'x40'); rmSync(state, { recursive: true, force: true });
const KEY = join(ROOT, '..', 'src', 'test', 'resources', 'oauth2', 'oidc-test-key.pem');
const B = await startServer({ port: 19398, dataDir: state, extraArgs: ['--yona.oauth2.oidc.signing-key-file=' + KEY] });
let status = 'ok'; const fail = (m) => { console.log('FAIL', m); status = 'fail'; }; const ok = (m) => console.log('ok  ', m);
const browser = await chromium.launch(); const page = await (await browser.newContext({ viewport: { width: 1280, height: 900 } })).newPage();
const callbacks = []; page.on('request', (r) => { if (r.url().startsWith('http://localhost:53113/callback')) callbacks.push(r.url()); });
try {
  await page.goto(B.base + '/users/loginform'); await page.fill('input[name=loginIdOrEmail]', 'admin'); await page.fill('input[name=password]', ADMIN.password); await page.click('.login-form-wrap button[type=submit]'); await page.waitForTimeout(800);
  await page.goto(B.base + '/user/editform/oauth-apps'); await page.click('#oauth-app-new'); await page.waitForSelector('#oauth-app-form');
  await page.fill('#oauth-name', 'OIDC 앱'); await page.fill('#oauth-home', 'https://rp.example'); await page.fill('#oauth-redirects', 'http://localhost:53113/callback');
  await page.check('#oauth-scope-openid'); await page.check('#oauth-scope-profile'); await page.check('#oauth-scope-email'); await page.screenshot({ path: join(ROOT, 'shots', 'x40-01-app-form.png'), fullPage: true });
  await page.click('#oauth-app-form button[type=submit]'); await page.waitForSelector('#edit-client-id');
  const clientId = (await page.textContent('#edit-client-id')).trim(); console.log('client', clientId);

  const config = await client.discovery(new URL(B.base), clientId, undefined, client.None(), { execute: [client.allowInsecureRequests] });
  const md = config.serverMetadata(); console.log('issuer', md.issuer, '| userinfo', md.userinfo_endpoint, '| jwks', md.jwks_uri);
  if (md.issuer !== B.base) fail('issuer가 다르다 ' + md.issuer);
  const verifier = client.randomPKCECodeVerifier(); const challenge = await client.calculatePKCECodeChallenge(verifier); const nonce = client.randomNonce(); const st = client.randomState();
  const url = client.buildAuthorizationUrl(config, { redirect_uri: 'http://localhost:53113/callback', scope: 'openid profile email', code_challenge: challenge, code_challenge_method: 'S256', nonce, state: st });
  await page.goto(url.href); await page.waitForSelector('#consent-form'); await page.screenshot({ path: join(ROOT, 'shots', 'x40-02-consent.png'), fullPage: true });
  await page.click('#consent-allow'); await page.waitForTimeout(1500);
  const cb = callbacks.at(-1); if (!cb) throw new Error('콜백이 없다');
  // authorizationCodeGrant는 ID 토큰의 서명(JWKS), iss, aud, exp, nonce와 state를 모두 검증한다
  const tokens = await client.authorizationCodeGrant(config, new URL(cb), { pkceCodeVerifier: verifier, expectedNonce: nonce, expectedState: st }, undefined, { execute: [client.allowInsecureRequests] });
  const claims = tokens.claims(); console.log('id_token claims', JSON.stringify(claims));
  if (!claims.sub || !claims.email || !claims.preferred_username) fail('클레임이 모자란다'); else ok('ID 토큰 검증 통과(서명, iss, aud, exp, nonce)');
  if ('email_verified' in claims) fail('email_verified를 보내면 안 된다');
  const info = await client.fetchUserInfo(config, tokens.access_token, claims.sub, { execute: [client.allowInsecureRequests] }) ; console.log('userinfo', JSON.stringify(info));
  if (info.sub === claims.sub && info.email === claims.email) ok('사용자 정보가 ID 토큰과 같다'); else fail('사용자 정보가 다르다');
  if (!tokens.access_token.startsWith('yona_oat_')) fail('액세스 토큰은 불투명이어야 한다'); else ok('액세스 토큰은 불투명 토큰');
} catch (e) { fail(String(e?.stack || e)); } finally { await browser.close(); await B.stop(); }
console.log('RESULT', status);
