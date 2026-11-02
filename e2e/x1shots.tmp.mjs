// X-1 확인용(한 번 쓰고 버리는 스크립트): 실제 서버에 외부 프로세스(Node)가 OAuth 클라이언트로 붙어 인가 코드 + PKCE, 갱신, 취소, CIMD를 확인하고 화면을 캡처한다.
import { join } from 'node:path';
import { readFileSync, rmSync } from 'node:fs';
import { createHash, randomBytes } from 'node:crypto';
import { createServer } from 'node:https';
import { chromium } from 'playwright';
import { ADMIN, ROOT, startServer } from './lib/server.mjs';

const OUT = join(ROOT, 'shots');
const state = join(ROOT, '.state', 'x1'); rmSync(state, { recursive: true, force: true });
const pem = readFileSync(join(ROOT, '.state', 'cimd.pem'));
const trust = join(ROOT, '..', 'src', 'test', 'resources', 'oauth2', 'cimd-test.p12');
process.env.JAVA_TOOL_OPTIONS = `-Djavax.net.ssl.trustStore=${trust} -Djavax.net.ssl.trustStoreType=PKCS12 -Djavax.net.ssl.trustStorePassword=changeit`;
const B = await startServer({ port: 19396, dataDir: state, extraArgs: ['--yona.oauth2.cimd-allow-private-hosts=true'] });
delete process.env.JAVA_TOOL_OPTIONS;

// CIMD 문서를 내는 클라이언트 쪽 서버(https, 자체 서명 인증서)
let docPort = 0;
const docServer = createServer({ key: pem, cert: pem }, (req, res) => {
  const url = `https://localhost:${docPort}/client.json`;
  res.setHeader('content-type', 'application/json'); res.setHeader('cache-control', 'max-age=600');
  res.end(JSON.stringify({ client_id: url, client_name: 'Demo CLI', client_uri: url.replace('/client.json', ''), redirect_uris: ['http://localhost/callback'], token_endpoint_auth_method: 'none', scope: 'read:user repo' }));
});
await new Promise((r) => docServer.listen(0, '127.0.0.1', r)); docPort = docServer.address().port;
const cimdId = `https://localhost:${docPort}/client.json`;

const b64 = (buf) => buf.toString('base64url');
const pkce = () => { const verifier = b64(randomBytes(48)); return { verifier, challenge: b64(createHash('sha256').update(verifier).digest()) }; };
const browser = await chromium.launch(); const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } }); const page = await ctx.newPage();
const shot = async (n) => { await page.waitForTimeout(400); await page.screenshot({ path: join(OUT, `x1-${n}.png`), fullPage: true }); console.log('shot', n); };
let status = 'ok'; const fail = (m) => { console.log('FAIL', m); status = 'fail'; };
const captured = [];
page.on('request', (r) => { if (/^http:\/\/(localhost|127\.0\.0\.1):\d+\/callback/.test(r.url())) captured.push(r.url()); });

try {
  await page.goto(B.base + '/users/loginform'); await page.fill('input[name=loginIdOrEmail]', 'admin'); await page.fill('input[name=password]', ADMIN.password); await page.click('.login-form-wrap button[type=submit]');

  // 1) 설정 화면과 앱 만들기
  await page.goto(B.base + '/user/editform/oauth-apps'); await shot('01-settings-empty');
  await page.click('#oauth-app-new'); await page.waitForSelector('#oauth-app-form');
  await page.fill('#oauth-name', 'My CLI'); await page.fill('#oauth-home', 'https://cli.example'); await page.fill('#oauth-redirects', 'http://localhost/callback'); await page.check('#oauth-scope-repo');
  await shot('02-app-form');
  await page.click('#oauth-app-form button[type=submit]'); await page.waitForSelector('#edit-client-id');
  const clientId = (await page.textContent('#edit-client-id')).trim(); console.log('client', clientId);

  // 2) 승인 화면과 코드 받기(외부 클라이언트가 만든 요청)
  const authorize = (id, p, scope, port) => `${B.base}/oauth2/authorize?response_type=code&client_id=${encodeURIComponent(id)}&scope=${encodeURIComponent(scope)}&state=abc123&redirect_uri=${encodeURIComponent(`http://localhost:${port}/callback`)}&code_challenge=${p.challenge}&code_challenge_method=S256`;
  const p1 = pkce(); await page.goto(authorize(clientId, p1, 'read:user repo', 53111)); await page.waitForSelector('#consent-form'); await shot('03-consent');
  await page.click('#consent-allow').catch(() => {}); await page.waitForTimeout(1200);
  const cb = captured.at(-1); console.log('callback', cb); const code = new URL(cb).searchParams.get('code'); if (!code) fail('no code');
  if (new URL(cb).searchParams.get('state') !== 'abc123') fail('state mismatch');

  // 3) 외부 프로세스(Node fetch)가 토큰 교환, API 호출, 갱신, 취소
  const form = (o) => ({ method: 'POST', headers: { 'content-type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams(o) });
  const tokenRes = await fetch(B.base + '/oauth2/token', form({ grant_type: 'authorization_code', code, redirect_uri: 'http://localhost:53111/callback', client_id: clientId, code_verifier: p1.verifier }));
  const token = await tokenRes.json(); console.log('token', tokenRes.status, Object.keys(token).join(','), token.access_token?.slice(0, 10), token.refresh_token?.slice(0, 10));
  if (!token.access_token?.startsWith('yona_oat_')) fail('token');
  const me = await fetch(B.base + '/-_-api/v1/user', { headers: { authorization: `Bearer ${token.access_token}` } }); const meBody = await me.json(); console.log('api user', me.status, meBody.loginId);
  if (me.status !== 200 || meBody.loginId !== 'admin') fail('api');
  const noAuth = await fetch(B.base + '/-_-api/v1/user'); console.log('401 challenge:', noAuth.status, noAuth.headers.get('www-authenticate'));
  if (noAuth.status !== 401 || !noAuth.headers.get('www-authenticate')?.includes('resource_metadata')) fail('challenge');
  const meta = await (await fetch(B.base + '/.well-known/oauth-authorization-server')).json(); console.log('metadata', meta.issuer, meta.client_id_metadata_document_supported, meta.registration_endpoint);
  const gitTry = await fetch(`${B.base}/admin/none/info/refs?service=git-upload-pack`, { headers: { authorization: 'Basic ' + Buffer.from(`admin:${token.access_token}`).toString('base64') } }); console.log('git with oauth token', gitTry.status);
  if (![401, 404].includes(gitTry.status)) fail('git');
  const refreshed = await (await fetch(B.base + '/oauth2/token', form({ grant_type: 'refresh_token', refresh_token: token.refresh_token, client_id: clientId }))).json(); console.log('refreshed', refreshed.access_token?.slice(0, 10), refreshed.refresh_token !== token.refresh_token);
  const reuse = await fetch(B.base + '/oauth2/token', form({ grant_type: 'refresh_token', refresh_token: token.refresh_token, client_id: clientId })); console.log('reuse old refresh', reuse.status);
  if (reuse.status !== 400) fail('reuse');
  const afterReuse = await fetch(B.base + '/oauth2/token', form({ grant_type: 'refresh_token', refresh_token: refreshed.refresh_token, client_id: clientId })); console.log('refresh after reuse detection', afterReuse.status);
  if (afterReuse.status !== 400) fail('family revoke');

  // 4) CIMD 클라이언트(문서 주소가 클라이언트 ID)
  const p2 = pkce(); await page.goto(authorize(cimdId, p2, 'read:user', 53222)); await page.waitForSelector('#consent-form'); await shot('04-consent-cimd');
  await page.click('#consent-allow').catch(() => {}); await page.waitForTimeout(1200);
  const code2 = new URL(captured.at(-1)).searchParams.get('code');
  const t2 = await fetch(B.base + '/oauth2/token', form({ grant_type: 'authorization_code', code: code2, redirect_uri: 'http://localhost:53222/callback', client_id: cimdId, code_verifier: p2.verifier }));
  const tok2 = await t2.json(); console.log('cimd token', t2.status, tok2.access_token?.slice(0, 10)); if (!tok2.access_token) fail('cimd token');

  // 5) 승인한 앱 목록, 취소, 관리자 화면
  await page.goto(B.base + '/user/editform/oauth-apps'); await shot('05-settings-authorized');
  page.once('dialog', (d) => d.accept()); await page.click('tr.authorized-row form button'); await page.waitForTimeout(600);
  const afterRevoke = await fetch(B.base + '/-_-api/v1/user', { headers: { authorization: `Bearer ${tok2.access_token}` } }); console.log('api after revoke', afterRevoke.status); if (afterRevoke.status !== 401) fail('revoke');
  await page.goto(B.base + '/sites/oauth-apps'); await shot('06-admin-apps');
  await page.goto(B.base + '/sites/security'); await shot('07-admin-security');
} catch (e) { console.log('ERROR', e.message); status = 'error'; await page.screenshot({ path: join(OUT, 'x1-error.png') }); }
console.log('STATUS', status);
await browser.close(); docServer.close(); await B.stop();
