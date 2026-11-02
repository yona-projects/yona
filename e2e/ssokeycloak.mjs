// X-16 단계 6: 실제 IdP(Keycloak)로 OIDC와 SAML 2 로그인을 확인하고 새 화면을 캡처한다(한 번 쓰고 버리는 스크립트).
import { join } from 'node:path';
import { rmSync } from 'node:fs';
import { chromium } from 'playwright';
import { ADMIN, ROOT, startServer } from './lib/server.mjs';

const KC = 'http://localhost:28180'; const REALM = 'corp';
const state = join(ROOT, '.state', 'sso'); rmSync(state, { recursive: true, force: true });
const B = await startServer({ port: 19410, dataDir: state });
const browser = await chromium.launch();
const ctx = async () => (await browser.newContext({ viewport: { width: 1280, height: 900 } })).newPage();
const shot = async (page, name) => { await page.waitForTimeout(400); await page.screenshot({ path: join(ROOT, 'shots', `sso-${name}.png`), fullPage: true }); console.log('shot', name); };
const results = [];
const check = (name, ok, detail = '') => { results.push([name, ok]); console.log(ok ? 'OK  ' : 'FAIL', name, detail); };
const adminLogin = async (page) => { await page.goto(B.base + '/users/loginform?local=true'); await page.fill('input[name=loginIdOrEmail]', ADMIN.loginId); await page.fill('input[name=password]', ADMIN.password); await page.click('.login-form-wrap button[type=submit]'); await page.waitForLoadState('networkidle'); };
const kcLogin = async (page, user, pw) => { await page.waitForURL(/localhost:28180/); await page.fill('#username', user); await page.fill('#password', pw); await page.click('#kc-login'); };
const me = async (page) => { const r = await page.goto(B.base + '/user/editform'); return r.status() === 200 ? (await page.inputValue('input[name=loginId], #loginId').catch(() => '')) || 'ok' : `status ${r.status()}`; };
const logout = async (page) => { await page.context().clearCookies(); };

try {
  const admin = await ctx(); await adminLogin(admin);

  // --- 관리자 화면에서 OIDC 설정 ---
  await admin.goto(B.base + '/sites/sso'); await shot(admin, 'admin-general');
  const tab = async (name) => { await admin.click(`#sso-tab-${name}`); await admin.waitForTimeout(300); };
  await tab('oidc'); await shot(admin, 'admin-oidc-empty');
  await admin.check('#oidc-enabled'); await admin.fill('#oidc-display', 'Keycloak(OIDC)'); await admin.fill('#oidc-issuer', `${KC}/realms/${REALM}`);
  await admin.fill('#oidc-client-id', 'yona-oidc'); await admin.fill('#oidc-client-secret', 'oidc-secret-1234'); await admin.fill('#oidc-loginid-claim', 'preferred_username');
  await admin.click('#sso-oidc-form button[type=submit]'); await admin.waitForLoadState('networkidle');
  check('OIDC 설정 저장', (await admin.locator('#oidc-enabled').isChecked()), await admin.locator('.alert, .flash, #notification').first().textContent().catch(() => ''));
  await shot(admin, 'admin-oidc'); await admin.click('#oidc-check'); await admin.waitForLoadState('networkidle');

  // --- SAML: SP 키 만들기, IdP 메타데이터 가져오기(주소) ---
  await tab('saml2-keys'); await shot(admin, 'admin-saml2-keys-empty'); await admin.click('#saml2-generate-keys'); await admin.waitForLoadState('networkidle');
  await tab('saml2'); await admin.fill('#sso-saml2-metadata-form input[name=metadataUrl]', `${KC}/realms/${REALM}/protocol/saml/descriptor`);
  await admin.click('#sso-saml2-metadata-form button[type=submit]'); await admin.waitForLoadState('networkidle');
  check('IdP 메타데이터 가져오기', (await admin.inputValue('#saml2-idp-entity')).includes('/realms/corp'), await admin.inputValue('#saml2-idp-entity'));
  check('IdP 인증서를 읽음', (await admin.locator('#saml2-idp-certificates .idp-cert').count()) >= 1);

  await tab('saml2'); await admin.check('#saml2-enabled'); await admin.fill('#saml2-display', 'Keycloak(SAML)'); await admin.fill('#saml2-loginid-attr', 'uid'); await admin.check('#saml2-initiate');
  await admin.click('#sso-saml2-form button[type=submit]'); await admin.waitForLoadState('networkidle');
  check('SAML 설정 저장', await admin.locator('#saml2-enabled').isChecked());

  // --- Keycloak에 SAML 클라이언트를 만든다(Yona의 SP 메타데이터로 변환) ---
  const tokenRes = await fetch(`${KC}/realms/master/protocol/openid-connect/token`, { method: 'POST', body: new URLSearchParams({ grant_type: 'password', client_id: 'admin-cli', username: 'admin', password: 'admin' }) });
  const kcToken = (await tokenRes.json()).access_token; const kc = (path, init = {}) => fetch(`${KC}/admin/realms/${REALM}${path}`, { ...init, headers: { Authorization: `Bearer ${kcToken}`, ...(init.headers || {}) } });
  const spXml = await (await fetch(B.base + '/sso/saml2/metadata/saml2')).text();
  check('SP 메타데이터에 암호화 키가 있음', spXml.includes('use="encryption"'));
  const converted = await (await kc('/client-description-converter', { method: 'POST', headers: { 'Content-Type': 'application/xml' }, body: spXml })).json();
  const client = { ...converted, enabled: true, protocol: 'saml', attributes: { ...converted.attributes, 'saml.server.signature': 'true', 'saml.assertion.signature': 'true', 'saml.encrypt': 'true', 'saml.client.signature': 'false',
    saml_name_id_format: 'email', saml_force_name_id_format: 'true', saml_idp_initiated_sso_url_name: 'yona', saml_idp_initiated_sso_relay_state: '/user/editform' },
    protocolMappers: [
      { name: 'email', protocol: 'saml', protocolMapper: 'saml-user-property-mapper', config: { 'user.attribute': 'email', 'attribute.name': 'email', 'attribute.nameformat': 'Basic' } },
      { name: 'name', protocol: 'saml', protocolMapper: 'saml-user-property-mapper', config: { 'user.attribute': 'firstName', 'attribute.name': 'name', 'attribute.nameformat': 'Basic' } },
      { name: 'uid', protocol: 'saml', protocolMapper: 'saml-user-property-mapper', config: { 'user.attribute': 'username', 'attribute.name': 'uid', 'attribute.nameformat': 'Basic' } }
    ] };
  const created = await kc('/clients', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(client) });
  check('Keycloak SAML 클라이언트 생성', created.status === 201, String(created.status) + ' ' + (created.status === 201 ? '' : await created.text()));

  await shot(admin, 'admin-saml2'); await tab('saml2-keys'); await shot(admin, 'admin-saml2-keys');

  // --- 로그인 화면 ---
  const user = await ctx();
  await user.goto(B.base + '/users/loginform'); await shot(user, 'login-page');
  check('로그인 화면에 단추 둘', (await user.locator('.page.full a.sso-login-btn').count()) === 2);

  // --- OIDC 로그인(Keycloak 화면) ---
  await user.goto(B.base + '/users/loginform?redirectUrl=/user/editform'); await user.click('a.sso-login-btn >> text=Keycloak(OIDC)'); await kcLogin(user, 'kim', 'kimpw1234');
  await user.waitForURL(/localhost:19410/); await shot(user, 'oidc-after-login');
  check('OIDC 로그인 후 원래 주소', user.url().endsWith('/user/editform'), user.url());
  check('OIDC 사용자가 만들어짐(로그인 ID=kim)', (await user.content()).includes('kim'));
  await logout(user);

  // --- OIDC: 이메일 미확인 사용자 거절 ---
  await user.goto(B.base + '/sso/oidc/login'); await kcLogin(user, 'lee', 'leepw1234'); await user.waitForURL(/localhost:19410/);
  check('이메일 미확인 사용자는 로그인 화면으로', user.url().includes('/users/loginform'), user.url()); await shot(user, 'oidc-unverified');
  await logout(user);

  // --- SAML 로그인(암호화된 어설션, 서명된 응답) ---
  await user.goto(B.base + '/sso/saml2/login?returnTo=/user/editform'); await kcLogin(user, 'kim', 'kimpw1234'); await user.waitForURL(/localhost:19410/, { timeout: 20000 });
  await shot(user, 'saml-after-login');
  check('SAML 로그인 성공(암호화된 어설션)', user.url().endsWith('/user/editform'), user.url());
  await logout(user);

  // --- IdP가 시작하는 SAML 로그인 ---
  await user.goto(`${KC}/realms/${REALM}/protocol/saml/clients/yona`); await kcLogin(user, 'kim', 'kimpw1234'); await user.waitForURL(/localhost:19410/, { timeout: 20000 });
  check('IdP 시작 SAML 로그인', user.url().endsWith('/user/editform'), user.url());
  await logout(user);

  // --- 연결된 계정 화면 ---
  await user.goto(B.base + '/sso/oidc/login'); await kcLogin(user, 'kim', 'kimpw1234'); await user.waitForURL(/localhost:19410/);
  await user.goto(B.base + '/user/editform/linked-accounts'); await shot(user, 'linked-accounts');
  check('연결된 계정에 OIDC와 SAML이 보임', (await user.locator('tr.linked-row').count()) >= 1, String(await user.locator('tr.linked-row').count()));

  // --- 관리자 사용자 목록의 연결 해제 단추 ---
  await admin.goto(B.base + '/sites/userList'); await shot(admin, 'user-list-unlink');

  // --- SSO 전용 모드 ---
  await admin.goto(B.base + '/sites/sso?tab=general'); await admin.check('#sso-only'); await admin.click('#sso-mode-form button[type=submit]'); await admin.waitForLoadState('networkidle');
  const anon = await ctx(); await anon.goto(B.base + '/users/loginform'); await shot(anon, 'login-sso-only');
  check('전용 모드에서 비밀번호 입력란 없음', (await anon.locator('form input[name=password]').count()) === 0);
  await anon.goto(B.base + '/users/loginform?local=true'); check('관리자 로컬 로그인 경로', (await anon.locator('form input[name=password]').count()) >= 1);
} finally {
  await browser.close(); await B.stop();
  const failed = results.filter(([, ok]) => !ok); console.log(`\n${results.length - failed.length}/${results.length} 통과`); if (failed.length) console.log('실패:', failed.map(([n]) => n).join(', '));
}
