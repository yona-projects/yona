// X-23 단계 5: Magpie 어댑터 문서(docs/magpie/tools-yona/operations.md)의 조리법을 실제 Yona 서버와 `yona` CLI로 그대로 실행하고,
// 각 동작의 출력이 Magpie 계약의 필드와 형식과 같은지 확인한다. 조리법은 문서에서 `<!-- recipe:이름 -->` 표지 뒤의 bash 블록을 꺼내 쓴다.
import { join } from 'node:path';
import { rmSync, readFileSync, writeFileSync, mkdtempSync, mkdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { execFileSync } from 'node:child_process';
import { ADMIN, ROOT, startServer } from './lib/server.mjs';
import { Session } from './lib/http.mjs';

const CLI_DIR = process.env.YONA_CLI_DIR || join(ROOT, '..', '..', 'yona-cli');
const DOC_DIR = join(ROOT, '..', 'docs', 'magpie', 'tools-yona');
const state = join(ROOT, '.state', 'magpie'); rmSync(state, { recursive: true, force: true });
const bin = mkdtempSync(join(tmpdir(), 'yona-cli-')); execFileSync('go', ['build', '-o', join(bin, 'yona'), '.'], { cwd: CLI_DIR, stdio: 'inherit' });
const B = await startServer({ port: 19420, dataDir: state });
const results = []; const check = (name, ok, detail = '') => { results.push([name, ok]); console.log(ok ? 'OK  ' : 'FAIL', name, ok ? '' : detail); };

// --- 조리법 꺼내기와 실행 ---
const recipes = {};
for (const file of ['operations.md', 'status-rollup.md', 'labels.md', 'issue-template.md']) {
  let doc = ''; try { doc = readFileSync(join(DOC_DIR, file), 'utf8'); } catch { continue; }
  for (const m of doc.matchAll(/<!-- recipe:(\w+) -->\n```bash\n([\s\S]*?)\n```/g)) recipes[m[1]] = m[2];
}
const day = 24 * 3600 * 1000; const date = (offsetDays) => new Date(Date.now() + offsetDays * day).toISOString().slice(0, 10);
const UPSTREAM = 'admin/magpie-demo'; const [OWNER, PROJECT] = UPSTREAM.split('/');
let token = '';
function run(name, values = {}, { fail = false } = {}) {
  let script = recipes.prelude + '\n' + recipes[name];
  const all = { upstream: UPSTREAM, owner: OWNER, project: PROJECT, since: date(-1), end: date(1), until: date(1), filters: '', 'bot-logins': '', ...values };
  script = script.replace(/<([A-Za-z-]+)>/g, (whole, key) => (key in all ? String(all[key]) : whole));
  const env = { ...process.env, PATH: bin + ':' + process.env.PATH, YONA_HOST: B.base, YONA_TOKEN: token, YONA_CLI_CONFIG_DIR: join(bin, 'cfg') };
  try { return execFileSync('bash', ['-c', script], { env, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim(); }
  catch (e) { if (fail) return null; throw new Error(`${name} 실패: ${String(e.stderr || e.message).slice(0, 400)}\n--- script ---\n${script}`); }
}
const json = (name, values, opts) => { const out = run(name, values, opts); return out === null ? null : JSON.parse(out); };
// v1.6 REST의 `createdAt` 형식(`yyyy-MM-dd a hh:mm:ss Z`, 영어 AM/PM)이다. 없으면 생성 시각이 비어 있다(v1.6 동작)
const v16Date = () => { const d = new Date(); const pad = (n) => String(n).padStart(2, '0'); const h = d.getUTCHours(); return `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())} ${h >= 12 ? 'PM' : 'AM'} ${pad(h % 12 === 0 ? 12 : h % 12)}:${pad(d.getUTCMinutes())}:${pad(d.getUTCSeconds())} +0000`; };
const isIsoZ = (v) => typeof v === 'string' && /^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\dZ$/.test(v);
const isIso = (v) => typeof v === 'string' && /^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(Z|[+-]\d\d:?\d\d)$/.test(v);

try {
  // --- 데이터 준비: 프로젝트, 이슈, 포크와 풀 리퀘스트 ---
  const admin = new Session(B.base); await admin.login(ADMIN.loginId); token = await admin.deviceToken('repo');
  const dev = new Session(B.base); await dev.signup('dev1', '개발자', 'dev1@example.com'); await dev.login('dev1'); const devToken = await dev.deviceToken('repo');
  await admin.api('POST', '/user/projects', { name: PROJECT, description: 'Magpie 확인', scope: 'PUBLIC', vcs: 'GIT' });
  const work = mkdtempSync(join(tmpdir(), 'magpie-work-')); const git = (cwd, ...a) => execFileSync('git', a, { cwd, stdio: 'pipe', encoding: 'utf8' });
  const origin = join(work, 'origin'); mkdirSync(origin); git(origin, 'init', '-q', '-b', 'master'); git(origin, 'config', 'user.email', 'a@example.com'); git(origin, 'config', 'user.name', 'admin');
  writeFileSync(join(origin, 'a.txt'), 'line1\nline2\nline3\n'); git(origin, 'add', '.'); git(origin, 'commit', '-q', '-m', 'base');
  git(origin, 'push', '-q', `http://admin:${token}@${B.base.replace('http://', '')}/${UPSTREAM}`, 'master');
  const issueBy = async (session, title, body) => { const r = await session.api('POST', `/owners/${OWNER}/projects/${PROJECT}/issues`, { issues: [{ title, body, createdAt: v16Date() }] }); const loc = Array.isArray(r) ? r[0] : r; const text = typeof loc === 'string' ? loc : (loc.location || loc.url || JSON.stringify(loc)); return { number: Number(String(text).match(/(\d+)\D*$/)[1]) }; };
  const i1 = await issueBy(dev, '첫 이슈', '문제가 있습니다'); const i2 = await issueBy(dev, '둘째 이슈', '두 번째'); const i3 = await issueBy(admin, '관리자의 이슈', '내가 연 이슈');
  const num = (r) => r.number;
  const n1 = num(i1), n2 = num(i2), n3 = num(i3);
  await admin.api('POST', `/owners/${OWNER}/projects/${PROJECT}/issues/${n1}/comments`, { body: '확인했습니다, 재현됩니다' });
  await admin.api('POST', `/owners/${OWNER}/projects/${PROJECT}/issues/${n2}/comments`, { body: '다른 이슈의 중복입니다 (duplicate)' });
  await dev.api('POST', `/owners/${OWNER}/projects/${PROJECT}/issues/${n3}/comments`, { body: '관리자 이슈에 남기는 의견' });
  await admin.api('PATCH', `/owners/${OWNER}/projects/${PROJECT}/issues/${n2}`, { state: 'closed' }, { ok: [200, 204] }).catch(async () => admin.api('PUT', `/owners/${OWNER}/projects/${PROJECT}/issues/${n2}`, { state: 'closed' }));
  // 포크와 두 개의 풀 리퀘스트
  await dev.api('POST', `/owners/${OWNER}/projects/${PROJECT}/forks`, {});
  const fork = join(work, 'fork'); git(work, 'clone', '-q', `http://dev1:${devToken}@${B.base.replace('http://', '')}/dev1/${PROJECT}`, 'fork'); git(fork, 'config', 'user.email', 'dev1@example.com'); git(fork, 'config', 'user.name', 'dev1');
  const branchPush = (name, file) => { git(fork, 'checkout', '-q', '-b', name, 'master'); writeFileSync(join(fork, file), `${name}\n`); git(fork, 'add', '.'); git(fork, 'commit', '-q', '-m', `feat: ${name}`); git(fork, 'push', '-q', 'origin', name); };
  branchPush('topic-a', 'b.txt'); branchPush('topic-b', 'c.txt');
  const cliEnv = { ...process.env, PATH: bin + ':' + process.env.PATH, YONA_HOST: B.base, YONA_CLI_CONFIG_DIR: join(bin, 'cfg2') };
  const createPr = (branch, title) => execFileSync('yona', ['pr', 'create', '-R', UPSTREAM, '--title', title, '--from', `dev1/${PROJECT}`, '--from-branch', branch, '--to-branch', 'master'], { env: { ...cliEnv, YONA_TOKEN: devToken }, encoding: 'utf8' });
  createPr('topic-a', '기능 A'); createPr('topic-b', '기능 B');
  const prs = (await admin.api('GET', `/owners/${OWNER}/projects/${PROJECT}/pullrequests?state=open`)).items; const pa = prs.find((p) => p.title === '기능 A').number; const pb = prs.find((p) => p.title === '기능 B').number;
  await admin.api('POST', `/owners/${OWNER}/projects/${PROJECT}/pullrequests/${pa}/comments`, { body: 'PR 전체에 남기는 댓글' }).catch(() => null);

  // --- 인증 ---
  check('auth_check', run('auth_check').includes(PROJECT));
  check('viewer', run('viewer') === 'admin');

  // --- tracker ---
  const filed = json('list_filed', { login: 'dev1' });
  check('list_filed 모양', Array.isArray(filed) && filed.length === 2 && filed.every((x) => typeof x.id === 'string' && x.permalink.startsWith(B.base) && 'created' in x && 'closed' in x && Array.isArray(x.labels)), JSON.stringify(filed));
  check('list_filed 닫은 시각', filed.some((x) => isIsoZ(x.closed)) && filed.some((x) => x.closed === null), JSON.stringify(filed));
  const triaged = json('list_triaged', { login: 'admin' });
  check('list_triaged: 남의 이슈에 단 댓글만', triaged.length === 2 && triaged.every((x) => x.pushback_candidate === null && isIsoZ(x.first_activity) && x.permalink.startsWith(B.base)), JSON.stringify(triaged));
  const commented = json('list_commented', { login: 'admin' });
  check('list_commented: 이슈와 풀 리퀘스트', commented.length >= 2 && commented.every((x) => isIsoZ(x.first_activity)), JSON.stringify(commented));
  const created = json('list_created', {});
  check('list_created: 이슈와 풀 리퀘스트와 첫 기여', created.some((x) => x.kind === 'issue') && created.some((x) => x.kind === 'change') && created.every((x) => typeof x.author === 'string' && typeof x.author_first_time === 'boolean'), JSON.stringify(created));
  check('list_created: dev1의 첫 이슈만 첫 기여', created.filter((x) => x.author === 'dev1' && x.author_first_time === true).length === 1, JSON.stringify(created.map((x) => [x.kind, x.author, x.author_first_time])));
  const reply = json('first_reply', { N: n1 });
  check('first_reply', reply && reply.author === 'admin' && isIso(reply.created) && reply.url.includes(`/issue/${n1}#comment-`) && reply.body.includes('재현'), JSON.stringify(reply));
  check('first_reply: 메인테이너 답이 없으면 null', run('first_reply', { N: n3 }) === 'null');

  // --- change-request 읽기 ---
  const open = json('list_open', { filters: '--author dev1' });
  check('list_open 모양', open.length === 2 && open.every((x) => typeof x.id === 'string' && x.title && x.author === 'dev1' && x.permalink.startsWith(B.base) && ['open', 'approved'].includes(x.state) && Array.isArray(x.labels)), JSON.stringify(open));
  check('list_open: 최근 갱신 순', Date.parse(open[0].updated) >= Date.parse(open[1].updated));
  check('list_open: 리뷰 요청자 필터', (json('list_open', { filters: '--review-requested admin' })).length === 0);
  const got = json('get', { N: pa });
  check('get 모양', got.id === String(pa) && got.base === 'master' && got.commits.length >= 1 && typeof got.commits[0].sha === 'string' && got.checks === 'none' && ['clean', 'conflicting', 'unknown'].includes(got.mergeable), JSON.stringify(got).slice(0, 400));
  check('get: 변경 내용(diff)', typeof got.diff === 'string' && got.diff.includes('@@') && got.diff.includes('b.txt'), String(got.diff).slice(0, 200));
  const disc0 = json('get_discussion', { N: pa });
  check('get_discussion 모양', Array.isArray(disc0.comments) && Array.isArray(disc0.participants) && typeof disc0.unresolved_count === 'number', JSON.stringify(disc0));
  const st0 = json('status', { N: pa });
  check('status', ['open', 'under-review'].includes(st0.state) && st0.checks === 'none' && ['clean', 'conflicting', 'unknown'].includes(st0.mergeable), JSON.stringify(st0));

  // --- change-request 쓰기 ---
  const bodyFile = join(work, 'body.txt'); writeFileSync(bodyFile, '리뷰 의견입니다\n');
  run('post_review', { N: pa, 'verdict-flag': '--approve', 'body-file': bodyFile });
  const disc1 = json('get_discussion', { N: pa });
  check('post_review 뒤의 토론', disc1.comments.some((c) => c.kind === 'approval' && c.author === 'admin' && c.body.includes('리뷰 의견')) && disc1.participants.includes('admin'), JSON.stringify(disc1));
  check('status: 승인', ['approved', 'under-review'].includes(json('status', { N: pa }).state), JSON.stringify(json('status', { N: pa })));
  const landed = json('land', { N: pa, strategy: 'squash' });
  check('land', landed && /^[0-9a-f]{7,40}$/.test(landed.landed_ref) && ['squash', 'merge', 'rebase'].includes(landed.strategy_used), JSON.stringify(landed));
  check('land 뒤 status는 landed', json('status', { N: pa }).state === 'landed');
  const reasonFile = join(work, 'reason.txt'); writeFileSync(reasonFile, '이 방향은 받지 않습니다\n');
  run('reject', { N: pb, 'reason-file': reasonFile });
  check('reject 뒤 status는 rejected', json('status', { N: pb }).state === 'rejected');
  check('reject: 이유가 댓글로 남음', json('get_discussion', { N: pb }).comments.some((c) => c.body.includes('받지 않습니다')), JSON.stringify(json('get_discussion', { N: pb })));

  // --- 활동 ---
  const authored = json('list_authored', { login: 'dev1' });
  check('list_authored', authored.total === 2 && authored.items.every((x) => typeof x.id === 'string' && x.author === 'dev1' && ['landed', 'rejected', 'open'].includes(x.state) && 'landed_at' in x && typeof x.author_first_time === 'boolean'), JSON.stringify(authored));
  check('list_authored: 병합한 것은 landed_at이 있다', authored.items.some((x) => x.state === 'landed' && x.landed_at), JSON.stringify(authored.items));
  const commits = json('list_authored_commits', { login: 'dev1' });
  check('list_authored_commits', commits.length >= 1 && commits.every((x) => /^[0-9a-f]{40}$/.test(x.sha) && x.change_id === null && x.author_email === 'dev1@example.com' && typeof x.signed_off_by === 'boolean'), JSON.stringify(commits));
  const reviewsAll = json('list_reviews_given_all', {});
  check('list_reviews_given(사람 생략)', reviewsAll.total >= 1 && reviewsAll.items.every((x) => x.reviewer === 'admin'), JSON.stringify(reviewsAll));
  const reviews = json('list_reviews_given', { login: 'admin' });
  check('list_reviews_given', reviews.total >= 1 && reviews.items.every((x) => typeof x.change_id === 'string' && x.reviewer === 'admin' && isIsoZ(x.first_review_at) && typeof x.body_length === 'number' && typeof x.inline_comment_count === 'number'), JSON.stringify(reviews));

  // --- 쓰기: 이슈, 마일스톤, 라벨, 풀 리퀘스트, 사람 (GitHub 어댑터와 같은 범위) ---
  const wf = (name, text) => { const p = join(work, name); writeFileSync(p, text); return p; };
  const w = { 'title-file': '', 'body-file': '' };
  const API = `/owners/${OWNER}/projects/${PROJECT}`;
  check('collaborators', run('collaborators').split('\n').sort().join() === 'admin', run('collaborators'));
  run('label_create', { label: 'needs triage', category: 'lifecycle', color: '#ff0000' }); run('label_create', { label: 'pr created', category: 'lifecycle', color: '#00ff00' });
  check('label_list', run('label_list').split('\n').includes('needs triage'), run('label_list'));
  // 셸이 풀어 버리면 안 되는 제목: 따옴표와 명령 치환이다. 실행되면 pwned 파일이 생긴다
  const hostile = `x' ; touch ${work}/pwned ; echo '$(touch ${work}/pwned2)`;
  const nNew = Number(run('issue_create', { ...w, 'title-file': wf('t1.txt', hostile + '\n'), 'body-file': wf('b1.txt', '### 필드\n값\n\n`code` $(touch ' + work + '/pwned3)\n') }));
  check('issue_create: 번호', nNew > n3, String(nNew));
  const view = json('issue_view', { N: nNew });
  check('issue_view: 제목은 원문 그대로', view.title === hostile && view.state.toLowerCase() === 'open' && view.body.includes('### 필드'), JSON.stringify(view));
  check('issue_create: 셸이 제목을 실행하지 않았다', ![ 'pwned', 'pwned2', 'pwned3' ].some((f) => { try { readFileSync(join(work, f)); return true; } catch { return false; } }));
  const labelsAfterAdd = run('issue_edit_labels', { N: nNew, 'add-labels': 'needs triage,pr created', 'remove-labels': '' });
  check('issue_edit_labels: 추가', labelsAfterAdd.split(',').sort().join() === 'needs triage,pr created', labelsAfterAdd);
  const labelsAfterRemove = run('issue_edit_labels', { N: nNew, 'add-labels': '', 'remove-labels': 'pr created' });
  check('issue_edit_labels: 제거', labelsAfterRemove === 'needs triage', labelsAfterRemove);
  check('issue_edit_labels: 모르는 라벨은 아무것도 바꾸지 않는다', run('issue_edit_labels', { N: nNew, 'add-labels': 'no such label', 'remove-labels': '' }, { fail: true }) === null && json('issue_view', { N: nNew }).labels.length === 1);
  check('issue_assign', run('issue_assign', { N: nNew, assignee: 'dev1' }) === 'dev1');
  check('issue_unassign', run('issue_unassign', { N: nNew }) === '');
  run('issue_edit_body', { N: nNew, 'body-file': wf('b2.txt', '### 필드\n새 값\n') });
  check('issue_edit_body', json('issue_view', { N: nNew }).body.includes('새 값'));
  run('issue_comment', { N: nNew, 'body-file': wf('c1.txt', '댓글 `x` $(touch ' + work + '/pwned4)\n') });
  const cs = await admin.api('GET', `${API}/issues/${nNew}/comments`);
  check('issue_comment', JSON.stringify(cs).includes('댓글') && !(() => { try { readFileSync(join(work, 'pwned4')); return true; } catch { return false; } })(), JSON.stringify(cs).slice(0, 300));
  run('issue_close', { N: nNew });
  check('issue_close', json('issue_view', { N: nNew }).state.toLowerCase() === 'closed');
  run('issue_reopen', { N: nNew });
  check('issue_reopen', json('issue_view', { N: nNew }).state.toLowerCase() === 'open');
  const ms = json('milestone_create', { target: 'v1.0', description: '첫 배포' });
  check('milestone_create', ms && ms.title === 'v1.0' && typeof ms.id === 'number', JSON.stringify(ms));
  const msList = json('milestone_list', { target: 'v1.0' });
  check('milestone_list', msList && msList.id === ms.id && msList.state === 'open', JSON.stringify(msList));
  check('milestone_assign', run('milestone_assign', { N: nNew, 'milestone-id': ms.id }) === 'v1.0');
  await admin.api('PATCH', `${API}/milestones/${ms.id}`, { state: 'closed' });
  check('milestone_list: 닫힌 것도 찾는다', json('milestone_list', { target: 'v1.0' }).state === 'closed');
  check('milestone_assign: 닫힌 마일스톤에도 지정한다', run('milestone_assign', { N: n1, 'milestone-id': ms.id }) === 'v1.0');
  const dup = json('milestone_create', { target: 'v1.0', description: '' });
  check('milestone_create: 같은 제목은 만들지 않는다', dup && dup.message, JSON.stringify(dup));
  branchPush('topic-c', 'd.txt');
  const prNew = Number(run('pr_create', { ...w, 'title-file': wf('t2.txt', '기능 C ' + hostile + '\n'), 'body-file': wf('b3.txt', '본문 $(touch ' + work + '/pwned5)\n'), 'from-owner': 'dev1', 'from-project': PROJECT, 'from-branch': 'topic-c', 'to-branch': 'master' }));
  check('pr_create', prNew > 0 && json('get', { N: prNew }).title.startsWith('기능 C x'), String(prNew));
  check('pr_create: 같은 브랜치 쌍은 다시 만들지 못한다', run('pr_create', { ...w, 'title-file': wf('t3.txt', '또\n'), 'body-file': wf('b4.txt', '또\n'), 'from-owner': 'dev1', 'from-project': PROJECT, 'from-branch': 'topic-c', 'to-branch': 'master' }, { fail: true }) === null);
  run('pr_edit_labels', { N: prNew, label: 'needs triage' });
  check('pr_edit_labels', JSON.stringify(await admin.api('GET', `${API}/pullrequests/${prNew}`)).includes('needs triage'));
  const prof = json('get_profile', { login: 'admin' });
  check('get_profile: 프로젝트 구성원', prof && prof.exists === true && typeof prof.display_name === 'string' && prof.linked_accounts.length === 0 && prof.website === null, JSON.stringify(prof));
  check('get_profile: 구성원이 아닌 계정은 NotApplicable', run('get_profile', { login: 'dev1' }) === '"NotApplicable"' && run('get_profile', { login: 'nobody' }) === '"NotApplicable"');
  const repoToken = token; token = await admin.deviceToken('repo read:org write:org admin:org', 'yona-e2e-org');
  await admin.api('POST', '/orgs', { name: 'acme', description: '회사' });
  check('add_team_member', run('add_team_member', { org: 'acme', login: 'dev1' }) === 'dev1');
  check('add_team_member: 이미 구성원이면 실패', run('add_team_member', { org: 'acme', login: 'dev1' }, { fail: true }) === null);
  token = repoToken;

  // --- 라벨 분류, 본문 필드, 상태 요약 댓글 ---
  const missing = run('labels_missing').split('\n');
  check('labels_missing', !missing.includes('needs triage') && !missing.includes('pr created') && missing.includes('announced') && missing.includes('wontfix'), missing.join(','));
  const nField = Number(run('issue_create', { ...w, 'title-file': wf('t5.txt', '필드 시험\n'), 'body-file': wf('b5.txt', '### 하나\n첫 값\n\n### 둘\n둘째 값\n') }));
  check('body_field_get', run('body_field_get', { N: nField, field: '둘' }) === '둘째 값' && run('body_field_get', { N: nField, field: '없음' }) === '');
  run('body_field_set', { N: nField, field: '하나', 'value-file': wf('v1.txt', '바뀐 값\n여러 줄\n') });
  check('body_field_set: 한 필드만 바뀐다', run('body_field_get', { N: nField, field: '하나' }) === '바뀐 값\n여러 줄' && run('body_field_get', { N: nField, field: '둘' }) === '둘째 값');
  run('body_field_set', { N: nField, field: '셋', 'value-file': wf('v2.txt', '새 필드\n') });
  check('body_field_set: 없는 제목은 끝에 더한다', run('body_field_get', { N: nField, field: '셋' }) === '새 필드' && run('body_field_get', { N: nField, field: '하나' }) === '바뀐 값\n여러 줄');
  check('rollup_find: 없으면 null', run('rollup_find', { N: nField }) === 'null');
  const rid = run('rollup_append', { N: nField, action: 'Import', 'entry-file': wf('e1.md', '**Imported.**\n\n**Next:** triage\n') });
  run('rollup_append', { N: nField, action: 'Sync (pr created)', 'entry-file': wf('e2.md', '**Sync.**\n') });
  const roll = json('rollup_find', { N: nField });
  check('rollup_append: 하나의 댓글에 항목이 쌓인다', roll && String(roll.id) === rid && roll.body.startsWith('<!-- apache-magpie: status-rollup v1 -->\n<details><summary>') && (roll.body.match(/<details><summary>/g) || []).length === 2 && roll.body.includes('\n\n---\n\n') && /· @admin · Import<\/summary>\n\n\*\*Imported/.test(roll.body) && roll.body.indexOf('Import') < roll.body.indexOf('Sync (pr created)'), JSON.stringify(roll));
  const comments = await admin.api('GET', `${API}/issues/${nField}/comments`);
  check('rollup_append: 댓글은 하나뿐', comments.result.filter((c) => c.body.startsWith('<!-- apache-magpie: status-rollup')).length === 1);
  await admin.api('POST', `${API}/issues/${nField}/comments`, { comment: '**Sync 2026-01-01 — old**' });
  const legacy = (await admin.api('GET', `${API}/issues/${nField}/comments`)).result.find((c) => c.body.startsWith('**Sync 2026'));
  run('rollup_delete_legacy', { N: nField, 'comment-id': legacy.id });
  check('rollup_delete_legacy', !(await admin.api('GET', `${API}/issues/${nField}/comments`)).result.some((c) => c.body.startsWith('**Sync 2026')));
  await admin.api('POST', `${API}/issues/${nField}/comments`, { comment: '<!-- apache-magpie: status-rollup v1 -->\n<details><summary>x</summary>\n\ny\n\n</details>' });
  check('rollup_find: 둘이면 멈춘다', run('rollup_find', { N: nField }, { fail: true }) === null);
} catch (e) {
  console.log('오류:', String(e.stack || e).slice(0, 1500)); results.push(['실행 중 오류', false]);
} finally {
  await B.stop();
  const failed = results.filter(([, ok]) => !ok); console.log(`\n${results.length - failed.length}/${results.length} 통과`); if (failed.length) { console.log('실패:', failed.map(([n]) => n).join(', ')); process.exitCode = 1; }
}
