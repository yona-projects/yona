---
title: 세션 경로와 API 경로의 분리
type: architecture
sources: [app/controllers/api/IssueApi.java:513-535, app/controllers/api/IssueApi.java:637-662, app/controllers/api/UserApi.java:136, app/controllers/api/UserApi.java:244-266, app/controllers/AttachmentApp.java, conf/routes]
updated: 2026-10-29
---

# 세션 경로와 API 경로의 분리 (X-5, D-36)

**상태: 설계 확정(선생님 지시 2026-10-29), 구현 전.** "`/-_-api/v1`은 토큰 전용이고, 여기서 세션 쿠키도 처리하던 것은 세션용으로 주소를 만든다. 새 API 경로는 `/-_-api/v1`이고 나중에 별칭으로 `/api/v1/**`를 건다. 옛 토큰 전용 네 개도 별칭을 남기지 않는다. 이런 차이점은 문서화한다." 토큰 자체의 설계는 [api-token.md](api-token.md)에 있다.

## 원칙

- **`/-_-api/v1/**`는 토큰 전용(외부 API)**이다. 이 아래에서는 `Authorization: token <값>`, `Authorization: Bearer <값>`, `Yona-Token`의 토큰만 사용자로 인정하고 **세션과 쿠키는 무시**한다. 토큰이 없으면 401이다.
- **세션을 처리하던 것은 세션용 새 이름 공간 `/-_-ajax/v1/**`로 옮긴다**(이름은 제안이며 바꿀 수 있다). 이 아래는 세션과 쿠키만 보고 토큰을 인정하지 않는다.
- 한 주소는 세션 전용이거나 토큰 전용이고 둘 다 받는 주소는 없다. 이름 공간이 인증 방식을 정한다.
- 나중에 `/api/v1/**`를 `/-_-api/v1/**`의 별칭으로 건다(로드맵 X-15, X-19). **옛 토큰 전용 네 개도 따로 별칭을 두지 않는다**(이미 `/-_-api/v1`에 있다).
- 예외는 Git, SVN의 HTTP 접근(비밀번호 칸의 토큰)이다. 웹 경로(`/{owner}/{project}/...`)는 세션 전용이다.

## 옮기는 것

### 가. 웹 화면의 AJAX(세션 전용)를 `/-_-ajax/v1/**`로 옮긴다 (24개)

지금 `/-_-api/v1` 아래에 있지만 외부 API가 아니라 웹 화면이 세션으로 부르는 것이다(템플릿과 스크립트에서 호출을 확인). 주소의 `/-_-api/v1`만 `/-_-ajax/v1`로 바꾼다(요청과 응답은 그대로).

| 컨트롤러 | 개수 | 주소(`/-_-api/v1` 뒤) |
|---|---|---|
| `IssueApiController` | 11 | `…/issues/{number}/assignableUsers`, `findSharer`, `sharableUsers`, `content`(PATCH), `assignees`, `commentNotiReceivers`, `detectChange`, `downvoteWeight`, `share`, `upvoteWeight`, `comments/{commentId}`(PUT) |
| `IssueFormApiController` | 2 | `/owners/{owner}/projects/{projectName}/assignableUsers`(풀 리퀘스트 담당자 후보에도 쓴다), `/titleHeads` |
| `BoardApiController` | 2 | `…/posts/{number}/content`(PATCH), `…/postlabel/{number}` |
| `FavoriteApiController` | 6 | `/favoriteIssues`, `/favoriteOrganizations`, `/favoriteProjects`(GET)와 각각의 POST |
| `TranslationApiController` | 1 | `/translation` |
| `HomeController` | 1 | `/user/defultLoginPage` |
| `UserApiController`(web/user) | 1 | `GET /users`(멘션과 구성원 검색 화면용) |

옛 `/-_-api/v1` 주소로는 이 요청들을 더 받지 않는다. 템플릿, 스크립트, 시험의 호출을 모두 새 주소로 고친다.

### 나. 세션과 토큰을 둘 다 받던 외부 API 경로

`/-_-api/v1` 아래의 **같은 주소를 토큰 전용(API)으로** 두고, 세션을 처리하던 쪽은 **세션 사본을 `/-_-ajax/v1` 아래에 새로 만든다**(같은 처리 코드, 요청과 응답 모양 그대로). 웹 화면이 이 경로들을 부르지 않는 것은 확인했으므로 사본은 v1.6의 세션 사용(예: 쿠키로 부르는 도구)을 위한 것이다.

| API 주소(`/-_-api/v1` 뒤, 토큰 전용) | 세션 사본(`/-_-ajax/v1` 뒤) | 필요한 범위 |
|---|---|---|
| `POST /owners/{owner}/projects/{projectName}/issues` | 같은 주소 | `repo`, `public_repo` |
| `POST …/issues/{number}/comments` | 같은 주소 | `repo`, `public_repo` |
| `POST …/issuelabel/{number}` | 같은 주소 | `repo`, `public_repo` |
| `POST …/issues/imports` | 같은 주소 | `repo`, `public_repo` |
| `POST …/posts` | 같은 주소 | `repo`, `public_repo` |
| `POST …/posts/{number}/comments` | 같은 주소 | `repo`, `public_repo` |
| `POST /owners/{owner}/projects` | 같은 주소 | `repo` |
| `POST …/labels`, `POST …/milestones`, `GET …/exports`, `GET …/posts/{number}/watchers` | 같은 주소(인증 방식은 구현 때 하나씩 확인) | `repo`, `public_repo` |
| `POST /users` | 같은 주소 | `site_admin` |
| `GET /users/{user}/statistics` | 같은 주소 | `read:user` |
| `GET /admin/users`, `PATCH /admin/users/{user}` | 같은 주소 | `site_admin` |
| `POST /files`(첨부 올리기) | 기존 `POST /files`(웹, 세션 전용) | `repo`, `public_repo` |

(`POST /files`의 API 주소는 새로 만드는 `POST /-_-api/v1/files`이다. 내려받기, 목록, 지우기 `GET /files/{id}`, `GET /files`, `POST /files/{id}`는 웹 세션 전용으로 그대로 둔다.)

### 다. 이미 토큰 전용이던 것(변화 없음, 별칭 없음)

`GET`, `PUT`, `PATCH` `/-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}`와 `GET /-_-api/v1/user/issues`이다. 주소와 인증이 그대로이다(범위만 더해진다). 필요한 범위는 `repo`, `public_repo`이다.

### 라. 삭제

`POST /-_-api/v1/users/token`(비밀번호로 토큰과 세션 쿠키 발급)은 삭제한다(D-36 7번). `GET /-_-api/v1/hello`는 토큰 없이 응답하는 상태 확인으로 둔다(인증 없음, 범위 없음).

## v1.6과 달라지는 점 (문서화할 차이)

1. **웹 화면의 AJAX 주소가 바뀐다**: `/-_-api/v1/...`에서 `/-_-ajax/v1/...`로(24개). 화면 동작은 그대로이다. v1.6의 요청 계약과 다른 의도된 차이이다.
2. **토큰 전용이 된 `/-_-api/v1`**: 이 접두사는 세션과 쿠키를 무시한다. 쿠키로 이 주소를 부르던 외부 도구는 401을 받으므로 `/-_-ajax/v1`의 세션 사본(가져오기 흐름)으로 옮기거나 토큰을 써야 한다. 그러나 쿠키를 API로 얻던 `users/token`은 삭제된다(웹 로그인으로 얻어야 한다).
3. **토큰으로 부르던 도구는 그대로 동작한다.** 주소가 같기 때문이다. 이전 제안(`/api/v1`로 옮김)과 달리 주소를 옮기지 않는다.
4. **이슈 댓글의 두 흐름은 v1.6 자체의 구조**이다(`IssueApi.newIssueComment`). 토큰 흐름은 본문 `{"comment": "..."}`에 응답 `201 {"result": {이슈 정보와 이벤트}}`이고 가져오기(세션) 흐름은 본문 `body`, `author`, `createdAt`, `temporaryUploadFiles`에 응답 `201 {"status": 201, "location": "댓글 주소"}`이다. 변경 뒤에는 **`/-_-api/v1`이 두 흐름을 모두 토큰 인증으로** 받고(본문에 `comment`가 있으면 토큰 흐름, 없으면 가져오기 흐름), `/-_-ajax/v1` 사본은 가져오기 흐름만 받는다. 응답은 각 흐름의 v1.6 모양을 그대로 돌려준다.
5. **첨부와 가져오기 JSON의 연결**: 가져오기 흐름은 첨부를 먼저 올리고(`POST /files` 또는 새 `POST /-_-api/v1/files`, `authorEmail`로 원작성자 명의) 그 번호를 JSON의 `temporaryUploadFiles`에 넣어 연결한다.
6. **세션 로그인을 겸하던 동작이 없어진다**: `users/token`을 삭제한다.
7. **잠긴, 삭제된 사용자의 토큰을 거절한다**(v1.6은 통과시켰다).
8. **토큰이 갈 수 있는 곳이 줄어든다**: v1.6은 모든 경로에서 토큰 헤더를 인정했으나 `/-_-api/v1/**`와 Git, SVN의 HTTP 접근에서만 인정한다.
9. **`/api/v1/**` 별칭은 나중에 건다**(이번에 만들지 않는다).

## 구현 방식

- `YonaAuthenticationFilter`가 요청 경로의 이름 공간으로 인증 출처를 정한다. `/-_-api/v1/**`는 토큰만, 그 밖은 세션과 쿠키만 본다. Git과 SVN은 기존 Basic 해결기에서 토큰을 처리한다. 토큰 헤더가 있는데 `/-_-api/v1` 밖이면 401이 아니라 무시하고 세션으로 처리한다(웹 요청에 헤더가 우연히 붙어도 깨지지 않게 한다).
- 컨트롤러는 한 처리 메서드가 두 주소(`/-_-api/v1/…`, `/-_-ajax/v1/…`)를 받는다. `CurrentUserProvider`가 이름 공간에 맞는 사용자를 돌려주므로 코드 변경은 주소 선언과 토큰 전용이던 `ApiRequests.tokenUser`를 `CurrentUserProvider`로 바꾸는 정도이다.
- 범위는 `/-_-api/v1/**` 요청에 한해 한 곳(인터셉터)에서 경로와 메서드별 대응표로 검사하고 대응표에 없는 경로는 거부한다.
- 이동 대상 24개는 `/-_-ajax/v1`로 옮기고 템플릿, 스크립트, 시험의 주소를 고친다. v1.6 라우트 대조 시험의 의도한 누락 목록에 올린다.
- 시험: 이름 공간별 인증 출처, 쿠키와 토큰이 함께 올 때(`/-_-api`는 토큰만), 각 경로의 요청과 응답 모양이 v1.6과 같은지, 범위 거부, 잠긴 사용자 거부, 옮긴 주소의 화면 동작(브라우저 검증).

## 아직 확인하지 못한 것

`ExternalProjectApiController`의 `labels`, `milestones`, `exports`, `watchers`가 어떤 방식(어노테이션이나 `currentUserProvider`)으로 인증하는지는 구현 때 하나씩 읽어 위 표를 보완한다.
