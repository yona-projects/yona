---
title: 세션 경로와 API 경로의 분리
type: architecture
sources: [app/controllers/api/IssueApi.java:513-535, app/controllers/api/IssueApi.java:637-662, app/controllers/api/UserApi.java:136, app/controllers/api/UserApi.java:244-266, app/controllers/AttachmentApp.java, conf/routes]
updated: 2026-10-29
---

# 세션 경로와 API 경로의 분리 (X-5, D-36)

**상태: 설계 제안, 확인 대기, 구현 전.** 선생님 지시(2026-10-29): "세션 인증과 토큰 인증을 동시에 처리하는 경로는 세션 인증으로 모두 바꾸고 API 전용 경로를 생성한다. 이런 차이점은 문서화한다." 이 문서는 그 변경이 v1.6과 어디서 달라지는지 한곳에 모은 목록이다. 토큰 자체의 설계는 [api-token.md](api-token.md)에 있다.

## 원칙

- **이름 공간이 인증 방식을 정한다.** 한 경로는 세션 전용이거나 토큰 전용이고 둘 다 받는 경로는 없다.
- **`/api/v1/**`는 토큰 전용 이름 공간**이다(로드맵 X-15, X-19의 REST 이름 공간과 같다). 이 아래에서는 `Authorization: token <값>`, `Authorization: Bearer <값>`, `Yona-Token`의 **토큰만** 사용자로 인정하고 세션과 쿠키는 무시한다.
- **그 밖의 모든 경로(`/-_-api/**`를 포함)는 세션 전용**이다. 토큰을 인정하지 않는다(401). 예외는 Git, SVN의 HTTP 접근(비밀번호 칸의 토큰)과 아래의 "옛 토큰 전용 주소" 네 개뿐이다.
- 두 이름 공간은 같은 처리 코드를 쓴다. 한 처리 메서드가 두 주소를 모두 받고(`CurrentUserProvider`가 요청의 이름 공간에 맞는 사용자를 돌려준다) 범위는 `/api/v1/**` 요청에만 검사한다. 그래서 컨트롤러 코드는 거의 바뀌지 않는다.

## 달라지는 경로

v1.6과 현재 v2에서 세션과 토큰을 둘 다 받던 경로이다. 새 열의 `/api/v1/…`는 앞부분 `/-_-api/v1/owners/{owner}/projects/{projectName}`을 `/api/v1/owners/{owner}/projects/{projectName}`으로 바꾼 것이다(요청과 응답 모양은 그대로).

| v1.6, 현재 v2 | 이전 인증 | 변경 뒤 기존 주소 | 새 API 주소(토큰 전용) | 필요한 범위 |
|---|---|---|---|---|
| `POST /-_-api/v1/owners/{owner}/projects/{projectName}/issues` | 세션 또는 토큰 | **세션 전용** | `POST /api/v1/owners/{owner}/projects/{projectName}/issues` | `repo`, `public_repo` |
| `POST …/issues/{number}/comments` | 헤더가 있으면 토큰, 없으면 세션 | **세션 전용**(가져오기 흐름만) | `POST /api/v1/…/issues/{number}/comments`(두 흐름 모두) | `repo`, `public_repo` |
| `POST …/issuelabel/{number}` | 세션 또는 토큰 | 세션 전용 | `POST /api/v1/…/issuelabel/{number}` | `repo`, `public_repo` |
| `POST …/issues/imports` | 세션 또는 토큰 | 세션 전용 | `POST /api/v1/…/issues/imports` | `repo`, `public_repo` |
| `POST …/posts` | 세션 또는 토큰 | 세션 전용 | `POST /api/v1/…/posts` | `repo`, `public_repo` |
| `POST …/posts/{number}/comments` | 세션 또는 토큰 | 세션 전용 | `POST /api/v1/…/posts/{number}/comments` | `repo`, `public_repo` |
| `POST /-_-api/v1/owners/{owner}/projects` | 세션 또는 토큰 | 세션 전용 | `POST /api/v1/owners/{owner}/projects` | `repo` |
| `POST …/labels`, `POST …/milestones`, `GET …/exports`, `GET …/posts/{number}/watchers` | 인증 방식 구현 때 확인 | 세션 전용 | 같은 모양으로 `/api/v1` 아래 | `repo`, `public_repo` |
| `POST /-_-api/v1/users` | 세션(사이트 관리자) | 세션 전용 | `POST /api/v1/users` | `site_admin` |
| `GET /-_-api/v1/users/{user}/statistics` | 세션 또는 토큰 | 세션 전용 | `GET /api/v1/users/{user}/statistics` | `read:user` |
| `GET /-_-api/v1/admin/users`, `PATCH /-_-api/v1/admin/users/{user}` | 세션 또는 토큰(`SiteManagerOnly`) | 세션 전용 | `/api/v1/admin/users`, `/api/v1/admin/users/{user}` | `site_admin` |
| `POST /files` (첨부 올리기) | 세션 또는 `Yona-Token` | **세션 전용** | `POST /api/v1/files` | `repo`, `public_repo` |
| `GET /files/{id}`, `GET /files`, `POST /files/{id}` | 세션 | 세션 전용(변화 없음) | 없음 | |

## 옛 토큰 전용 주소 (예외 네 개)

v1.6에서 이미 토큰 전용이던 주소이다. 외부 클라이언트가 지금 쓰고 있을 수 있어 **옛 주소를 토큰 전용 별칭으로 남기고** 같은 처리를 `/api/v1` 아래에도 연다. 이 네 개만 `/api` 밖에서 토큰을 인정하는 예외이다(범위 검사는 같다). 새 클라이언트는 `/api/v1`을 쓰고 옛 별칭은 폐지 대상이다(일정은 정하지 않는다).

| 옛 주소(토큰 전용 별칭) | 새 API 주소 | 필요한 범위 |
|---|---|---|
| `GET /-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}` | `/api/v1/…/issues/{number}` | `repo`, `public_repo` |
| `PUT …/issues/{number}` | 같은 주소 | `repo`, `public_repo` |
| `PATCH …/issues/{number}` | 같은 주소 | `repo`, `public_repo` |
| `GET /-_-api/v1/user/issues` | `GET /api/v1/user/issues` | `repo`, `public_repo` |

## v1.6과 달라지는 점 (문서화할 차이)

1. **세션으로 부르던 가져오기 도구가 깨진다.** 이슈, 게시글, 댓글, 라벨, 가져오기, 첨부를 **세션 쿠키**로 부르던 외부 도구는 기존 주소가 세션 전용으로 남아 동작은 유지되지만, 쿠키를 얻는 경로였던 `POST /-_-api/v1/users/token`이 삭제되어 쿠키를 **API로 얻을 수 없다**. 도구는 새 `/api/v1` 주소와 설정 화면에서 만든 토큰을 써야 한다.
2. **토큰으로 부르던 도구는 주소가 바뀐다.** 이슈 만들기, 댓글(`comment` 모양), 첨부 등을 `Authorization: token`으로 기존 주소에 보내던 도구는 **세션 전용이 된 기존 주소에서 401**을 받는다. 새 `/api/v1` 주소로 옮겨야 한다. 이 변경이 가장 크다. (조회, 수정, 상태 변경, 내 이슈 목록은 위 별칭으로 옛 주소가 계속 동작한다.)
3. **응답과 요청 모양은 그대로이다**(아래 4번 포함). 주소만 다르다.
4. **이슈 댓글의 두 흐름**(v1.6부터 한 주소에 흐름이 둘): 토큰 흐름은 본문 `{"comment": "..."}`에 응답 `201 {"result": {이슈 정보와 이벤트}}`, 가져오기(세션) 흐름은 본문 `body`, `author`, `createdAt`, `temporaryUploadFiles`에 응답 `201 {"status": 201, "location": "댓글 주소"}`이다. 변경 뒤 기존 주소(세션 전용)는 **가져오기 흐름만** 받는다(헤더가 없는 경우와 같다). 새 `/api/v1/…/comments`는 **두 흐름을 모두** 받고 본문에 `comment`가 있으면 토큰 흐름, 없으면 가져오기 흐름으로 가려 각자 v1.6의 응답을 돌려준다.
5. **첨부와 가져오기 JSON의 연결**: 가져오기 흐름은 `POST /files`(또는 새 `POST /api/v1/files`)로 올린 첨부 번호를 JSON의 `temporaryUploadFiles`에 넣어 연결한다. 올리기는 `authorEmail`, `authorLoginId`로 원작성자 명의가 가능하다.
6. **세션 로그인을 겸하던 동작이 없어진다.** v1.6은 `POST /-_-api/v1/users/token`이 토큰과 세션 쿠키를 함께 발급했으나 이 주소를 삭제한다.
7. **잠긴, 삭제된 사용자의 토큰을 거절한다**(v1.6은 통과시켰다).
8. **토큰이 갈 수 있는 곳이 줄어든다.** v1.6은 모든 경로에서 토큰 헤더를 인정했으나 `/api/v1/**`, 옛 토큰 전용 별칭 네 개, Git과 SVN의 HTTP 접근에서만 인정한다.

## 결정 필요 (선생님)

1. **새 API 이름 공간을 `/api/v1/**`로 한다**(로드맵 X-15, X-19와 같은 이름 공간). 다른 이름(예: `/-_-api/v2`)을 원하시는지.
2. **옛 토큰 전용 주소 네 개**를 별칭으로 남기는 것이 맞는지(권장). 없애려면 외부 클라이언트의 조회, 수정이 깨진다.
3. **`/api/v1/**`에서는 세션과 쿠키를 무시한다.** 세션 쿠키와 토큰 헤더를 함께 보내도 토큰만 보고(api-token.md 17번과 같다) 토큰이 없으면 401이다.

## 구현 방식

- `YonaAuthenticationFilter`가 요청 경로로 인증 출처를 정한다. `/api/v1/**`는 토큰만, 별칭 네 개는 토큰만, 그 밖은 세션과 쿠키만 본다(Git, SVN은 기존 Basic 해결기에서 토큰을 처리한다). 이 한 곳이 세션, 토큰 분리의 책임이다.
- 컨트롤러는 한 메서드가 두 주소(`/-_-api/v1/…`, `/api/v1/…`)를 모두 받는다. 사용자는 `CurrentUserProvider`가 요청의 이름 공간에 맞게 돌려준다. 토큰 전용이던 `ApiRequests.tokenUser`는 `CurrentUserProvider`로 바꾼다.
- 범위는 `/api/v1/**` 요청에 한해 한 곳(인터셉터)에서 경로와 메서드별 대응표로 검사한다. 대응표에 없는 `/api/v1` 경로는 거부한다(기본 거부).
- 시험: 이름 공간별 인증 출처, 세션 쿠키와 토큰이 함께 올 때의 우선순위, 각 경로의 요청과 응답 모양이 v1.6과 같은지, 범위 거부, 잠긴 사용자 거부.
