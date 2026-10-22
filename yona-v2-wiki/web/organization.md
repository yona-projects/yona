---
title: 조직 (생성, 홈, 목록, 설정, 구성원, 삭제, 가입 신청)
type: web
sources: [app/controllers/OrganizationApp.java, app/controllers/EnrollOrganizationApp.java, app/controllers/EnrollProjectApp.java, app/views/organization/*.scala.html, app/views/organizationLayout.scala.html, app/models/Organization.java, app/models/OrganizationUser.java, app/utils/ReservedWordsValidator.java, app/models/User.java:66]
updated: 2026-10-22
---

# 조직

주소는 `/organizations/...`이고 목록은 `/orgs`이다. 이름은 로그인 ID와 같은 규칙을 쓰며 사용자 로그인 ID와 겹칠 수 없다.

## 이름 규칙 (`LoginIdRules`)

형식 `[a-zA-Z0-9가-힣-]+([_.][a-z_.A-Z0-9가-힣-]+)*`(점이나 밑줄 뒤에 점이나 밑줄이 이어져도 된다. `a..b` 허용). 예약어 34개: v1.6 라우트 표의 첫 구간(`-_-api`, `assets`, `files`, `users`, `projects`, `messages.js` 등)에 `new`, `projects`, `orgs`, `organizations`를 더한 것이며 대소문자를 무시한다. v1.6은 기동 때 라우트에서 모으지만 v2는 고정 목록이다.

## 생성 (`GET/POST /organizations/new`)

로그인해야 하고 게스트는 쓸 수 없다. 검증 순서: 이름 없음(`error.required`) -> 형식(`user.wrongloginId.alert`) -> 예약어(`validation.reservedWord`) -> 중복(`organization.name.duplicate`, 사용자 로그인 ID나 다른 조직과 대소문자 무시). 실패하면 `400`으로 폼을 입력값과 함께 다시 보여 주고 안내가 폼 위(`.n-alert .warning`)와 알림에 같이 보인다. 성공하면 만든 사람이 조직 관리자가 되고 `303`으로 조직 홈에 간다.

## 홈 (`GET /organizations/:name`)

조직 로고 배경 머리글(그룹 표시, 이름), 메뉴(조직 홈, 이슈, 게시판, 풀 리퀘스트 — 뒤의 세 화면은 해당 단계에서 옮긴다), 조직 관리자와 사이트 관리자에게만 보이는 설정 아이콘과 새 프로젝트 버튼. 가입 신청 버튼은 조직에 속하지 않은 로그인 사용자(사이트 관리자 제외)에게만 보이고 신청했으면 취소 버튼이다.

프로젝트 목록은 이름순이며 보는 사람에 따라 다르다: 조직 관리자와 사이트 관리자는 모두, 일반 구성원은 비공개가 아니거나 구성원인 것, 그 밖은 목록 숨김 설정이 꺼져 있을 때 공개(게스트 제외)이거나 구성원인 것.

오른쪽 구성원 영역은 사이트 관리자, 일반 구성원, 조직 관리자에게만 보인다. 탈퇴 버튼 조건은 v1.6의 뒤집힌 규칙을 따른다(아래 결함).

## 목록 (`GET /orgs`)

게스트는 막힌다. 사이트가 목록을 숨기면 403, 쪽 번호가 1보다 작으면 404. 이름이나 설명에 검색어가 들어 있는 조직을 최신순으로 30개씩 보여 준다(대소문자 무시, v1.6 운영 DB MariaDB와 같게). 조직 읽기 권한은 누구에게나 있으므로 접근 불가 줄은 나오지 않는다.

## 설정 (`GET settingform`, `POST setting`)

조직 관리자(와 사이트 관리자)만 쓴다: 없는 조직 404, 권한 없음 403. 하위 메뉴는 설정, 구성원, 삭제이다. 저장은 이름, 설명, 로고이다.

- 이름 형식이 틀리거나 예약어이면 v1.6처럼 오류(500)이다. 중복이면 `400`과 이름 아래 안내. 이름이 바뀌었는지는 대소문자까지 구별하고 중복은 대소문자를 무시하므로 **대소문자만 바꾸면 자기 자신과 겹쳐 거부된다**(R-19).
- 로고는 jpg, jpeg, png, gif, bmp이고 5MB 이하여야 한다. 아니면 `400`과 알림. 새 로고는 이전 로고를 대체한다.
- 이름이 바뀌면 조직의 모든 프로젝트 소유자를 바꾸고 Git 저장소 디렉터리를 옮기며, 즐겨찾기한 조직 이름도 바꾼다. 성공하면 `303`으로 새 이름의 설정 폼.
- 대상 조직은 주소로 정한다. 폼의 `id`는 쓰지 않는다(A-24).

## 구성원 (`GET/POST members`, `POST member/:id/edit`, `DELETE member/:id/delete`)

구성원 화면은 조직 관리자만 본다. 구성원 추가는 일반 구성원으로 넣으며 검사 순서는 사용자 없음(`organization.member.unknownUser`), 게스트 사용자(`error.forbidden.to.guest.user`), 조직 없음, 권한(`needManagerRole`), 이미 구성원(`alreadyMember`)이고 어느 경우든 flash와 함께 `303`으로 구성원 화면에 간다.

역할 변경과 삭제는 거부 이유를 flash로 남기고 `200 {"location": 구성원 화면}`으로 알린다. 역할 변경은 조직 관리자와 조직 멤버 역할 번호만 받는다(그 밖은 알 수 없는 역할, A-24). 마지막 관리자를 멤버로 낮추거나 내보낼 수 없다(`atLeastOneAdmin`). 삭제 성공 시 본인이면 조직 홈, 아니면 구성원 화면 주소를 돌려준다.

## 탈퇴 (`DELETE member/leave`, 로그인 필요)

조직을 고칠 수 없는 사용자이고 관리자가 한 명뿐이면 `403 {"errorMsg": "organization.member.atLeastOneAdmin"}`, 없는 조직은 `404` JSON. 성공하면 `200 {"location": 조직 홈}`.

**v1.6 결함(R-19)**: 조건이 거꾸로라서 **관리자는 한 명뿐이어도 탈퇴할 수 있고**(관리자가 없는 조직이 된다) **일반 구성원은 관리자가 한 명뿐이면 탈퇴할 수 없다.** 홈의 탈퇴 버튼도 같은 규칙으로 보인다.

## 삭제 (`GET deleteForm`, `DELETE /organizations/:name`)

화면은 설정과 같은 권한이다. 삭제는 없는 조직, 삭제 권한 없음, 프로젝트가 남아 있음을 모두 **`404` JSON**(`errorMsg`)으로 알린다(v1.6이 `notFound`를 쓴다). 성공하면 구성원 행, 가입 신청, 즐겨찾기와 조직을 지우고 `303` `/`.

## 가입 신청 (`POST /organizations/:name/enroll`, `.../cancel/enroll`, `POST /:user/:project/enroll`, `.../cancel/enroll`)

조직에 속하지 않은(게스트) 로그인 사용자만 쓸 수 있다. 조직은 아니면 `400`(익명, 구성원, 없는 조직 포함), 성공하면 `202`와 `{"message": "You enrolled in 이름.", "statusMonitorUrl": "http://호스트/organizations/이름"}`(취소는 `You canceled to enroll in 이름.`). 프로젝트는 로그인해야 하고 게스트가 아니면 `400`, 성공하면 빈 `200`. 이미 신청했는데 다시 신청하거나 신청하지 않고 취소하면 아무 일도 하지 않고 성공으로 답한다. 신청과 취소는 이벤트(`ProjectMemberRequestEvent`, `OrganizationMemberRequestEvent`)로 알리고 알림 단계에서 수신기를 붙인다.

## 아직 없는 부분

조직의 이슈, 게시판, 풀 리퀘스트 목록(`/organizations/:name/issues`, `/boards`, `/pullrequests`)과 조직 검색은 해당 서브시스템을 옮길 때 만든다. 메뉴 링크는 이미 있다.

## 검증

`LoginIdRulesTest` 5건, `OrganizationCreateTest` 8건, `OrganizationViewTest` 20건, `OrganizationSettingTest` 13건, `OrganizationMemberTest` 27건, `EnrollTest` 9건.
