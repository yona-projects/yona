// 개인용 API 토큰의 범위를 화면에서 만들고, 편집하고, 저장한 뒤 실제 API가 그 범위대로 허용하고 거부하는지 확인한다(D-126).
//
// 실행: BASE=http://127.0.0.1:19402 E2E_USER=admin E2E_PASS=... [E2E_LEGACY_TOKEN=옛_토큰_값] node e2e/token-scope.mjs
// 서버는 실행 중이어야 한다. E2E_LEGACY_TOKEN은 v1.6 사용자 토큰(n4user.token)을 이관 명령(scripts/migrate-v16.sh)으로 옮긴 뒤의 값이며,
// 주면 "범위 제한 없음" 옛 토큰 흐름도 확인한다. E2E_MIGRATED_TOKEN은 이전 빌드가 `read:user` 한 범위를 쉼표 문자열로 저장해 둔 토큰으로,
// 이관 명령이 범위 행으로 옮긴 뒤에도 그 범위만 허용되는지 확인한다. 하나라도 어긋나면 종료 코드 1이다.
//
// 범위마다 다른 API 두 개로 판정한다.
//   GET /-_-api/v1/users/{user}   : read:user 가 필요하다
//   GET /-_-api/v1/user/ssh_keys  : read:ssh_key 가 필요하다 (admin:ssh_key 는 write, read 를 포함한다)
import { chromium, request as pwRequest } from '/home/jiho/yona-convert/node_modules/playwright/index.mjs';

const BASE = process.env.BASE ?? 'http://127.0.0.1:19402';
const USER = process.env.E2E_USER ?? 'admin';
const PASS = process.env.E2E_PASS;
const LEGACY = process.env.E2E_LEGACY_TOKEN;
const MIGRATED = process.env.E2E_MIGRATED_TOKEN;
if (!PASS) { console.error('E2E_PASS가 필요하다'); process.exit(2); }

let failed = 0;
const check = (name, ok, detail = '') => { console.log(`${ok ? '통과' : '실패'}  ${name}${detail ? '  ' + detail : ''}`); if (!ok) failed++; };

const api = await pwRequest.newContext({ baseURL: BASE });
const status = async (path, token) => (await api.get(path, { headers: { Authorization: `token ${token}` }, maxRedirects: 0 })).status();
const USER_API = `/-_-api/v1/users/${USER}`;
const SSH_API = '/-_-api/v1/user/ssh_keys';
/** 두 API의 상태를 [read:user 필요한 것, read:ssh_key 필요한 것] 순서로 돌려준다. */
const probe = async (token) => [await status(USER_API, token), await status(SSH_API, token)];

const browser = await chromium.launch();
const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
const xsrf = async () => (await ctx.cookies(BASE)).find((c) => c.name === 'XSRF-TOKEN')?.value;
await ctx.request.get(BASE + '/users/loginform');
const login = await ctx.request.post(BASE + '/users/login', { form: { loginIdOrEmail: USER, password: PASS }, headers: { 'X-XSRF-TOKEN': await xsrf() }, maxRedirects: 0 });
check('로그인', [200, 302, 303].includes(login.status()), `상태 ${login.status()}`);
const page = await ctx.newPage();
page.on('dialog', (d) => d.accept());

const box = (value) => page.locator(`#token-edit input[name=scopes][value="${value}"], #token-create input[name=scopes][value="${value}"]`);
/** 편집(또는 만들기) 폼의 범위를 [want]만 체크된 상태로 맞춘다. */
async function setScopes(want) {
  const all = await page.locator('input[name=scopes]').all();
  for (const input of all) {
    const value = await input.getAttribute('value');
    const on = want.includes(value);
    if ((await input.isChecked()) !== on) await input.setChecked(on);
  }
}
const checkedNow = async () => (await page.locator('input[name=scopes]:checked').evaluateAll((els) => els.map((e) => e.value))).sort();
const listScopes = async (name) => (await page.locator('#token-list tr', { has: page.locator('a.token-edit', { hasText: name }) }).locator('td:nth-child(3) .token-scope').allInnerTexts()).sort();

async function createToken(name, scopes) {
  await page.goto(BASE + '/user/editform/token/new', { waitUntil: 'load' });
  await page.fill('#token-name', name);
  await setScopes(scopes);
  await Promise.all([page.waitForLoadState('load'), page.click('#token-create button[type=submit]')]);
  const value = await page.inputValue('#new-token-value');
  return value;
}
async function openEdit(name) {
  await page.goto(BASE + '/user/editform/token', { waitUntil: 'load' });
  await Promise.all([page.waitForLoadState('load'), page.locator('a.token-edit', { hasText: name }).first().click()]);
}
async function saveEdit(scopes) {
  await setScopes(scopes);
  await Promise.all([page.waitForLoadState('load'), page.click('#token-edit button[type=submit]')]);
}

const name = `범위 e2e ${Date.now()}`;

// 1) 만들 때 고른 범위가 그대로 적용된다
const token = await createToken(name, ['read:user']);
check('만든 토큰의 값이 한 번 보인다', token.startsWith('yona_pat_'));
check('목록에 고른 범위만 보인다', JSON.stringify(await listScopes(name)) === JSON.stringify(['read:user']), JSON.stringify(await listScopes(name)));
let [u, s] = await probe(token);
check('read:user 토큰: 사용자 API는 허용', u === 200, `상태 ${u}`);
check('read:user 토큰: SSH 키 API는 거부', s === 403, `상태 ${s}`);

// 2) 편집해서 범위를 바꾸고 저장하면 바로 새 범위로 판정한다
await openEdit(name);
check('편집 화면에 저장된 범위가 체크되어 있다', JSON.stringify(await checkedNow()) === JSON.stringify(['read:user']), JSON.stringify(await checkedNow()));
await saveEdit(['read:ssh_key']);
check('저장 뒤 목록의 범위가 바뀐다', JSON.stringify(await listScopes(name)) === JSON.stringify(['read:ssh_key']), JSON.stringify(await listScopes(name)));
[u, s] = await probe(token);
check('read:ssh_key 로 바꾼 뒤: 사용자 API는 거부', u === 403, `상태 ${u}`);
check('read:ssh_key 로 바꾼 뒤: SSH 키 API는 허용', s === 200, `상태 ${s}`);
await openEdit(name);
check('다시 열면 저장한 범위가 체크되어 있다', JSON.stringify(await checkedNow()) === JSON.stringify(['read:ssh_key']), JSON.stringify(await checkedNow()));

// 3) 상위 범위는 하위 범위를 포함한다 (admin:ssh_key 는 read:ssh_key 를 포함하지만 read:user 는 포함하지 않는다)
await saveEdit(['admin:ssh_key']);
[u, s] = await probe(token);
check('admin:ssh_key: SSH 키 API는 허용(포함 관계)', s === 200, `상태 ${s}`);
check('admin:ssh_key: 사용자 API는 거부', u === 403, `상태 ${u}`);

// 4) 두 범위를 함께 고르면 둘 다 허용된다
await openEdit(name);
await saveEdit(['read:user', 'read:ssh_key']);
check('두 범위 목록', JSON.stringify(await listScopes(name)) === JSON.stringify(['read:ssh_key', 'read:user']), JSON.stringify(await listScopes(name)));
[u, s] = await probe(token);
check('두 범위: 사용자 API 허용', u === 200, `상태 ${u}`);
check('두 범위: SSH 키 API 허용', s === 200, `상태 ${s}`);

// 5) 범위를 모두 해제해도 일반 토큰은 "범위 제한 없음"이 되지 않는다
await openEdit(name);
await saveEdit([]);
[u, s] = await probe(token);
check('범위를 모두 해제한 일반 토큰: 사용자 API 거부', u === 403, `상태 ${u}`);
check('범위를 모두 해제한 일반 토큰: SSH 키 API 거부', s === 403, `상태 ${s}`);
check('범위를 모두 해제해도 "범위 제한 없음" 표시는 없다', (await page.locator('#token-list tr', { has: page.locator('a.token-edit', { hasText: name }) }).locator('.token-scope.bg-warning').count()) === 0);

// 5-1) 이전 빌드가 문자열로 저장한 범위는 이관 뒤에도 그 범위만 허용한다
if (MIGRATED) {
  [u, s] = await probe(MIGRATED);
  check('문자열에서 이관한 토큰(read:user): 사용자 API 허용', u === 200, `상태 ${u}`);
  check('문자열에서 이관한 토큰(read:user): SSH 키 API 거부', s === 403, `상태 ${s}`);
  await page.goto(BASE + '/user/editform/token', { waitUntil: 'load' });
  const got = JSON.stringify(await listScopes('old build token'));
  check('문자열에서 이관한 토큰의 범위가 목록에 보인다', got === JSON.stringify(['read:user']), got);
}

// 6) 옛 토큰(v1.6 사용자 토큰): 범위 제한이 없고, 범위를 골라 저장하면 그 범위만 가진다
if (LEGACY) {
  [u, s] = await probe(LEGACY);
  check('옛 토큰: 사용자 API 허용(범위 제한 없음)', u === 200, `상태 ${u}`);
  check('옛 토큰: SSH 키 API 허용(범위 제한 없음)', s === 200, `상태 ${s}`);
  await page.goto(BASE + '/user/editform/token', { waitUntil: 'load' });
  const legacyRow = page.locator('#token-list tr', { has: page.locator('.legacy-warning') });
  check('옛 토큰 줄에 "범위 제한 없음" 표시가 있다', (await legacyRow.locator('.token-scope.bg-warning').count()) === 1);
  await Promise.all([page.waitForLoadState('load'), legacyRow.locator('a.token-edit').click()]);
  check('옛 토큰 편집 화면은 체크된 범위가 없다', (await checkedNow()).length === 0);
  await saveEdit(['read:user']);
  [u, s] = await probe(LEGACY);
  check('옛 토큰을 read:user 로 저장한 뒤: 사용자 API 허용', u === 200, `상태 ${u}`);
  check('옛 토큰을 read:user 로 저장한 뒤: SSH 키 API 거부', s === 403, `상태 ${s}`);
  await page.goto(BASE + '/user/editform/token', { waitUntil: 'load' });
  check('저장한 뒤에는 "범위 제한 없음" 표시가 사라진다', (await page.locator('#token-list tr', { has: page.locator('.legacy-warning') }).locator('.token-scope.bg-warning').count()) === 0);
} else {
  console.log('건너뜀  옛 토큰 흐름 (E2E_LEGACY_TOKEN 없음)');
}

// 정리: 만든 토큰을 지운다
await openEdit(name);
const id = page.url().split('/').pop();
const del = await ctx.request.post(`${BASE}/user/token/${id}/delete`, { headers: { 'X-XSRF-TOKEN': await xsrf() }, maxRedirects: 0 });
check('만든 토큰 삭제', [200, 302, 303].includes(del.status()), `상태 ${del.status()}`);
check('삭제한 토큰은 더 이상 쓸 수 없다', (await status(USER_API, token)) === 401);

await browser.close();
console.log(failed === 0 ? '\n모두 통과' : `\n실패 ${failed}건`);
process.exit(failed === 0 ? 0 : 1);
