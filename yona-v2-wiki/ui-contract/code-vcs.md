---
title: 화면 동작 계약, 저장소 종류별 차이와 프로토콜 서빙 경로
type: ui-contract
sources:
  - src/main/kotlin/dev/yona/domain/vcs/VcsType.kt
  - src/main/kotlin/dev/yona/web/access/VcsServingGate.kt
  - src/main/kotlin/dev/yona/web/git/GitHttpController.kt
  - src/main/kotlin/dev/yona/web/hg/HgHttpFilter.kt
  - src/main/kotlin/dev/yona/web/svn/*.kt
  - src/main/kotlin/dev/yona/web/lfs/*.kt
  - src/main/kotlin/dev/yona/web/ssh/SshInternalController.kt
  - src/main/kotlin/dev/yona/web/project/RepositoryUrls.kt
  - src/main/kotlin/dev/yona/web/code/CodePageAssembler.kt
updated: 2026-11-11
---

# 저장소 종류별 차이와 프로토콜 서빙 경로

화면 계약은 [code.md](code.md), [pullrequest.md](pullrequest.md)에 있고 여기서는 `project.vcs`(`GIT`, `Subversion`, `Mercurial`)에 따라 화면이 달라지는 규칙과 화면이 가리키는 프로토콜 주소를 정리한다. 근거 표기: `D:`=`src/main/kotlin/dev/yona/domain/`, `K:`=`src/main/kotlin/dev/yona/web/`, `T:`=`src/main/resources/templates/`. 기존 위키: [subsystems/git-http.md](../subsystems/git-http.md), [web/subversion.md](../web/subversion.md).

## 1. 기능 지원표(`D:vcs/VcsType.kt:13-61`)

화면과 컨트롤러는 `vcs == "GIT"` 비교 대신 `VcsCapabilities.supports`, 템플릿은 `projectView.vcs.<기능>`(`VcsSupport`)를 쓴다. 모르는 값이면 아무 기능도 없다(`project.vcs`는 대소문자 구분).

| 기능 | Git | Subversion | Mercurial | 화면 영향 |
|---|---|---|---|---|
| `BRANCHES` | O | - | O | 코드 보기 `Branches` 탭, `/branches` 화면(Subversion은 `400`) |
| `TAGS`, `TAG_MANAGEMENT` | O | - | O | `Tags` 탭, `/tags`, 태그 삭제 단추 |
| `FORK` | O | - | O | `/newFork`, `/fork`, `/clone`(Subversion `400`) |
| `PULL_REQUESTS` | O | - | O | 코드 주고받기 전 화면(Subversion `400`) |
| `DEFAULT_BRANCH` | O | - | - | (화면 단추는 이 기능을 보지 않음, code-F7) |
| `ARCHIVE_DOWNLOAD` | O | - | O | 코드 보기 `내려받기` |
| `ONLINE_EDITING` | O | - | - | `새 파일`, `Edit` 링크(`postform`). 단 `Edit` 링크는 `loggedIn && !binary`만 보고 기능을 보지 않는다 |
| `LINE_COMMENT_THREADS` | O | - | O | 파일 머리의 댓글 수(`reviewThreads.countOnCommit`) |
| `COMMIT_EMAILS` | O | - | O | 작성자를 이메일로 식별 |
| `NUMERIC_REVISIONS` | - | O | - | 파일 리비전 `Revision N`, `git` 플래그가 거짓(이력 헤더 `code.commitDate`, `Branches`, `Tags` 탭 없음) |
| `AUTHOR_LOGIN_IDS` | - | O | - | 작성자를 로그인 ID로 찾음, 이메일 없음 |
| `LFS`, `LFS_SCREEN` | O, O | - | -, O | LFS 포인터 안내(`lfsView`는 `lfs.available(project)`) |
| `DEPLOY_KEYS`, `PROTECTED_REFS` | O | - | O | 설정 화면(다른 영역) |
| `README_POSTING` | O | - | O | 다른 영역 |
| 짧은 커밋 ID 길이 | 7 | (리비전 번호) | 12 | `shortId`(`DiffHtmlRenderer`, 이력 행). 코드 보기의 리비전 표시는 7자로 고정(code-F7) |

## 2. 화면별 차이

| 화면 | Git | Subversion | Mercurial |
|---|---|---|---|
| `/code`(빈 저장소) | `code/nohead`(`git clone`, `git init` 안내, HTTP/SSH 전환) | `code/nohead-svn`(`svn co`, 제목 `title.commitHistory`, SSH 없음) | `code/nohead-hg`(`hg clone`, `hg init`, SSH 전환) |
| 복제 주소 | `<origin>/<소유자>/<이름>`(구성원이면 `로그인ID@`) | `<origin>/svn/<소유자>/<이름>`(로그인 ID 안 넣음, 명령에 `--username`) | Git과 같은 형식. SSH: `ssh://git@호스트[:포트]/<소유자>/<이름>`(scp 형식 없음) |
| SSH 주소 | 포트 22면 `git@호스트:소유자/이름.git`, 아니면 `ssh://git@호스트:포트/소유자/이름.git` | 없음 | 위와 같음 |
| 코드 보기 | 탭 4개, 브랜치와 태그 select(구역 2개), `내려받기`, `새 파일` | 탭 2개(`Files`, `Commits`), select 구역 없음, 파일 리비전 `Revision N`, 열기 주소의 리비전은 번호 | `새 파일` 없음, 나머지 Git과 같음 |
| 이력 | 탭 4개, 날짜 열 `code.authorDate`, 작성자는 이메일 | 탭 2개, `code.commitDate`, 작성자는 로그인 ID | Git과 같음(`git` 플래그 참) |
| 커밋 보기 | `code/diff`(서버가 diff HTML) | `code/svn-diff`(패치 원문을 브라우저가 해석, 미니맵, 댓글 목록) | `code/diff` |
| 비교 | `code/compare` | `code/compare-svn` | `code/compare` |
| 줄 댓글 스레드 | 있음(범위 스레드) | 없음(줄 단위 커밋 코멘트: `path`, `line`, `side`) | 있음 |
| 브랜치, 태그 화면 | 있음 | `400` | 있음 |
| 포크, PR | 있음 | `400` | 있음(병합 방식 포함 시험 `HgMergeMethodWebTest`) |

## 3. 화면이 가리키는 프로토콜 주소

화면의 복제 주소 안내 텍스트가 가리키는 경로이다. 인증과 권한은 `K:access/VcsServingGate.kt`가 종류 공통으로 처리한다: HTTP Basic(`로그인ID:개인 API 토큰`, 비밀번호는 거부), 형식 오류 `400`, 연속 실패 차단 `429`, 토큰 범위 부족 `403`, 익명 `401`(`WWW-Authenticate`), 로그인 사용자 권한 부족 `403`(안내 문구). 복제와 가져오기는 읽기 권한, 푸시는 수정 권한, 코드 구성원 제한 프로젝트는 읽기에도 구성원이 필요하다.

| 종류 | 요청 | 구현 | 비고 |
|---|---|---|---|
| Git smart HTTP | `GET /:owner/:project/info/refs?service=git-upload-pack\|git-receive-pack`, `POST /:owner/:project/git-upload-pack`, `.../git-receive-pack` | `K:git/GitHttpController.kt:38-147` | `service` 없음 `403 Unsupported service: getanyfile`. 프로젝트가 `GIT`이 아니면 `404`. 같은 이름의 프로젝트가 없고 이름이 `.wiki.git`/`.wiki`로 끝나면 위키 저장소(항상 git) |
| Mercurial wire v1 | `GET/POST /:owner/:project?cmd=...` | `K:hg/HgHttpFilter.kt`(필터, 서블릿 라우트가 아님) | `cmd`가 없는 요청, Git과 Subversion 프로젝트는 건드리지 않고 넘김. 프로젝트 홈 `GET /:owner/:project`와 같은 경로 |
| Subversion WebDAV | `/svn/...`, WebDAV 메서드(`PROPFIND`, `MKACTIVITY` 등) | `K:svn/SvnDavFilter.kt` + `SvnDavService.kt` | 사이트 비밀 키를 정하기 전에는 가로채지 않음(`SvnSecretGateController`가 기본 관리자 입력 화면). `POST /!svn-fake/sevice/`는 v1.6 역방향 주소용 가짜 경로로 항상 본문 없는 `404`(`SvnFakeServiceController`) |
| Git LFS | `POST /:owner/:project[.git]/info/lfs/objects/batch`, `PUT\|GET .../info/lfs/objects/:oid`, `POST .../info/lfs/verify`, `.../info/lfs/locks[/verify\|/:id/unlock]`, `.../info/lfs/tus/:oid`(`POST`, `HEAD`, `PATCH`) | `K:lfs/LfsController.kt`, `LfsLockController.kt`, `LfsTusController.kt` | 응답 `application/vnd.git-lfs+json`, 오류 `{"message":...}`. 공개 프로젝트는 익명도 내려받기 가능. `tus`는 올리기에만 |
| Mercurial LFS | `/:owner/:project/.git/info/lfs/...`, `/.hg/lfs/objects/...` | `K:hg/HgHttpFilter.kt` | 필터가 처리 |
| 브라우저에서 LFS 내려받기 | `GET /:user/:project/lfs/:oid?name=` | `K:lfs/LfsDownloadController.kt:26-38` | 코드 보기의 LFS 안내 링크 대상. `@CodeAccessCheck`, 웹 세션, 포크는 원본 객체를 읽기로 빌림 |
| SSH 인증 훅 | `POST /internal/ssh/authenticate`(`X-Yona-Internal-Secret` 헤더, 본문 `{"publicKey":"종류 본문"}`) | `K:ssh/SshInternalController.kt:35-60` | 시스템 sshd의 `AuthorizedKeysCommand`용. 루프백 주소와 비밀 값이 모두 맞을 때만(아니면 `403`), 알 수 없는 키나 잠긴 사용자 `404`, 키 형식 오류 `400`, 성공 `{"principal": <표>}`. `yona.ssh.relay.enabled=true`일 때만 컨트롤러가 생김 |

화면과의 연결: nohead 화면의 `HTTP/SSH` 전환은 `sshCloneUrl != null`일 때만(내장 SSH 서버 또는 시스템 sshd 연동을 켠 사이트의 Git, Mercurial) 나온다. 전환 단추는 `.clone-url` 글자를 바꾸는 순수 클라이언트 동작이고 서버 요청이 없다(`T:code/nohead.html:52-63`).

## 4. 발견 사항

[code.md](code.md)의 code-F6(NoheadViews 문서 불일치), code-F7(Mercurial 짧은 ID와 기본 브랜치 단추)이 이 문서의 표와 관련 있다. 추가 확인: 코드 보기의 `Edit`, `새 파일` 링크는 로그인 여부만 보는 화면 조건이고 서버 검사는 다른 영역(`postform`)에 있어 이 조사에서는 따라가지 않았다(`ONLINE_EDITING`을 지원하는 종류는 Git뿐인데 `Edit` 링크 조건은 기능 지원을 보지 않는다. 이 불일치가 실제로 Mercurial 파일 보기에서 링크를 내는지는 `CodePageAssembler.kt:128`의 조건 `loggedIn && !binary`로 확인했고, 링크 대상의 서버 응답은 확인하지 못했다).
