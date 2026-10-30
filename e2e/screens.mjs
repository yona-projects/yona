// 전 화면 목록(로드맵 X-38a, decisions.md D-53). v1.6 경로 표(src/test/resources/v16/routes.tsv)의 화면 경로와 v2에서 더한 화면을 고정 데이터(.state/fixture.json)에 맞춰 나열한다.
// 항목: id(고유 이름), path, roles(어느 사용자로 볼지), expect(기대 상태 코드, 기본 200. 리다이렉트는 따라간 뒤의 상태), note(왜 이 화면인지, 어떤 상태인지).
// 목록에 없는 화면: JSON이나 파일 내려받기 경로, 메일 인증 링크처럼 비밀 값이 필요한 경로(감사 목록은 architecture/bs-migration-harness.md에 있다).

export function screens(fx) {
  const { owner, name } = fx.project;
  const P = `/${owner}/${name}`;
  const [i1, i2, i3] = fx.issues, [p1] = fx.posts;
  const S = [];
  const add = (id, path, opts = {}) => S.push({ id, path, roles: ['admin'], expect: 200, ...opts });

  // --- 첫 화면과 계정 ---
  add('home', '/', { roles: ['anon', 'admin', 'alice'], note: '처음 화면(익명, 로그인)' });
  add('login', '/users/loginform', { roles: ['anon'] });
  add('signup', '/users/signupform', { roles: ['anon'] });
  add('lost-password', '/lostPassword', { roles: ['anon'] });
  add('restricted', '/restricted', { roles: ['anon'], expect: 200 });
  add('help-toc', '/_help', { roles: ['anon'] });
  add('help-uikit', '/_UIKit', { roles: ['anon'], note: 'UI 키트: 컴포넌트 모음이라 전환 점검에 가장 중요하다' });
  add('error-404', '/nonexistent-page-xyz', { roles: ['anon'], expect: 404 });
  add('error-403', `/${fx.project.private.owner}/${fx.project.private.name}`, { roles: ['bob'], expect: 403, note: '비공개 프로젝트에 구성원이 아닌 사용자' });
  add('projects', '/projects', { roles: ['anon', 'admin'], note: '프로젝트 목록' });
  add('orgs', '/orgs', { roles: ['admin'], note: '조직 목록' });
  add('search-project', '/search?keyword=web&searchType=project', { note: '프로젝트 검색' });
  add('search-issue', '/search?keyword=로그인&searchType=issue', { note: '이슈 검색' });
  add('search-all', '/search?keyword=web&searchType=auto', { note: '통합 검색' });
  add('notifications', '/notifications');
  add('user-profile', `/${fx.users.admin}`, { roles: ['admin', 'alice'], note: '사용자 페이지' });
  add('user-sidebar', '/user/sidebar?path=/&hash=', { note: '왼쪽 사이드바 레이아웃' });
  add('user-files', '/user/files');
  add('my-issues', '/user/issues', { roles: ['admin', 'alice'] });
  add('my-issues-created', '/user/issues?state=open&filter=created');
  add('my-issue-new', '/user/issues/new');
  add('my-issue-new-mine', '/user/issues/new/mine');
  add('settings-profile', '/user/editform');
  add('settings-password', '/user/editform/password');
  add('settings-emails', '/user/editform/emails');
  add('settings-notifications', '/user/editform/notifications');
  add('settings-token', '/user/editform/token', { note: 'X-5 토큰 목록' });
  add('settings-token-new', '/user/editform/token/new');
  add('device', '/users/device', { note: 'X-37i 기기 인증 코드 입력' });
  add('device-consent', '/users/device?user_code=BCDF-GHJK', { expect: 400, note: '없는 코드: 오류 상태' });

  // --- 조직 ---
  add('org-new', '/organizations/new');
  add('org-home', '/organizations/acme', { roles: ['admin', 'alice', 'bob'] });
  add('org-issues', '/organizations/acme/issues');
  add('org-boards', '/organizations/acme/boards');
  add('org-pulls', '/organizations/acme/pullrequests');
  add('org-pulls-closed', '/organizations/acme/closedPullrequests');
  add('org-settings', '/organizations/acme/settingform');
  add('org-delete', '/organizations/acme/deleteForm');
  add('org-members', '/organizations/acme/members');
  add('org-search', '/organizations/acme/search?keyword=web&searchType=auto');

  // --- 프로젝트 ---
  add('project-new', '/projectform');
  add('project-home', P, { roles: ['anon', 'admin', 'alice', 'bob'], note: '프로젝트 홈: 역할마다 보이는 것이 다르다' });
  add('project-home-private', `/${fx.project.private.owner}/${fx.project.private.name}`, { note: '비공개 프로젝트(빈 저장소)' });
  add('project-settings', `${P}/settingform`);
  add('project-members', `${P}/members`);
  add('project-webhooks', `${P}/webhooks`);
  add('project-watchers', `${P}/watchers`);
  add('project-transfer', `${P}/transfer`);
  add('project-delete', `${P}/deleteform`);
  add('project-labels', `${P}/labels`);
  add('project-statistics', `${P}/statistics`);
  add('project-search', `${P}/search?keyword=로그인&searchType=auto`);
  add('project-rulesets', `${P}/setting/rulesets`, { note: 'X-34 규칙 묶음 목록' });
  add('project-ruleset-new', `${P}/setting/rulesets/new`, { note: 'X-34 규칙 묶음 만들기' });
  add('project-fork-new', `${P}/newFork`, { roles: ['alice'], note: '포크 만들기 화면' });

  // --- 코드 ---
  add('code-root', `${P}/code`);
  add('code-tree', `${P}/code/master/!/src`, { note: '디렉터리' });
  add('code-file', `${P}/code/master/src/main.js`, { note: '소스 파일' });
  add('code-markdown', `${P}/code/master/docs/guide.md`, { note: '마크다운 파일' });
  add('code-image', `${P}/code/master/logo.png`, { note: '이미지 파일' });
  add('code-branch', `${P}/code/feature-a`);
  add('commits', `${P}/commits`);
  add('commits-path', `${P}/commits/master/src`);
  add('commit', `${P}/commit/${fx.featureACommit}`, { note: '커밋 보기(diff)' });
  add('compare', `${P}/compare/master..feature-a`, { knownErrors: ["reading 'top'"], note: 'v1.6 코드(yobi.code.Diff.js)의 알려진 JS 오류: 리뷰 목록이 없는 화면에서 높이를 계산한다' });
  add('branches', `${P}/branches`);
  add('tags', `${P}/tags`, { note: 'X-18 태그' });
  add('svn-home', `/${fx.project.svn.owner}/${fx.project.svn.name}`, { note: 'Subversion 프로젝트' });
  add('svn-code', `/${fx.project.svn.owner}/${fx.project.svn.name}/code`);

  // --- 이슈, 마일스톤 ---
  add('issues', `${P}/issues`, { roles: ['admin', 'bob'] });
  add('issues-closed', `${P}/issues?state=closed`);
  add('issues-filtered', `${P}/issues?state=open&milestoneId=1`);
  add('issue-new', `${P}/issueform`);
  add('issue-view', `${P}/issue/${i1}`, { roles: ['admin', 'alice', 'bob'], note: '라벨, 담당자, 댓글, 할 일 목록, 멘션이 있는 이슈' });
  add('issue-view-plain', `${P}/issue/${i2}`, { note: '댓글 없는 이슈' });
  add('issue-view-closed', `${P}/issue/${i3}`, { note: '닫힌 이슈' });
  add('issue-edit', `${P}/issue/${i1}/editform`);
  add('issue-labels-form', `${P}/issue/labelsform`);
  add('milestones', `${P}/milestones`);
  add('milestone-new', `${P}/newMilestoneForm`);
  add('milestone-view', `${P}/milestone/1`);
  add('milestone-edit', `${P}/milestone/1/editform`);

  // --- 게시판 ---
  add('posts', `${P}/posts`);
  add('post-new', `${P}/postform`);
  add('post-view', `${P}/post/${p1}`, { note: '댓글이 있는 게시글' });
  add('post-edit', `${P}/post/${p1}/editform`);

  // --- 풀 리퀘스트와 리뷰 ---
  add('pulls', `${P}/pullRequests`);
  add('pulls-closed', `${P}/closedPullRequests`);
  add('pulls-sent', `${P}/sentPullRequests`);
  add('pull-new', `${P}/newPullRequestForm`, { roles: ['admin'] });
  add('pull-open', `${P}/pullRequest/${fx.prOpen}`, { roles: ['admin', 'alice'], note: '열림: 리뷰 요청, 변경 요청 판정, 댓글' });
  add('pull-merged', `${P}/pullRequest/${fx.prMerged}`, { note: '병합됨' });
  add('pull-closed', `${P}/pullRequest/${fx.prConflict}`, { note: '닫힘' });
  add('pull-changes', `${P}/pullRequest/${fx.prOpen}/changes`, { note: '변경 보기(diff, 줄 댓글)' });
  add('pull-edit', `${P}/pullRequest/${fx.prOpen}/editform`);
  add('reviews', `${P}/reviews`, { note: '리뷰 스레드 목록' });

  // --- 사이트 관리 ---
  add('site-users', '/sites/userList');
  add('site-projects', '/sites/projectList');
  add('site-posts', '/sites/postList');
  add('site-issues', '/sites/issueList');
  add('site-mail', '/sites/mail');
  add('site-massmail', '/sites/massmail');
  add('site-update', '/sites/update');
  add('site-diagnostic', '/sites/diagnostic');
  add('site-data', '/sites/data');
  add('import', '/_import', { note: '프로젝트 가져오기 화면' });
  add('migration', '/migration', { expect: 403, note: '이주 화면: 이 서버의 설정으로는 접근할 수 없다(403이 정상)' });
  return S;
}
