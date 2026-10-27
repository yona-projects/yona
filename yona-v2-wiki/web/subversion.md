---
title: Subversion
type: web
sources: [app/playRepository/SVNRepository.java, app/playRepository/SvnCommit.java, app/controllers/SvnApp.java, app/controllers/CodeApp.java, app/controllers/CodeHistoryApp.java, app/controllers/CompareApp.java, app/views/code/nohead_svn.scala.html, app/views/code/svnDiff.scala.html, app/views/code/compare_svn.scala.html, app/utils/PlayServletRequest.java, app/utils/PlayServletResponse.java, app/models/CommitComment.java, app/models/Project.java:350-395]
updated: 2026-10-25
---

# Subversion

프로젝트의 저장소 종류가 `Subversion`이면 SVNKit(1.10.11)으로 저장소를 만들고 읽는다. 결정은 D-13(SVNKit), D-26(WebDAV), A-98(구현 판단), 결함은 R-62이다.

## 저장소 (`repository/svn/SvnRepositories`)
위치는 `<데이터 디렉터리>/repo/svn/<소유자>/<이름>`이다. 만들기, 지우기, 옮기기(이름 바꾸기, 소유자 바꾸기, 조직 이름 바꾸기)는 `RepositoryService`가 VCS에 따라 Git과 나눈다. 브랜치는 `HEAD` 하나이고 기본 브랜치도 `HEAD`이며 바꿀 수 없다. 열기, 복제(포크), 가져오기는 Git만 된다. 저장소 종류 바꾸기(Git과 Subversion 사이)는 기존 저장소를 지우고 새로 만든다.

## 코드 브라우저
`CodeBrowserService`가 Subversion이면 `SvnCodeBrowser`로 넘긴다. 폴더는 `{type: folder, data: {이름: {type, msg, author, avatar, userName, userLoginId, createdDate, commitMessage, commiter, commitDate, commitId(리비전 번호), commitUrl, size}}}`, 파일은 `{type: file, revisionNo, author, …, size, isBinary, mimeType, data}`이다. 중간 폴더를 건너뛰지 않는다. 파일 보기는 리비전을 `Revision N`으로 보이고 원본과 열기 주소에 리비전 번호를 쓴다. 빈 저장소는 `svn co`로 시작하는 안내 화면(`code/nohead-svn`)이다. README는 `README.md`, `readme.md`, `/trunk/README.md`, `/trunk/readme.md` 순으로 찾는다.

## 이력, 커밋 보기, 비교, 코멘트
- 이력(`commits`): `SvnRepositories.history`가 최신부터 쪽 단위로 모은다. 작성자는 로그인 ID로 사용자를 찾는다. 브랜치 탭이 없고 날짜 열은 "커밋 날짜"이다.
- 커밋 보기(`commit/{리비전}`): 템플릿 `code/svn-diff`. 패치는 `#commit`에 담아 `code.SvnDiff` 스크립트가 그리고, 커밋 코멘트 목록, 구독 단추, 코멘트 입력란이 있다.
- 비교(`compare/{A}..{B}`): 템플릿 `code/compare-svn`. 변경이 없으면 안내한다.
- 커밋 코멘트: `POST commit/{리비전}/comments`(내용 필수 `400`, 쓸 권한 `403`, 없는 리비전 `404`)는 `CommitCommentService`가 만들고 작성자의 임시 첨부를 옮기며 알림(`NEW_COMMENT`, 수신자: 커밋 작성자, 이전 코멘트 작성자, 프로젝트와 커밋 구독자, 멘션된 사용자)을 낸다. `DELETE commit/{리비전}/comments/{번호}/delete`는 지울 권한이 있어야 하고 커밋 보기로 돌아간다. 프로젝트 구성원은 남의 코멘트도 지울 수 있다.
- 멘션 후보(`mentionListAtCommitDiff`)는 코멘트 작성자와 커밋 작성자를 모아 뒤집은 순서(최근 코멘트 작성자, 커밋 작성자), 구성원 순이다(v1.6 `addCodeCommenters`의 `reverse`).

## HTTP(WebDAV) 접근 (`web/svn/SvnDavService`, `SvnDavFilter`, `JavaxServletBridge`)
`/svn/소유자/프로젝트/…`로 `svn checkout`, `svn commit` 같은 클라이언트가 접근한다.
- 구성: Maven Central의 `svnkit-dav:1.10.11`은 war이므로 빌드 때 `WEB-INF/classes`를 꺼내 jar(`svnDavClasses` 작업)로 묶어 쓴다. DAV 서블릿은 `javax.servlet`을 쓰므로 `JavaxServletBridge`가 Jakarta 요청과 응답을 javax API의 동적 프록시로 바꿔 준다(DAV 서블릿이 호출하는 메서드만 연결).
- 가로채기: 디스패처 서블릿은 WebDAV 메서드를 `501`로 거부하므로 필터가 `/svn/` 아래 모든 요청을 처리한다. 보안 방화벽은 WebDAV 메서드를 허용하도록 설정했다.
- 순서: Basic 자격 해석(형식 오류 `400`) → 주소를 `svn`, 소유자, 나머지로 가름(짧으면 `403`) → 프로젝트 찾기(없거나 Subversion이 아니면 `404`) → 사용자 확인(Basic 자격, 없으면 세션과 토큰) → 권한(`OPTIONS`, `PROPFIND`, `GET`, `REPORT`은 읽기, 그 밖에는 수정) → 권한이 없으면 익명은 `401`(`WWW-Authenticate: Basic realm="Yobi"`, 본문 `Invalid username or password`), 인증한 사용자는 `403`(`You have no permission to access this repository.`) → `DAVServlet`이 `<저장소 디렉터리>/<소유자>/`를 부모 경로로 처리 → 인증한 사용자의 최근 방문에 기록.
- 검증: 실제 SVNKit HTTP 클라이언트로 읽기(익명), 커밋(구성원), 거부(익명, 비구성원, 틀린 비밀번호), 비공개 프로젝트, 주소 오류를 확인한다(`SvnDavTest`).

## 공식 v1.16.0과 남은 차이
- `/svn/…`의 안내 화면 쪽 `Powered by SVN/1.9.0 SVNKit/1.9.2 …` 문구와 오류 본문의 스택 줄은 라이브러리 버전(1.10.11, A-98)을 따르므로 다르다.
- `Content-Type`의 문자 집합 표기(`text/html;charset="utf-8"` 대 `charset=utf-8`, 본문 `text/plain`의 `charset` 유무)는 서블릿 컨테이너가 다시 쓰는 부분이라 다르다.
- 없는 리비전, 없는 경로, 알 수 없는 리비전 문자열은 v1.6이 500이고 v2는 404 또는 200이다(R-118과 같은 종류).

