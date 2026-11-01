// 확인 도구의 고정 데이터를 심는다(로드맵 X-38a). 서버를 띄우고(없으면 만들고) 사용자, 조직, 프로젝트(Git, Subversion),
// 코드, 이슈, 게시글, 풀 리퀘스트, 리뷰, 알림을 만든다. 결과의 번호와 이름은 .state/fixture.json에 남아 화면 목록(screens.json)이 쓴다.
// 이미 심었으면(.state/fixture.json이 있으면) 건너뛴다. 다시 심으려면 .state를 지운다.
import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { ADMIN, ROOT, startServer } from './lib/server.mjs';
import { Session } from './lib/http.mjs';

const FIXTURE = join(ROOT, '.state', 'fixture.json');
const PORT = Number(process.env.E2E_PORT || 19191);
const ALL_SCOPES = 'repo read:org write:org admin:org read:repo_hook write:repo_hook admin:repo_hook site_admin';
const git = (cwd, ...args) => execFileSync('git', ['-c', 'user.email=seed@example.com', '-c', 'user.name=Seed', '-c', 'init.defaultBranch=master', ...args], { cwd, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim();
const PNG = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DwHwAFAAH/q842iQAAAABJRU5ErkJggg==', 'base64');

export async function seed({ port = PORT } = {}) {
  if (existsSync(FIXTURE)) return JSON.parse(readFileSync(FIXTURE, 'utf8'));
  mkdirSync(join(ROOT, '.state'), { recursive: true });
  const server = await startServer({ port });
  try {
    const f = await seedData(server.base);
    writeFileSync(FIXTURE, JSON.stringify(f, null, 2));
    return f;
  } finally { await server.stop(); }
}

async function seedData(base) {
  const fx = { base, users: { admin: ADMIN.loginId, alice: 'alice', bob: 'bob' }, password: ADMIN.password };
  // 사용자: alice(조직과 프로젝트 구성원), bob(아무 데도 속하지 않음)
  for (const [id, name] of [['alice', 'Alice Kim'], ['bob', 'Bob Lee']]) await new Session(base).signup(id, name, `${id}@example.com`);
  const admin = new Session(base); await admin.login(ADMIN.loginId); await admin.deviceToken(ALL_SCOPES);
  const alice = new Session(base); await alice.login('alice'); await alice.deviceToken();
  const ok409 = { ok: [200, 201, 204, 409] };

  // 조직과 프로젝트
  await admin.api('POST', '/orgs', { name: 'acme', description: '확인 도구용 조직' }, ok409);
  await admin.api('POST', '/orgs/acme/members', { loginId: 'alice', role: 'member' }, ok409);
  await admin.api('POST', '/orgs/acme/projects', { name: 'web', description: '공개 Git 프로젝트', scope: 'PUBLIC', vcs: 'GIT' }, ok409);
  await admin.api('POST', '/user/projects', { name: 'secret', description: '비공개 Git 프로젝트', scope: 'PRIVATE', vcs: 'GIT' }, ok409);
  await admin.api('POST', '/user/projects', { name: 'legacy', description: 'Subversion 프로젝트', scope: 'PUBLIC', vcs: 'Subversion' }, ok409);
  await admin.api('POST', '/owners/acme/projects/web/members', { loginId: 'alice', role: 'member' }, ok409);
  fx.project = { owner: 'acme', name: 'web', private: { owner: 'admin', name: 'secret' }, svn: { owner: 'admin', name: 'legacy' } };
  const P = '/owners/acme/projects/web';

  // 코드: master, feature-a(열린 PR), feature-b(병합될 PR), feature-c(충돌), 태그
  const work = mkdtempSync(join(tmpdir(), 'yona-seed-'));
  const url = `http://admin:${admin.token}@${base.replace('http://', '')}/acme/web`;
  git(work, 'init', '-q'); git(work, 'remote', 'add', 'origin', url);
  const write = (name, text) => { mkdirSync(join(work, name.split('/').slice(0, -1).join('/') || '.'), { recursive: true }); writeFileSync(join(work, name), text); };
  write('README.md', '# web\n\n확인 도구용 프로젝트이다.\n\n- [ ] 할 일 하나\n- [x] 한 일 하나\n');
  write('src/main.js', 'function greet(name) {\n  return "hello " + name;\n}\nmodule.exports = { greet };\n');
  write('src/util.js', 'exports.sum = (a, b) => a + b;\n');
  writeFileSync(join(work, 'logo.png'), PNG);
  git(work, 'add', '.'); git(work, 'commit', '-qm', '첫 커밋'); fx.firstCommit = git(work, 'rev-parse', 'HEAD');
  write('docs/guide.md', '# 안내\n\n```js\nconst a = 1;\n```\n\n| 이름 | 값 |\n|---|---|\n| a | 1 |\n');
  git(work, 'add', '.'); git(work, 'commit', '-qm', '문서 추가');
  git(work, 'push', '-q', 'origin', 'HEAD:master');
  const branch = (name, file, text, msg) => { git(work, 'checkout', '-q', '-B', name, 'master'); write(file, text); git(work, 'add', '.'); git(work, 'commit', '-qm', msg); git(work, 'push', '-q', '-f', 'origin', `${name}:${name}`); };
  branch('feature-a', 'src/main.js', 'function greet(name) {\n  return "hi " + name + "!";\n}\nfunction bye(name) {\n  return "bye " + name;\n}\nmodule.exports = { greet, bye };\n', '인사 바꾸기');
  fx.featureACommit = git(work, 'rev-parse', 'HEAD');
  branch('feature-b', 'src/extra.js', 'exports.extra = () => 42;\n', '추가 기능');
  branch('feature-c', 'src/util.js', 'exports.sum = (a, b) => a - b;\n', '충돌 만들기');
  git(work, 'checkout', '-q', 'master'); write('src/util.js', 'exports.sum = (a, b) => a * b;\n'); git(work, 'add', '.'); git(work, 'commit', '-qm', '마스터 쪽 변경'); git(work, 'push', '-q', 'origin', 'master');
  fx.headCommit = git(work, 'rev-parse', 'HEAD');
  await admin.api('POST', `${P}/tags`, { name: 'v1.0.0', target: 'master', message: '첫 배포' }, ok409);
  await admin.api('POST', `${P}/tags`, { name: 'v0.9', target: 'master' }, ok409);

  await seedWiki(admin, P, fx);

  // 라벨, 마일스톤
  await admin.api('POST', `${P}/labels`, { labels: [{ labelName: 'bug', labelColor: '#d73a4a', category: 'type' }, { labelName: 'feature', labelColor: '#0e8a16', category: 'type' }, { labelName: 'high', labelColor: '#b60205', category: 'priority' }] }, ok409);
  const labels = await admin.api('GET', `${P}/labels`); fx.labels = Object.fromEntries((labels.labels || []).map((l) => [l.labelName, l.id]));
  await admin.api('POST', `${P}/milestones`, { milestones: [{ title: '1.0 릴리스', description: '첫 배포', due_on: '2027-01-31', state: 'open' }, { title: '0.9 베타', description: '지난 마일스톤', due_on: '2026-06-30', state: 'closed' }] }, ok409);

  // 이슈: 열림(라벨, 담당자, 마일스톤, 멘션, 할 일 목록), 닫힘, 댓글
  const issues = [
    { title: '로그인 버튼이 눌리지 않는다', body: '재현 방법:\n\n1. 로그인 화면\n2. 버튼 클릭\n\n- [ ] 원인 확인\n- [x] 로그 수집\n\n@alice 확인 부탁합니다.', labels: [{ labelName: 'bug', labelColor: '#d73a4a', category: 'type' }], assignees: [{ email: 'alice@example.com' }], milestoneTitle: '1.0 릴리스' },
    { title: '다크 모드 지원', body: '**다크 모드**를 지원하자.\n\n`prefers-color-scheme`을 쓴다.', labels: [{ labelName: 'feature', labelColor: '#0e8a16', category: 'type' }, { labelName: 'high', labelColor: '#b60205', category: 'priority' }] },
    { title: '오래된 문제', body: '이미 해결했다.', state: 'closed' },
    { title: '긴 제목의 이슈입니다. 목록에서 제목이 어떻게 줄바꿈되고 잘리는지 확인하기 위한 아주 긴 제목이다 이슈 이슈 이슈 이슈', body: '' }
  ];
  const made = await admin.api('POST', `${P}/issues`, { issues });
  fx.issues = (made || []).map((m) => Number(String(m.location).split('/').pop()));
  const n = fx.issues[0];
  await alice.api('POST', `${P}/issues/${n}/comments`, { comment: '확인했습니다. **곧 고치겠습니다.**\n\n```js\nconsole.log(1)\n```' });
  await admin.api('POST', `${P}/issues/${n}/comments`, { comment: '감사합니다.' });

  // 게시판
  const posts = await admin.api('POST', `${P}/posts`, { posts: [{ title: '공지: 서버 점검', body: '이번 주 토요일 서버를 점검합니다.' }, { title: '자유 게시글', body: '자유롭게 이야기합니다.' }] });
  fx.posts = (posts || []).map((m) => Number(String(m.location).split('/').pop()));
  await alice.api('POST', `${P}/posts/${fx.posts[0]}/comments`, { body: '알겠습니다.' });

  // 풀 리퀘스트: 열림(리뷰, 줄 댓글), 병합, 닫힘, 충돌
  const pr = (title, branch, body = '') => admin.api('POST', `${P}/pullrequests`, { title, body, fromProject: { owner: 'acme', name: 'web' }, fromBranch: branch, toBranch: 'master' });
  const open = await pr('인사 바꾸기', 'feature-a', '인사말을 바꾸고 `bye`를 더했다.');
  fx.prOpen = open.number;
  const merged = await pr('추가 기능', 'feature-b'); fx.prMerged = merged.number;
  await admin.api('POST', `${P}/pullrequests/${fx.prMerged}/merge`, { method: 'merge' }, { ok: [200, 409] });
  const conflict = await pr('충돌 확인', 'feature-c'); fx.prConflict = conflict.number;
  await admin.api('POST', `${P}/pullrequests/${fx.prConflict}/close`, undefined, { ok: [200, 409] });
  await admin.api('POST', `${P}/pullrequests/${fx.prOpen}/requestedreviewers`, { reviewer: 'alice' }, { ok: [200, 201, 400, 409] });
  await alice.api('POST', `${P}/pullrequests/${fx.prOpen}/comments`, { body: '`bye`의 이름을 바꾸면 어떨까요?', path: 'src/main.js', commitId: fx.featureACommit, endSide: 'B', endLine: 5, startSide: 'B', startLine: 4 }, { ok: [200, 201, 400] });
  await alice.api('POST', `${P}/pullrequests/${fx.prOpen}/reviews`, { verdict: 'CHANGES_REQUESTED', body: '수정이 필요합니다.' });
  await alice.api('POST', `${P}/pullrequests/${fx.prOpen}/comments`, { body: '전체 의견입니다.' });

  // 웹훅, 구성원은 이미 있음
  await admin.api('POST', `${P}/hooks`, { payloadUrl: 'http://localhost:1/hook', secret: 'secret', webhookType: 'SIMPLE', gitPush: true }, { ok: [200, 201, 400, 409] });
  fx.generatedAt = new Date().toISOString();
  return fx;
}

if (process.argv[1] && process.argv[1].endsWith('seed.mjs')) {
  seed().then((f) => console.log('고정 데이터 준비됨:', JSON.stringify({ issues: f.issues, posts: f.posts, prOpen: f.prOpen, prMerged: f.prMerged })), (e) => { console.error(e); process.exit(1); });
}

/** 위키(X-17) 고정 데이터를 심는다: 홈, 안내 페이지 두 개, 사이드바, 푸터, 홈의 수정 이력(비교 화면용). 이미 있으면(409) 건너뛴다. */
export async function seedWiki(admin, P, fx) {
  const ok409 = { ok: [200, 201, 204, 409] };
  // 위키는 새 프로젝트만 켜져 있다. 예전에 만든 고정 데이터도 쓰도록 켠다(D-61).
  await admin.api('PATCH', P, { menus: { wiki: true } });
  const wiki = async (title, content, message) => admin.api('POST', `${P}/wiki/pages`, { title, content, message }, ok409);
  await wiki('Home', '# 프로젝트 위키\n\n이 프로젝트의 **안내 문서**입니다.\n\n- [설치 방법](Install-Guide)\n- [[자주 묻는 질문|FAQ]]\n- [[아직 없는 페이지]]\n\n![로고](uploads/logo.png)\n', '홈 만들기');
  await wiki('Install Guide', '# 설치 방법\n\n```sh\ngit clone https://example.com/web.git\n```\n\n| 단계 | 설명 |\n|---|---|\n| 1 | 내려받기 |\n| 2 | 실행 |\n', '설치 안내');
  await wiki('FAQ', '## 자주 묻는 질문\n\n**Q.** 위키는 어디에 저장되나요?\n\n**A.** 프로젝트마다 별도의 git 저장소에 저장됩니다.\n', 'FAQ 추가');
  await wiki('_Sidebar', '**바로 가기**\n\n- [홈](Home)\n- [FAQ](FAQ)\n', '사이드바');
  await wiki('_Footer', '문의는 이슈로 남겨 주세요.', '푸터');
  const PNG = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DwHwAFAAH/q842iQAAAABJRU5ErkJggg==', 'base64');
  await admin.apiUpload(`${P}/wiki/files`, 'logo.png', PNG);
  await admin.api('PATCH', `${P}/wiki/pages/Home`, { content: '# 프로젝트 위키\n\n이 프로젝트의 **안내 문서**입니다. 최근에 고쳤습니다.\n\n- [설치 방법](Install-Guide)\n- [[자주 묻는 질문|FAQ]]\n- [[아직 없는 페이지]]\n- [변경 이력](Home/_history)\n\n![로고](uploads/logo.png)\n', message: '홈 고치기' });
  fx.wikiRevisions = ((await admin.api('GET', `${P}/wiki/pages/Home/history`)).history || []).map((c) => c.id);
}
