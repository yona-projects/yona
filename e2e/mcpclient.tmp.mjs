// X-6 확인용(한 번 쓰고 버리는 스크립트): 실제 서버에 공식 MCP SDK 클라이언트가 붙어 인증 발견(RFC 9728, 8414), 승인(PKCE, resource), 도구 목록과 호출을 확인한다.
import { join } from 'node:path';
import { rmSync } from 'node:fs';
import { chromium } from 'playwright';
import { ADMIN, ROOT, startServer } from './lib/server.mjs';
import { Session } from './lib/http.mjs';
const SDK = '/tmp/claude-1000/-home-jiho-yona-convert-yona/572b44fd-d452-40b9-ac89-25d34813d6ac/scratchpad/mcpclient/node_modules/@modelcontextprotocol/sdk/dist/esm';
const { Client } = await import(SDK + '/client/index.js');
const { StreamableHTTPClientTransport } = await import(SDK + '/client/streamableHttp.js');
const { UnauthorizedError } = await import(SDK + '/client/auth.js');

const OUT = join(ROOT, 'shots'); const state = join(ROOT, '.state', 'x6'); rmSync(state, { recursive: true, force: true });
const B = await startServer({ port: 19397, dataDir: state });
let status = 'ok'; const fail = (m) => { console.log('FAIL', m); status = 'fail'; };
const browser = await chromium.launch(); const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } }); const page = await ctx.newPage();
const callbacks = []; page.on('request', (r) => { if (r.url().startsWith('http://localhost:53112/callback')) callbacks.push(r.url()); });
try {
  const admin = new Session(B.base); await admin.login(ADMIN.loginId); await admin.deviceToken('repo');
  await admin.api('POST', '/user/projects', { name: 'mcpdemo', description: 'MCP 확인', scope: 'PUBLIC', vcs: 'GIT' }, { ok: [200, 201, 204, 409] });
  await admin.api('POST', '/owners/admin/projects/mcpdemo/issues', { issues: [{ title: '기존 이슈', body: '본문' }] }, { ok: [200, 201] });

  await page.goto(B.base + '/users/loginform'); await page.fill('input[name=loginIdOrEmail]', 'admin'); await page.fill('input[name=password]', ADMIN.password); await page.click('.login-form-wrap button[type=submit]'); await page.waitForTimeout(800);
  await page.goto(B.base + '/user/editform/oauth-apps'); await page.click('#oauth-app-new'); await page.waitForSelector('#oauth-app-form');
  await page.fill('#oauth-name', 'MCP SDK'); await page.fill('#oauth-home', 'https://sdk.example'); await page.fill('#oauth-redirects', 'http://localhost:53112/callback');
  for (const b of await page.$$('input[name=scope]')) await b.check();
  await page.click('#oauth-app-form button[type=submit]'); await page.waitForSelector('#edit-client-id');
  const clientId = (await page.textContent('#edit-client-id')).trim(); console.log('client', clientId);

  // 공식 SDK 클라이언트: 401의 resource_metadata를 따라 인가 서버를 찾고, PKCE와 resource로 승인 요청을 만든다
  let verifier = ''; let tokens;
  const provider = {
    get redirectUrl() { return 'http://localhost:53112/callback'; },
    get clientMetadata() { return { client_name: 'MCP SDK', redirect_uris: ['http://localhost:53112/callback'], token_endpoint_auth_method: 'none', grant_types: ['authorization_code', 'refresh_token'], response_types: ['code'], scope: 'read:repo repo' }; },
    clientInformation() { return { client_id: clientId }; },
    tokens() { return tokens; }, saveTokens(t) { tokens = t; },
    saveCodeVerifier(v) { verifier = v; }, codeVerifier() { return verifier; },
    async redirectToAuthorization(url) { console.log('authorize url', url.toString()); await page.goto(url.toString()); },
  };
  const url = new URL(B.base + '/mcp');
  let transport = new StreamableHTTPClientTransport(url, { authProvider: provider });
  let client = new Client({ name: 'e2e', version: '1' });
  try { await client.connect(transport); fail('인증 없이 연결됨'); } catch (e) { if (!(e instanceof UnauthorizedError)) fail('예상 밖 오류: ' + e); else console.log('unauthorized -> authorize redirect ok'); }
  await page.waitForSelector('#consent-form'); await page.screenshot({ path: join(OUT, 'x6-01-consent.png'), fullPage: true });
  await page.click('#consent-allow'); await page.waitForTimeout(1500);
  const cb = callbacks.at(-1); console.log('callback', cb); const code = new URL(cb).searchParams.get('code'); if (!code) fail('코드 없음');
  await transport.finishAuth(code); console.log('token ok', !!tokens?.access_token, tokens?.scope);
  transport = new StreamableHTTPClientTransport(url, { authProvider: provider }); client = new Client({ name: 'e2e', version: '1' });
  await client.connect(transport);
  const list = await client.listTools(); console.log('tools', list.tools.map((t) => t.name).join(','));
  if (list.tools.length < 22) fail('도구 수 ' + list.tools.length);
  const call = async (name, args) => { const r = await client.callTool({ name, arguments: args }); return { error: !!r.isError, text: r.content?.[0]?.text ?? '' }; };
  const p = await call('get_project', { owner: 'admin', project: 'mcpdemo' }); console.log('get_project', p.error, p.text.slice(0, 120)); if (p.error) fail('get_project');
  const li = await call('list_issues', { owner: 'admin', project: 'mcpdemo', state: 'all' }); console.log('list_issues', li.error, li.text.slice(0, 160)); if (li.error || !li.text.includes('기존 이슈')) fail('list_issues');
  const ci = await call('create_issue', { owner: 'admin', project: 'mcpdemo', title: 'SDK가 만든 이슈', body: '에이전트' }); console.log('create_issue', ci.error, ci.text.slice(0, 160)); if (ci.error) fail('create_issue');
  const li2 = await call('list_issues', { owner: 'admin', project: 'mcpdemo', state: 'all' }); if (!li2.text.includes('SDK가 만든 이슈')) fail('만든 이슈가 목록에 없음');
  const none = await call('get_project', { owner: 'admin', project: 'nothing' }); console.log('missing', none.error, none.text.slice(0, 100)); if (!none.error) fail('없는 프로젝트가 오류가 아님');
  await client.close();
  // 토큰의 대상: MCP용 토큰은 외부 API에서 거부된다
  const api = await fetch(B.base + '/-_-api/v1/user', { headers: { Authorization: 'Bearer ' + tokens.access_token } }); console.log('api with mcp token', api.status); if (api.status !== 401) fail('MCP 토큰이 외부 API에서 통과함 ' + api.status);
  // 개인용 토큰은 /mcp에서 거부된다
  const pat = await fetch(B.base + '/mcp', { method: 'POST', headers: { 'content-type': 'application/json', accept: 'application/json, text/event-stream', Authorization: 'Bearer ' + admin.token }, body: '{"jsonrpc":"2.0","id":1,"method":"tools/list"}' }); console.log('pat on mcp', pat.status); if (pat.status !== 401) fail('PAT가 /mcp에서 통과함');
} catch (e) { fail(String(e?.stack || e)); } finally { await browser.close(); await B.stop(); }
console.log('RESULT', status);
