---
title: v1.6과 남은 차이 (상세)
type: architecture
sources: [tools/v16-diff/, yona-diff-v2/report-*.md, decisions.md, refactoring-backlog.md]
updated: 2026-10-28
---

# v1.6과 남은 차이 (상세)

공식 v1.16.0 릴리스(JDK 8, H2)와 v2(JDK 21, H2)에 같은 요청을 보내 응답을 대조한 뒤에도 남은 차이를 모두 적는다.
시나리오와 도구는 [공식 v1.16.0 실행 비교 시험](v16-release-differential-test.md)이 설명한다. 이 문서는 "무엇이 아직 다른가, 왜 다른가, 고칠 수 있는가"에 답한다.
건수는 상태 코드가 다른 요청(C, D 합 169)과 본문이나 머리글만 다른 요청(A, B, F 합 320)을 합친 489건(시나리오 20개의 `다름` 합계)에서 나온 것이다. 숫자는 2026-10-28 마지막 회귀 실행(`regress.sh`, 이어서 `clients.py`, `clients2.py`, 메일과 IMAP 대조) 기준이다.

## 1. 분류와 규모

| 분류 | 뜻 | 요청 건수 | 상태 |
|---|---|---|---|
| **A. 고칠 수 없음** | 라이브러리 버전, 난수, 환경 설정, 서버 내부 번호 때문에 응답이 다를 수밖에 없다 | A, B, F를 합쳐 320 | 닫음 |
| **B. 의도해서 둠** | 근거를 `decisions.md`나 `refactoring-backlog.md`에 적고 둔 차이 | (위에 포함) | 닫음(승인 대기 포함) |
| **C. v1.6 오류를 v2가 정상 처리** | v1.6이 `500`/`400`을 내는 입력을 v2는 `404`/`400`/`200`/`303`으로 처리 | 154 | **미결: 재현 여부 결정 필요** |
| **D. 권한·동작 차이** | v1.6과 결과가 다른 권한 판정과 동작 | 15 → 0 | **해결(A-173)** |
| **E. 메일 수신(IMAP)** | 메일로 만든 글의 결함 | 시험 30통 중 6종 | 일부 미결 |
| **F. 도구가 만든 가짜 차이** | 비교 도구의 정규화 한계로 보이는 차이 | (위에 포함) | 닫음 |

"고칠 수 없음"과 "의도해서 둠"은 응답이 달라도 사용자가 겪는 동작은 같다. C만 사용자가 겪는 동작이 v1.6과 다를 수 있다.

## 2. A. 고칠 수 없음

### G-01 JGit, SVNKit 버전 문자열
- **내용**: git 광고(`GET /:owner/:project/info/refs?service=...`)의 `agent=JGit/4.5.0.201609210915-r`(v1.6)와 `agent=JGit/7.8.0.202609011348-r`(v2). 그 밖의 기능 목록과 참조 목록은 같다.
- **영향**: `repo1` 8건, `proto1` 21건, `life1` 5건.
- **이유**: v1.6은 JGit 4.5, v2는 7.8을 쓴다. 한쪽 버전에 맞추려고 라이브러리를 낮추지 않는다.

### G-02 JGit 푸시 진행률과 `--atomic`
- **내용**: 푸시 출력의 `Updating references: 12% (2/16)`(v1.6)와 `13% (2/16)`(v2)처럼 반올림이 다르다. `git push --atomic`은 v1.6이 "the receiving end does not support --atomic push"로 거절하고 v2는 받는다. 이어지는 `--delete`, `--mirror` 결과도 이 때문에 다르다.
- **영향**: `clients2.py` 6건. 푸시 자체의 결과(참조 목록, 객체)는 같다.
- **이유**: JGit 버전 차이(G-01).

### G-03 난수와 UUID
- **내용**: svn 저장소 UUID(`3565927f-...` 대 `c1c4d281-...`), 잠금 토큰, 트랜잭션 ID(`!svn/act/…`), 프로젝트 이전 확인 키, 사용자 API 토큰(`access_token`)과 내 토큰 화면(`/user/editform/token`)의 토큰 값, 세션 ID.
- **영향**: `clients2` 2건, `life1` 메일 3통, `api1` 2건, 토큰 화면 `pages1` 4건과 `forms1` 1건, `proto1` 일부.
- **이유**: 매번 새로 만드는 값이다.

### G-04 서버 내부 번호(Ebean 번호 묶음)
- **내용**: v1.6의 Ebean은 번호(시퀀스)를 20개씩 묶어 가져가고, 묶음 안에서 뒤쪽 번호를 먼저 쓴다(예: 33~40을 쓴 뒤 21~32). 그래서 같은 순서로 만든 이슈, 댓글, 마일스톤, 첨부의 내부 번호가 서버마다 다르다(`/files/34` 대 `/files/22`). 사용자 번호도 v1.6은 33부터, v2는 2부터이다.
- **영향**: "번호만 다름" 약 60건(`org2` 19, `att1` 9, `pr1` 8, `proto1` 8, `review1` 7 등). 정렬 기준이 같은 값(예: `numOfComments` 동점)일 때 항목 순서가 다른 경우 17건(`bulk1` 16, `counts` 1).
- **이유**: 번호가 만든 순서와 다르다. v2는 만든 순서대로 번호를 쓴다. v1.6의 발급 방식(JVM 재시작과 묶음 크기에 달림)은 재현할 수 없고 재현할 이유도 없다.
- **비교 도구의 처리**: 응답에서 번호를 읽어 `<이름>`으로 바꿔 비교한다. 화면에 번호 모양 값이 우연히 겹치면 도구가 잘못 바꾸는 경우가 있다(F).

### G-05 환경 값
- **업데이트 알림 배지**: v1.6 서버는 `application.update.check.use=false`여도 관리자 화면에 새 버전 알림(`notification-badge`)을 보일 때가 있다. `pages1` 11, `site1` 5, `forms1` 4, `hook1` 2, `life1` 1.
- **GitHub 클라이언트 ID**: `/authenticate/github`의 `Location`에 실리는 `client_id`가 서버 설정 값이다(`anon_basic` 1).
- **호스트 이름**: 메일 본문의 주소 등은 두 서버가 같은 호스트(`10.126.201.1`)로 바꿔 비교한다.

### G-06 응답 머리글 집합
- v2에만 있는 헤더: `X-Content-Type-Options`, `X-Frame-Options: SAMEORIGIN`, `X-XSS-Protection`(Spring Security 필터 체인, 승인한 기술 결정), `Content-Language`, `Connection`, `Keep-Alive`, 길이를 모르는 본문의 `Transfer-Encoding: chunked`.
- `Content-Type`의 `; charset` 공백 표기(`text/xml;charset="utf-8"` 대 `text/xml;charset=utf-8`)는 Tomcat이 다시 쓴다.
- 쿠키: v1.6은 `PLAY_SESSION`, `PLAY_FLASH`, 일부 `Set-Cookie: yobi.token`, v2는 `JSESSIONID`와 서버 세션이다.
- **영향**: `proto1`의 `Content-Type` 28건 등. 클라이언트가 읽는 값(상태, 본문, 인증)은 같다.

### G-07 엑셀 파일의 날짜 바이트
- **내용**: `reviews?format=xls` 응답의 xls 안에 들어가는 생성 시각 바이트(OLE 시간 필드)가 실행마다 다르다. 셀 값은 같다.
- **영향**: `review1` 4건.

### G-08 웹훅 `Authorization` 값 끝 공백
- **내용**: v1.6이 보내는 `Authorization: token <값> `에는 끝 공백이 있고, JDK HttpClient가 보내는 v2에는 없다. 본문은 모두 같다.
- **영향**: `hookcompare` 전부(웹훅 요청마다 1줄).

### G-09 v1.6의 마크다운 렌더링 캐시
- **내용**: v1.6 Ebean은 마크다운 변환 결과를 원문 해시로 캐시한다. 알림 메일은 HTTP 문맥 없이 렌더링해 이슈 링크 변환을 건너뛰고 그 결과가 캐시에 남아, 같은 원문을 화면에서도 변환하지 않는 경우가 있다. v2는 항상 변환한다.
- **영향**: `md1` 2건, 메일 2통.

### G-10 `messages.js` 키 순서
- v1.6은 `HashMap` 순서, v2는 다른 순서로 JS 객체를 내보낸다. 키와 값의 집합은 `msgcompare.py`로 같음을 확인했다. 동작에는 영향이 없다(`anon_basic` 1, `pages1` 5).

## 3. B. 의도해서 둔 차이

### G-20 마일스톤 목록의 `labels.css` 위치 (A-151)
- v1.6의 `milestone/list.scala.html`은 레이아웃을 부르기 전에 `<link rel="stylesheet" href="…/issue/labels.css">`를 찍어 이 링크가 `<!DOCTYPE>`보다 앞에 나온다. v2는 레이아웃 안(프로젝트 메뉴 앞)에 낸다. 브라우저는 이 링크를 `head`로 옮기므로 보이는 화면은 같다. Thymeleaf의 `th:replace`로는 문서 앞에 쓸 수 없다.
- **영향**: `pages1` 30, `bulk1` 16, `life1` 5, `org2` 5, `api1` 1.

### G-21 알림(flash)이 둘 이상일 때의 표시 순서 (R-122)
- **내용**: 알림이 둘 이상 남을 때 순서가 v1.6에서 페이지 종류와 실행마다 다르다. `GET /nope`는 경고, 실패 순이고 403 화면과 라우트 없는 방식의 404 화면은 실패, 경고 순이다. 같은 요청을 세 번 돌려도 3~4건이 오락가락한다. 규칙을 찾지 못해 v2는 넣은 순서로 둔다.
- **영향**: `pages1` 34, `methods1` 4, `life1` 3, `anon_basic` 1.

### G-22 알림(flash)의 저장 방식 (A-157)
- v1.6은 `PLAY_FLASH` 쿠키 하나에 담고 심은 요청과 다음 요청에서 모두 보인다. v2는 서버 세션의 FlashMap에 담고 같은 동작을 재현한다. 쿠키로 보이는 모양은 다르다.

### G-23 프로젝트 조회 캐시 (A-07 철회, A-173)
- v1.6의 `CacheStore.projectMap`을 옮겼다. 이름을 바꿔도 한 번 연 예전 이름이 계속 열리는 v1.6의 결함까지 같다(R-126). 이 항목은 더는 차이가 아니다.

### G-24 마크다운 엔진
- v1.6이 쓰는 `marked.js`를 Nashorn에서 그대로 실행한다(A-154). 선생님께서 사용해도 된다고 승인하셨다(2026-10-28). 입력 160여 개와 무작위 1900건이 같다.

### G-25 메일로 받은 첨부는 저장함 (D-25, R-61)
- v1.6은 HTTP 요청이 없는 메일 스레드에서 `Attachment.save`가 항상 실패해 첨부를 저장하지 못한다. v2는 저장한다.

### G-26 DB 컬럼 형식
- v1.6 릴리스 H2의 `attachment.created_date`와 `issue_sharer.created`는 `DATE`(Ebean이 애노테이션 없는 `java.util.Date`를 날짜로 저장)이고, v2는 `TIMESTAMP`이다. 값은 0시로 맞춰 저장한다(A-168).
- 그 밖에 `TINYINT`(v1.6 H2 생성 스키마)와 `BOOLEAN`(v2), `SMALLINT` 대 `INTEGER` 차이가 있으나 MariaDB용 evolution(`datetime` 등)과 의미가 같다. v1.6에만 있는 옛 열(`n4user.avatar_url`, `project.logo_path` 등)과 옛 표(`item`, `line`)는 v2에 없다.

### G-27 v2에만 있는 기능 (사용자 결정으로 유지)
LFS 옛 이름, Deploy Key 임시 사용자, 2FA 우회 방지, 프로젝트 이전 REST, 위키 저장소 이동, 커밋 댓글 JSON API.

### G-28 일부러 같게 만든 v1.6의 결함
v1.6 결함을 고치지 않고 재현해 둔 것이다. 모두 `refactoring-backlog.md`에 있다.

| 재현한 결함 | 근거 |
|---|---|
| `/user/sidebar`에 `hash`가 없으면 500 | R-123, A-172 |
| 익명의 `favoriteIssues` 목록이 500 | R-123, A-172 |
| `Referer` 없는 `/users/logout`은 `Location: null`, `/logout`은 `Location: Result(200, Map(...))` | R-03, A-172 |
| `/restricted`의 거절 응답에 `Cache-Control`이 없음 | A-172 |
| 첨부 올리기가 `authorEmail`로 다른 사용자 명의 업로드를 허용(권한 검사 없음) | A-168, R-121 |
| 프로필 아바타가 남의 첨부 번호도 받음 | A-31 철회 |
| 멘션 목록에 읽기 권한 검사가 없음 | R-125, A-173 |
| 프로젝트 이름 캐시가 예전 이름을 남김 | R-126, A-173 |
| 구성원 역할 바꾸기가 입력을 거의 검사하지 않음 | R-120, A-173 |
| 이슈 일괄 변경의 상태 값 검사와 없는 번호의 500 | R-127, A-173 |
| 첨부 `created_date`가 날짜만(0시) | A-168 |
| 라벨, 첨부, 분류 JSON의 필드 순서가 `HashMap` 순서 | A-163, A-168 |
| 라벨 JSON `Content-Type`에 문자 집합이 없음 | A-172 |
| 모든 `OPTIONS`가 404, 라우트 없는 POST/PUT/DELETE의 404에 `Cache-Control` 없음 | A-168, A-169 |
| 숫자 경로 변수에 글자가 오면 접근 검사 전에 400 | A-169 |
| git 푸시 본문 크기 제한 없음(130MB까지 확인) | A-170 |
| `Accept-Language: ko`가 `ko-KR`로 풀림 | A-170 |
| svn `LOCK`에 `Depth`가 없어도 받음 | A-170 |
| svn `get-location-segments` 경로에 앞 슬래시 없음 | A-171 |
| 익명 사용자 이름이 서버 JVM 기본 언어로 쓰임 | A-172 |

## 4. C. v1.6 오류를 v2가 정상 처리 (미결: 재현할지 결정 필요)

v1.6이 `NullPointerException`, `NumberFormatException`, `IllegalStateException: No value`(폼 검증 실패 뒤 `form.get()`), `NoSuchElementException` 등으로 500을 내는 요청이다. v2는 입력을 검증해 `400`/`404`로 답하거나 기본값으로 처리한다.
오류 화면 자체("Server error occurred; service is not available")는 v2에도 이미 있어, 재현하려면 입력 판정만 v1.6과 맞추면 된다.
선택지: (가) 모두 v1.6처럼 500/400으로 재현한다. (나) 지금처럼 v2의 정상 처리를 유지하고 `R-118` 정책으로 문서에만 남긴다. 기본 원칙(v1.6 결함도 재현)대로면 (가)이다.

### C1. 목록 화면의 이상한 질의값 (33건)
v1.6은 `pageNum=0`, `-1`에서 500, `pageNum=abc`에서 400, 없는 정렬 이름과 방향에서 500이다. v2는 무시하고 기본값으로 목록을 그린다(200).
| 요청 | 시나리오 | v1.6 | v2 | 건수 |
|---|---|---|---|---|
| `GET /-_-api/v1/user/issues?filter=bogus` | api1 | 500 | 400 | 1 |
| `GET /tester/proj1/issues?milestoneId=none` | bulk1 | 500 | 400 | 4 |
| `GET /tester/proj1/issues?orderBy=nonexistent` | bulk1 | 500 | 200 | 4 |
| `GET /tester/proj1/issues?orderDir=sideways` | bulk1 | 500 | 200 | 4 |
| `GET /tester/proj1/issues?pageNum=-1` | bulk1 | 500 | 200 | 4 |
| `GET /tester/proj1/issues?pageNum=0` | bulk1 | 500 | 200 | 4 |
| `GET /tester/proj1/issues?pageNum=abc` | bulk1 | 400 | 200 | 4 |
| `GET /tester/proj1/reviews?state=all` | review1 | 500 | 400 | 4 |
| `GET /tester/proj1/reviews?state=bogus` | review1 | 500 | 400 | 4 |

### C2. 없는 대상을 조작 (15건)
이슈, 글, 댓글, 프로젝트가 없는데 수정, 삭제, 댓글, 추천 취소를 하는 요청이다. v1.6은 `null`을 그대로 써서 500이고 v2는 404이다. `counts`의 3건은 손으로 만든 요청에 필요한 숨은 필드가 없어 v1.6이 500이고 v2가 303으로 처리하는 경우이다.
| 요청 | 시나리오 | v1.6 | v2 | 건수 |
|---|---|---|---|---|
| `PUT /-_-api/v1/owners/tester/projects/proj1/issues/N` | api1 | 500 | 404 | 1 |
| `DELETE /comments/review_comment/N` | review1 | 500 | 400 | 1 |
| `DELETE /sites/project/delete/<번호>` | site1 | 500 | 404 | 1 |
| `DELETE /sites/project/delete/N` | site1 | 500 | 404 | 1 |
| `DELETE /tester/proj1/issue/N/comment/N/delete` | counts | 500 | 303 | 1 |
| `DELETE /tester/proj1/issue/N/comment/N/delete` | forms1 | 500 | 404 | 1 |
| `POST /tester/proj1/issue/N/comments` | forms1 | 500 | 404 | 1 |
| `DELETE /tester/proj1/issue/N/delete` | forms1 | 500 | 404 | 1 |
| `POST /tester/proj1/issue/N/edit` | counts | 500 | 303 | 1 |
| `POST /tester/proj1/issue/N/edit` | forms1 | 500 | 400 | 1 |
| `POST /tester/proj1/issue/N/edit` | forms1 | 500 | 404 | 1 |
| `POST /tester/proj1/issue/N/unvote` | misc1 | 500 | 404 | 1 |
| `DELETE /tester/proj1/post/N/comment/N/delete` | counts | 500 | 303 | 1 |
| `DELETE /tester/proj1/post/N/comment/N/delete` | forms1 | 500 | 404 | 1 |
| `POST /tester/proj1/post/N/edit` | forms1 | 500 | 404 | 1 |

### C3. 필요한 폼 항목이 없는 요청 (9건)
화면의 폼이 보내는 숨은 항목(`id`, `fromProjectId` 등)이나 필수 값이 빠진 요청이다. v1.6은 검증 실패 뒤 `form.get()`이 `No value`로 500이고 v2는 검증 오류 화면(400)을 보인다. 화면에서는 JS가 먼저 막으므로 요청을 직접 만들어야 닿는다(H-09).
| 요청 | 시나리오 | v1.6 | v2 | 건수 |
|---|---|---|---|---|
| `POST /other/proj1/pullRequests` | pr1 | 500 | 400 | 1 |
| `PUT /tester/proj1/issue/label/<번호>` | forms1 | 500 | 400 | 1 |
| `POST /tester/proj1/issue/label/<번호>/delete` | forms1 | 500 | 400 | 1 |
| `POST /tester/proj1/issues` | forms1 | 500 | 303 | 1 |
| `POST /tester/proj1/issues/latest` | forms1 | 500 | 400 | 1 |
| `POST /tester/proj1/pullRequest/<번호>/edit` | pr1 | 500 | 400 | 1 |
| `POST /tester/proj1/setting` | forms1 | 500 | 400 | 3 |

### C4. 지원하지 않는 요청 방식과 멀티파트가 아닌 업로드 (35건)
`POST /notifications`처럼 GET만 있는 한 조각 주소에 POST를 보내면, v1.6은 맨 아래 `POST /:user`(`resetUserPasswordBySiteManager`)가 받아 필수 값이 없어 500이다. v2도 같은 라우트가 받지만 필수 값 누락을 400(JSON `BAD_REQUEST`)으로 답한다. `POST /files`는 멀티파트가 아니면 v1.6이 `NullPointerException`(500), v2는 400이다(R-121).
| 요청 | 시나리오 | v1.6 | v2 | 건수 |
|---|---|---|---|---|
| `POST /-_-api` | methods1 | 500 | 400 | 2 |
| `POST /_UIKit` | methods1 | 500 | 400 | 2 |
| `POST /_help` | methods1 | 500 | 400 | 2 |
| `POST /_init` | methods1 | 500 | 400 | 2 |
| `POST /categories` | methods1 | 500 | 400 | 2 |
| `POST /favicon.ico` | methods1 | 500 | 400 | 2 |
| `POST /files` | att1, forms1 | 500 | 400 | 3 |
| `POST /labels` | methods1 | 500 | 400 | 2 |
| `POST /logout` | methods1 | 500 | 400 | 2 |
| `POST /messages.js` | methods1 | 500 | 400 | 2 |
| `POST /migration` | methods1 | 500 | 400 | 2 |
| `POST /notification` | methods1 | 500 | 400 | 2 |
| `POST /notifications` | methods1 | 500 | 400 | 2 |
| `POST /orgs` | methods1 | 500 | 400 | 2 |
| `POST /projectform` | methods1 | 500 | 400 | 2 |
| `POST /restricted` | methods1 | 500 | 400 | 2 |
| `POST /search` | methods1 | 500 | 400 | 2 |

### C5. 즐겨찾기 API (6건)
익명 요청과 숫자가 아닌 번호에서 v1.6은 500이고 v2는 403/400이다. 즐겨찾기 이슈 목록은 즐겨찾기가 하나라도 있으면 v1.6이 `issue.author` 지연 로딩 실패로 500이다(R-117). HEAD의 두 건은 익명 즐겨찾기 목록이 v1.6에서 처음 한 번만 500이고 그다음부터 200인 상태 의존 동작이어서 생긴다(R-123).
| 요청 | 시나리오 | v1.6 | v2 | 건수 |
|---|---|---|---|---|
| `GET /-_-api/v1/favoriteIssues` | api1 | 500 | 200 | 1 |
| `HEAD /-_-api/v1/favoriteOrganizations` | methods1 | 500 | 200 | 1 |
| `HEAD /-_-api/v1/favoriteProjects` | methods1 | 500 | 200 | 1 |
| `POST /-_-api/v1/favoriteProjects/N` | api1 | 500 | 403 | 1 |
| `POST /-_-api/v1/favoriteProjects/abc` | api1, forms1 | 500 | 400 | 2 |

### C6. 없는 리비전, 경로, 파일 (35건)
존재하지 않는 리비전, 경로, 비교 범위를 코드 보기에 주면 v1.6은 SVNKit/JGit 예외가 그대로 올라와 500이고(svn은 오류 본문에 예외 스택이 실린다) v2는 404 화면이다. `mentionListAtCommitDiff?commitId=zzz`는 v1.6이 500이고 v2는 빈 목록 200이다.
| 요청 | 시나리오 | v1.6 | v2 | 건수 |
|---|---|---|---|---|
| `GET /tester/proj1/rawcode/master/nope` | repo1 | 500 | 404 | 5 |
| `GET /tester/svn1/code/N/trunk` | svn1 | 500 | 404 | 5 |
| `GET /tester/svn1/commit/N` | svn1 | 500 | 404 | 5 |
| `GET /tester/svn1/commit/abc` | svn1 | 500 | 404 | 5 |
| `GET /tester/svn1/compare/x..y` | svn1 | 500 | 404 | 5 |
| `GET /tester/svn1/mentionListAtCommitDiff?commitId=zzz` | svn1 | 500 | 200 | 5 |
| `GET /tester/svn1/rawcode/HEAD/trunk/nope` | svn1 | 500 | 404 | 5 |

### C7. 저장소가 비어 있는 상태의 화면 (21건)
`pages1`은 git 저장소를 올리기 전(data1) 상태에서 돈다. 이때 `branches`와 없는 글의 수정 폼, 익명의 `/info/leave/…`는 v1.6이 500이고 v2는 404/303이다. 저장소가 있으면(`repo1`) `branches`는 둘 다 200이다.
| 요청 | 시나리오 | v1.6 | v2 | 건수 |
|---|---|---|---|---|
| `GET /info/leave/tester/proj1` | pages1 | 500 | 303 | 1 |
| `GET /tester/priv1/branches` | pages1 | 500 | 404 | 3 |
| `GET /tester/priv1/post/N/editform` | pages1 | 500 | 404 | 5 |
| `GET /tester/proj1/branches` | pages1 | 500 | 404 | 5 |
| `GET /tester/prot1/branches` | pages1 | 500 | 404 | 2 |
| `GET /tester/prot1/post/N/editform` | pages1 | 500 | 404 | 5 |

## 5. D. 권한·동작 차이 (해결: 2026-10-28, A-173)

네 가지 모두 v1.6대로 되돌렸고, 해당 시나리오(`forms1`, `org2`, `life1`, `pages1`)에서 차이가 사라졌다.

| 항목 | v1.6 | 이전 v2 | 지금 |
|---|---|---|---|
| D1 비공개·보호 프로젝트의 `mentionList` | 읽기 권한 검사가 없어 익명도 200(후보를 받음). 없는 프로젝트는 `mentionType`이 없으면 `{}`(200), `user`/`issue`이면 500 | 403 | v1.6대로 (R-125). 비공개 프로젝트의 멤버 정보가 익명에게 보이는 v1.6의 결함까지 같다 |
| D2 이름을 바꾼 프로젝트의 예전 이름 | 한 번 연 이름은 캐시 때문에 계속 열림 | 403 | v1.6대로 캐시를 옮김 (R-126) |
| D3 구성원 역할 바꾸기 | 역할 번호가 없으면 500, 없는 번호는 204(조직은 변화 없음, 프로젝트는 역할이 비게 됨), 존재하는 역할은 종류를 가리지 않음 | 거절(안내나 400) | v1.6대로 (R-120) |
| D4 이슈 일괄 변경 | 상태 값이 열거형 이름이 아니면 400, 없는 이슈 번호가 있으면 500 | 303 | v1.6대로 (R-127) |

## 6. E. 메일 수신(IMAP)과 알림 메일

GreenMail 두 대(v1.6은 143번 포트, v2는 19243번)에 시험 메일 30통을 넣어 `imap1`과 `mailcompare.py`로 대조했다. 만들어진 이슈의 제목과 본문은 대부분 같고, 차이는 다음과 같다(R-124).

| 번호 | v1.6 | v2 | 재현 여부 |
|---|---|---|---|
| E-1 빈 제목 메일 | 제목이 `null`로 저장되고 이슈 보기 제목이 `null`, **그 프로젝트의 이슈 목록 화면이 500** | 제목이 빈 문자열이고 목록은 정상 | **미결**: 재현하면 메일 한 통으로 이슈 목록 전체가 500이 된다 |
| E-2 255자를 넘는 제목 | 저장에 실패하지만 이슈 번호를 두 개 쓴다(재시도). 이후 이슈 번호가 2 어긋난다 | 저장에 실패하고 번호를 쓰지 않는다 | 재현하지 않음 |
| E-3 인라인 이미지(`cid:`) 메일 | 이슈를 만들고도 "Unexpected error" 회신 메일을 보낸다 | 이슈와 첨부를 만들고 회신하지 않는다 | 재현하지 않음 |
| E-4 커밋 주소(`…/commit/해시@도메인`) 답장 | 번호로 읽으려다 `NumberFormatException`, 처리 실패 | 결과를 확인하지 못했다 | 미확인 |
| E-5 한 메일을 두 프로젝트에 보냄 | 두 번째 이슈의 알림 메일이 나가지 않음(T-4와 같은 원인) | 두 번째 이슈도 알림 | 재현하지 않음 |
| E-6 첨부 | 저장 실패(G-25) | 저장 | 결정 D-25 |

알림 메일 대조: `forms1`, `md1`, `review1`, `org2`, `att1`, `mail1`, `bulk1`(100통)이 만든 메일의 본문, 제목, 수신자는 같다. 같은 제목의 댓글 메일이 둘 이상이면 v1.6 번호 묶음(G-04) 때문에 도착 순서가 바뀌어 짝이 어긋나는 경우가 3통 있다. `life1`의 프로젝트 이전 메일은 확인 키가 난수(G-03)라 본문이 다르다.

## 7. F. 도구가 만든 가짜 차이
- 요청에 고정 번호를 쓰는 경우: `issues?assigneeId=2`의 2는 v2의 사용자 번호이고 v1.6에는 그 번호의 사용자가 없어 담당자 필터의 선택 표시(`active`, `selected`)가 다르다(`pages1` 3).
- 번호 정규화가 우연히 겹치는 숫자를 바꾼 경우: `issueform?parentIssueId=<이름>` 등(`att1` 2).
- `forms1`에서 만든 웹훅의 유형: 폼에 유형을 보내지 않으면 v1.6은 유형이 없어 화면에 아무것도 안 나오고 v2는 `SIMPLE`을 보인다(`forms1` 1, 손으로 만든 폼).
- `proto1`/`svn1`의 `# SVN 프로젝트` 7+5건: v1.6이 문자 집합 없는 `text/plain`으로 한글 파일을 내려 대조기가 다르게 디코딩한 것으로, 바이트는 같다.

## 8. 이 문서를 고치는 법
회귀 실행은 `tools/v16-diff/regress.sh`이다. 차이 건수가 달라지면 이 문서의 해당 항목과 [시나리오 설명 표](v16-release-differential-test.md)를 같이 고친다.
새로 고친 차이는 `decisions.md`(에이전트 단독 판단 표)와 `refactoring-backlog.md`에 근거를 적고 이 문서의 "재현한 결함" 표에 옮긴다.
