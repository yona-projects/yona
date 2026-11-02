// X-4 화면 확인용(한 번 쓰고 버리는 스크립트): 실제 gpg로 만든 키와 서명 커밋을 푸시하고 화면을 캡처한다.
import { join } from 'node:path';
import { execFileSync } from 'node:child_process';
import { mkdtempSync, writeFileSync, mkdirSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { chromium } from 'playwright';
import { ADMIN, ROOT, startServer } from './lib/server.mjs';
import { Session } from './lib/http.mjs';

const OUT = join(ROOT, 'shots');
const state = join(ROOT, '.state', 'x4');
rmSync(state, { recursive: true, force: true });
const B = await startServer({ port: 19393, dataDir: state });
const tmp = mkdtempSync(join(tmpdir(), 'x4-'));
const run = (cwd, env, ...args) => execFileSync(args[0], args.slice(1), { cwd, env: { ...process.env, ...env }, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim();

// gpg 키 둘: 등록할 키(admin@example.com)와 등록하지 않을 키(eve@example.com)
const home = (name) => { const d = join(tmp, name); mkdirSync(d, { mode: 0o700 }); return { GNUPGHOME: d }; };
const mine = home('mine'), eve = home('eve');
for (const [env, who] of [[mine, 'Admin <admin@example.com>'], [eve, 'Eve <eve@example.com>']]) {
  run(tmp, env, 'gpg', '--batch', '--pinentry-mode', 'loopback', '--passphrase', '', '--quick-generate-key', who, 'default', 'default', 'never');
}
const fpr = (env, mail) => run(tmp, env, 'gpg', '--list-keys', '--with-colons', mail).split('\n').find((l) => l.startsWith('fpr')).split(':')[9];
const pub = (env, mail) => run(tmp, env, 'gpg', '--armor', '--export', mail);
const myFpr = fpr(mine, 'admin@example.com'), eveFpr = fpr(eve, 'eve@example.com');

try {
  const admin = new Session(B.base); await admin.login(ADMIN.loginId);
  await admin.deviceToken('repo admin:gpg_key write:repo_hook');
  await admin.api('POST', '/user/projects', { name: 'signed-demo', description: '서명 검증 확인용', scope: 'PUBLIC', vcs: 'GIT' });
  const url = `http://admin:${admin.token}@${B.base.replace('http://', '')}/admin/signed-demo`;

  const work = join(tmp, 'work'); mkdirSync(work);
  const git = (env, ...a) => run(work, env, 'git', '-c', 'user.name=Admin', '-c', 'user.email=admin@example.com', ...a);
  git({}, 'init', '-q', '-b', 'master'); git({}, 'remote', 'add', 'origin', url);
  const commit = (env, file, msg, signKey, email = 'admin@example.com') => {
    writeFileSync(join(work, file), msg + '\n'); git(env, 'add', '.');
    const args = ['-c', `user.email=${email}`, 'commit', '-q', '-m', msg]; if (signKey) args.splice(3, 0, `-S${signKey}`);
    git({ ...env, GIT_COMMITTER_DATE: '2026-11-02T09:00:00+0900' }, ...args);
  };
  // 이력: 서명 없음 -> 등록한 키로 서명 -> 등록하지 않은 키로 서명 -> 이메일이 다른 서명 -> 등록한 키로 서명
  commit({}, 'a.txt', '서명 없는 커밋', null);
  commit(mine, 'b.txt', '등록한 키로 서명한 커밋', myFpr);
  commit(eve, 'c.txt', '등록하지 않은 키로 서명한 커밋', eveFpr, 'eve@example.com');
  commit(mine, 'd.txt', '커밋터 이메일이 키와 다른 커밋', myFpr, 'other@example.com');
  commit(mine, 'e.txt', '마지막 Verified 커밋', myFpr);
  git(mine, 'tag', '-u', myFpr, '-m', '서명한 릴리스', 'v1.0');
  git({}, 'tag', '-a', '-m', '서명 없는 릴리스', 'v0.9', 'HEAD~4');
  git({}, 'push', '-q', 'origin', 'master', '--tags');
  const head = git({}, 'rev-parse', 'HEAD'), signedId = git({}, 'rev-parse', 'HEAD~3'), unknownId = git({}, 'rev-parse', 'HEAD~2');

  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 }, locale: 'ko-KR' });
  const t = new Session(B.base); await t.login(ADMIN.loginId); await ctx.addCookies(t.playwrightCookies());
  await ctx.route((u) => u.origin !== B.base, (r) => r.abort());
  const page = await ctx.newPage();
  const shot = async (name, path, opts = {}) => {
    if (path) await page.goto(B.base + path, { waitUntil: 'load' });
    await page.waitForLoadState('networkidle', { timeout: 6000 }).catch(() => {});
    await page.screenshot({ path: join(OUT, `x4-${name}.png`), fullPage: !!opts.full });
  };

  await shot('01-gpg-list-empty', '/user/editform/gpg');
  await shot('02-gpg-add-form', '/user/editform/gpg/new');
  await page.fill('#gpg-name', '잘못된 키'); await page.fill('#gpg-public-key', 'ssh-ed25519 AAAAC3Nza... user@host');
  await page.click('#gpg-create button[type=submit]'); await shot('03-gpg-add-error', null);
  // 두 키를 설정 화면에서 추가한다(등록하는 키 하나는 이름을 붙이고 하나는 비운다)
  await page.goto(B.base + '/user/editform/gpg/new');
  await page.fill('#gpg-name', '내 노트북'); await page.fill('#gpg-public-key', pub(mine, 'admin@example.com'));
  await page.click('#gpg-create button[type=submit]'); await page.waitForLoadState('load');
  await shot('04-gpg-list', '/user/editform/gpg');

  await shot('05-commit-history', `/admin/signed-demo/commits`);
  await shot('06-commit-verified', `/admin/signed-demo/commit/${signedId}`);
  await shot('07-commit-unverified-unknown-key', `/admin/signed-demo/commit/${unknownId}`);
  await shot('08-tags', `/admin/signed-demo/tags`);

  // 규칙 묶음: 서명 커밋 필수
  await shot('09-ruleset-form', `/admin/signed-demo/setting/rulesets/new`);
  await page.fill('input[name=name]', '서명 필수'); await page.fill('input[name=pattern]', 'master');
  await page.check('input[name=requireSignedCommits]');
  await page.click('form#rulesetForm button[type=submit]'); await page.waitForLoadState('load');
  await shot('10-ruleset-list', `/admin/signed-demo/setting/rulesets`);

  // 푸시 거부: 서명 없는 커밋을 master로 푸시한다(바이패스를 켜지 않았으므로 관리자도 막힌다)
  commit({}, 'f.txt', '서명 없는 새 커밋', null);
  let rejected = '';
  try { git({}, 'push', 'origin', 'master'); } catch (e) { rejected = String(e.stderr || e.message); }
  writeFileSync(join(OUT, 'x4-11-push-rejected.txt'), rejected);
  console.log('PUSH OUTPUT:\n' + rejected);

  // 풀 리퀘스트: feature 브랜치(서명 없는 커밋)를 만들어 master로 보내면 병합이 막힌다
  git({}, 'reset', '-q', '--hard', head); git({}, 'checkout', '-q', '-b', 'feature');
  commit({}, 'g.txt', '서명 없는 기능 커밋', null);
  git({}, 'push', '-q', 'origin', 'feature');
  const pr = await admin.api('POST', '/owners/admin/projects/signed-demo/pullrequests', { title: '서명 없는 기능', body: '', fromBranch: 'feature', toBranch: 'master' });
  const number = pr.result?.number ?? pr.number;
  // 병합 검사가 끝나 차단 안내가 나올 때까지 기다린다
  for (let i = 0; i < 40; i++) {
    await page.goto(B.base + `/admin/signed-demo/pullRequest/${number}`, { waitUntil: 'load' });
    if (!(await page.content()).includes('안전한지 확인')) break;
    await new Promise((r) => setTimeout(r, 1500));
  }
  await shot('12-pull-request-blocked', null);
  await shot('13-pull-request-changes-commit', `/admin/signed-demo/pullRequest/${number}/changes`);

  // 서명한 커밋으로 만든 풀 리퀘스트: 커밋 하나를 고르면 정보에 Verified가 보인다
  git({}, 'reset', '-q', '--hard', head); git({}, 'checkout', '-q', '-b', 'feature2');
  commit(mine, 'h.txt', '서명한 기능 커밋', myFpr);
  const signedFeature = git({}, 'rev-parse', 'HEAD');
  git({}, 'push', '-q', 'origin', 'feature2');
  const pr2 = await admin.api('POST', '/owners/admin/projects/signed-demo/pullrequests', { title: '서명한 기능', body: '', fromBranch: 'feature2', toBranch: 'master' });
  const number2 = pr2.result?.number ?? pr2.number;
  for (let i = 0; i < 40; i++) {
    await page.goto(B.base + `/admin/signed-demo/pullRequest/${number2}`, { waitUntil: 'load' });
    if (!(await page.content()).includes('안전한지 확인')) break;
    await new Promise((r) => setTimeout(r, 1500));
  }
  await shot('14-pull-request-changes-signed-commit', `/admin/signed-demo/pullRequest/${number2}/changes/${signedFeature}`);

  await browser.close();
  console.log('ok', number);
} finally { await B.stop(); rmSync(tmp, { recursive: true, force: true }); }
