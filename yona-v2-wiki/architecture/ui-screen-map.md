---
title: 화면별 새 IA 대응표: 템플릿 전수
type: architecture
sources:
  - ui-ia-gitlab.md
  - ui-htmx-plan.md
  - ui-client-storage.md
  - ui-shortcuts.md
  - ../ui-contract/index.md
  - ../ui-contract/shell.md
  - ../ui-contract/shell-common-fragments.md
  - ../ui-contract/shell-scripts.md
  - ../ui-contract/admin-role.md
  - ../ui-contract/project.md
  - ../ui-contract/project-settings.md
  - ../ui-contract/organization.md
  - ../ui-contract/user.md
  - ../ui-contract/site.md
  - src/main/resources/templates/**
  - src/main/resources/static/assets/javascripts/esm/**
updated: 2026-11-11
---

# 화면별 새 IA 대응표: 템플릿 전수

> **상태: 조사 결과(설계 입력).** `src/main/resources/templates/**`의 **템플릿 230개 전부**를 새 셸의 맥락, 사이드바 항목, 경로와 페이지 머리, Bootstrap 5 구성 요소, htmx, 단축키 선언, 클라이언트 저장소에 한 줄씩 대응시켰다. 코드, 템플릿, 시험은 바꾸지 않았다. **주소, 요청, 응답, 기능은 바꾸지 않는다**(D-134). `ui-ia-gitlab.md` 4.2절의 묶음은 **검증할 가설**로 대했고, 맞지 않거나 빠진 것은 숨기지 않고 "골격 적합도" 열과 아래 두 절에 드러냈다.
>
> **확인한 것과 추측**: 주소, 보이는 조건, 권한, 단축키, 저장소 키, 스크립트 줄 번호는 `ui-contract/*.md`와 소스에서 확인한 사실이다. "Bootstrap 구성 요소" 열은 계약의 화면 서술과 소스의 옛 클래스 표지(`ybtn`, `yobicon`, `page-wrap` 등)를 근거로 한 **설계안**이며 마크업을 줄 단위로 전수 대조한 것은 아니다(옛 표지가 남은 템플릿은 그 이름을 적었다. 표지가 없으면 지금 마크업이 BS5 모양이라는 뜻이 아니라 표지가 발견되지 않았다는 뜻이다). htmx 열의 "swap-safe" 판정은 스크립트의 문서와 창 수준 핸들러, 타이머를 읽어 낸 **예비 판정**이며 `ui-htmx-plan.md` 5절의 시험(일반 이동과 htmx 이동의 동일성)을 통과해야 확정된다.

## 읽는 법

| 표기 | 뜻 |
|---|---|
| 종류 | 전체 화면(셸 안), 셸 밖(독립 화면), 조각(다른 템플릿이 끼움, 쓰이는 화면을 htmx 열에 적음), 레이아웃(틀 자체), 이메일이나 비화면 |
| H-기존 | 이미 D-96으로 `div[list-container]`를 htmx가 바꾸는 목록 화면(소스에서 `hx-*` 속성을 확인함) |
| H-셸 | 사이드바와 경로 링크의 `hx-boost`로 `#shell-main`만 바꾸는 **옵트인 후보**. 화면의 스크립트가 swap-safe로 판정된 뒤에만 켠다 |
| H-셸+폼 | H-셸에 더해 폼을 `hx-post`로 보내고 검증 실패는 422 + 폼 조각, 성공은 `HX-Redirect`, 제출 단추는 `hx-disabled-elt`(`ui-htmx-plan.md` 4, 6절) |
| H-조각 | 화면 안의 부분 교체나 지연 불러오기 후보(교체 영역을 적음) |
| N-일반 | 일반 이동. 이유를 적음(문서와 창 수준 핸들러, 타이머, 한 번만 보이는 비밀 값, 인증 흐름 등) |
| 골격 적합도 | **적합**: `ui-ia-gitlab.md` 4.2절의 묶음에 자연스럽게 들어간다. **어색함**: 들어가기는 하나 중복, 이중 구현, 진입 경로 문제가 있다. **자리 없음**: 골격에 항목이나 맥락이 없다 |
| 저장 | `ui-client-storage.md` 4절의 `drafts`, `switcher`, `prefs`, `visited`, `search-history`, `meta`. "저장 금지"는 같은 문서 5절의 비밀 값 |

경로는 `맥락 / 묶음 / 항목`(4.3절), 페이지 머리는 제목 한 줄, 선택 설명, 오른쪽 주요 단추 한 개 강조(4.4절)를 기본으로 하고 표에는 그 화면의 값만 적었다. 모든 화면의 본문은 `#shell-main` 안이며 **본문 영역이 여백을 책임져** 지금의 `.row` 음수 여백 +10px 넘침(IA 2절)을 없앤다.

## 요약

### 수량표

| 구분 | 수 |
|---|---|
| 전체 템플릿 | **230** (`find src/main/resources/templates -name '*.html' \| wc -l` = 230) |
| 전체 화면(셸 안) | 121 |
| 셸 밖 독립 화면 | 10 (`user/login`, `login-two-factor`, `signup`, `verified`, `reset-password`, `device`, `site/lost-password`, `oauth2/consent`, `welcome/secret`, `welcome/restart`) |
| 조각 | 91 |
| 레이아웃 자체 | 7 |
| 이메일이나 비화면 | 1 (`common/notification-mail`) |

| htmx 판정(화면 131개 = 전체 화면 121 + 셸 밖 10) | 수 |
|---|---|
| H-기존(이미 htmx) | 4 |
| H-셸 후보(조회 중심) | 30 |
| H-셸+폼 후보(폼과 행 갱신) | 52 |
| **htmx 후보 합계** | **86** (H-기존 4 + 후보 82) |
| **일반 이동** | **45** (전체 화면 35 + 셸 밖 10) |

| 골격 적합도(230개 전체) | 수 |
|---|---|
| 적합 | 200 |
| 어색함 | 20 |
| 자리 없음 | 10 |

- 조각 91개 가운데 htmx 교체 대상이거나 직접 관여하는 것은 H-기존 4(이슈 목록 교체 조각, 상세 검색 폼, 내 이슈 검색, 코드 주고받기 목록 교체 조각)와 H-조각 5, 전환기 조각 3, 나머지는 쓰이는 화면의 방식을 따른다.
- **D-96의 "5개 목록 화면"은 정확히는 `hx-*` 속성이 있는 템플릿 5개**(`issue/partial-list-wrap`, `issue/partial-searchform`, `issue/my-partial-search`, `organization/issue-list`, `pullrequest/partial-search`)이고, 화면으로는 4개(프로젝트 이슈 목록, 내 이슈, 조직 이슈 목록, 코드 주고받기 목록)이다. **조직 코드 주고받기 목록(`organization/pullrequest-list`)은 `list-container` 표지만 있고 `hx-*` 속성이 없다**(소스 확인).
- 셸 이동은 모든 화면이 아니라 **화면 단위 옵트인**이다. 아래 "swap-safe 예비 판정 근거"의 모듈을 쓰는 화면은 일반 이동으로 시작한다.

### 새 셸 부품과 책임

| 부품 | 한 줄 책임 | 대응하는 지금의 템플릿 |
|---|---|---|
| 상단 바 | 전역 기능만: 사이드바 열기(☰, 지금의 핀 화살표 자리), 로고, 검색(범위 선택), `+`(새 이슈, 새 이슈 - 개인 inbox, 새 프로젝트 만들기, 새 그룹 만들기), 내 이슈(열린 수 배지), 알림 아이콘(`/` 링크), 사이트 관리 렌치(관리자만), 아바타 | `common/navbar`, `common/usermenu` |
| 아바타 메뉴 | 사용자 머리, 프로필 페이지, 사용자 설정, 로그아웃만 가진 짧은 드롭다운(좁음은 시트) | `common/usermenu`의 계정 링크 3개 |
| 사이드바: 전환기 | 즐겨찾기, 프로젝트(하위 4탭), 최근 읽은 이슈, 이름 필터, 새로고침. 데이터는 지금과 같다. `switcher`에 조각을 보관 | `common/sidebar`, `#mySidenav`의 목록, `index/*-list` 조각 |
| 사이드바: 맥락 메뉴 | 맥락(전역, 프로젝트, 조직, 사용자 설정, 사이트 관리)마다 묶음과 항목. **한 곳의 목록 데이터**에서 생성(맥락, 묶음, 항목, 아이콘, 주소, 보이는 조건, 단축키) | `project/menu`, `project/partial-settingmenu`, `organization/menu`, `organization/partial-settingmenu`, `user/partial-edit-tabmenu`, `layout/site-mng-layout`의 메뉴, `common/my-series-menu-tab` |
| 경로(breadcrumb) | 현재 위치. 마지막 항목은 링크가 아님. 이동 뒤 OOB로 갱신 | 없음(새 부품) |
| 페이지 머리 | 제목, 선택 설명, 주요 단추 | `project/header`, `organization/header`, 화면마다 다른 제목 |
| 본문 영역 `#shell-main` | 여백과 최대 폭 책임, htmx 교체 대상, 오류 때 일반 이동으로 물러남 | 각 레이아웃의 본문 래퍼 |
| 전환기와 알림 영역 | 토스트(`#yobiToasts`), 확인 대화상자(`#yobiDialog`), flash 메시지, 관리자 로그인 띠(로그인 ID가 `admin`일 때 상단 바 위), 업데이트 알림(관리자) | `common/scripts`, `layout/layout` |
| 로그인 모달 | 익명이 쓰기나 로그인 필요 단추를 누르면 연다 | `common/login-dialog` |
| 푸터 | 푸터 | `common/footer` |
| 키 수집기와 안내 | `data-shortcut` 선언을 한 번 수집, `?` 안내 대화상자를 수집된 키에서 자동 생성 | `ShortcutKey.js`, `help/keymap` |
| 첫 그림 스크립트 | `<head>` 맨 위에서 사이드바 접힘을 동기로 읽음(넓음 폭만) | `layout/layout`의 핀 스크립트 |

## 기존 골격에 빠졌거나 어색한 것

`ui-ia-gitlab.md` 4.2절의 맥락과 묶음을 230개 템플릿에 실제로 대 본 결과이다. 사실은 소스나 계약에서 확인한 것이고 제안은 "총괄이 판단할 것"으로 연결했다.

| # | 항목 | 확인한 사실 | 골격에서의 처리 | 제안 연결 |
|---|---|---|---|---|
| 1 | **사이트 관리의 데이터 화면** | `/sites/data`, `/sites/export`, `/sites/import`는 컨트롤러가 있으나 사이드바 13항목에 링크가 없고 어떤 템플릿에도 링크가 없다(admin-role-F22, `site-mng-layout.html:25-41`). v1.6 메뉴도 앞 8개뿐이었다 | 시스템 묶음에 항목이 없다 | 판단 12 |
| 2 | **사용자 공개 프로필** `user/view`(`/{user}`)와 그 탭(이슈, 코드 주고받기, 프로젝트) | 어느 맥락에도 속하지 않는다. 사용자 설정이 아니라 남의 프로필도 보는 화면이고, `{user}`가 조직 이름이면 조직 페이지로 303한다 | 4.2절에 맥락이 없다. 아바타 메뉴의 "프로필 페이지"로만 간다 | 판단 3 |
| 3 | **검색 결과 3종** | `search/result-site`(전역), `result-organization`, `result-project`는 상단 검색의 범위 선택이 여는 결과 화면이다. 사이드바 항목이 아니다. 조직과 프로젝트 범위는 각 맥락 셸 안에서 그려진다 | 골격은 검색을 상단 바에만 둔다. 결과 화면의 맥락이 정해져 있지 않다 | 판단 3 |
| 4 | **프로젝트 통계** | `project/statistics`는 영어 `Under Construction`뿐이고 어느 템플릿에도 링크가 없다(project-F11) | 항목이 없다. 만들 필요도 없다 | 판단 5 |
| 5 | **프로젝트 구독자 목록** | 프로젝트 머리의 구독자 수 링크(`view.canWatch`)로만 들어간다(`project/header.html:72`) | 사이드바 항목이 없다 | 판단 5 |
| 6 | **리뷰의 전역 목록** | `reviewthread/list`는 프로젝트 안 목록뿐이다. 전역의 "내 리뷰", "내 코드 주고받기"는 v1.6에도 없다 | 홈 사이드바에 없고 넣지 않는다(IA 8절 결정 10) | 판단 7 |
| 7 | **프로젝트 가져오기와 포크 흐름** | `project/importing`은 `project/create`의 단추로만 간다(`create.html:22`). 포크(`pullrequest/fork`, `clone`)는 프로젝트 홈의 단추로만 간다(`project/home.html:99`). `clone`은 진행 화면이다 | 만들기 흐름 화면이라 사이드바 항목이 아니다. 맥락이 없다 | 판단 4 |
| 8 | **프로젝트 위키 설정** | 위키 전용 설정 화면은 없다. `project/setting`의 메뉴 체크박스 `menu.wiki`(`setting.html:168-169`)가 위키 항목과 `W` 탭 노출만 정한다. 위키 화면에는 자체 오른쪽 `Pages` 사이드바(`wiki/shell`)가 있어 맥락 사이드바와 겹친다 | 문서 묶음에 위키만 있다 | 판단 9 |
| 9 | **알림 설정 화면** | `user/edit-notifications`는 사용자 설정의 탭 하나이고 홈의 **알림** 화면과 이름이 비슷하다. 4.2절의 "알림" 묶음은 항목이 이것 하나뿐이다 | 단독 묶음 | 판단 14 |
| 10 | **이전(Migration)** | `/migration`은 어떤 템플릿에서도 링크가 없다(확인함). 별도 레이아웃(`migration-layout`)이 푸터도 전환기 패널도 다르게 구현하고 패널은 항상 "Loading..."이다(shell-F9). 설정 `yona.github.allow-migration`이 꺼지면 403 | 맥락이 없다 | 판단 3 |
| 11 | **감사 로그가 세 곳, 이름이 둘** | 사이트(`site/audit`, 시스템 묶음 "감사 로그"), 조직(`organization/audit`, 설정 "감사 로그"), 사용자(`user/edit-audit`, 접근과 보안 "보안 기록")가 같은 조각 `audit/list`를 쓴다 | 세 맥락에 따로 있어 맞다. 이름만 다르다 | 맥락 횡단 표 |
| 12 | **오류 화면에서의 이동** | 오류 9개는 모두 "홈" 단추 하나이고(프로젝트 404만 "목록"), 기본 오류 5개는 맥락이 없는 `site-layout`(404는 별도 `layout/layout`과 자체 상단 바, 하드코딩된 피드백 주소)이다 | 오류 화면의 셸이 정해져 있지 않다 | 판단 2 |
| 13 | **도움말** | `help/toc`(`/_help`)로 가는 링크는 `notfound-default`의 자체 상단 바에만 있다(확인함). 일반 상단 바에는 없다 | 골격에 도움말 항목이 없다 | 판단 3 |
| 14 | **코드 비교 화면** | `code/compare`와 `compare-svn`을 가리키는 링크가 템플릿 어디에도 없다(확인함). 서버 코드도 이 주소를 만드는 곳을 찾지 못했다 | 코드 묶음에 항목이 없고 "커밋" 아래에 둔다 | 판단 3 |
| 15 | **설정 계열이 맥락이 아닌 전역 틀** | 조직 설정 10개, 사용자 설정 28개, 프로젝트 목록과 만들기 3개가 모두 `site-layout`을 쓴다(`shell.md` 2.2). 그래서 조직 설정 화면에는 조직 머리와 조직 메뉴가 없다 | 골격은 조직 설정을 조직 맥락에 넣으므로 템플릿 쪽이 틀린 것이다 | 판단 1 |
| 16 | **같은 화면이 두 맥락에서** | `issue/create`는 프로젝트 안(`/{o}/{p}/issueform`)과 전역(`/user/issues/new`, `/new/mine`) 둘에서 쓰인다. `board/create`는 게시판 글 쓰기, README, 이슈 템플릿, 저장소 파일 온라인 커밋을 모두 맡는다 | 맥락이 하나로 정해지지 않는다 | 판단 4 |
| 17 | **라벨의 위치** | 골격은 라벨을 설정에서 계획으로 옮긴다. 그런데 이슈 메뉴를 끄면 이슈 주소가 404이고 **라벨 관리 주소만 열려 있다**(`index.md`, 설정 화면이 쓴다). 사이드바 항목 숨김과 주소 규칙을 맞추려면 라벨 항목의 조건이 모호하다 | 계획 묶음의 항목 | 판단 6 |
| 18 | **중복 입구** | 홈 사이드바의 "알림"과 상단 바 알림 아이콘이 같은 주소 `/`이다. 홈 사이드바의 "프로젝트 목록"과 상단 바의 "전체 목록" 링크가 같은 주소이고 둘 다 `hideProjectListing`이면 사라진다(`navbar.html:26`) | 이미 승인된 구성이므로 두 입구를 유지 | 없음(기록만) |
| 19 | **사용자 OAuth 앱 항목의 조건** | 사용자 앱 사용을 끄면 `/user/editform/oauth-apps/new`가 403이다(oauth.md 3.3). 사용자 설정 탭 11개는 현재 조건이 없다 | "애플리케이션" 묶음의 항목은 조건부여야 한다 | 판단 14 |
| 20 | **위키와 리뷰의 단축키** | 프로젝트 맥락 키 `H B C I M P Q`에 `R`(리뷰)과 `W`(위키)가 없다(`shell-scripts.md` 5.2) | 키가 있는 항목과 없는 항목이 섞인다. 새 키를 만들지 않는다 | 없음 |

## 맥락 횡단 기능

같은 기능이 여러 맥락에 흩어져 있거나 같은 화면이 맥락에 따라 다르게 보이는 것이다. 템플릿은 지금 맥락마다 따로 구현된 것이 많고, 새 IA에서는 **사이드바 항목은 맥락마다 두되 본문 부품은 공유**하는 쪽이 자연스럽다. 이 표는 합치자는 제안이 아니라 사실의 정리이다(기능은 바꾸지 않는다).

| 기능 | 어디에 있는가(템플릿) | 서로 다른 점 | 새 IA에서의 자리 |
|---|---|---|---|
| 감사 로그 | `site/audit`, `organization/audit`, `user/edit-audit`(조각 `audit/list` 공유) | 범위가 사이트, 조직, 개인. 사이트와 조직은 내려받기와 무결성 검증이 있을 수 있음(`exportable`, `verifiable`) | 사이트 관리 > 시스템 > 감사 로그, 조직 > 설정 > 감사 로그, 사용자 설정 > 접근과 보안 > 보안 기록. 이름 통일 여부는 별도 결정 |
| OAuth 앱 | `site/oauth-apps`(사이트 정책), `organization/oauth-apps`, `oauth-app-form`, `oauth-app-secret`, `oauth-policy`(조직), `user/edit-oauth-apps`, `edit-oauth-app-form`, `edit-oauth-app-secret`(사용자), 조각 `oauth/partial-apps` 공유 | 사이트는 허용 설정, 조직은 앱과 정책(승인, 거부), 사용자는 만든 앱과 승인한 앱 | 사이트 설정 > OAuth 앱, 조직 설정 > 접근, 사용자 설정 > 애플리케이션 |
| 이슈 목록 | `issue/list`(프로젝트), `organization/issue-list`(조직), `issue/my-list`(내 이슈), `site/issue-list`(관리자), 사용자 페이지의 이슈 탭, 검색 결과 | 범위와 필터가 다름. 앞 세 개만 htmx(D-96), 같은 `issue.List.js` 공유 | 프로젝트 > 이슈, 조직 > 계획 > 이슈, 전역 > 내 작업 > 내 이슈, 사이트 관리 > 개요 > 이슈 |
| 게시판 목록 | `board/list`(프로젝트), `organization/board-list`(조직), `site/post-list`(관리자) | 조직 목록은 프로젝트 다중 선택과 정렬 링크가 있고 줄 모양은 `board/partial-list` 공유 | 프로젝트와 조직의 계획 > 게시판, 사이트 > 개요 > 게시물 |
| 코드 주고받기 목록 | `pullrequest/list`(프로젝트, htmx), `organization/pullrequest-list`(조직, htmx 아님), 사용자 페이지의 탭 | 프로젝트는 열림, 닫힘, 보냄 필터 탭. 조직은 열림만 | 프로젝트와 조직의 협업 > 코드 주고받기 |
| 프로젝트 목록 | `project/list`, `organization/view`(조직의 프로젝트), `site/project-list`, 사용자 페이지의 프로젝트 탭, 전환기의 프로젝트 탭, 검색 결과 | 보이는 범위가 다름(읽기 권한, 관리자 전체) | 전역 > 탐색 > 프로젝트 목록 외는 각 맥락 본문 |
| 구성원 | `project/members`, `organization/members`, `project/watchers`(구독자) | 역할 변경, 가입 신청 수락, 삭제 | 각 맥락의 설정 > 멤버. 구독자는 별도(골격 3번 항목) |
| 설정 일반, 이관과 삭제 | `project/setting`, `transfer`, `delete`, `change-vcs` / `organization/setting`, `delete-form` | 프로젝트는 메뉴 체크박스와 저장소 종류까지 있음 | 설정 > 일반, 고급(위험 구역) |
| 새 이슈 | `issue/create`를 프로젝트 안과 전역 둘이 씀(`/user/issues/new`, `/new/mine`) | 전역은 프로젝트 선택부터, `mine`은 개인 inbox | 상단 바 `+`와 프로젝트 계획 > 이슈 |
| 만들기 흐름 | `project/create`, `project/importing`, `organization/create`, `pullrequest/fork`, `clone` | 소유자가 사용자 또는 조직 | 상단 바 `+`(사이드바 항목 아님). 맥락 정하기는 판단 4 |
| 로그인 입구 둘 | `user/login`(전체 화면)과 `common/login-dialog`(모달) | 같은 `POST /users/login`. 모달은 익명이 쓰기 단추를 누를 때, 전체 화면은 403 로그인 필요와 직접 이동 | 셸 밖 화면 하나와 셸 안 모달 하나 |
| 비밀번호 | `user/edit-password`(변경), `user/reset-password`(재설정), `site/lost-password`(찾기) | 인증 상태가 다름 | 앞의 것은 사용자 설정, 뒤의 둘은 셸 밖 |
| 알림 | 홈 `index/notifications`(받은 알림), `user/edit-notifications`(설정), 상단 알림 아이콘(링크만), `common/notification-mail`(이메일) | 보기, 설정, 이메일 | 전역 > 내 작업 > 알림, 사용자 설정 > 알림 |
| 검색 | 상단 검색의 범위 3개(프로젝트, 그룹, 전체)와 결과 화면 3개, 전환기의 이름 필터, 목록 화면의 검색 폼 | 범위가 다름. 관리자의 "전체" 범위는 항상 보임 | 상단 바 한 곳과 결과 화면 3개 |
| 도움말 | `help/toc`, `help/keymap`, `help/markdown` | 화면, 모달, 편집기 접기 | 키 안내는 `?` 대화상자로 대체 |
| 첨부 파일 | `user/files`(내 파일), 업로더 조각(`common/file-uploader`, `upload-form`) | 목록 화면과 입력 부품 | 전역 > 내 작업 > 내 파일 |
| 라벨 | `project/issue-labels`, 이슈 목록과 폼의 라벨 select, `issue/partial-select-label`의 관리 링크 | 관리와 선택 | 판단 6 |

## 역할별 사이드바 가시성

계약의 "조건"과 권한 행렬(`project.md` 1.1, `project-settings.md` 1절과 12절, `organization.md` 2.3과 2.4, `admin-role.md` 1절과 8절, `site.md`)에서 뽑았다. "보임"은 항목이 사이드바에 나온다는 뜻이고 서버의 허용과 별개이다(서버는 숨김에 기대지 않는다, A-459).

| 항목 | 비로그인 | 로그인 일반 | 프로젝트 구성원 | 프로젝트 관리자(MANAGER) | 조직 관리자 | 사이트 관리자 |
|---|---|---|---|---|---|---|
| 전역: 내 작업(알림, 내 이슈, 내 파일) | 없음(소개 화면) | 보임 | 보임 | 보임 | 보임 | 보임 |
| 전역: 탐색(프로젝트 목록, 그룹 목록) | 사이트가 허용하고 `hideProjectListing`이 꺼졌을 때만 | `hideProjectListing`이 꺼지고 게스트가 아닐 때(게스트는 `/projects`가 `/`로 303) | 같음 | 같음 | 같음 | `hideProjectListing`이 켜지면 **403이므로 항목도 숨김**(관리자도 예외 없음) |
| 상단 바 렌치(사이트 관리) | 없음 | 없음 | 없음 | 없음 | 없음 | 보임 |
| 프로젝트 > 홈 | 읽기 권한이 있으면 보임 | 같음 | 보임 | 보임 | 조직 소유면 보임 | 보임 |
| 프로젝트 > 코드(파일, 커밋, 브랜치, 태그), 협업(코드 주고받기, 리뷰) | 메뉴 켜짐 + `codeAccessible` | 같음 | 보임 | 보임 | 코드 열람을 구성원으로 제한했으면 구성원이 아닐 때 **숨김** | 보임(`hasMember` 허용) |
| 프로젝트 > 계획(이슈, 마일스톤) | 메뉴 켜짐일 때만. **꺼지면 항목 숨김과 주소 404가 일치** | 같음 | 같음 | 같음 | 같음 | 같음(관리자도 404, 우회 없음) |
| 프로젝트 > 게시판 | 메뉴 켜짐일 때(꺼져도 목록 주소는 열리고 글쓰기 단추만 없음) | 같음 | 같음 | 같음 | 같음 | 같음 |
| 프로젝트 > 위키 | `menuSetting.wiki == true`일 때 | 같음 | 같음 | 같음 | 같음 | 같음 |
| 프로젝트 > 구독, 구독자 수 | 없음 | 공개 프로젝트이거나 구성원이면(`canWatch`) | 보임 | 보임 | 보임 | 보임 |
| 프로젝트 > 설정 묶음 전체 | 없음 | 없음 | 없음(일반 멤버는 불가) | 보임(가입 신청 수 배지) | 조직 소유 프로젝트면 보임 | 보임 |
| 프로젝트 설정 단축키 `Q` | 없음 | 없음 | 없음 | **보임(MANAGER 역할일 때만)** | 없음 | **없음, 서버는 허용**(`admin-role.md` 8절) |
| 설정 > 규칙, 배포 키, Git LFS | 해당 없음 | 해당 없음 | 해당 없음 | 저장소 종류가 `protectedRefs`, `deployKeys`, `lfsScreen` 능력이 있을 때(Subversion이면 없음) | 같음 | 같음 |
| 설정 > 고급 > 저장소 종류 변경 | | | | `menuSetting.code`가 켜졌을 때만 | 같음 | 같음 |
| 프로젝트 > 가입 신청과 탈퇴(머리 단추) | 가입 신청은 로그인 필요 | 역할이 없으면 가입 신청 | 탈퇴(`projectMember` + `LEAVE`) | 탈퇴 | 가입 신청 아님 | **가입 신청 아님, 탈퇴는 역할 행이 있는 프로젝트만** |
| 조직 > 홈, 이슈, 게시판, 코드 주고받기 | 보임(공개 범위 안에서) | 보임 | 보임 | 보임 | 보임 | 보임 |
| 조직 > 설정 묶음 | 없음 | 없음 | 없음 | 없음 | 보임(`canManage`) | 보임 |
| 사용자 설정(5묶음 11항목) | 없음(로그인 필요, 익명은 로그인 화면으로 303) | 보임. 단 OAuth 앱 항목은 사용자 앱 사용이 꺼지면 숨기는 것이 일치 | 보임 | 보임 | 보임 | 보임 |
| 사용자 설정 > 사용자 토큰의 `site_admin` 범위 | | | | | | 관리자만 고를 수 있음(항목이 아니라 화면 안) |
| 사이트 관리(4묶음 13항목) | **403**(익명도 로그인 폼이 아니라 403) | 403 | 403 | 403 | 403 | 보임. 업데이트 배지 `1`은 알림을 껐어도 남음 |
| 관리자 로그인 띠, 업데이트 알림 | 없음 | 없음 | 없음 | 없음 | 없음 | 로그인 ID가 `admin`일 때 띠(권한과 별개), 알림은 관리자에게만 |

## 영역별 사이드바 항목

### 전역(홈)

| 묶음 | 항목 | 주소 | 보이는 조건 |
|---|---|---|---|
| 내 작업 | 알림 | `/` | 로그인 |
| | 내 이슈(열린 수 배지, 열림과 닫힘 필터는 화면 안) | `/user/issues` | 로그인 |
| | 내 파일 | `/user/files` | 로그인 |
| 탐색 | 프로젝트 | `/projects` | `hideProjectListing` 꺼짐, 게스트 아님 |
| | 그룹 | `/orgs` | `hideProjectListing` 꺼짐 |
| (골격에 없음, 판단 3) | 도움말 | `/_help` | 누구나 |

### 프로젝트

(D-140, 2026-11-11: 프로젝트 좌측 메뉴는 묶음 제목 없이 지금의 가로 메뉴와 같은 이름과 순서의 평평한 목록이다. 코드와 설정은 선택되었을 때 하위 항목이 펼쳐진다.)

| 순서 | 항목 | 주소 | 보이는 조건 | 단축키 |
|---|---|---|---|---|
| 1 | 홈 | `/{o}/{n}` | 읽기 권한 | `H` |
| 2 | 코드(선택하면 하위 펼침) | `/{o}/{n}/code` | `menuSetting.code` + `codeAccessible` | `C` |
| | └ 파일 | `/{o}/{n}/code` | 같음 | |
| | └ 커밋 | `/{o}/{n}/commits` | 같음(Subversion도 있음) | |
| | └ 브랜치 | `/{o}/{n}/branches` | 같음 + `vcs.branches`(Git, Mercurial) | |
| | └ 태그 | `/{o}/{n}/tags` | 같음 + `vcs.tags`(Git, Mercurial) | |
| 3 | 이슈(열린 수 배지) | `/{o}/{n}/issues` | `menuSetting.issue`. **꺼지면 `issues`, `issue/*`, `issueform`이 404** | `I`, 화면 `N` |
| 4 | 코드 주고받기(열린 수 배지) | `view.pullRequestPath`(포크는 `/sentPullRequests`, 아니면 `/pullRequests`) | `menuSetting.pullRequest` + `vcs.pullRequests` + `codeAccessible` | `P` |
| 5 | 리뷰(열린 스레드 수 배지) | `/{o}/{n}/reviews` | `menuSetting.review` + `codeAccessible` | |
| 6 | 마일스톤 | `/{o}/{n}/milestones` | `menuSetting.milestone`. **꺼지면 `milestones`, `milestone/*`, `newMilestoneForm`이 404** | `M` |
| 7 | 게시판(글 수 배지) | `/{o}/{n}/posts` | `menuSetting.board` | `B`, 화면 `N` |
| 8 | 위키 | `/{o}/{n}/wiki` | `menuSetting.wiki == true` | |
| 9 | 설정(선택하면 하위 펼침, 가입 신청 수 배지) | `/{o}/{n}/settingform` | `view.canUpdate` | `Q`(MANAGER만) |
| | └ 일반 | `/{o}/{n}/settingform` | 같음 | |
| | └ 구성원 | `/{o}/{n}/members` | 같음 | |
| | └ 규칙 | `/{o}/{n}/setting/rulesets` | `vcs.protectedRefs` | |
| | └ 배포 키 | `/{o}/{n}/setting/deploykeys` | `vcs.deployKeys` | |
| | └ Git LFS(Mercurial은 이름 `LFS`) | `/{o}/{n}/setting/lfs` | `vcs.lfsScreen` | |
| | └ 웹훅 | `/{o}/{n}/webhooks` | 같음 | |
| | └ 라벨 | `/{o}/{n}/issue/labelsform` | 같음(이슈 메뉴를 꺼도 서버가 주소를 열어 두므로 항상 보임) | |
| | └ 위험 구역: 이전, 저장소 종류 변경, 삭제 | `/transfer`, `/changeVCS`, `/deleteform` | 이전과 삭제 항상, 저장소 종류 변경은 `menuSetting.code` | |

구독자 목록(`/watchers`)은 사이드바 항목이 아니라 프로젝트 머리의 구독자 수 링크(`canWatch`)로 들어간다.

**일치 규칙**: 이슈와 마일스톤 항목의 숨김 조건은 서버의 `checkMenuEnabled`(404)와 같은 값(`menuSetting.issue`, `menuSetting.milestone`)을 쓴다. 코드, 게시판, 위키는 서버가 아직 주소를 막지 않고 메뉴만 숨기므로 항목이 숨은 프로젝트에서도 주소는 열린다(`ui-ia-gitlab.md` 9절 끝 규칙). 사이드바 데이터의 조건 필드는 화면용이고 서버 검사를 대신하지 않는다.

### 조직

| 묶음 | 항목 | 주소 | 보이는 조건 |
|---|---|---|---|
| (개요) | 조직 홈 | `/organizations/{name}` | 누구나 |
| 계획 | 이슈 | `/organizations/{name}/issues` | 누구나 |
| | 게시판 | `/organizations/{name}/boards` | 누구나 |
| 협업 | 코드 주고받기 | `/organizations/{name}/pullrequests` | 누구나 |
| 설정 | 일반 | `/organizations/{name}/settingform` | `canManage`(조직 관리자 또는 사이트 관리자) |
| | 멤버 | `/organizations/{name}/members` | 같음 |
| | 접근: OAuth 앱 | `/organizations/{name}/oauth-apps` | 같음 |
| | 접근: OAuth 앱 정책 | `/organizations/{name}/oauth-policy` | 같음 |
| | 감사 로그 | `/organizations/{name}/audit` | 같음 |
| | 삭제(고급) | `/organizations/{name}/deleteForm` | 같음 |

조직 검색 범위 항목은 조직 구성원이나 조직 관리자만 본다(`navbar.html:55-58`, 사이트 관리자는 포함되지 않음, admin-role-F17).

### 사용자 설정

| 묶음 | 항목 | 주소 | 보이는 조건 |
|---|---|---|---|
| 프로필 | 프로필 수정 | `/user/editform` | 로그인 |
| 계정 | 비밀번호 변경 | `/user/editform/password` | 로그인 |
| | 이메일 설정 | `/user/editform/emails` | 로그인 |
| | 연결된 계정 | `/user/editform/linked-accounts` | 로그인 |
| 알림 | 알림 설정 | `/user/editform/notifications` | 로그인(단독 항목, 판단 14) |
| 접근과 보안 | 사용자 토큰 | `/user/editform/token` | 로그인 |
| | SSH 키 | `/user/editform/ssh` | 로그인 |
| | GPG 키 | `/user/editform/gpg` | 로그인 |
| | 2단계 인증 | `/user/editform/two-factor` | 로그인(강제 대상이면 다른 화면이 여기로 303) |
| | 보안 기록 | `/user/editform/audit` | 로그인 |
| 애플리케이션 | OAuth 앱 | `/user/editform/oauth-apps` | 로그인. 사용자 앱 사용이 꺼지면 `new`가 403이므로 항목 조건 필요 |

### 사이트 관리

| 묶음 | 항목 | 주소 | 보이는 조건 |
|---|---|---|---|
| 개요 | 사용자 | `/sites/userList` | 사이트 관리자(`@SiteManagerOnly`, 비관리자와 익명은 403) |
| | 프로젝트 | `/sites/projectList` | 같음 |
| | 이슈 | `/sites/issueList` | 같음 |
| | 게시물 | `/sites/postList` | 같음 |
| 메시지 | 메일 발송 | `/sites/mail` | 같음 |
| | 대량 메일 발송 | `/sites/massmail` | 같음 |
| 시스템 | 업데이트(새 버전이면 배지 `1`) | `/sites/update` | 같음 |
| | 시스템 진단 | `/sites/diagnostic` | 같음 |
| | 감사 로그 | `/sites/audit` | 같음 |
| | 데이터(후보, 판단 12) | `/sites/data` | 같음 |
| 설정 | 보안 | `/sites/security` | 같음 |
| | SSO | `/sites/sso` | 같음 |
| | OAuth 앱 | `/sites/oauth-apps` | 같음 |
| | LFS | `/sites/lfs` | 같음 |

활성 표시는 지금 경로 **정확히 같을 때만**이라 상세 화면에서 꺼진다(shell.md 2.3). 새 사이드바는 접두 일치로 맞춘다.

## 합치거나 없애는 화면과 새로 필요한 화면

**합치거나 없애는 것**(기능과 주소는 그대로)

| 대상 | 어디로 | 근거 |
|---|---|---|
| 사용자 오른쪽 패널 `#mySidenav`(`common/usermenu`) | 아바타 짧은 드롭다운 + 전환기. 목록 중복 삭제 | IA 4.2.2, D-134 |
| iframe 핀 사이드바(`layout/layout-framed`, `common/sidebar` 프레임 구조) | 사이드바 전환기. iframe 제거. `/user/sidebar`는 이동으로 유지 | IA 4.2.1 |
| 홈 탭 3개 `common/my-series-menu-tab` | 홈 사이드바 "내 작업" | IA 4.2.2 |
| 프로젝트 상단 메뉴 `project/menu`, 설정 탭 `project/partial-settingmenu` | 프로젝트 사이드바 | IA 5절 |
| 조직 상단 메뉴 `organization/menu`, 설정 탭 `organization/partial-settingmenu` | 조직 사이드바 | IA 5절 |
| 사용자 설정 탭 11개 `user/partial-edit-tabmenu` | 사용자 설정 사이드바 5묶음 | IA 5절 |
| 코드 보조 탭(파일, 커밋, 브랜치, 태그)과 이슈 보조 메뉴 | 코드와 계획 묶음 | IA 5절 |
| 레이아웃 5개(`layout`, `project-`, `organization-`, `site-`, `site-mng-layout`)와 `migration-layout` | 공통 셸 하나 + 맥락별 사이드바 조각 | IA 6절 |
| 단축키 안내 `help/keymap` | `?` 대화상자(수집된 키에서 자동 생성) | `ui-shortcuts.md` 6절 |
| `notfound-default`의 자체 상단 바와 푸터 | 공통 셸의 상단 바와 푸터 | `shell.md` 9.2 |
| 서버의 `isMac`(User-Agent) 판별 | 클라이언트 판별 | `ui-shortcuts.md` 5절 |

**합칠 수 있으나 이번에 하지 않는 것**: 이슈 목록 세 종류, 게시판 목록 둘, 감사 화면 셋처럼 템플릿이 이중 구현된 화면(맥락 횡단 표). 본문 부품을 공유하는 리팩터링일 뿐 IA 대응에는 필요 없다.

**새로 필요한 화면**: **없음.** 새 부품은 승인된 설계의 전환기, 경로, 페이지 머리, `?` 안내 대화상자, 알림 아이콘(링크만)뿐이고 모두 기존 데이터와 주소를 쓴다. 골격에 자리가 없는 기존 화면은 항목을 더할지만 정하면 된다(판단 3, 5, 12).

## swap-safe 예비 판정 근거

`esm/**`에서 모듈 최상위나 호출마다 문서와 창 수준 핸들러, 타이머를 등록하는 곳을 읽었다(2026-11-11, 소스 확인). 아래 모듈을 쓰는 화면은 `htmx:load` 규약 모듈(`Page.js`, 가칭)로 고치기 전까지 N-일반으로 두었다.

| 모듈과 줄 | 등록하는 것 | 이 모듈을 쓰는 템플릿 |
|---|---|---|
| `CommentForm.js:37-38` | `$(window).on("keydown")`, `$(window).on("beforeunload")` | `issue/view`, `board/view`, `code/diff`, `code/svn-diff`, `pullrequest/changes`(`common/comment-form` 포함 화면) |
| `issue.View.js:421` | `setInterval`(타임라인 갱신) | `issue/view` |
| `issue.Write.js:70`, `board.Write.js:63` | `$(window).on("beforeunload")` | `issue/create`, `issue/edit`, `board/create`, `board/edit` |
| `code.Browser.js:102` | `$(window).on("hashchange")` | `code/view` |
| `code.Diff.js:113,330,348,589` | `hashchange`, 창 이벤트, `scroll` | `code/diff`, `code/compare`, `pullrequest/changes` |
| `code.SvnDiff.js:501,503` | `scroll`, `resize` | `code/svn-diff`, `code/compare-svn` |
| `code.Nohead.js:50,51,75` | 창 `focus`와 `blur`, `setInterval` | `code/nohead`, `nohead-hg`, `nohead-svn` |
| `git.View.js:203,210` | 사용자 입력 감시, `setInterval`(상태 폴링) | `pullrequest/view` |
| `pullrequest.Reviewers.js:101,107`, `review.CommentEdit.js:21` | 모듈 최상위 `$(document).on("click")` | `pullrequest/view`, `pullrequest/changes` |
| `Migration.js:596,603` | `setInterval`, `beforeunload` | `migration/home` |
| `issue.List.js:210` | `$(document).on("htmx:afterSwap")`(호출마다 중복) | `issue/list`, `issue/my-list`, `organization/issue-list`. 목록 안 교체는 H-기존으로 문제가 없으나 **셸 이동으로 진입하면 중복**이므로 가드가 필요 |

**안전하다고 본 것**(근거): `Mention.js:154-161`(설치 가드 `bInstalled`), `Pagination.js:345`, `ui/Tabs.js`, `ui/SubmitOnce.js`, `ui/ItemSearch.js`(모두 `index.js`로 한 번만 로드되고 모듈 최상위에서 한 번 등록), `temporarySaveHandler.js:181-182`(한 번만 도는 `setInterval`). 요소에 바인딩하는 `project.*`, `organization.*`, `user.Setting`, `site.MassMail`, `milestone.*` 모듈은 `loadModule`이 이동마다 다시 호출되어도 새 요소에 붙으므로 후보로 두었으나, 성공 뒤 `document.location.reload/replace`를 쓰는 곳(`project.Member.js:206`, `organization.Member.js:173,263`, `organization.View.js:71,73`, `project.Transfer.js:66,68`, `issue.LabelEditor.js:339`)은 `HX-Redirect`로 바꿔야 한다. `ShortcutKey.js`는 `beforeunload`에서 스스로 해제한다(F3)는 점이 htmx 이동에서는 문제가 아니나(`beforeunload`가 일어나지 않음) 선언형 수집기로 바꿀 때 규약이 필요하다.

## 클라이언트 저장 대응

| 지금의 저장 | 쓰는 템플릿과 모듈 | 새 저장소 |
|---|---|---|
| localStorage: 현재 경로 문자열과 댓글별 키(초안), 초안 저장 시각 JSON | `issue/create`, `edit`, `board/create`, `edit`, `milestone/create`, `edit`, `pullrequest/create`, `edit`, 댓글 폼과 리뷰 폼(`temporarySaveHandler`, `CommentForm.js`). `issue/edit.html`은 템플릿에 인라인 `localStorage` 호출이 있어 `esm/store` 경유로 바꿔야 한다 | `drafts`(30일). 위키 편집은 초안을 쓰지 않는다 |
| localStorage: `shallWeOpenLeftNavigation`, `sidebarActiveMenu` | `layout/layout`, `common/sidebar` | 사이드바 접힘은 새 키 `shellSidebarCollapsed` localStorage 거울(넓음 폭만, 폰과 폴드 접힘은 읽기, 쓰기, 이전 모두 제외). 전환기 탭은 `prefs` |
| localStorage: `useTwoColumnMode` | `twoColumnMode.js`, 목록 화면 | `prefs` + 첫 그림 거울(레이아웃 값) |
| localStorage: `showSubtasksAlways` | `showSubtask.js`, 이슈 목록, 내 이슈, 사용자 페이지 | `prefs` |
| localStorage: `yobitab-<id>` | `ui/Tabs.js`, 사용하는 곳은 `site/sso`(`#sso-tabs`) 하나뿐이다(소스 확인) | `prefs` |
| localStorage: `yobi-intro` | `index/notifications` 인라인 스크립트 | `prefs`(`yobi-intro`) |
| sessionStorage: htmx 이동 기록 | htmx 확장 | 그대로(동기 API 필요) |
| 전환기 목록(저장 없음, 서버가 그림) | `common/sidebar`, `index/*-list` | `switcher`(조각, 7일), 최근 방문은 `visited` |
| 검색 최근 검색어 | 없음(새 기능) | `search-history`(판단 11) |
| 비밀 값(토큰, 백업 코드, 2단계 시드, OAuth 비밀) | `user/edit-token`, `edit-two-factor-codes`, `edit-two-factor-setup`, `edit-oauth-app-secret`, `organization/oauth-app-secret` | **저장 금지**, htmx 이동 이력에도 남기지 않으려고 N-일반 |

## 전수 대응표

아래 표가 템플릿 230개를 영역별로 한 줄씩 담는다. 같은 약어(H-셸 등)의 정의는 "읽는 법"에 있다.

### 1. 셸과 공통(layout, common, index, error, help)

| 템플릿 | 종류 | 새 맥락과 사이드바 항목 | 경로(breadcrumb)와 페이지 머리 | 주소(바꾸지 않음) | Bootstrap 구성 요소 | htmx 사용 | 단축키 선언 후보 | 클라이언트 저장(IndexedDB) | 골격 적합도 | 메모(계약 근거와 주의) |
|---|---|---|---|---|---|---|---|---|---|---|

| `common/analytics.html` | 조각 | 셸 `<head>` 분석 태그(설정이 켜졌을 때만) | 해당 없음 | 전체 | - | - | - | - | 적합 | 화면 종류만 마스킹해 보내므로 htmx 이동마다 `page_view`를 직접 보내야 함(미확인) |
| `common/attachment-file.html` | 조각 | 첨부 한 줄(업로더 안) | 해당 없음 | - | `list-group-item`, `btn-close` | 조각, 쓰이는 화면: 이슈, 게시글, 마일스톤, 코드 주고받기 편집 화면 | - | - | 적합 | `data-href`에 컨텍스트 경로 접두어가 없음(shell-F28) |
| `common/calendar.html` | 조각 | 달력 위젯 로더 | 해당 없음 | - | `dropdown`(달력 팝업은 라이브러리) | 조각, 쓰이는 화면: 이슈 목록, 보기, 작성, 수정 | - | - | 적합 | vanilla-calendar-pro 3.4.0 + `esm/ui/Calendar.js` |
| `common/child-comment-form.html` | 조각 | 한 줄 댓글 입력 폼 | 해당 없음 | `POST` 댓글 주소 | `form-control`, `btn btn-success` ; 옛 표지: ybtn-success, ybtn | 조각, 쓰이는 화면: 이슈 보기, 게시글 보기 | `CTRL+ENTER`(제출), `Esc`(닫기) | - | 적합 | 단추 글자 `OK`는 영어 고정 |
| `common/child-comments.html` | 조각 | 한 줄 댓글 목록 | 해당 없음 | - | `list-group` ; 옛 표지: data-toggle="comment-delete" | 조각, 쓰이는 화면: 이슈 보기, 게시글 보기 | - | - | 적합 | 삭제는 `[data-toggle=comment-delete]` |
| `common/comment-count.html` | 조각 | 댓글 수 배지 | 해당 없음 | - | `badge` ; 옛 표지: yobicon | 조각, 쓰이는 화면: 이슈, 게시글, 리뷰, 사용자 목록들 | - | - | 적합 |  |
| `common/comment-delete-modal.html` | 조각 | 댓글 삭제 확인 모달 | 해당 없음 | `DELETE <댓글 주소>` | `modal` ; 옛 표지: ybtn, ybtn-danger | 조각, 쓰이는 화면: 이슈 보기, 게시글 보기, 코드 비교, 코드 주고받기 변경 | - | - | 적합 | `yona.Comment.init`이 인라인으로 돌아 이동 때마다 다시 실행되면 위임이 쌓임 |
| `common/comment-form.html` | 조각 | 댓글 작성 폼 | 해당 없음 | `POST` 댓글 주소 | `form-control`, `btn`, `nav nav-tabs`(편집, 미리보기) ; 옛 표지: ybtn, ybtn-success, ybtn-disabled | 조각, 쓰이는 화면: 이슈 보기, 게시글 보기, 코드 비교, 코드 주고받기 변경 | `CTRL+ENTER`, `CTRL+SHIFT+ENTER`(이슈) | 초안 `drafts`(경로 키와 댓글별 키) | 적합 | `CommentForm.js:37-38`이 `window` 키 입력과 이탈 확인을 등록해 이 조각을 쓰는 화면은 일반 이동 |
| `common/comment-update-form.html` | 조각 | 댓글 수정 폼 | 해당 없음 | `POST {action}/{id}` | `form-control`, `btn`, `form-check` ; 옛 표지: ybtn, ybtn-info, ybtn-cancel | 조각, 쓰이는 화면: 이슈 보기, 게시글 보기 | - | 초안 `drafts` | 적합 |  |
| `common/editor.html` | 조각 | 마크다운 편집기(탭, 도구줄, 미리보기) | 해당 없음 | `POST /markdown/{owner}/{project}` | `nav nav-tabs`, `btn-group`, `form-control` ; 옛 표지: ybtn-small, ybtn, yobicon, ybtn-warning | 조각, 쓰이는 화면: 편집과 작성 화면 13곳 | `Tab`, `Shift+Tab`(들여쓰기) | 초안 `drafts`, 안내 `draft.saved` | 적합 | `Mention`은 설치 가드가 있어 이동에 안전(`Mention.js:154-161`) |
| `common/file-uploader.html` | 조각 | 첨부 업로더 | 해당 없음 | `POST /files` | `list-group`, `progress`, `btn` ; 옛 표지: yobicon | 조각, 쓰이는 화면: 편집과 작성 화면 | - | - | 적합 |  |
| `common/footer.html` | 조각 | 푸터 | 해당 없음 | 전체 | `footer` | - | - | - | 적합 | 프로젝트, 조직, 사이트 레이아웃이 포함(`shell.md` 7) |
| `common/importmap.html` | 조각 | 셸 `<head>` import map | 해당 없음 | 전체 | - | - | - | - | 적합 | 모듈 태그보다 앞에 있어야 한다(Firefox) |
| `common/login-dialog.html` | 조각 | 익명 셸의 로그인 모달 | 해당 없음 | `POST /users/login`, `/users/login/2fa` | `modal`, `form-floating`, `btn` ; 옛 표지: ybtn, ybtn-primary, yobicon | N-일반: 로그인 성공 뒤 전체 새로 고침이 계약 | - | - | 적합 | `/users/…` 경로에서는 모달 처리기를 달지 않음(`LoginDialog.js:33-35`). 계약 `shell-common-fragments.md` 1.7 |
| `common/markdown.html` | 조각 | 마크다운 렌더러 설정과 스크립트 로드 | 해당 없음 | `POST /markdown/{owner}/{project}` | - | 조각, 쓰이는 화면: 마크다운을 보이는 모든 화면 | - | - | 적합 |  |
| `common/my-series-menu-tab.html` | 조각 | 없앰(홈 탭 3개가 홈 사이드바 "내 작업"으로 감) | 해당 없음 | - | `nav nav-tabs`(삭제) ; 옛 표지: ybtn | 해당 없음 | - | - | 적합 | 쓰는 화면: 알림, 내 이슈, 내 파일 |
| `common/navbar.html` | 조각 | 상단 바(전역 부품) | 해당 없음 | 전체 | `navbar`, `dropdown`(`+` 메뉴, 검색 범위), `form-control`, `btn` ; 옛 표지: yobicon, data-toggle="search-scope", ybtn | 검색 범위 선택은 일반 폼 제출(`hx-boost` 후보 아님) | `ALT+S`(`accesskey="S"`)와 `/` 새 키로 검색 초점, `data-shortcut`으로 선언 | - | 적합 | `+` 4항목, 내 이슈 배지, 렌치(관리자만), 알림 아이콘(링크만), 검색 범위(프로젝트, 그룹, 전체)는 지금 조건 그대로(`admin-role.md` 1-1) |
| `common/notification-mail.html` | 이메일이나 비화면 | 이메일 본문 조각(화면 아님) | 해당 없음 | - | - | 해당 없음 | - | - | 적합 | 새 IA 대상 아님. 이메일 클라이언트 호환 마크업이라 BS 대상 아님 |
| `common/partial-history.html` | 조각 | 이슈와 게시글의 변경 이력 영역 | 해당 없음 | - | `accordion` 또는 `collapse` ; 옛 표지: ybtn-small, ybtn-info, ybtn | 조각, 쓰이는 화면: 이슈 보기, 게시글 보기 | - | - | 적합 |  |
| `common/review-form.html` | 조각 | 코드 리뷰 댓글 폼 | 해당 없음 | `POST` 리뷰 주소 | `form-control`, `btn` ; 옛 표지: ybtn-small, ybtn, ybtn-default, ybtn-success | 조각, 쓰이는 화면: 코드 비교, 코드 주고받기 변경 | - | 초안 `drafts` | 적합 |  |
| `common/scripts.html` | 조각 | 셸 하단 공통 초기화(확인 대화상자 `#yobiDialog`, 알림 `#yobiToasts`, 전역 키, flash 메시지) | 해당 없음 | 전체 | `modal`, `toast` ; 옛 표지: ybtn-info, ybtn, data-toggle="search-scope" | 문서 수준 초기화가 많아 `htmx:load` 규약 모듈로 쪼개야 함 | `A` `U` `CTRL+ENTER`를 `data-shortcut`으로 | - | 적합 | `yona.Csrf.js`가 이 조각에서 로드됨(`scripts.html:41`). flash는 JS 문자열 리터럴에 들어가는 구조라 htmx 이동 때 재실행되지 않음(총괄 판단 15번) |
| `common/select2.html` | 조각 | select2 로더와 mustache 템플릿 5개 | 해당 없음 | - | `form-select`(select2 래핑) | 조각, 쓰이는 화면: 목록, 작성, 수정 화면 18곳 | - | - | 적합 | 기존 라이브러리 의존 그대로 |
| `common/sharer-count.html` | 조각 | 공유자 수 | 해당 없음 | - | `badge` ; 옛 표지: yobicon | 조각, 쓰이는 화면: 이슈 목록 | - | - | 적합 |  |
| `common/show-subtasks-checkbox.html` | 조각 | 하위 이슈 항상 보기 체크 | 해당 없음 | - | `form-check form-switch` | 조각, 쓰이는 화면: 이슈 목록, 내 이슈, 사용자 페이지 | - | `prefs`(`showSubtasksAlways`) | 적합 |  |
| `common/sidebar.html` | 조각 | 사이드바 맨 위 전환기(즐겨찾기, 프로젝트 하위 4탭, 최근 읽은 이슈, 이름 필터, 새로고침) | 해당 없음 | 전체 | `offcanvas`, `nav nav-tabs`, `list-group`, `input-group` ; 옛 표지: yobicon | `hx-get` 조각 지연 불러오기(`load`), 이름 필터 `input changed delay:250ms` + `hx-sync` | `F` `ㄹ` | `switcher`(조각), `prefs`(마지막 탭 `sidebarActiveMenu`) | 적합 | 예전 `sidebarActiveMenu` 키는 `prefs`로 이전. 목록 조각은 비밀 값을 담지 않아야 함 |
| `common/tasklist-bar.html` | 조각 | 할 일 목록 진행 막대 | 해당 없음 | - | `progress` | 조각, 쓰이는 화면: 이슈 보기, 게시글 보기 | - | - | 적합 |  |
| `common/two-column-mode-checkbox-area.html` | 조각 | 2단 보기 체크 | 해당 없음 | - | `form-check form-switch` | 조각, 쓰이는 화면: 이슈 목록, 게시판 목록, 풀 리퀘스트 목록, 조직 목록, 사용자 페이지 | - | `prefs`(`useTwoColumnMode`)와 첫 그림 거울(localStorage) | 적합 | IA 3절에 따라 레이아웃 값이라 동기 거울 대상 |
| `common/upload-form.html` | 조각 | 업로드 폼 틀 | 해당 없음 | `POST /files` | `form-control`, `progress` ; 옛 표지: yobicon | 조각, 쓰이는 화면: file-uploader | - | - | 적합 |  |
| `common/usermenu.html` | 조각 | 아바타 짧은 드롭다운(머리, 프로필, 사용자 설정, 로그아웃)과 `+` 메뉴. 오른쪽 패널 `#mySidenav`는 없앰 | 해당 없음 | 전체 | `dropdown`(좁음은 `offcanvas-bottom` 시트), `list-group` ; 옛 표지: yobicon, ybtn-success, ybtn | 드롭다운은 일반 링크, 목록 탭은 전환기로 이동 | `F`(`ㄹ`)는 전환기 열기와 이름 필터 초점으로 바뀜 | 전환기 조각을 `switcher`에 보관(7일, 로그아웃 때 삭제) | 적합 | 패널 전역값 6개와 `Usermenu.js`의 `keypress`는 선언형 키로 흡수(`ui-shortcuts.md` 6절) |
| `common/vote-count.html` | 조각 | 추천 수 배지 | 해당 없음 | - | `badge` ; 옛 표지: yobicon | 조각, 쓰이는 화면: 이슈 목록, 하위 이슈, 사용자 페이지 | - | - | 적합 |  |
| `error/badrequest-default.html` | 전체 화면 | 전역(사이드바 없음, 상단 바만) | 머리 제목만. 경로 없음 | 오류가 난 모든 주소(400) | `alert alert-warning`, `btn` ; 옛 표지: ybtn-info, ybtn, project-page-wrap | N-일반: 오류 화면은 항상 전체 HTML(`shell.md` 9.1)이라 조각 교체 대상이 아님 | 전역만 | - | 어색함 | 오류 후 이동(홈 단추 하나)이 전부라 맥락이 사라짐. 총괄 판단 2번 |
| `error/badrequest.html` | 전체 화면 | 프로젝트 맥락 유지(프로젝트 사이드바) | 프로젝트 경로 | 오류가 난 프로젝트 주소(400) | `alert` ; 옛 표지: project-page-wrap, ico-err2 | N-일반: 오류 화면은 항상 전체 HTML | 전역 + 프로젝트 맥락 키 | - | 적합 | `menuType`에 따라 활성 메뉴를 정함 |
| `error/forbidden-default.html` | 전체 화면 | 전역(상단 바만) | 머리 제목만 | 403 | `alert alert-danger`, `btn` ; 옛 표지: ybtn-primary, ybtn, project-page-wrap | N-일반: 오류 화면은 항상 전체 HTML | 전역만 | - | 어색함 | 사이트 관리자 검사 실패도 이 화면(`error.auth.unauthorized.waringMessage`) |
| `error/forbidden-organization.html` | 전체 화면 | 조직 맥락 유지 | 조직 경로 | 조직 안 403 | `alert` ; 옛 표지: project-page-wrap, ico-err2 | N-일반: 오류 화면은 항상 전체 HTML | 전역만 | - | 적합 |  |
| `error/forbidden.html` | 전체 화면 | 프로젝트 맥락 유지 | 프로젝트 경로 | 프로젝트 안 403 | `alert`, 익명이면 `btn`(로그인) ; 옛 표지: ybtn-primary, ybtn, project-page-wrap | N-일반: 오류 화면은 항상 전체 HTML | 전역 + 프로젝트 맥락 키 | - | 적합 | 익명은 로그인 모달 단추 |
| `error/internal-server-error-default.html` | 전체 화면 | 전역(상단 바만, 셸이 못 그려지면 최소 문서) | 머리 제목만 | 500 | `alert alert-danger` ; 옛 표지: ybtn-primary, ybtn, project-page-wrap | N-일반: 오류 화면은 항상 전체 HTML | 전역만 | - | 어색함 | 오류 처리기는 `LayoutModelAdvice`가 적용되지 않아 모델을 직접 채움(`GlobalErrorHandler.kt:95-99`). 총괄 판단 2번 |
| `error/notfound-default.html` | 전체 화면 | 전역(상단 바만) | 머리 제목만 | 404 | `alert`, `btn` ; 옛 표지: ybtn-info, ybtn, project-page-wrap | N-일반: 오류 화면은 항상 전체 HTML | 전역만 | - | 어색함 | 지금 `layout/layout`을 쓰며 자체 상단 바와 푸터, 하드코딩된 피드백 주소(`github.com/nforge/yobi/issues`)를 가짐(`shell.md` 9.2) |
| `error/notfound.html` | 전체 화면 | 프로젝트 맥락 유지(종류별 활성 항목) | 프로젝트 경로 | 프로젝트 안 404 | `alert`, `btn`(목록으로) ; 옛 표지: ybtn-primary, ybtn, project-page-wrap | N-일반: 오류 화면은 항상 전체 HTML | 전역 + 프로젝트 맥락 키 | - | 적합 | `backUrl`은 종류별(이슈, 게시판, 마일스톤, 코드는 설정 화면) |
| `error/request-text-entity-too-large.html` | 전체 화면 | 전역(상단 바만) | 머리 제목만 | 413(폼 본문 한도 초과, 쓰기 화면 6곳이 반환) | `alert` ; 옛 표지: project-page-wrap, ico-err2 | N-일반: 오류 화면은 항상 전체 HTML | 전역만 | - | 어색함 | 입력한 글이 사라지는 화면이라 초안 복원 안내가 필요하나 새 기능이므로 적지 않음. 관리자에게만 설정 안내가 보임 |
| `help/keymap.html` | 조각 | 단축키 안내(`?`로 여는 모달). 수집된 키에서 자동 생성한 표로 대체 | 해당 없음 | - | `modal`, `table` ; 옛 표지: ybtn, ybtn-small, ybtn-mini, ybtn-inverse | 해당 없음(클라이언트가 만든 대화상자) | `?`가 열고 `Esc`가 닫음 | - | 적합 | 서버의 `isMac`(User-Agent) 판별을 클라이언트 판별로 바꿈(`ui-shortcuts.md` 5절) |
| `help/markdown.html` | 조각 | 마크다운 문법 도움말(편집기 안 접기) | 해당 없음 | - | `collapse` 또는 `accordion` ; 옛 표지: data-toggle="markdown-help", ico-like | 조각, 쓰이는 화면: 편집기 | - | - | 적합 | 본문은 한국어만 있음 |
| `help/toc.html` | 전체 화면 | 전역 > (자리 없음: 도움말 항목이 골격에 없음) | `도움말` 한 줄, 머리 제목 `도움말` | `/_help` | `accordion` ; 옛 표지: yobicon | H-셸: `#shell-main`, 링크 `hx-boost`(swap-safe 판정 시험 뒤 옵트인) | `?`는 단축키 안내이므로 도움말 화면과 별개 | - | 자리 없음 | 어떤 템플릿에서도 이 화면으로 가는 링크가 없다(`notfound-default`의 상단 바에만 있음, 확인함). 상단 바나 사이드바 하단 후보(총괄 판단 3번) |
| `index/menu-project.html` | 조각 | 전환기의 프로젝트 한 줄 | 해당 없음 | `/{owner}/{name}/go` | `list-group-item` ; 옛 표지: yobicon | 조각, 쓰이는 화면: 전환기 조각 | `F` | `switcher` | 적합 | 별 토글은 `POST /user/favoriteProjects/{id}` |
| `index/my-organization-list.html` | 조각 | 전환기 "즐겨찾기" 탭 내용 | 해당 없음 | - | `list-group`, `accordion`(조직 묶음) ; 옛 표지: yobicon | 조각, 쓰이는 화면: 전환기, `hx-get` 지연 불러오기 | `F` | `switcher` | 적합 |  |
| `index/my-project-list.html` | 조각 | 전환기 "프로젝트" 탭(하위 4탭) | 해당 없음 | - | `nav nav-pills`(하위 탭), `list-group` ; 옛 표지: nav-subtab | 조각, 쓰이는 화면: 전환기 | `F` | `switcher`, `prefs` | 적합 |  |
| `index/my-recent-issue-list.html` | 조각 | 전환기 "최근 읽은 이슈" 탭 | 해당 없음 | - | `list-group` | 조각, 쓰이는 화면: 전환기 | `F` | `switcher`, `visited` | 적합 |  |
| `index/notifications.html` | 전체 화면 | 전역(홈) > 내 작업 > 알림(`/`). 익명은 사이드바 없이 소개와 로그인 | `알림` 한 줄, 머리는 제목 `알림` + 소개 접기 블록. 익명은 머리 없음 | `/`, `/users/login`, `/-_-api`, `/-_-api/v1/`, `/notifications` | `list-group`, `collapse`(소개 블록), 빈 상태 `p.text-muted`, 이어 붙이기 `btn` ; 옛 표지: ybtn-success, ybtn, yobicon | H-셸: `#shell-main`, 링크 `hx-boost`(swap-safe 판정 시험 뒤 옵트인) 알림 이어 붙이기는 `hx-get` + `beforebegin`(조각 `partial-notifications`). 홈 탭 3개는 사이드바 항목이라 탭 지연 불러오기 불필요 | - | `prefs`(`yobi-intro` 소개 접힘) | 적합 | 탭 3개를 사이드바로 옮기면 `common/my-series-menu-tab` 불필요. 소개 접힘은 인라인 스크립트가 localStorage에 직접 씀(`notifications.html:70,73`) |
| `index/partial-intro.html` | 조각 | 익명 홈의 사이트 소개 | 해당 없음 | `GET /`(익명) | `card` 또는 `jumbotron` 대체 `p-5 bg-body-tertiary rounded-3` ; 옛 표지: yobicon, ybtn-success, ybtn-padding, ybtn | 조각, 쓰이는 화면: 홈 | - | - | 적합 |  |
| `index/partial-notifications.html` | 조각 | 알림 목록 이어 붙이기 조각과 More 단추 | 해당 없음 | `GET /notification?from&limit` | `list-group`, `btn` ; 옛 표지: yobicon, ybtn, data-toggle="learnmore" | H-조각: More는 `hx-get` + `hx-swap="beforebegin"`, 마지막이면 단추 제거 | - | - | 적합 | 40, 49개에서 More가 사라지는 것까지 확인됨(A-456) |
| `index/usermenu-tab-content-list.html` | 조각 | 전환기 조각(세 탭 내용) | 해당 없음 | `GET /user/usermenuTabContentList` | `tab-content` | `hx-get` + `hx-trigger="load"`. 보관본을 먼저 그림(stale-while-revalidate) | `F` | `switcher` | 적합 | 익명도 빈 내용으로 응답(`UserMenuTest.kt:281`) |
| `layout/layout-framed.html` | 레이아웃 | 없앰(iframe 핀 사이드바, 전환기로 흡수) | 해당 없음 | `GET /user/sidebar?path=&hash=`(주소 계약, 이동으로 유지) | 해당 없음 ; 옛 표지: yobicon | N-일반: `path#hash`로 되돌리는 이동만 남음 | `F`는 전환기 열기로 바뀜 | 예전 키 `shallWeOpenLeftNavigation`을 넓음에서만 한 번 이전하고 지움(IA 4.5) | 적합 | `hash` 파라미터가 없으면 500(v1.6 재현, `UserMenuController.kt:39-42`). 이동으로 바뀔 때 이 500을 유지할지 총괄 판단 8번 |
| `layout/layout.html` | 레이아웃 | 공통 셸 문서 틀(상단 바, 사이드바 슬롯, 경로 슬롯, 페이지 머리 슬롯, `#shell-main`, 알림 영역, 푸터) | 슬롯을 제공(경로와 머리는 화면이 채움) | 모든 일반 화면 | `navbar`, `offcanvas`(좁음), `breadcrumb`, `toast`, `modal` ; 옛 표지: yobicon, ybtn-small, ybtn | 본문 표지 `shell-main`, 조각 응답 규약(`Vary: HX-Request`), OOB 갱신 슬롯 | 전역 키 수집기 한 번 등록(`data-shortcut`) | `<head>` 첫 인라인 스크립트가 `shellSidebarCollapsed`를 동기로 읽음(localStorage 거울, 넓음 폭만) | 적합 | 핀 이동 스크립트(`shallWeOpenLeftNavigation`, 모든 이동이 이중 로드, F6)가 사라지고 iframe 분기(`window.parent`)도 없어진다(`ui-contract/shell.md` 2.1, 6.2) |
| `layout/migration-layout.html` | 레이아웃 | 공통 셸에 합침(푸터와 분석 태그가 없는 변형) | 해당 없음 | `/migration` | 상단 바만 쓰는 최소 셸 ; 옛 표지: yobicon, ybtn-small, ybtn | N-일반: Vue 앱이 모듈 최상위에서 `setInterval`과 `beforeunload`를 등록(`Migration.js:596,603`) | 전역 키만 | 사용자 패널 코드를 자체 구현(폭 420px)하는 인라인 스크립트가 사라짐 | 어색함 | `Usermenu.js`와 전역값이 없어 패널이 항상 "Loading..."(shell-F9). 구글 글꼴 3종을 외부에서 불러온다(`migration-layout.html:21-23`) |
| `layout/organization-layout.html` | 레이아웃 | 조직 맥락 사이드바 조각을 끼우는 틀 | 경로 `조직 / 섹션 / 페이지` | `/organizations/{name}/...` | `offcanvas` 사이드바, `nav flex-column` | 셸 이동 대상 `#shell-main` | `data-shortcut` 없음(조직 키는 v1.6에 없음) | - | 적합 | 지금은 설정 계열 화면이 이 틀이 아니라 `site-layout`을 쓴다. 새 틀에서는 조직 설정도 이 맥락이어야 한다(총괄 판단 1번) |
| `layout/project-layout.html` | 레이아웃 | 프로젝트 맥락 사이드바 조각을 끼우는 틀(56개 화면이 사용) | 경로 `소유자 / 프로젝트 / 섹션 / 페이지` | `/{owner}/{project}/...` | `offcanvas` 사이드바, `nav flex-column`, `collapse`(하위 항목) | 셸 이동 대상 `#shell-main` | 맥락 키 `H B C I M P Q`를 사이드바 항목 데이터에서 파생 | - | 적합 | `project/header`(로고, 즐겨찾기 별, 가입 신청, 구독)가 페이지 머리로 옮겨 간다 |
| `layout/site-layout.html` | 레이아웃 | 맥락 없는 전역 틀(상단 바만) 또는 맥락별 사이드바 틀의 공통부 | 맥락에 따라 다름 | 여러 주소 | `navbar` | 셸 이동 대상 `#shell-main` | - | - | 어색함 | 지금 사용자 설정 28, 조직 설정 10, 프로젝트 목록과 만들기 3, 로그인과 가입, 오류, 도움말을 모두 이 한 틀이 맡는다(`shell.md` 2.2). 새 틀에서는 맥락마다 나눠야 한다 |
| `layout/site-mng-layout.html` | 레이아웃 | 사이트 관리 사이드바 조각(13항목에 `/sites/data` 추가 후보) | `사이트 관리 / 묶음 / 항목` | `/sites/*` | `list-group` 또는 `nav flex-column`, `badge`(업데이트 1) | 셸 이동 대상 `#shell-main` | - | - | 적합 | 활성 표시가 `requestPath` 정확 일치라 상세 화면에서 꺼진다(shell.md 2.3). 접두 일치로 바꿔야 한다 |

### 2. 프로젝트(project, milestone, reviewthread)

| 템플릿 | 종류 | 새 맥락과 사이드바 항목 | 경로(breadcrumb)와 페이지 머리 | 주소(바꾸지 않음) | Bootstrap 구성 요소 | htmx 사용 | 단축키 선언 후보 | 클라이언트 저장(IndexedDB) | 골격 적합도 | 메모(계약 근거와 주의) |
|---|---|---|---|---|---|---|---|---|---|---|

| `milestone/create.html` | 전체 화면 | 프로젝트 > 마일스톤(활성 유지) | 경로 `... / 마일스톤 / 새 마일스톤` | `GET /newMilestoneForm`, `POST /milestones` | `form-floating`, `btn`, 달력 ; 옛 표지: ybtn, ybtn-info, project-page-wrap | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) 단 `milestone.Write.js`의 이탈 처리와 초안 | - | `drafts`(`temporarySaveHandler`, 경로 키) | 적합 | 메뉴가 꺼지면 404 |
| `milestone/edit.html` | 전체 화면 | 프로젝트 > 마일스톤(활성 유지) | 경로 `... / 마일스톤 / 수정` | `GET /milestone/{id}/editform`, `POST /milestone/{id}/edit` | `form-floating`, `btn` ; 옛 표지: ybtn, ybtn-info, project-page-wrap | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) | - | `drafts` | 적합 |  |
| `milestone/list.html` | 전체 화면 | 프로젝트 > 마일스톤(`menuSetting.milestone`) | 경로 `... / 계획 / 마일스톤`, 머리 `마일스톤` + 새 마일스톤 | `/milestones?state=&orderBy=&orderDir=` | `nav nav-tabs`(상태), `list-group`, `progress`, `dropdown`(정렬), `input-group`(검색) ; 옛 표지: yobicon, ybtn-success, ybtn, project-page-wrap | H-셸: `#shell-main`, 링크 `hx-boost`(swap-safe 판정 시험 뒤 옵트인) 상태 탭과 정렬은 같은 주소 질의라 `hx-get` 목록 교체 후보 | `N`(새 마일스톤, `milestone/list.html:156`) | - | 적합 | 메뉴를 끄면 주소가 404이므로 항목도 숨겨야 함. |
| `milestone/partial-status.html` | 조각 | 프로젝트 홈의 마일스톤 현황 | 해당 없음 | - | `progress` | 조각, 쓰이는 화면: 프로젝트 홈 | - | - | 적합 |  |
| `milestone/view.html` | 전체 화면 | 프로젝트 > 마일스톤(활성 유지) | 경로 `... / 마일스톤 / 제목`, 머리 제목 + 고치기와 삭제 | `/milestone/{id}?state=` | `nav nav-tabs`, `list-group`, `progress`, `modal`(삭제), `btn-group` ; 옛 표지: ybtn, ybtn-danger, yobicon, project-page-wrap | H-셸: `#shell-main`, 링크 `hx-boost`(swap-safe 판정 시험 뒤 옵트인) 이슈 탭은 질의 교체 후보. 일괄 변경은 `milestone.View.js`와 `issue.MassUpdate.js` 모듈 | `CTRL+A`(일괄 변경, 입력란이 아닐 때) | - | 적합 | `issue.MassUpdate.js:124`가 `CTRL+A` 등록, 이동 시 해제 규약 필요 |
| `project/change-vcs.html` | 전체 화면 | 프로젝트 > 설정 > 고급(저장소 종류 변경, `menuSetting.code`가 켜졌을 때만) | 경로 `... / 설정 / 고급`, 머리 `저장소 종류 변경` | `GET /changeVCS`, `POST /changeVCS` | `form-select`, `alert`, `btn-danger` ; 옛 표지: ybtn, yobicon, ybtn-danger, project-page-wrap | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) | - | - | 적합 | 항목은 코드 체크 해제 시 스크립트가 숨김(`project-settings.md` 1절) |
| `project/create.html` | 전체 화면 | 전역 > (자리 없음: 만들기 화면은 `+` 메뉴에서 옴) | 경로 `새 프로젝트`, 머리 제목 | `/projectform`, `POST /projects` | `form-floating`, `form-select`, `form-check`, `btn` ; 옛 표지: ybtn, ybtn-success, ybtn-small, project-page-wrap | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) 단 Subversion 경고와 소유자 선택이 `project.New.js`(select2)에 의존해 판정 필요 | - | - | 어색함 | `+` 메뉴 항목이라 사이드바 항목이 아님. 소유자 이름이 정해지면 조직 맥락 안의 `조직 > 프로젝트` 흐름도 같은 화면(총괄 판단 4번) |
| `project/delete.html` | 전체 화면 | 프로젝트 > 설정 > 고급(위험 구역: 삭제) | 경로 `... / 설정 / 고급`, 머리 `프로젝트 삭제` | `GET /deleteform`, `DELETE /delete` | `card border-danger`, `btn-danger`, `modal` ; 옛 표지: ybtn, ybtn-danger, yobicon, project-page-wrap | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) 삭제 뒤 이동은 `HX-Redirect`. 모달 처리는 `project.Delete.js`(요소 바인딩) | - | - | 적합 | 삭제 확인 입력 스크립트 `project.Delete.js` |
| `project/deploykey-new.html` | 전체 화면 | 프로젝트 > 설정 > 배포 키(활성 유지) | 경로 `... / 배포 키 / 새 키` | `GET /setting/deploykeys/new`, `POST /setting/deploykeys` | `form-floating`, `form-control` ; 옛 표지: project-page-wrap | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) | - | - | 적합 |  |
| `project/deploykeys.html` | 전체 화면 | 프로젝트 > 설정 > 배포 키(`vcs.deployKeys`) | 경로 `... / 설정 / 배포 키`, 머리 `배포 키` + 추가 | `GET /setting/deploykeys`, `POST /setting/deploykeys/{id}/delete` | `table table-hover`, `btn` ; 옛 표지: project-page-wrap | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) 삭제 행 갱신 | - | - | 적합 | Subversion이면 항목이 없음 |
| `project/header.html` | 조각 | 프로젝트 페이지 머리(로고, 이름, 즐겨찾기 별, 구독 단추와 구독자 수 링크, 가입 신청). 모든 프로젝트 화면의 머리 | 경로 `소유자 / 프로젝트` 아래 제목과 주요 단추 | - | `breadcrumb`, `btn-group`, `badge`, 별은 `btn` ; 옛 표지: yobicon, ybtn, ybtn-watching, ybtn-small | 조각, 쓰이는 화면: 프로젝트 레이아웃 56개 화면. 구독과 별 토글은 `hx-post` + `hx-swap="outerHTML"`(행 갱신) 후보 | - | - | 적합 | 구독자 수 링크(`view.canWatch`)가 `watchers` 화면으로 가는 유일한 입구(`project.md` 2.2) |
| `project/home.html` | 전체 화면 | 프로젝트 > 프로젝트 홈 | 경로 `소유자 / 프로젝트`, 머리는 프로젝트 이름과 구독, 복제 주소, 포크 단추 | `/{owner}/{project}?tabId=` | `nav nav-tabs`(README, 최근 이력, 대시보드), `card`, `input-group`(복제 주소), `modal`(탈퇴 확인), `progress` ; 옛 표지: ybtn, yobicon, ybtn-minimum, ybtn-success | H-셸: `#shell-main`, 링크 `hx-boost`(swap-safe 판정 시험 뒤 옵트인) 탭 3개는 같은 주소의 `tabId` 질의라 `hx-get` + `hx-target`(탭 내용) + `hx-push-url` 후보. 탈퇴 모달은 일반 폼 | 전역 + 프로젝트 맥락 키 | - | 적합 | `project.Home.js:117`이 이전 클릭 처리기를 떼고 다시 붙여 중복에 안전하나 판정 시험 필요. 탈퇴 단추는 `projectMember`일 때만 |
| `project/importing.html` | 전체 화면 | 전역 > (자리 없음: 만들기 흐름의 하위) | 경로 `새 프로젝트 / 저장소 가져오기` | `/_import` | `form-floating`, `btn` ; 옛 표지: ybtn, ybtn-small, ybtn-primary, project-page-wrap | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) 단 `project.New.js` 공유 | - | - | 자리 없음 | 이 화면으로 가는 링크는 `project/create.html:22`의 단추뿐이다(확인함) |
| `project/issue-labels.html` | 전체 화면 | 프로젝트 > 설정 > 라벨(설정 탭에서 옮김, 주소 그대로) | 경로 `... / 계획 / 라벨`, 머리 `이슈 라벨` + 추가 | `/issue/labelsform` 외 | `list-group`, `badge`, `btn`, `form-control-color` ; 옛 표지: ybtn, ybtn-info, ybtn-default, ybtn-primary | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) 라벨 추가와 삭제는 행 갱신. 목록 조각 `partial-issue-labels-list` | - | - | 적합 | 이슈 메뉴를 꺼도 이 경로는 열려 있다(`index.md`). 사이드바에서는 "이슈 메뉴 켜짐"과 별개로 설정 쪽에도 접근점이 필요한지 판단(총괄 판단 6번) |
| `project/lfs-hg.html` | 전체 화면 | 프로젝트 > 설정 > LFS(Mercurial 이름) | 경로 `... / 설정 / LFS` | `GET /setting/lfs` | `table`, `btn` ; 옛 표지: project-page-wrap | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) | - | - | 적합 | 같은 주소를 저장소 종류에 따라 다른 템플릿이 그림 |
| `project/lfs.html` | 전체 화면 | 프로젝트 > 설정 > Git LFS(`vcs.lfsScreen`) | 경로 `... / 설정 / Git LFS` | `GET /setting/lfs`, `POST /setting/lfs/locks/{id}/delete` | `table`, `btn` ; 옛 표지: project-page-wrap | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) | - | - | 적합 | Git용. 이름은 Mercurial이면 `project.lfs.hg` |
| `project/list.html` | 전체 화면 | 전역(홈) > 탐색 > 프로젝트 목록(`hideProjectListing`이면 항목과 화면 모두 없음) | 경로 `프로젝트 목록`, 머리 제목 + 검색 | `/projects?filter=&pageNum=&labelIds=` | `input-group`(검색), `list-group`, `pagination`, 위쪽 탭 둘은 사이드바 항목으로 이동 ; 옛 표지: yobicon, project-page-wrap, ico-err1 | H-셸: `#shell-main`, 링크 `hx-boost`(swap-safe 판정 시험 뒤 옵트인) 검색은 `hx-get` + `hx-target`(목록) 후보(지금은 일반 폼). 페이지 링크 `hx-boost` | `←` `→`(쪽 이동) | - | 적합 | 게스트 계정은 `/`로 303, 숨김 설정이면 403. 사이드바 항목 숨김과 일치해야 함 |
| `project/members.html` | 전체 화면 | 프로젝트 > 설정 > 멤버(가입 신청 수 배지) | 경로 `... / 설정 / 멤버`, 머리 `멤버` + 추가 폼 | `/members`, `POST /members`, `POST /member/{id}/edit`, `DELETE /member/{id}/delete` | `table table-hover`, `form-select`, `badge`, `btn-group` ; 옛 표지: ybtn, yobicon, ybtn-success, ybtn-small | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) 역할 변경과 삭제는 `hx-post`/`hx-delete` + 행 `outerHTML` | - | - | 적합 | 삭제는 읽기 + (본인이거나 UPDATE) |
| `project/menu.html` | 조각 | 없앰(프로젝트 사이드바 데이터로 대체) | 해당 없음 | - | `nav flex-column`으로 대체 ; 옛 표지: yobicon | 해당 없음 | `H B C I M P Q`를 사이드바 항목 데이터에서 파생(조건: `menuSetting`, `codeAccessible`, `vcs`, `settingShortcutEnabled`) | - | 적합 | `project.Global` 모듈이 별 토글 등도 담당해 사이드바 이전 때 같이 옮겨야 함(`shell.md` 5.2) |
| `project/partial-dashboard.html` | 조각 | 프로젝트 홈의 대시보드 탭 내용 | 해당 없음 | `?tabId=dashboard` | `card`, `progress`, `list-group` ; 옛 표지: ybtn-small, ybtn, yobicon | 조각, 쓰이는 화면: 프로젝트 홈. 탭 교체 영역 | - | - | 적합 | 담당자별, 마일스톤별, 라벨별 집계. 이슈 메뉴가 꺼지면 해당 영역이 없음 |
| `project/partial-history.html` | 조각 | 프로젝트 홈의 최근 이력 탭 내용 | 해당 없음 | `?tabId=history` | `list-group` | 조각, 쓰이는 화면: 프로젝트 홈. 탭 교체 영역(`hx-trigger="load"` 또는 탭 클릭) | - | - | 적합 | 항목 최대 40줄, 빈 목록 문구 없음 |
| `project/partial-issue-labels-list.html` | 조각 | 이슈 라벨 목록 | 해당 없음 | - | `list-group` ; 옛 표지: ybtn, yobicon, ybtn-small, ybtn-mini | 조각, 쓰이는 화면: 이슈 라벨 화면. 행 단위 교체 영역 | - | - | 적합 |  |
| `project/partial-readme.html` | 조각 | 프로젝트 홈의 README 탭 내용 | 해당 없음 | `GET /{o}/{p}?tabId=readme` | `card` ; 옛 표지: ybtn, yobicon | 조각, 쓰이는 화면: 프로젝트 홈. 탭 교체 영역 | - | - | 적합 |  |
| `project/partial-settingmenu.html` | 조각 | 없앰(프로젝트 설정 사이드바 묶음으로 대체) | 해당 없음 | - | `nav nav-tabs`(삭제) | 해당 없음 | - | - | 적합 | 조건 항목이 사이드바 데이터로 옮겨 감(`vcs.protectedRefs` 등) |
| `project/ruleset-form.html` | 전체 화면 | 프로젝트 > 설정 > 규칙(활성 유지) | 경로 `... / 규칙 / 편집` | `GET /setting/rulesets/new`, `GET/POST /setting/rulesets/{id}` | `form-floating`, `form-check`, `btn` ; 옛 표지: ybtn, ybtn-success, ybtn-danger, project-page-wrap | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) | - | - | 적합 |  |
| `project/rulesets.html` | 전체 화면 | 프로젝트 > 설정 > 규칙(`vcs.protectedRefs`) | 경로 `... / 설정 / 규칙`, 머리 `규칙 묶음` + 새로 만들기 | `GET /setting/rulesets`, `POST /setting/rulesets/{id}/delete` | `table table-hover`, `badge`, `btn` ; 옛 표지: ybtn-success, ybtn, project-page-wrap | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) 삭제 행 갱신 | - | - | 적합 |  |
| `project/setting.html` | 전체 화면 | 프로젝트 > 설정 > 일반 | 경로 `소유자 / 프로젝트 / 설정`, 머리 제목 `프로젝트 설정` + 저장 | `GET /settingform`, `POST /setting` | `form-floating`, `form-check form-switch`(메뉴 켜기와 끄기 체크박스, 위키 포함), `form-select`, `card` ; 옛 표지: ybtn, yobicon, ybtn-success, project-page-wrap | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) | - | - | 적합 | 메뉴 체크박스가 사이드바 항목과 주소(이슈, 마일스톤 404)를 동시에 정한다. `project.Setting.js`가 코드 체크 해제 시 `changeVCS` 항목을 숨김 |
| `project/statistics.html` | 전체 화면 | 프로젝트 > (자리 없음) | 경로 `... / 통계` | `/{o}/{p}/statistics` | `h1`만(영어 `Under Construction`) ; 옛 표지: project-page-wrap | N-일반: 본문이 한 줄이라 이점이 없음, 화면 자체가 미완 | - | - | 자리 없음 | 이 템플릿으로 가는 링크가 어디에도 없다(project-F11, 확인함). 사이드바 항목을 만들지 않는 쪽을 권장(총괄 판단 5번) |
| `project/transfer.html` | 전체 화면 | 프로젝트 > 설정 > 고급(위험 구역: 이관) | 경로 `... / 설정 / 고급`, 머리 `프로젝트 이관` | `/transfer`, `PUT /transfer`, `GET /project/transfer/{id}/{key}` | `card border-danger`, `form-floating`, `btn-danger` ; 옛 표지: ybtn, ybtn-danger, yobicon, project-page-wrap | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) 성공 뒤 `document.location`으로 이동하는 코드(`project.Transfer.js:66,68`)를 `HX-Redirect`로 바꿈. 소유자 입력 스크립트는 판정 필요 | - | - | 적합 | 수락 주소는 받는 쪽 사용자가 이메일에서 오는 화면이라 사이드바 진입이 아님 |
| `project/watchers.html` | 전체 화면 | 프로젝트 > (자리 없음: 머리의 구독자 수 링크로만 들어옴) | 경로 `... / 구독자` | `/{o}/{p}/watchers` | `card`(구성원 카드), `row`, `col-lg-6` ; 옛 표지: project-page-wrap | H-셸: `#shell-main`, 링크 `hx-boost`(swap-safe 판정 시험 뒤 옵트인) | - | - | 어색함 | 사이드바 항목 후보는 프로젝트 홈 아래 `구독자`. 읽기 권한 필요, 숨김 설정과 무관 |
| `project/webhooks.html` | 전체 화면 | 프로젝트 > 설정 > 웹훅 | 경로 `... / 설정 / 웹훅`, 머리 `웹훅` + 추가 | `GET /webhooks`, `POST /webhooks`, `DELETE /webhooks/{id}` | `table table-hover`, `form-floating`, `btn` ; 옛 표지: ybtn, ybtn-small, ybtn-primary, ybtn-danger | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) 삭제는 `hx-delete` + 행 제거 | - | - | 적합 | 웹훅 비밀 값 평문 표시는 v1.6 유지 항목(project-F21) |
| `reviewthread/list.html` | 전체 화면 | 프로젝트 > 리뷰(`menuSetting.review` + `codeAccessible`, 열린 스레드 수 배지) | 경로 `... / 협업 / 리뷰`, 머리 `리뷰` | `/{o}/{p}/reviews` | `list-group`, `nav nav-tabs`, `badge`, `pagination` ; 옛 표지: data-toggle="filter", yobicon, data-toggle="order", ybtn | H-셸: `#shell-main`, 링크 `hx-boost`(swap-safe 판정 시험 뒤 옵트인) 필터는 질의라 목록 교체 후보. `review.List.js` 판정 필요 | `←` `→` | - | 적합 | 전역 "내 리뷰" 목록은 없다(v1.6에도 없음, 총괄 판단 7번) |

### 3. 이슈, 게시판, 위키(issue, board, wiki)

| 템플릿 | 종류 | 새 맥락과 사이드바 항목 | 경로(breadcrumb)와 페이지 머리 | 주소(바꾸지 않음) | Bootstrap 구성 요소 | htmx 사용 | 단축키 선언 후보 | 클라이언트 저장(IndexedDB) | 골격 적합도 | 메모(계약 근거와 주의) |
|---|---|---|---|---|---|---|---|---|---|---|

| `board/create.html` | 전체 화면 | 프로젝트 > 게시판(새 글). README, 이슈 템플릿, 저장소 파일 온라인 커밋도 이 화면 | 경로 `... / 게시판 / 새 글`, 머리 `새 글` | `GET /postform`, `POST /posts` | `form-floating`, `nav nav-tabs`(편집기), `form-check`, `btn` ; 옛 표지: ybtn, ybtn-success, project-page-wrap | N-일반: `board.Write.js:63`이 `window` 이탈 확인 등록, 초안 | `CTRL+ENTER` | `drafts` | 어색함 | 코드 저장소 파일을 고치는 용도(`ONLINE_EDITING`)가 게시판 항목 아래에 있다. 코드 > 파일에서 오는 진입점은 사이드바에 없음(총괄 판단 4번) |
| `board/edit.html` | 전체 화면 | 프로젝트 > 게시판(활성 유지) | 경로 `... / 게시판 / 제목 / 수정` | `GET /post/{n}/editform`, `POST /post/{n}/edit` | `form-floating`, `btn` ; 옛 표지: ybtn, ybtn-info, project-page-wrap | N-일반: `board.Write.js` 이탈 확인 | `CTRL+ENTER` | `drafts` | 적합 | 수정 권한이 없으면 원본 보기로 열림 |
| `board/list.html` | 전체 화면 | 프로젝트 > 게시판(`menuSetting.board`, 글 수 배지) | 경로 `... / 계획 / 게시판`, 머리 `게시판` + 새 글 | `/{o}/{p}/posts` | `list-group`, `dropdown`(정렬), `form-select`(라벨), `input-group`, `pagination`, 공지 `badge` ; 옛 표지: yobicon, ybtn-success, ybtn, project-page-wrap | H-셸: `#shell-main`, 링크 `hx-boost`(swap-safe 판정 시험 뒤 옵트인) 필터와 정렬은 같은 주소 질의라 교체 후보. 페이지 링크 `hx-boost` | `N`(새 글, 메뉴가 켜졌을 때), `←` `→` | `prefs`(2단 보기) | 적합 | 메뉴를 꺼도 목록 주소는 열린다(글쓰기 단추만 없음). 지금은 htmx를 쓰지 않는다(일반 폼) |
| `board/partial-comments.html` | 조각 | 게시글 댓글 목록 | 해당 없음 | - | `card` ; 옛 표지: yobicon, data-toggle="comment-edit", data-toggle="comment-delete" | 조각, 쓰이는 화면: 게시글 보기 | - | - | 적합 |  |
| `board/partial-list.html` | 조각 | 글 한 줄 | 해당 없음 | - | `list-group-item` | 조각, 쓰이는 화면: 게시판 목록, 조직 게시판 목록 | - | - | 적합 |  |
| `board/view.html` | 전체 화면 | 프로젝트 > 게시판(활성 유지) | 경로 `... / 게시판 / 제목`, 머리 제목, 구독, 수정 | `/{o}/{p}/post/{n}` | `card`, `btn-group`, `badge`, `modal`(댓글 삭제), `form-select`(라벨) ; 옛 표지: yobicon, ybtn, ybtn-watching, ybtn-success | N-일반: 댓글 작성 조각의 `window` 키 입력과 이탈 확인(`CommentForm.js:37-38`) | `N`, `L`, `E`(수정 권한), `CTRL+ENTER` | `drafts` | 적합 | 키 `title.boardDetail` 번들 없음(board-F1, v1.6도 같음) |
| `issue/create.html` | 전체 화면 | 프로젝트 > 이슈(새 이슈). `/user/issues/new`와 `/user/issues/new/mine`은 전역에서 진입 | 경로 `... / 이슈 / 새 이슈`, 머리 `새 이슈` | `GET /{o}/{p}/issueform`, `/user/issues/new`, `/user/issues/new/mine`, `POST .../issues/latest` | `form-floating`, `form-select`, `nav nav-tabs`(편집기), `btn`, 달력 ; 옛 표지: ybtn, yobicon, ybtn-watching, ybtn-success | N-일반: `issue.Write.js:70`이 `window` 이탈 확인을 등록, 초안 복원, 제목 자동 완성(`TitleHeadAutoCompletion.js`) | `CTRL+ENTER`(제출) | `drafts`(경로 키, 5초 뒤 저장) | 어색함 | 같은 템플릿이 프로젝트 맥락과 전역 맥락(프로젝트 선택부터)에서 모두 쓰임. 전역 쪽의 맥락이 골격에 없음(총괄 판단 4번) |
| `issue/edit.html` | 전체 화면 | 프로젝트 > 이슈(활성 유지) | 경로 `... / 이슈 / #번호 / 수정` | `GET /issue/{n}/editform`, `POST /issue/{n}/edit` | `form-floating`, `form-select`, `btn` ; 옛 표지: ybtn, yobicon, ybtn-info, ybtn-watching | N-일반: `issue.Write.js:70` 이탈 확인 등록 | `CTRL+ENTER` | `drafts`, 인라인 `localStorage`(`issue/edit.html`에서 직접 사용) | 적합 | 템플릿에 인라인 `localStorage` 호출이 있어 `esm/store` 경유로 바꿔야 함 |
| `issue/list.html` | 전체 화면 | 프로젝트 > 이슈(`menuSetting.issue`, 열린 이슈 수 배지) | 경로 `... / 계획 / 이슈`, 머리 `이슈` + 새 이슈 | `/{o}/{p}/issues?state=...` | `nav nav-tabs`(상태), `dropdown`, `form-select`(select2), `list-group`, `pagination`, `badge` ; 옛 표지: project-page-wrap | H-기존(D-96): `div[list-container]` 교체 지금 5개 htmx 템플릿 중 하나(프로젝트 이슈 목록, 영역 `div[list-container]`). 셸 이동으로 들어올 때는 N-일반: `issue.List.js:210`이 모듈 호출마다 `$(document).on("htmx:afterSwap")`를 더해 중복됨, 가드 필요 | `N`(새 이슈), `←` `→`, `CTRL+A`(일괄 변경) | `prefs`(`useTwoColumnMode`, `showSubtasksAlways`), 2단 보기는 첫 그림 거울 | 적합 | 메뉴를 끄면 주소가 404(`issue-F24`). `N`은 `menuSetting.issue`일 때만 등록 |
| `issue/my-list.html` | 전체 화면 | 전역(홈) > 내 작업 > 내 이슈 | 경로 `내 이슈`, 머리 `내 이슈` + 열림, 닫힘 필터 | `GET /user/issues` | `nav nav-tabs`(열림, 닫힘), `list-group`, `badge`, `pagination` | H-기존(D-96): `div[list-container]` 교체 목록 영역 `div[list-container]`(내 이슈 목록, `my-partial-search.html`의 hx 속성). 셸 이동 진입은 `issue.List.js` 가드 필요 | `A`(전역 내 이슈 이동), `←` `→` | `prefs`(2단 보기, 하위 이슈) | 적합 | `/user/issues?pageNum=0`은 이제 1쪽(site-F2 수정). 즐겨찾기 라벨의 `undefined` 요청(shell-F21)은 v1.6 유지 |
| `issue/my-partial-list.html` | 조각 | 내 이슈 줄 | 해당 없음 | - | `list-group-item` ; 옛 표지: yobicon | 조각, 쓰이는 화면: 내 이슈 | - | - | 적합 |  |
| `issue/my-partial-search.html` | 조각 | 내 이슈 검색과 교체 영역 | 해당 없음 | `GET /user/issues`(`hx-get`) | `form-control`, `btn-group` ; 옛 표지: yobicon, lst-stacked, ico-err1 | H-기존(D-96): `div[list-container]` 교체 `hx-target="this"` + `hx-select` + `hx-push-url`(확인함) | - | - | 적합 |  |
| `issue/partial-assignee.html` | 조각 | 담당자 select | 해당 없음 | - | `form-select`(select2) | 조각, 쓰이는 화면: 이슈 보기, 작성, 수정 | - | - | 적합 | 수정 권한이 없으면 요청이 200 `{"issue": 경로}`만 오고 바뀌지 않음(issue-F4, 유지) |
| `issue/partial-comment.html` | 조각 | 댓글 한 개 | 해당 없음 | - | `card` ; 옛 표지: yobicon, data-toggle="comment-edit", data-toggle="comment-delete" | 조각, 쓰이는 화면: 이슈 타임라인 | - | `drafts` | 적합 |  |
| `issue/partial-comments.html` | 조각 | 타임라인(댓글과 이벤트) | 해당 없음 | `GET .../timeline` | `list-group` | H-조각: 타임라인은 `hx-get` 폴링 후보(`every`)이나 지금은 스크립트 타이머 | - | - | 적합 | 타이머를 `hx-trigger="every 30s [visible]"`로 바꾸는 것은 새 동작이 아니라 구현 교체(지금 값은 `issue.View.js`에서 확인 필요) |
| `issue/partial-index-comments.html` | 조각 | 댓글 색인(보기 오른쪽 아래) | 해당 없음 | - | `list-group` ; 옛 표지: yobicon | 조각, 쓰이는 화면: 이슈 보기 | - | - | 적합 |  |
| `issue/partial-list-quicksearch.html` | 조각 | 빠른 검색 4개 | 해당 없음 | `/issues?...` | `nav nav-pills` 또는 `btn-group` ; 옛 표지: lst-stacked | 조각, 쓰이는 화면: 이슈 목록. 링크는 `hx-boost` 대상 | - | - | 적합 |  |
| `issue/partial-list-wrap.html` | 조각 | 이슈 목록 교체 조각(`listWrap`) | 해당 없음 | 같은 주소 + `HX-Request` | `list-group`, `pagination` ; 옛 표지: ybtn, yobicon, ybtn-success, ico-err1 | H-기존(D-96): `div[list-container]` 교체 `hx-target="this"`, `hx-select="div[list-container] > *"`, `hx-swap="innerHTML show:window:top"`, `hx-push-url`(확인함) | `←` `→` | - | 적합 | 목록 표지가 없는 응답은 일반 이동(`yona.Htmx.js`) |
| `issue/partial-list.html` | 조각 | 이슈 한 줄과 초안 한 줄 | 해당 없음 | - | `list-group-item`, `badge` ; 옛 표지: yobicon, data-toggle="issue-checkbox" | 조각, 쓰이는 화면: 이슈 목록 | - | - | 적합 |  |
| `issue/partial-massupdate.html` | 조각 | 일괄 변경 막대 | 해당 없음 | 일괄 변경 POST | `btn-group`, `dropdown`, `form-check` | 조각, 쓰이는 화면: 이슈 목록, 마일스톤 보기 | `CTRL+A` | - | 적합 | 상태는 OPEN, CLOSED만, 같은 배타 분류 라벨 둘은 400(`index.md`) |
| `issue/partial-searchform.html` | 조각 | 상세 검색 폼 | 해당 없음 | `GET /issues`(`hx-get`) | `form-control`, `form-select`, `input-group`, `btn` ; 옛 표지: yobicon, ybtn-mini, ybtn-default, ybtn | H-기존(D-96): `div[list-container]` 교체 폼이 `hx-get`(확인함, `partial-searchform.html:10`) | - | - | 적합 | 라벨 관리 링크는 `search.canManageLabels`일 때 |
| `issue/partial-select-label.html` | 조각 | 라벨 select2 | 해당 없음 | - | `form-select`(select2) | 조각, 쓰이는 화면: 이슈 목록, 작성, 수정, 보기 | - | - | 적합 |  |
| `issue/partial-select-subtask.html` | 조각 | 상위 이슈와 대상 프로젝트 선택 | 해당 없음 | - | `form-select`(select2) | 조각, 쓰이는 화면: 이슈 작성, 수정 | - | - | 적합 | `Subtask.js` |
| `issue/partial-show-selected-label.html` | 조각 | 읽기 전용 라벨 목록 | 해당 없음 | - | `badge` | 조각, 쓰이는 화면: 이슈 보기(수정 권한 없을 때) | - | - | 적합 |  |
| `issue/partial-subtasks.html` | 조각 | 부모와 하위 이슈 목록 | 해당 없음 | - | `list-group` ; 옛 표지: yobicon | 조각, 쓰이는 화면: 이슈 보기 | - | - | 적합 |  |
| `issue/partial-voter-list.html` | 조각 | 추천자 모달 내용 | 해당 없음 | - | `modal`, `list-group` ; 옛 표지: ybtn-small, ybtn-info, ybtn | 조각, 쓰이는 화면: 이슈 보기, `hx-get` 지연 불러오기 후보 | - | - | 적합 |  |
| `issue/partial-voters.html` | 조각 | 추천자 아바타 | 해당 없음 | - | `avatar` 직접 CSS | 조각, 쓰이는 화면: 이슈 보기 | - | - | 적합 |  |
| `issue/view.html` | 전체 화면 | 프로젝트 > 이슈(활성 유지) | 경로 `... / 이슈 / #번호`, 머리 제목, 상태, 즐겨찾기 별, 수정 | `/{o}/{p}/issue/{n}` | `card`, `btn-group`(상태), `dropdown`, `badge`, `modal`(추천자, 댓글 삭제), `progress` ; 옛 표지: yobicon, ybtn, ybtn-small, ybtn-watching | N-일반: 댓글 작성 조각이 `window` 키 입력과 이탈 확인을 등록(`CommentForm.js:37-38`), `issue.View.js:421` 타임라인 갱신 타이머, `detectChange` | `N`, `L`, `E`(수정 권한), `CTRL+ENTER`, `CTRL+SHIFT+ENTER` | `drafts`(댓글별 키), `prefs`(`showSubtasksAlways`) | 적합 | 가장 무거운 화면. 단축키 `N`은 `menuSetting.issue`일 때만. 오른쪽 열(라벨, 담당자, 마일스톤, 공유자)은 본문 안 2열 |
| `wiki/compare.html` | 전체 화면 | 프로젝트 > 위키(활성 유지) | 경로 `... / 위키 / 페이지 / 비교` | `/wiki/{page}/_compare` | `card`(diff는 직접 CSS) ; 옛 표지: ybtn-small, ybtn, project-page-wrap | H-셸: `#shell-main`, 링크 `hx-boost`(swap-safe 판정 시험 뒤 옵트인) | - | - | 적합 |  |
| `wiki/edit.html` | 전체 화면 | 프로젝트 > 위키(활성 유지) | 경로 `... / 위키 / 새 페이지` 또는 `/ 수정` | `/wiki/_new`, `/wiki/{page}/_edit`, `POST /wiki`, `POST /wiki/{page}/_edit` | `form-floating`, `nav nav-tabs`(편집기), `btn`, `modal`(삭제) ; 옛 표지: ybtn, ybtn-success, ybtn-small, ybtn-danger | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) 검증 실패 4xx를 422 조각으로 교체 | `CTRL+ENTER` | 없음(초안 저장 모듈을 쓰지 않음, 확인함) | 적합 | 위키 편집은 초안 저장이 없다(v1.6 동작). 새 기능으로 더하지 않는다 |
| `wiki/empty.html` | 전체 화면 | 프로젝트 > 위키 | 경로 `... / 위키`, 머리 안내 + 만들기(`canWrite`) | `/{o}/{p}/wiki`(Home 없음) | `card`, `btn` ; 옛 표지: ybtn-success, ybtn, project-page-wrap | H-셸: `#shell-main`, 링크 `hx-boost`(swap-safe 판정 시험 뒤 옵트인) | - | - | 적합 |  |
| `wiki/history.html` | 전체 화면 | 프로젝트 > 위키(활성 유지) | 경로 `... / 위키 / 페이지 / 기록` | `/wiki/{page}/_history` | `list-group`, `form-check`(비교할 두 개), `btn` ; 옛 표지: ybtn-small, ybtn, project-page-wrap | H-셸: `#shell-main`, 링크 `hx-boost`(swap-safe 판정 시험 뒤 옵트인) | - | - | 적합 |  |
| `wiki/shell.html` | 조각 | 위키 오른쪽 `Pages` 목록, 복제 주소, 푸터(`sidebar`, `footer` 조각) | 해당 없음 | - | `list-group`, `input-group` | 조각, 쓰이는 화면: 위키 화면 전부 | - | - | 어색함 | 맥락 사이드바 안 위키 하위 항목으로 올릴지 본문 보조 패널로 둘지 결정 필요(총괄 판단 9번) |
| `wiki/view.html` | 전체 화면 | 프로젝트 > 위키(`menuSetting.wiki == true`) | 경로 `... / 문서 / 위키 / 페이지`, 머리 페이지 제목 + 수정, 기록 | `/{o}/{p}/wiki`, `/wiki/{page}` | `card`, `list-group`(Pages), `btn-group` ; 옛 표지: ybtn-small, ybtn, ybtn-success, project-page-wrap | H-셸: `#shell-main`, 링크 `hx-boost`(swap-safe 판정 시험 뒤 옵트인) 페이지 링크 `hx-boost`. 마크다운 렌더는 `common/markdown` 재실행 필요 | - | - | 어색함 | 오른쪽 `Pages` 사이드바(`wiki/shell.html`)가 맥락 사이드바와 겹침(총괄 판단 9번). 위키 메뉴 `W`에는 단축키가 없다 |

### 4. 코드, 코드 주고받기, 검색(code, pullrequest, search)

| 템플릿 | 종류 | 새 맥락과 사이드바 항목 | 경로(breadcrumb)와 페이지 머리 | 주소(바꾸지 않음) | Bootstrap 구성 요소 | htmx 사용 | 단축키 선언 후보 | 클라이언트 저장(IndexedDB) | 골격 적합도 | 메모(계약 근거와 주의) |
|---|---|---|---|---|---|---|---|---|---|---|

| `code/branches.html` | 전체 화면 | 프로젝트 > 코드 > 브랜치(`vcs.branches`: Git, Mercurial) | 경로 `... / 코드 / 브랜치`, 머리 `브랜치` | `GET /branches`, `POST /code/:branch/setAsDefault`, `DELETE /code/:branch/` | `table table-hover`, `btn-sm`, `badge`(기본), `modal`(삭제 확인) ; 옛 표지: ybtn-small, ybtn, ybtn-default, ybtn-danger | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) 기본으로 설정과 삭제는 `hx-post`/`hx-delete` + 행 갱신 후보. 지금은 스크립트가 없는 일반 폼 | - | - | 적합 | Subversion이면 400이므로 항목도 없음. Mercurial은 기본으로 설정이 북마크 이름을 기억(code-F7) |
| `code/compare-svn.html` | 전체 화면 | 프로젝트 > 코드 > 커밋(Subversion 비교) | 위와 같음 | 위와 같음 | `card` ; 옛 표지: project-page-wrap | N-일반: `code.SvnDiff.js`와 같은 이유 | - | - | 어색함 |  |
| `code/compare.html` | 전체 화면 | 프로젝트 > 코드 > 커밋(두 커밋 비교, 활성 유지) | 경로 `... / 코드 / 비교 / revA..revB` | `GET .../compare/:revA..:revB` | `card`, `btn-group` ; 옛 표지: project-page-wrap | N-일반: `code.Diff.js`와 같은 이유 | - | - | 어색함 | 비교 화면으로 가는 사이드바 항목이 없고 템플릿 어디에도 `/compare/` 링크가 없다(확인함) |
| `code/diff.html` | 전체 화면 | 프로젝트 > 코드 > 커밋(활성 유지) | 경로 `... / 코드 / 커밋 / 짧은 ID`, 머리 커밋 메시지, 작성자, 구독 | `GET /{o}/{p}/commit/:id`(Git, Mercurial) | `card`, `btn-group`, `modal`(댓글 삭제), `form-select`, 스크립트가 만드는 미니맵은 직접 CSS ; 옛 표지: yobicon, ybtn, ybtn-default, ybtn-watching | N-일반: `code.Diff.js:113,330,348,589`가 `hashchange`, `scroll`, 창 이벤트 등록, 줄 댓글 팝업, `CodeCommentBlock.js:260` 타이머, 댓글 폼의 `window` 키 입력 | - | `drafts`(리뷰 카드 댓글) | 적합 | 화면 계약은 `code-diff-script.md`. `code.Diff`를 쓰는 템플릿은 diff, compare, 코드 주고받기 변경까지 셋 |
| `code/history.html` | 전체 화면 | 프로젝트 > 코드 > 커밋 | 경로 `... / 코드 / 커밋`, 머리 브랜치 선택 | `/commits`, `/commits/:branch[/]`, `/commits/:branch/*path` | `table`, `form-select`(select2), `btn-group`, `pagination`(이전, 다음) ; 옛 표지: ybtn, yobicon, ybtn-mini, project-page-wrap | H-셸: `#shell-main`, 링크 `hx-boost`(swap-safe 판정 시험 뒤 옵트인) 이전, 다음 쪽 링크는 `hx-boost` 후보. 브랜치 이동은 `location.href`라 일반 이동 | `A`(새쪽), `S`(옛쪽). `A`는 전역에 덮임(R-147) | - | 적합 | 새 셸에서는 선언형 우선순위가 화면 > 전역이므로 `A`가 이전 쪽으로 동작(결과 변경, 총괄 판단 10번) |
| `code/nohead-hg.html` | 전체 화면 | 프로젝트 > 코드 > 파일(빈 저장소 안내, Mercurial) | 위와 같음 | 위와 같음 | 위와 같음 ; 옛 표지: project-page-wrap | N-일반: `code.Nohead.js` 폴링 | `C` | - | 적합 |  |
| `code/nohead-svn.html` | 전체 화면 | 프로젝트 > 코드 > 파일(빈 저장소 안내, Subversion) | 위와 같음 | 위와 같음 | `card`, `input-group` ; 옛 표지: project-page-wrap | N-일반: `code.Nohead.js` 폴링 | `C` | - | 적합 | SSH 전환이 없다 |
| `code/nohead.html` | 전체 화면 | 프로젝트 > 코드 > 파일(빈 저장소 안내, Git) | 경로 `... / 코드`, 머리 `저장소가 비어 있음` + 복제 안내 | `/{o}/{p}/code`(저장소 비었을 때) | `card`, `nav nav-pills`(HTTP, SSH 전환), `input-group`(복제 주소) ; 옛 표지: project-page-wrap | N-일반: `code.Nohead.js:50-51,75`가 창 포커스와 `setInterval` 폴링을 등록 | `C` | - | 적합 |  |
| `code/partial-comment-thread.html` | 조각 | 리뷰 스레드(줄 댓글 묶음) | 해당 없음 | - | `card`, `btn-sm` ; 옛 표지: yobicon, ybtn-small, ybtn, ybtn-default | 조각, 쓰이는 화면: 코드 커밋 보기, 코드 주고받기 변경 | - | `drafts` | 적합 |  |
| `code/partial-signature.html` | 조각 | 서명 배지(GPG, SSH 검증) | 해당 없음 | - | `badge` | 조각, 쓰이는 화면: 코드 보기, 이력, 커밋 보기 | - | - | 적합 |  |
| `code/svn-diff.html` | 전체 화면 | 프로젝트 > 코드 > 커밋(활성 유지, Subversion) | 경로 `... / 코드 / 커밋 / 리비전` | `GET .../commit/:id`(Subversion) | `dropdown`(브랜치 토글), `card` ; 옛 표지: ybtn, yobicon, project-page-wrap | N-일반: `code.SvnDiff.js:501,503` `scroll`과 `resize` 등록, 댓글 폼 `window` 키 | - | `drafts` | 적합 | 패치 원문을 브라우저가 해석 |
| `code/tags.html` | 전체 화면 | 프로젝트 > 코드 > 태그(`vcs.tags`) | 경로 `... / 코드 / 태그` | `GET /tags`, `DELETE /tags/:tag` | `table table-hover`, `btn-sm`, `modal` ; 옛 표지: ybtn-small, ybtn-danger, ybtn, project-page-wrap | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) 삭제 행 갱신 | - | - | 적합 |  |
| `code/thread-wrapper.html` | 조각 | 스레드를 감싸는 틀 | 해당 없음 | - | `card` | 조각, 쓰이는 화면: 코드 커밋 보기, 코드 주고받기 변경 | - | - | 적합 |  |
| `code/view.html` | 전체 화면 | 프로젝트 > 코드 > 파일(`menuSetting.code` + `codeAccessible`) | 경로 `... / 코드 / 브랜치 / 경로`, 머리 브랜치 선택 + 내려받기, 새 파일 | `/{o}/{p}/code`(303), `/code/:branch`, `/code/:branch/*path` | `breadcrumb`(파일 경로), `form-select`(select2 브랜치), `table table-hover`, `btn-group`, `popover`(직접 열기). 파일 탭 4개는 사이드바 항목이라 삭제 ; 옛 표지: ybtn, yobicon, project-page-wrap | N-일반: `code.Browser.js:102` 모듈 호출마다 `$(window).on("hashchange")`, ace 편집기와 마크다운 렌더, `htmx:load` 규약 전까지 | `C`(맥락) | - | 적합 | 브랜치와 태그 select는 Subversion에 없음. 저장소 종류별 탭 수가 다름(Git 4, Subversion 2)이므로 사이드바 항목이 `vcs` 능력에 맞춰 줄어듦 |
| `pullrequest/changes.html` | 전체 화면 | 프로젝트 > 코드 주고받기(활성 유지, 변경 탭) | 경로 `... / 코드 주고받기 / #번호 / 변경` | `.../pullRequest/:id/changes[/:commitId]` | `nav nav-tabs`, `card`, `modal`, `form-select` ; 옛 표지: yobicon, ybtn, ybtn-small, ybtn-default | N-일반: `code.Diff.js`, `CodeCommentBox.js`, `review.CommentEdit.js:21`의 `$(document).on("click")` 최상위 등록, 댓글 폼 `window` 키 | - | `drafts` | 적합 | 코드 리뷰 스레드가 코드 화면과 같은 스크립트를 공유 |
| `pullrequest/clone.html` | 전체 화면 | 프로젝트 > (포크 진행 화면) | 머리 `복제 진행` | `POST /fork` 응답, 이어서 `POST /clone`(JSON) | `progress`, `spinner-border` ; 옛 표지: project-page-wrap | N-일반: 3초 뒤 복제 요청을 보내는 타이머, 응답으로 이동 | - | - | 자리 없음 | 사용자가 직접 여는 주소가 아닌 진행 화면. 경로와 머리는 최소만 |
| `pullrequest/create.html` | 전체 화면 | 프로젝트 > 코드 주고받기(새 요청) | 경로 `... / 코드 주고받기 / 새 요청`, 머리 `새 코드 주고받기` | `GET /newPullRequestForm`, `GET /newPullRequest/mergeResult`, `POST /pullRequests` | `form-select`(select2), `form-floating`, `nav nav-tabs`(편집기), `table` ; 옛 표지: ybtn, yobicon, ybtn-success, project-page-wrap | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) 브랜치를 고르면 커밋 표를 `hx-get`(`partial-merge-result`)으로 가져오는 것이 자연스러운 후보. `git.Write.js`, `pullrequest.Meta.js` 판정 필요 | `CTRL+ENTER` | `drafts` | 적합 | 비구성원이 비공개 프로젝트를 보내는 쪽으로 지정하던 결함은 서버가 막음(A-459) |
| `pullrequest/edit.html` | 전체 화면 | 프로젝트 > 코드 주고받기(활성 유지) | 경로 `... / 코드 주고받기 / #번호 / 수정` | `GET .../pullRequest/:id/editform`, `POST .../:id/edit` | `form-floating`, `btn` ; 옛 표지: ybtn, yobicon, ybtn-success, project-page-wrap | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) `git.Write.js` 판정 필요 | `CTRL+ENTER` | `drafts` | 적합 |  |
| `pullrequest/fork.html` | 전체 화면 | 프로젝트 > 코드 주고받기(포크 만들기). 진입은 프로젝트 홈의 포크 단추 | 경로 `... / 포크`, 머리 `포크` | `GET /newFork`, `POST /fork` | `card`, `list-group`(소유자 선택), `btn` ; 옛 표지: ybtn, yobicon, ybtn-info, project-page-wrap | N-일반: `project.Fork.js`의 소유자 선택과 `POST /fork` 뒤 `clone` 화면으로 이어지는 흐름 | - | - | 어색함 | 포크 흐름 화면 2개(`fork`, `clone`)는 사이드바 항목이 없고 프로젝트 홈 단추로만 진입 |
| `pullrequest/list.html` | 전체 화면 | 프로젝트 > 코드 주고받기(열림, 닫힘, 보냄은 화면 안 필터 탭) | 경로 `... / 협업 / 코드 주고받기`, 머리 `코드 주고받기` + 새 요청 | `/pullRequests`, `/closedPullRequests`, `/sentPullRequests` | `nav nav-tabs`(열림, 닫힘, 보냄), `list-group`, `badge`, `pagination` ; 옛 표지: project-page-wrap | H-기존(D-96): `div[list-container]` 교체 지금 5개 htmx 템플릿의 하나(`partial-search`가 `div[list-container]` 교체, 폼 `hx-get`) | `←` `→` | `prefs`(2단 보기) | 적합 | 포크한 프로젝트의 메뉴 링크는 `/sentPullRequests`(`ProjectViewContext.kt:154`). `P`는 Git과 Mercurial만 |
| `pullrequest/partial-branch.html` | 조각 | 보낸 브랜치와 받는 브랜치 표시 | 해당 없음 | - | `badge`, `input-group` ; 옛 표지: yobicon | 조각, 쓰이는 화면: 코드 주고받기 보기 | - | - | 적합 |  |
| `pullrequest/partial-info.html` | 조각 | 개요 탭의 정보와 탭 줄 | 해당 없음 | - | `nav nav-tabs`, `card` ; 옛 표지: ybtn, ybtn-success, ybtn-default, ybtn-disabled | 조각, 쓰이는 화면: 코드 주고받기 보기 | - | - | 적합 |  |
| `pullrequest/partial-merge-result.html` | 조각 | 브랜치 선택 뒤 커밋 표 | 해당 없음 | `GET .../newPullRequest/mergeResult` | `table` ; 옛 표지: yobicon | H-조각: 브랜치 select의 `change`에 `hx-get`, 대상 영역은 커밋 표 | - | - | 적합 |  |
| `pullrequest/partial-search.html` | 조각 | 코드 주고받기 목록 교체 조각 | 해당 없음 | 같은 주소 + `HX-Request` | `list-group`, `pagination` ; 옛 표지: yobicon, ybtn-success, ybtn, ico-err1 | H-기존(D-96): `div[list-container]` 교체 `list-container` 교체(확인함, `partial-search.html:11`) | - | - | 적합 |  |
| `pullrequest/partial-state-page.html` | 조각 | 상태 조각을 감싼 화면(조각 단독 요청용) | 해당 없음 | `GET .../pullRequest/:id/state` | `alert` | 조각, 쓰이는 화면: 상태 조각 단독 응답 | - | - | 적합 |  |
| `pullrequest/partial-state.html` | 조각 | 상태와 병합 단추 영역 | 해당 없음 | `GET .../pullRequest/:id/state` | `btn-group`, `alert` ; 옛 표지: yobicon, ybtn-mini, ybtn, ybtn-primary | H-조각: XHR이면 JSON, 아니면 조각 HTML이라 `hx-get` 교체 후보 | - | - | 적합 | JSON과 조각 두 응답을 같은 주소가 줌 |
| `pullrequest/partial-timeline.html` | 조각 | 타임라인 `<li>` 모음 | 해당 없음 | `GET .../pullRequest/:id/timeline` | `list-group` ; 옛 표지: ybtn-mini, ybtn | H-조각: 담당자, 라벨, 리뷰 요청 변경 뒤 `hx-get` + `outerHTML` 교체 후보(지금은 Ajax 직접) | - | - | 적합 |  |
| `pullrequest/view.html` | 전체 화면 | 프로젝트 > 코드 주고받기(활성 유지, 개요 탭) | 경로 `... / 코드 주고받기 / #번호`, 머리 제목, 상태, 담당자 | `/{o}/{p}/pullRequest/{id}` | `nav nav-tabs`(개요, 변경), `card`, `badge`, `modal`, `btn-group` ; 옛 표지: ybtn, ybtn-mini, ybtn-small, yobicon | N-일반: `git.View.js:203,210`이 `setInterval` 상태 폴링, `pullrequest.Reviewers.js:101,107`이 `$(document).on("click")`을 모듈 최상위에서 등록, 댓글 폼 `window` 키 | - | `drafts` | 적합 | 병합된 요청의 복구가 폴링 뒤 GET 링크가 되는 v1.6 동작(pullrequest-F4, 유지) |
| `search/partial-items.html` | 조각 | 이슈, 게시글, 댓글, 리뷰 결과 줄 | 해당 없음 | - | `list-group` | 조각, 쓰이는 화면: 검색 결과 | - | - | 적합 |  |
| `search/partial-milestones.html` | 조각 | 마일스톤 결과 줄 | 해당 없음 | - | `list-group` | 조각, 쓰이는 화면: 검색 결과 | - | - | 적합 |  |
| `search/partial-projects.html` | 조각 | 프로젝트 결과 줄 | 해당 없음 | - | `list-group` ; 옛 표지: yobicon | 조각, 쓰이는 화면: 검색 결과(프로젝트 범위 제외) | - | - | 적합 |  |
| `search/partial-search.html` | 조각 | 검색 탭 줄, 검색 폼, 결과 영역과 강조 스크립트 | 해당 없음 | - | `nav nav-tabs`, `input-group` ; 옛 표지: data-toggle="search-category", project-page-wrap, ybtn | 조각, 쓰이는 화면: 검색 결과 3화면. 결과 영역이 교체 대상 후보 | `ALT+S` | - | 적합 |  |
| `search/partial-users.html` | 조각 | 사용자 결과 줄 | 해당 없음 | - | `list-group` | 조각, 쓰이는 화면: 검색 결과 | - | - | 적합 |  |
| `search/result-organization.html` | 전체 화면 | 조직 > (자리 없음: 조직 검색 범위) | 경로 `조직 / 검색` | `/organizations/{name}/search` | `nav nav-tabs`, `list-group` | H-셸: `#shell-main`, 링크 `hx-boost`(swap-safe 판정 시험 뒤 옵트인) | `ALT+S`, `/` | `search-history` | 자리 없음 | 조직 구성원 또는 조직 관리자만 범위 항목이 보임(`navbar.html:55-58`) |
| `search/result-project.html` | 전체 화면 | 프로젝트 > (자리 없음: 프로젝트 검색 범위) | 경로 `소유자 / 프로젝트 / 검색` | `/{o}/{p}/search` | `nav nav-tabs`, `list-group` | H-셸: `#shell-main`, 링크 `hx-boost`(swap-safe 판정 시험 뒤 옵트인) | `ALT+S`, `/` | `search-history` | 자리 없음 | 프로젝트 결과에는 프로젝트 탭이 없다 |
| `search/result-site.html` | 전체 화면 | 전역(상단 검색, 범위 "전체") > (자리 없음: 사이드바 항목이 아님) | 경로 `검색`, 머리 검색 폼 + 결과 제목 | `/search` | `nav nav-tabs`(결과 종류), `list-group`, `input-group`, `pagination` | H-셸: `#shell-main`, 링크 `hx-boost`(swap-safe 판정 시험 뒤 옵트인) 탭과 쪽 이동은 질의라 `hx-get`(결과 영역) 후보 | `ALT+S`, `/`(검색 초점), `←` `→` | `search-history`(새 기능, 총괄 판단 11번) | 자리 없음 | `hideProjectListing`일 때도 "전체" 범위가 관리자에게 남는다(admin-role-F3). 사이드바 항목이 아니라 상단 검색의 결과 화면 |

### 5. 조직(organization)

| 템플릿 | 종류 | 새 맥락과 사이드바 항목 | 경로(breadcrumb)와 페이지 머리 | 주소(바꾸지 않음) | Bootstrap 구성 요소 | htmx 사용 | 단축키 선언 후보 | 클라이언트 저장(IndexedDB) | 골격 적합도 | 메모(계약 근거와 주의) |
|---|---|---|---|---|---|---|---|---|---|---|

| `organization/audit.html` | 전체 화면 | 조직 > 설정 > 감사 로그 | 경로 `조직 / 설정 / 감사 로그` | `/organizations/{name}/audit` | `table`, `form-select`(필터), `btn`(내려받기, 무결성 검증) ; 옛 표지: project-page-wrap | H-셸: `#shell-main`, 링크 `hx-boost`(swap-safe 판정 시험 뒤 옵트인) 필터는 질의라 목록 교체 후보. `audit/list` 조각을 공유 | - | - | 적합 | v1.6에 없는 추가 화면. 같은 조각이 사이트와 사용자 감사에도 쓰임 |
| `organization/board-list.html` | 전체 화면 | 조직 > 계획 > 게시판 | 경로 `조직 / 계획 / 게시판`, 머리 `게시판` + 프로젝트 선택 | `/organizations/{name}/boards` | `form-select`(select2 다중), `list-group`, `dropdown`(정렬), `pagination` ; 옛 표지: yobicon, project-page-wrap, ico-err1 | H-셸: `#shell-main`, 링크 `hx-boost`(swap-safe 판정 시험 뒤 옵트인) 프로젝트 선택과 정렬이 `#option_form` 제출이라 `hx-get` 목록 교체 후보 | `←` `→` | `prefs`(2단 보기) | 어색함 | 프로젝트 게시판 목록(`board/list`)과 같은 화면이 다른 템플릿으로 이중 구현. 맥락 횡단 기능 표 참조 |
| `organization/create.html` | 전체 화면 | 전역 > (자리 없음: `+` 메뉴에서 옴) | 경로 `새 그룹`, 머리 `새 그룹` | `GET,POST /organizations/new` | `form-floating`, `btn` ; 옛 표지: ybtn, yobicon, ybtn-success, project-page-wrap | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) `organization.New.js` 판정 필요 | - | - | 어색함 | 지금 `site-layout`을 쓰는 만들기 화면 |
| `organization/delete-form.html` | 전체 화면 | 조직 > 설정 > 고급(삭제) | 경로 `조직 / 설정 / 삭제` | `GET /deleteForm`, `DELETE /organizations/{name}` | `card border-danger`, `btn-danger` ; 옛 표지: ybtn, ybtn-danger, project-page-wrap | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) 삭제 뒤 `HX-Redirect` | - | - | 적합 |  |
| `organization/header.html` | 조각 | 조직 페이지 머리(로고 배경, 가입 신청) | 경로 `조직` 아래 제목과 가입 단추 | - | `card`, `btn` ; 옛 표지: yobicon, ybtn-info, ybtn, ybtn-small | 조각, 쓰이는 화면: 조직 레이아웃 화면 | - | - | 적합 | `organization.Global.js`를 이 조각이 로드 |
| `organization/issue-list.html` | 전체 화면 | 조직 > 계획 > 이슈 | 경로 `조직 / 계획 / 이슈`, 머리 `이슈` + 검색 | `/organizations/{name}/issues` | `form-select`, `list-group`, `nav nav-tabs`, `pagination` ; 옛 표지: yobicon, lst-stacked, ico-err1 | H-기존(D-96): `div[list-container]` 교체 지금 5개 htmx 템플릿의 하나(`hx-get` 폼과 `list-container`, 확인함). 셸 이동 진입은 `issue.List.js` 가드 필요 | `←` `→` | `prefs`(2단 보기, 하위 이슈) | 어색함 | 이슈 목록이 프로젝트, 조직, 내 이슈 세 템플릿으로 나뉨 |
| `organization/list.html` | 전체 화면 | 전역(홈) > 탐색 > 그룹 목록(`hideProjectListing`이면 403) | 경로 `그룹 목록`, 머리 검색 | `/orgs` | `input-group`, `list-group`, `pagination` ; 옛 표지: yobicon, project-page-wrap, ico-err1 | H-셸: `#shell-main`, 링크 `hx-boost`(swap-safe 판정 시험 뒤 옵트인) | `←` `→` | - | 적합 | 프로젝트 목록과 위쪽 탭 한 쌍이었던 것을 사이드바 두 항목으로 나눔 |
| `organization/members.html` | 전체 화면 | 조직 > 설정 > 멤버 | 경로 `조직 / 설정 / 멤버`, 머리 `구성원` + 추가 | `/members` 외 | `table table-hover`, `form-select`, `badge`, `btn-group` ; 옛 표지: ybtn, ybtn-mini, yobicon, ybtn-info | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) 역할 변경과 삭제 행 갱신. 성공 뒤 `location.replace`(`organization.Member.js:173,263`) | - | - | 적합 | 조직 탈퇴 차단 조건: 관리자가 한 명뿐일 때 |
| `organization/menu.html` | 조각 | 없앰(조직 사이드바 데이터로 대체) | 해당 없음 | - | `nav flex-column`으로 대체 ; 옛 표지: yobicon | 해당 없음 | - | - | 적합 | 활성 비교가 소문자와 퍼센트 해제를 맞춰 정함(F9). 설정 아이콘은 `canManage`일 때만 |
| `organization/oauth-app-form.html` | 전체 화면 | 조직 > 설정 > 접근 > OAuth 앱(활성 유지) | 경로 `... / OAuth 앱 / 새 앱` 또는 `/ 수정` | `GET .../oauth-apps/new`, `.../{id}`, `POST` 오류 시 400 | `form-floating`, `form-check`(범위), `btn` ; 옛 표지: project-page-wrap | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) `data-submit-once` | - | - | 적합 | `appForm` 조각 공유. 소유자당 앱 20개 |
| `organization/oauth-app-secret.html` | 전체 화면 | 조직 > 설정 > 접근 > OAuth 앱(활성 유지) | 경로 `... / OAuth 앱 / 비밀 값` | `POST .../oauth-apps`(기밀 앱 성공), `.../{id}/secret` | `alert`, `input-group`(복사) ; 옛 표지: project-page-wrap | N-일반: 만든 직후 한 번만 보이는 비밀 값이므로 조각으로 교체해도 되나 IndexedDB와 history에 남기지 않음. 일반 이동 권장 | - | 저장 금지(`ui-client-storage.md` 5절) | 적합 | 비밀 값(`yona_cs_`)은 해시만 저장 |
| `organization/oauth-apps.html` | 전체 화면 | 조직 > 설정 > 접근 > OAuth 앱 | 경로 `조직 / 설정 / 접근 / OAuth 앱` | `/organizations/{name}/oauth-apps` | `table`, `btn` ; 옛 표지: project-page-wrap | H-셸: `#shell-main`, 링크 `hx-boost`(swap-safe 판정 시험 뒤 옵트인) | - | - | 적합 | `oauth/partial-apps`의 `mine` 조각을 사용자 설정과 공유 |
| `organization/oauth-policy.html` | 전체 화면 | 조직 > 설정 > 접근 > OAuth 앱 정책 | 경로 `조직 / 설정 / 접근 / OAuth 앱 정책` | `/organizations/{name}/oauth-policy` | `table`, `form-check form-switch`, `badge` ; 옛 표지: tf-group, project-page-wrap | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) 승인, 거부는 행 갱신 | - | - | 적합 | 상태 `APPROVED`, `DENIED`, `PENDING` |
| `organization/partial-settingmenu.html` | 조각 | 없앰(조직 설정 사이드바 묶음으로 대체) | 해당 없음 | - | `nav nav-tabs`(삭제) | 해당 없음 | - | - | 적합 | OAuth 앱 탭만 `startsWith`로 하위 화면까지 활성 |
| `organization/pullrequest-list.html` | 전체 화면 | 조직 > 협업 > 코드 주고받기 | 경로 `조직 / 협업 / 코드 주고받기` | `/organizations/{name}/pullrequests` | `list-group`, `pagination` ; 옛 표지: yobicon, project-page-wrap, ico-err1 | H-셸: `#shell-main`, 링크 `hx-boost`(swap-safe 판정 시험 뒤 옵트인) 이 화면은 `list-container`가 있으나 `hx-*` 속성이 없다(확인함). D-96 "5개 목록"에 들지 않음 | `←` `→` | `prefs`(2단 보기는 없음) | 적합 | 쪽 번호가 숫자가 아니면 1쪽(이슈와 게시판은 400) |
| `organization/setting.html` | 전체 화면 | 조직 > 설정 > 일반 | 경로 `조직 / 설정 / 일반`, 머리 `조직 설정` + 저장 | `GET /settingform`, `POST /setting` | `form-floating`, `form-control`(로고 업로드), `btn` ; 옛 표지: yobicon, ybtn-success, ybtn, project-page-wrap | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) `organization.Setting.js`는 `document.forms`로 폼을 읽는 요소 바인딩 | - | - | 적합 | 지금은 `site-layout`이라 조직 이름이 머리에 없음(`shell.md` 2.2) |
| `organization/view.html` | 전체 화면 | 조직 > 조직 홈 | 경로 `조직`, 머리 조직 로고와 이름, 가입 신청 | `/organizations/{name}` | `card`, `list-group`(프로젝트), `input-group`(필터), `nav nav-tabs`(미확인), `badge` ; 옛 표지: yobicon, ybtn, ybtn-minimum, ybtn-mini | H-셸: `#shell-main`, 링크 `hx-boost`(swap-safe 판정 시험 뒤 옵트인) 프로젝트 필터는 `ItemSearch.js`(클라이언트 필터)라 htmx 불필요 | - | - | 적합 | `organization.View.js:71,73`이 성공 뒤 `location.replace/reload` |

### 6. 사이트 관리(site)

| 템플릿 | 종류 | 새 맥락과 사이드바 항목 | 경로(breadcrumb)와 페이지 머리 | 주소(바꾸지 않음) | Bootstrap 구성 요소 | htmx 사용 | 단축키 선언 후보 | 클라이언트 저장(IndexedDB) | 골격 적합도 | 메모(계약 근거와 주의) |
|---|---|---|---|---|---|---|---|---|---|---|

| `site/audit.html` | 전체 화면 | 사이트 관리 > 시스템 > 감사 로그 | 경로 `사이트 관리 / 시스템 / 감사 로그` | `/sites/audit` | `table`, `form-select`, `btn` | H-셸: `#shell-main`, 링크 `hx-boost`(swap-safe 판정 시험 뒤 옵트인) 필터 질의 교체 후보 | - | - | 적합 | `audit/list` 조각 공유. 스트림 표와 검증 실패 문구가 계약에 추가됨 |
| `site/data.html` | 전체 화면 | 사이트 관리 > (자리 없음: 사이드바 링크 없음) | 경로 `사이트 관리 / 데이터` | `/sites/data`, `GET /sites/export`, `POST /sites/import` | `card`, `form-control`(파일), `btn` ; 옛 표지: ybtn-primary, ybtn | N-일반: 내려받기와 파일 가져오기(multipart)가 중심이라 이점이 없음 | - | - | 자리 없음 | 사이드바 링크가 없고 주소로만 열린다(admin-role-F22, 확인함). 새 사이드바에 넣을지 총괄 판단 12번 |
| `site/diagnostic.html` | 전체 화면 | 사이트 관리 > 시스템 > 시스템 진단 | 경로 `... / 시스템 / 시스템 진단` | `/sites/diagnostic` | `list-group`, `alert` | H-셸: `#shell-main`, 링크 `hx-boost`(swap-safe 판정 시험 뒤 옵트인) 진단 실행은 `hx-trigger="load"` 지연 불러오기 후보(점검이 느릴 수 있음, 미확인) | - | - | 적합 |  |
| `site/issue-list.html` | 전체 화면 | 사이트 관리 > 개요 > 이슈 | 경로 `사이트 관리 / 개요 / 이슈` | `/sites/issueList` | `table`, `nav nav-tabs`, `pagination` ; 옛 표지: yobicon | H-셸: `#shell-main`, 링크 `hx-boost`(swap-safe 판정 시험 뒤 옵트인) | `←` `→` | - | 적합 | 이슈 메뉴를 끈 프로젝트의 이슈도 관리자 목록에는 보일 수 있음(미확인) |
| `site/lfs.html` | 전체 화면 | 사이트 관리 > 설정 > LFS | 경로 `... / 설정 / LFS` | `/sites/lfs` | `form-check form-switch`, `btn` | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) | - | - | 적합 |  |
| `site/lost-password.html` | 셸 밖 | 셸 밖(독립 화면) | 화면 제목만 | `/lostPassword` | `card`, `form-floating`, `btn` ; 옛 표지: ybtn-primary, ybtn-large, ybtn-fullsize, ybtn | N-일반: 독립 인증 화면, 성공 뒤 이메일 안내 | - | - | 적합 | 현재 `site-layout`이라 상단 바가 있음. 총괄 판단 13번 |
| `site/mail.html` | 전체 화면 | 사이트 관리 > 메시지 > 메일 발송 | 경로 `사이트 관리 / 메시지 / 메일 발송` | `GET,POST /sites/mail` | `form-floating`, `btn`, `alert` ; 옛 표지: ybtn-primary, ybtn | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) | - | - | 적합 | 메일 설정 오류는 `errorMessage` 검사로 안내 |
| `site/mass-mail.html` | 전체 화면 | 사이트 관리 > 메시지 > 대량 메일 발송 | 경로 `... / 대량 메일 발송` | `GET /sites/massmail`, `POST /sites/mailList` | `form-check`, `btn`, `list-group` ; 옛 표지: ybtn, data-toggle="mail-type", ybtn-primary | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) `site.MassMail.js`가 숨은 폼을 `document.body`에 붙임(`:133`), 판정 필요 | - | - | 적합 |  |
| `site/oauth-apps.html` | 전체 화면 | 사이트 관리 > 설정 > OAuth 앱 | 경로 `... / 설정 / OAuth 앱` | `/sites/oauth-apps` | `form-check form-switch`, `btn` | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) | - | - | 적합 |  |
| `site/post-list.html` | 전체 화면 | 사이트 관리 > 개요 > 게시물 | 경로 `사이트 관리 / 개요 / 게시물` | `/sites/postList` | `table`, `pagination` ; 옛 표지: yobicon | H-셸: `#shell-main`, 링크 `hx-boost`(swap-safe 판정 시험 뒤 옵트인) | `←` `→` | - | 적합 |  |
| `site/project-list.html` | 전체 화면 | 사이트 관리 > 개요 > 프로젝트 | 경로 `사이트 관리 / 개요 / 프로젝트` | `/sites/projectList` | `table`, `btn-danger`, `modal`(삭제), `pagination` ; 옛 표지: ybtn, ybtn-danger, data-toggle="delete-project", yobicon | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) 삭제 행 갱신 | `←` `→` | - | 적합 | 삭제 실패 표시가 계약에 추가됨 |
| `site/security.html` | 전체 화면 | 사이트 관리 > 설정 > 보안 | 경로 `사이트 관리 / 설정 / 보안` | `/sites/security` | `form-floating`, `form-check form-switch`, `card`, `btn` | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) 저장 단추 세 개가 각자 영역을 저장(`site.security.saveScope`)하므로 영역별 `hx-post` | - | - | 적합 | 2단계 인증 강제와 로컬 로그인 정책 포함 |
| `site/sso.html` | 전체 화면 | 사이트 관리 > 설정 > SSO | 경로 `... / 설정 / SSO` | `/sites/sso` | `nav nav-tabs`(LDAP, OIDC 탭 `#sso-tabs`), `form-floating`, `btn` | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) 검사 단추는 `hx-post` + 결과 영역 | - | `prefs`(`yobitab-sso-tabs`) | 적합 | BS 탭이 마지막으로 고른 탭을 localStorage에 둠(`ui/Tabs.js:53,71`) |
| `site/update.html` | 전체 화면 | 사이트 관리 > 시스템 > 업데이트(새 버전이면 배지 `1`) | 경로 `... / 시스템 / 업데이트` | `GET /sites/update`, `POST /sites/watchUpdate`, `POST /sites/unwatchUpdate` | `card`, `badge`, `btn` ; 옛 표지: ybtn, ybtn-success, ybtn-info | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) 알림 켜기와 끄기 `hx-post` + 상태 영역 `outerHTML` | - | - | 적합 | 배지는 알림을 껐어도 남음(값만 봄) |
| `site/user-list.html` | 전체 화면 | 사이트 관리 > 개요 > 사용자 | 경로 `사이트 관리 / 개요 / 사용자`, 머리 `사용자` + 검색 | `/sites/userList` | `table table-hover`, `badge`, `btn-group`, `dropdown`, `pagination` ; 옛 표지: ybtn, ybtn-small, ybtn-danger, data-toggle="reset-password" | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) 상태 변경, 잠금, 삭제는 `hx-post` + 행 갱신 후보. 지금은 `userList?pageNum=0` 등으로 되돌리는 흐름 | `←` `→` | - | 적합 | 자기 자신과 기본 admin은 삭제와 잠금 불가 |

### 7. 사용자, 인증, OAuth, 기타(user, oauth, oauth2, audit, migration, welcome)

| 템플릿 | 종류 | 새 맥락과 사이드바 항목 | 경로(breadcrumb)와 페이지 머리 | 주소(바꾸지 않음) | Bootstrap 구성 요소 | htmx 사용 | 단축키 선언 후보 | 클라이언트 저장(IndexedDB) | 골격 적합도 | 메모(계약 근거와 주의) |
|---|---|---|---|---|---|---|---|---|---|---|

| `audit/list.html` | 조각 | 감사 로그 표, 필터, 내려받기, 무결성 검증(`auditList(exportable, verifiable, showFilter)`) | 해당 없음 | `/sites/audit`, `/organizations/{name}/audit`, `/user/editform/audit` | `table`, `form-select`, `btn` | 조각, 쓰이는 화면: 사이트, 조직, 사용자 감사 화면. 필터와 쪽 이동은 목록 교체 후보 | - | - | 적합 | 맥락 횡단 기능의 대표(감사 로그 3곳) |
| `migration/home.html` | 전체 화면 | 전역 > (자리 없음: 링크가 없음) | 경로 `이전`, 머리 `Yona 프로젝트를 GitHub로 옮기기` | `GET /migration`(설정 `yona.github.allow-migration`이 켜졌을 때만) | Vue 앱, `card`, `progress` ; 옛 표지: yobicon | N-일반: Vue 앱이 `setInterval`과 `beforeunload`를 모듈 최상위에서 등록(`Migration.js:596,603`), 외부 글꼴 로드 | - | - | 자리 없음 | 어떤 템플릿에서도 이 화면으로 가는 링크가 없다(확인함). 프로젝트별 JSON 주소는 프로젝트 매니저, 조직 관리자, 사이트 관리자 전용 |
| `oauth/partial-apps.html` | 조각 | OAuth 앱 목록, 만들기와 수정 양식, 비밀 값 화면을 담은 조각 묶음(`mine`, `appForm`, `secret`) | 해당 없음 | - | `table`, `form-floating` ; 옛 표지: tf-group | 조각, 쓰이는 화면: 사용자 설정과 조직 설정의 OAuth 앱 화면 6개 | - | 저장 금지(비밀 값) | 적합 | 사용자와 조직이 한 조각을 공유(맥락 횡단) |
| `oauth2/consent.html` | 셸 밖 | 셸 밖(OAuth 동의 화면, IA 결정) | 화면 제목만 | `GET /oauth2/consent?client_id=&scope=&state=` | `card`, `list-group`(범위), `btn` ; 옛 표지: ybtn, ybtn-primary | N-일반: 외부 앱의 승인 화면, `frame-ancestors 'none'` | - | - | 적합 | 지금 `site-layout`이지만 IA에서 셸 밖으로 정함(`ui-ia-gitlab.md` 9절). 허용 폼은 요청 범위 전체를 보냄(줄일 수 없음) |
| `user/device.html` | 셸 밖 | 셸 밖(명령줄 도구 승인 화면, 동의 화면과 같은 성격) | 화면 제목만 | `GET /users/device?user_code=`, `POST /users/device/approve`, `POST /users/device/deny` | `card`, `form-floating`, `btn` ; 옛 표지: ybtn, ybtn-success | N-일반: 승인 화면은 전체 문서 + 요청 기기 정보 | - | - | 어색함 | 지금 `site-layout`을 써서 상단 바가 있다. 5회 틀리면 15분 잠금(429) |
| `user/edit-audit.html` | 전체 화면 | 사용자 설정 > 접근과 보안 > 보안 기록 | 경로 `... / 접근과 보안 / 보안 기록` | `GET /user/editform/audit` | `table`, `btn` | H-셸: `#shell-main`, 링크 `hx-boost`(swap-safe 판정 시험 뒤 옵트인) | - | - | 적합 | `audit/list` 조각 공유. 사이트와 조직에도 같은 조각의 감사 화면 |
| `user/edit-emails.html` | 전체 화면 | 사용자 설정 > 계정 > 이메일 설정 | 경로 `... / 계정 / 이메일 설정` | `GET,POST /user/editform/emails` | `list-group`, `form-floating`, `btn` ; 옛 표지: ybtn, ybtn-small, yobicon | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) 추가와 삭제 행 갱신 | - | - | 적합 |  |
| `user/edit-gpg-new.html` | 전체 화면 | 사용자 설정 > 접근과 보안 > GPG 키(활성 유지) | 경로 `... / GPG 키 / 추가` | `GET /user/editform/gpg/new`, 오류 시 `POST /user/gpg` 400 | `form-floating`(textarea) | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) | - | - | 적합 |  |
| `user/edit-gpg.html` | 전체 화면 | 사용자 설정 > 접근과 보안 > GPG 키 | 경로 `... / GPG 키`, 머리 `GPG 키` + 추가 | `GET /user/editform/gpg` | `list-group`, `btn-danger` | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) 삭제 행 갱신 | - | - | 적합 | 키 설정은 목록+추가 두 화면(메모리 지침) |
| `user/edit-linked-accounts.html` | 전체 화면 | 사용자 설정 > 계정 > 연결된 계정 | 경로 `... / 계정 / 연결된 계정` | `GET /user/editform/linked-accounts` | `list-group`, `btn` | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) 해제 행 갱신 | - | - | 적합 | SSO가 꺼진 사이트에서는 목록이 빔(미확인) |
| `user/edit-notifications.html` | 전체 화면 | 사용자 설정 > 알림 > 알림 설정 | 경로 `... / 알림 / 알림 설정` | `GET,POST /user/editform/notifications` | `accordion`(프로젝트별), `form-check form-switch` ; 옛 표지: lst-stacked, data-toggle="switch" | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) | - | - | 적합 | 프로젝트별 알림 종류. 사이드바에서는 "알림" 묶음에 항목이 하나뿐이라 묶음이 어색함(총괄 판단 14번) |
| `user/edit-oauth-app-form.html` | 전체 화면 | 사용자 설정 > 애플리케이션 > OAuth 앱(활성 유지) | 경로 `... / OAuth 앱 / 새 앱` 또는 `/ 수정` | `GET /user/editform/oauth-apps/new`, `/{id}`, 오류 시 `POST` 400 | `form-floating`, `form-check`(범위) | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) `data-submit-once` | - | - | 적합 | 이전(transfer) 구역 포함 |
| `user/edit-oauth-app-secret.html` | 전체 화면 | 사용자 설정 > 애플리케이션 > OAuth 앱(활성 유지) | 경로 `... / OAuth 앱 / 비밀 값` | `POST /user/oauth-apps`, `POST .../{id}/secret` | `alert`, `input-group` | N-일반: 한 번만 보이는 비밀 값 | - | 저장 금지(비밀 값) | 적합 |  |
| `user/edit-oauth-apps.html` | 전체 화면 | 사용자 설정 > 애플리케이션 > OAuth 앱(사용자 앱 사용 안 함이면 항목과 새 앱 추가 숨김, 직접 주소는 403) | 경로 `... / 애플리케이션 / OAuth 앱` | `GET /user/editform/oauth-apps` | `table`, `btn` | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) 승인 취소는 `hx-post` + 행 갱신 | - | - | 적합 | 내가 만든 앱과 내가 승인한 앱 두 목록 |
| `user/edit-password.html` | 전체 화면 | 사용자 설정 > 계정 > 비밀번호 변경 | 경로 `... / 계정 / 비밀번호 변경` | `GET,POST /user/editform/password` | `form-floating`, `btn` ; 옛 표지: ybtn, ybtn-success, ybtn-fail | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) | - | - | 적합 | 새 비밀번호 8자 이상 128자 이하, 흔한 값 거절 |
| `user/edit-ssh-new.html` | 전체 화면 | 사용자 설정 > 접근과 보안 > SSH 키(활성 유지) | 경로 `... / SSH 키 / 추가` | `GET /user/editform/ssh/new`, 오류 시 `POST /user/ssh` 400 | `form-floating`(textarea) | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) | - | - | 적합 |  |
| `user/edit-ssh.html` | 전체 화면 | 사용자 설정 > 접근과 보안 > SSH 키 | 경로 `... / SSH 키` | `GET /user/editform/ssh` | `list-group`, `btn-danger` | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) | - | - | 적합 |  |
| `user/edit-token-edit.html` | 전체 화면 | 사용자 설정 > 접근과 보안 > 사용자 토큰(활성 유지) | 경로 `... / 사용자 토큰 / 수정` | `GET /user/editform/token/{id}`, `POST /user/token/{id}` | `form-floating`, `btn-danger` ; 옛 표지: ybtn, ybtn-success, ybtn-danger | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) | - | - | 적합 |  |
| `user/edit-token-new.html` | 전체 화면 | 사용자 설정 > 접근과 보안 > 사용자 토큰(활성 유지) | 경로 `... / 사용자 토큰 / 새 토큰` | `GET /user/editform/token/new`, 오류 시 `POST /user/token` 400 | `form-floating`, `form-check`(범위), 달력 ; 옛 표지: ybtn, ybtn-success | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) | - | - | 적합 | `partial-token-form` 공유 |
| `user/edit-token.html` | 전체 화면 | 사용자 설정 > 접근과 보안 > 사용자 토큰 | 경로 `... / 접근과 보안 / 사용자 토큰`, 머리 `사용자 토큰` + 새 토큰 | `GET /user/editform/token`, `POST /user/token`, `POST /user/token/{id}/regenerate` | `table`, `btn`, `alert`(만든 직후 값) ; 옛 표지: ybtn, ybtn-success, ybtn-small | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) 만든 직후 값 한 번 표시는 응답 조각. 다시 만들기 `hx-post` | - | 저장 금지(토큰 값, `ui-client-storage.md` 5절) | 적합 | `data-submit-once` |
| `user/edit-two-factor-codes.html` | 전체 화면 | 사용자 설정 > 접근과 보안 > 2단계 인증(백업 코드, 한 번만 표시) | 경로 `... / 2단계 인증 / 백업 코드` | `POST /user/two-factor/totp`, `POST /user/two-factor/backup-codes`, `GET /user/two-factor/webauthn/done` | `list-group`, `alert`, `btn`(복사, 인쇄) | N-일반: 한 번만 보이는 백업 코드이므로 이동 이력에 조각으로 남기지 않음 | - | 저장 금지(백업 코드) | 적합 |  |
| `user/edit-two-factor-key.html` | 전체 화면 | 사용자 설정 > 접근과 보안 > 2단계 인증(활성 유지) | 경로 `... / 2단계 인증 / 보안 키` | `GET /user/editform/two-factor/security-key` | `form-floating`, `btn` | N-일반: `webauthn.js`가 `navigator.credentials` 호출 후 `POST`, 완료 화면이 따로 있음 | - | - | 적합 | 강제 모드에서 이 경로가 두 번째 이동 대상에서 빠지던 결함은 수정(usersec-F5) |
| `user/edit-two-factor-setup.html` | 전체 화면 | 사용자 설정 > 접근과 보안 > 2단계 인증(활성 유지) | 경로 `... / 2단계 인증 / 인증 앱 등록` | `POST /user/two-factor/begin`, 오류 시 `POST /user/two-factor/totp` 400 | `card`, `form-floating`, QR 이미지 | N-일반: 등록 시드와 QR을 담는 화면이라 htmx history에 남기지 않기 위해 일반 이동 | - | 저장 금지(시드) | 적합 |  |
| `user/edit-two-factor.html` | 전체 화면 | 사용자 설정 > 접근과 보안 > 2단계 인증 | 경로 `... / 2단계 인증` | `GET /user/editform/two-factor` | `card`, `list-group`, `btn` | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) | - | - | 적합 | 강제 대상이면 다른 화면이 여기로 303(`twoFactor.enforced`) |
| `user/edit.html` | 전체 화면 | 사용자 설정 > 프로필 > 프로필 수정 | 경로 `사용자 설정 / 프로필 / 프로필 수정`, 머리 제목 + 저장 | `GET /user/editform`, `GET,POST /user/editform/profile`, `POST /user/edit` | `form-floating`, `form-control`(아바타), `btn` ; 옛 표지: ybtn, ybtn-success, ybtn-small | H-셸+폼: `#shell-main` + 폼 `hx-post`(검증 실패 422 + 폼 조각, 성공 `HX-Redirect`, `hx-disabled-elt`) `user.Setting.js:339`의 `setTimeout`은 한 번성이라 안전. 판정 필요 | - | - | 적합 | 오류 시 `POST /user/resetPassword` 400도 이 화면 |
| `user/files.html` | 전체 화면 | 전역(홈) > 내 작업 > 내 파일 | 경로 `내 파일`, 머리 `내 파일` + 검색 | `GET /user/files?filter=&pageNum=` | `input-group`, `table table-hover`, `pagination` ; 옛 표지: yobicon, ybtn | H-셸: `#shell-main`, 링크 `hx-boost`(swap-safe 판정 시험 뒤 옵트인) 검색은 `hx-get` 목록 교체 후보 | `←` `→` | - | 적합 | 열 이름이 영문으로 보임(IA 4.2.2, v1.6 번들과의 일치 미확인) |
| `user/login-two-factor.html` | 셸 밖 | 셸 밖(독립 2단계 코드 화면) | 화면 제목만 | `GET /users/login/2fa` | `card`, `form-floating` ; 옛 표지: ybtn, ybtn-primary | N-일반: `webauthn.js` 보안 키 호출과 인증 흐름 | - | - | 적합 |  |
| `user/login.html` | 셸 밖 | 셸 밖(독립 로그인 화면) | 화면 제목만 | `GET /users/loginform`, `POST /users/login` 400 재표시 | `card`, `form-floating`, `btn` ; 옛 표지: ybtn, ybtn-primary, ybtn-large, ybtn-fullsize | N-일반: 로그인과 가입 흐름은 인증 상태가 바뀌어 전체 새 문서가 안전 | - | - | 적합 | 403 로그인 필요 화면도 이 템플릿(`ErrorViews.loginRequired`). SSO 전용 모드는 비밀번호란이 없고 로컬 로그인 링크 |
| `user/partial-edit-tabmenu.html` | 조각 | 없앰(사용자 설정 사이드바 5묶음으로 대체) | 해당 없음 | - | `nav nav-tabs`(삭제) | 해당 없음 | - | - | 적합 | 항상 11개, 조건 없음 |
| `user/partial-issues.html` | 조각 | 사용자 페이지의 이슈 한 줄 | 해당 없음 | - | `list-group-item` ; 옛 표지: yobicon | 조각, 쓰이는 화면: 사용자 페이지 | - | - | 적합 | 초안은 제외(user-F10) |
| `user/partial-projectlist.html` | 조각 | 사용자 페이지의 프로젝트 한 줄 | 해당 없음 | - | `list-group-item` ; 옛 표지: yobicon, ybtn | 조각, 쓰이는 화면: 사용자 페이지 | - | - | 적합 |  |
| `user/partial-pull-requests.html` | 조각 | 사용자 페이지의 코드 주고받기 한 줄 | 해당 없음 | - | `list-group-item` ; 옛 표지: yobicon | 조각, 쓰이는 화면: 사용자 페이지 | - | - | 적합 |  |
| `user/partial-token-form.html` | 조각 | 토큰 이름, 범위, 만료 입력 | 해당 없음 | - | `form-floating`, 달력 ; 옛 표지: ybtn | 조각, 쓰이는 화면: 토큰 만들기, 수정 | - | - | 적합 | `ui/Calendar.js` |
| `user/reset-password.html` | 셸 밖 | 셸 밖(이메일 링크로 들어온 새 비밀번호) | 화면 제목만 | `GET /resetPassword?s=` | `card`, `form-floating` ; 옛 표지: ybtn-primary, ybtn-fullsize, ybtn | N-일반: 인증 흐름, `resetPassword.js` | - | - | 적합 |  |
| `user/signup.html` | 셸 밖 | 셸 밖(독립 가입 화면) | 화면 제목만 | `GET /users/signupform`, `POST /users/signup` 400 | `card`, `form-floating` ; 옛 표지: ybtn-primary, ybtn-large, ybtn-fullsize, ybtn | N-일반: `user.SignUp.js` 검사와 인증 흐름 | - | - | 적합 | 아이디는 한글 허용, `_`와 `.`를 연달아 쓰지 못함 |
| `user/verified.html` | 셸 밖 | 셸 밖(가입 이메일 확인 완료 안내) | 화면 제목만 | `GET /verify/{loginId}/{verificationCode}` | `alert alert-success` | N-일반: 안내 한 장 | - | - | 적합 |  |
| `user/view.html` | 전체 화면 | 전역 > (자리 없음: 사용자 프로필은 맥락이 아님) | 경로 `사용자 / 이름`, 머리 아바타, 이름, 로그인 ID, 기간 선택 | `/{user}?daysAgo=&selected=` | `nav nav-tabs`(이슈, 코드 주고받기, 프로젝트), `card`, `list-group`, `form-select`(기간) ; 옛 표지: yobicon, ybtn-mini, ybtn-default | H-셸: `#shell-main`, 링크 `hx-boost`(swap-safe 판정 시험 뒤 옵트인) 탭은 `data-bs-toggle="tab"`(서버가 세 목록을 한 번에 그림). `user.View.js` 판정 필요 | `U`(내 프로필 이동) | `prefs`(2단 보기, 하위 이슈), 쿠키 `daysAgo`는 서버 값 | 자리 없음 | `@{/{user}}`가 조직 이름이면 303으로 조직 페이지. 사이드바 항목이 없음(총괄 판단 3번) |
| `welcome/restart.html` | 셸 밖 | 셸 밖(비밀 키 설정 뒤 재시작 안내) | 화면 제목만 | 같은 인터셉터 | `card` ; 옛 표지: yobicon | N-일반: 안내 한 장 | - | - | 적합 |  |
| `welcome/secret.html` | 셸 밖 | 셸 밖(최초 기동 설정, 비밀 키 정하기) | 화면 제목만 | 비밀 키를 정하기 전의 모든 요청, `POST /` | `card`, `form-floating` ; 옛 표지: yobicon, ybtn-success, ybtn | N-일반: 서버 설정 상태가 바뀌는 독립 문서 | - | - | 적합 | `layout/`을 쓰지 않는 독립 문서 |


## 총괄이 판단할 것

선택지, 권장안, 이유를 적는다. 표의 "총괄 판단 N번"이 이 번호를 가리킨다. 주소, 요청, 응답, 기능을 바꾸는 선택지는 넣지 않았다.

1. **조직 설정과 조직 만들기를 조직 셸로 옮기는가.** 지금 조직 설정 10개 화면은 조직 머리와 조직 메뉴가 없는 `site-layout`을 쓴다(`shell.md` 2.2). 선택지: (가) 조직 셸(조직 사이드바 + 조직 경로)로 옮긴다. (나) 지금처럼 전역 틀에 둔다. 권장: **(가)**. 승인된 4.2절③이 이미 조직 사이드바에 설정을 넣었고, (나)는 설정 화면에서 조직 이름과 다른 조직 항목으로 이동할 방법이 없다. 조직 만들기(`organization/create`)는 맥락이 아직 없으므로 4번에서 함께 정한다.
2. **오류 화면의 셸.** 선택지: (가) 맥락이 있으면 그 맥락 셸(프로젝트와 조직 오류는 지금처럼), 기본 오류는 사이드바 없는 상단 바만, 500과 413은 셸이 못 그려질 때를 위한 최소 문서로 물러난다. (나) 모든 오류를 셸 밖 독립 화면으로 한다. (다) 모두 전역 셸. 권장: **(가)**. 사용자가 오류를 만난 자리에서 이동할 수 있고, 오류 처리기가 레이아웃 모델을 직접 채우는 구조(`GlobalErrorHandler.kt:95-99`)와 같다. 지금 기본 오류의 이동은 "홈" 단추 하나뿐이다. 오류 화면은 어떤 요청에도 항상 전체 HTML이라 htmx 교체 대상이 아니다(`shell.md` 9.1).
3. **골격에 자리가 없는 전역 화면의 맥락**: 사용자 프로필 `/{user}`, 도움말 `/_help`, 이전 `/migration`, 코드 비교 `compare`, 전역 검색 결과. 선택지: (가) 전역 셸에 두되 사이드바는 홈의 "탐색"을 그대로 쓰고, 프로필과 검색 결과는 본문 안 탭과 결과 영역으로 처리한다. 도움말은 상단 바나 아바타 메뉴에 링크를 더한다. (나) 사용자 프로필 맥락(사용자 사이드바)을 새로 만든다. (다) 지금처럼 링크 없는 주소로 둔다(v1.6과 같다). 권장: **프로필과 검색 결과는 (가), 도움말 링크는 아바타 메뉴 추가를 선생님께 묻는다.** (나)는 항목이 3개(이슈, 코드 주고받기, 프로젝트)뿐이라 과하다. `/migration`과 `compare`는 링크가 원래 없던 화면이므로 (다)를 권장한다(v1.6 동등성, 메뉴를 더하면 화면 모양이 바뀜).
4. **만들기 흐름과 이중 맥락 화면**: `project/create`, `importing`, `organization/create`, `pullrequest/fork`, `clone`, 전역 `issue/create`(`/user/issues/new`), `board/create`의 코드 파일 온라인 커밋. 선택지: (가) 만들기는 사이드바 없는 전역 셸(상단 바 `+`에서 진입), 경로만 `새 프로젝트`처럼 두고 소유자가 조직이면 조직 맥락으로 돌아가는 링크를 둔다. (나) 소유자 맥락 셸 안에서 연다(조직 안에서 새 프로젝트를 만들면 조직 사이드바). 권장: **(가)**. 같은 화면이 사용자와 조직 소유 둘을 다루고 진입점이 `+`이기 때문이다. 포크와 `clone` 진행 화면은 프로젝트 맥락이지만 진행 화면은 경로와 머리만 최소로 둔다. `board/create`의 온라인 커밋은 맥락을 바꾸지 않고 게시판 항목 아래에 둔다(주소 계약 때문에 이동할 수 없다).
5. **통계와 구독자 목록**: `project/statistics`는 미완(영어 한 줄)이고 링크가 없다. 구독자 목록은 머리의 링크로만 간다. 선택지: (가) 통계는 사이드바에 만들지 않고 그대로 둔다, 구독자는 프로젝트 홈 아래 항목으로 올린다. (나) 둘 다 만들지 않는다. 권장: **통계는 (가)의 앞부분(항목 없음), 구독자는 현행 유지(항목 없음, 머리 링크)**. 항목을 더하면 새 화면 모양이 생기고 v1.6에는 없었다. 구독자는 `canWatch`일 때만 링크가 있어 사이드바 조건이 별도로 필요해진다.
6. **라벨 항목의 위치와 조건.** 이슈 메뉴를 끄면 이슈와 마일스톤 주소는 404이지만 라벨 관리 주소는 설정 화면이 쓰므로 열려 있다. 선택지: (가) 계획 묶음에 두고 `menuSetting.issue`일 때만 보인다(메뉴를 끄면 설정에서 라벨을 관리할 입구가 없다). (나) 설정 묶음에 그대로 둔다(IA 8절 결정 5를 철회). (다) 계획 묶음에 두되 이슈 메뉴를 꺼도 항상 보인다. 권장: **(다)**. 서버가 주소를 열어 두고 "라벨 관리 경로는 설정 화면이 쓰므로 예외"라는 규칙(`index.md`)이 이미 있으며, 항목 숨김과 주소 규칙이 일치한다(주소가 404가 아니므로).
7. **전역 "내 리뷰"와 "내 코드 주고받기"**: v1.6에 화면이 없다. 권장: **넣지 않는다**(IA 결정 10 그대로). 기능을 임의로 더하지 않는다는 지침과 같다.
8. **`/user/sidebar?path=&hash=`**: `hash` 파라미터가 없으면 500(v1.6 `None.get` 재현). 이동으로 남길 때 선택지: (가) v1.6처럼 유지하고 `refactoring-backlog.md`에 기록한다. (나) 없으면 `path`만으로 이동한다. 권장: **(가)**. 프로젝트 지침(v1.6 결함은 그대로 따르고 backlog에 기록)과 같다.
9. **위키의 `Pages` 목록 위치**(`wiki/shell`, 위키 화면 6개). 선택지: (가) 위키 항목 아래 하위 항목으로 올려 사이드바 한 곳에 모은다. (나) 지금처럼 본문 오른쪽 보조 패널로 둔다(`col-lg-3`, 992px 미만에서 아래로 쌓임). 권장: **(나)**. 페이지 수가 많아질 수 있고 `_Sidebar`, `_Footer` 페이지 렌더링과 복제 주소가 같은 조각에 있어 서버 동작을 바꾸지 않고 둘 수 있다. 사이드바에 올리면 목록을 위해 맥락 메뉴가 길어진다.
10. **코드 이력의 `A`와 `S`**: 지금은 전역 `A`가 이겨 이전 쪽 `A`가 죽는 결함이다(R-147). 새 셸의 우선순위(화면 > 맥락 > 전역, `ui-shortcuts.md` 3절)에서는 화면 키가 이긴다. 선택지: (가) 그대로 따른다(결과가 의도한 동작으로 바뀐다). (나) v1.6처럼 전역이 이기게 예외를 둔다. 권장: **(가)**. D-137이 이미 정한 규칙이며 이 화면에서 `A`는 이전 쪽으로 동작하는 것이 v1.6의 의도였다. 동작이 바뀌므로 `differences-from-v16.md`에 기록한다.
11. **검색 최근 검색어**(`search-history`)는 v1.6에 없는 새 기능이다. D-135의 결정 4가 정한다. 이 문서는 결과 화면 3개에 저장소를 적었을 뿐 허용 여부는 거기서 정한다. 권장은 거기의 권장과 같다.
12. **`/sites/data`를 사이드바에 넣는가.** 컨트롤러와 화면이 있으나 링크가 없다(admin-role-F22). 선택지: (가) 시스템 묶음에 "데이터" 항목을 넣는다. (나) 지금처럼 주소로만 연다. 권장: **(가)**. 링크 없는 기능은 사실상 쓸 수 없는 상태인데 항목을 넣는 것은 기능 추가가 아니라 기존 화면의 입구이기 때문이다. 단 v1.6에도 링크가 없었으므로 화면 모양을 바꾸는 일이라 선생님 확인이 필요하다.
13. **셸 밖 독립 화면의 범위.** 로그인 계열(`user/login`, `login-two-factor`, `signup`, `verified`, `reset-password`, `site/lost-password`)과 `oauth2/consent`, `welcome/*`는 셸 밖으로 두었고, `user/device`(명령줄 기기 승인)는 동의 화면과 성격이 같아 셸 밖으로 두었다(지금은 `site-layout`이라 상단 바가 있음). 선택지: (가) 위 10개를 셸 밖. (나) 로그인과 가입만 셸 밖, 나머지는 상단 바만 있는 전역 셸. 권장: **(가)**. 인증 상태가 바뀌는 흐름이라 전체 문서 이동이 안전하고 사이드바가 의미 없다. `oauth2/consent`는 IA가 이미 셸 밖으로 정했다(`ui-ia-gitlab.md` 9절).
14. **사용자 설정 5묶음의 모양.** "알림" 묶음에 항목이 하나(`알림 설정`)이고 "애플리케이션"도 하나(`OAuth 앱`)다. 선택지: (가) 5묶음 유지(GitLab의 관례, 앞으로 항목이 늘 여지). (나) 항목이 하나인 묶음은 제목 없는 단독 항목으로 보인다. (다) 알림 설정을 "계정"에 합친다. 권장: **(나)**. 사용자에게 보이는 묶음 제목이 줄고 구조는 같다. OAuth 앱 항목은 사용자 앱 사용 설정이 꺼지면 숨기는 조건이 필요하다(서버는 `new`를 403으로 막는다).
15. **flash 메시지와 htmx 이동.** flash는 `common/scripts`의 인라인 스크립트가 문서 끝에서 한 번 실행한다(`scripts.html:131-138`, JS 문자열 리터럴에 서버 값을 그대로 넣는 구조). htmx로 본문만 바꾸면 이동 뒤에 심은 flash가 보이지 않는다. 선택지: (가) flash를 응답 헤더(`HX-Trigger`)나 OOB 조각으로 내려 토스트를 띄운다. (나) flash가 있는 응답은 일반 이동으로 물러난다(`HX-Redirect`나 목록 표지 검사와 같은 방식). 권장: **(나)로 시작해 (가)로 옮긴다.** (나)는 서버 변경 없이 안전하고, 폼 성공 뒤 이동에 flash가 필수인 화면(저장 성공 안내)이 많아 (가)는 폼 전환 단계에서 필요해진다. 어느 쪽이든 서버가 이스케이프한 값만 쓴다.
16. **H-셸 옵트인의 시작 범위.** 이 표는 H-셸과 H-셸+폼을 합쳐 82개 화면을 후보로 적었다. 선택지: (가) 사이트 관리와 사용자 설정(IA P1)부터 켜고 나머지는 판정 뒤. (나) 후보 전체를 한 번에. 권장: **(가)**. `ui-htmx-plan.md` 5절의 옵트인 원칙과 같고 두 맥락은 스크립트가 단순하다(`user.Setting.js`와 `site.MassMail.js`만 모듈). 일반 이동 35개 화면(셸 밖 제외)의 해제 순서는 "swap-safe 예비 판정 근거" 표의 모듈 정리 순서를 따른다.

## 범위 점검

230개 템플릿이 전수 대응표에 한 번씩 들어갔는지 디렉터리별로 확인했다(`find src/main/resources/templates -name '*.html'` 결과와 표의 템플릿 열을 대조해 누락 0, 중복 0, 목록에 없는 행 0).

| 디렉터리 | 개수 | 대응표 구역 | 템플릿 |
|---|---|---|---|
| `common/` | 30 | 1 | `analytics`, `attachment-file`, `calendar`, `child-comment-form`, `child-comments`, `comment-count`, `comment-delete-modal`, `comment-form`, `comment-update-form`, `editor`, `file-uploader`, `footer`, `importmap`, `login-dialog`, `markdown`, `my-series-menu-tab`, `navbar`, `notification-mail`, `partial-history`, `review-form`, `scripts`, `select2`, `sharer-count`, `show-subtasks-checkbox`, `sidebar`, `tasklist-bar`, `two-column-mode-checkbox-area`, `upload-form`, `usermenu`, `vote-count` |
| `error/` | 9 | 1 | `badrequest-default`, `badrequest`, `forbidden-default`, `forbidden`, `forbidden-organization`, `internal-server-error-default`, `notfound-default`, `notfound`, `request-text-entity-too-large` |
| `help/` | 3 | 1 | `keymap`, `markdown`, `toc` |
| `index/` | 8 | 1 | `menu-project`, `my-organization-list`, `my-project-list`, `my-recent-issue-list`, `notifications`, `partial-intro`, `partial-notifications`, `usermenu-tab-content-list` |
| `layout/` | 7 | 1 | `layout-framed`, `layout`, `migration-layout`, `organization-layout`, `project-layout`, `site-layout`, `site-mng-layout` |
| `milestone/` | 5 | 2 | `create`, `edit`, `list`, `partial-status`, `view` |
| `project/` | 26 | 2 | `change-vcs`, `create`, `delete`, `deploykey-new`, `deploykeys`, `header`, `home`, `importing`, `issue-labels`, `lfs-hg`, `lfs`, `list`, `members`, `menu`, `partial-dashboard`, `partial-history`, `partial-issue-labels-list`, `partial-readme`, `partial-settingmenu`, `ruleset-form`, `rulesets`, `setting`, `statistics`, `transfer`, `watchers`, `webhooks` |
| `reviewthread/` | 1 | 2 | `list` |
| `board/` | 6 | 3 | `create`, `edit`, `list`, `partial-comments`, `partial-list`, `view` |
| `issue/` | 22 | 3 | `create`, `edit`, `list`, `my-list`, `my-partial-list`, `my-partial-search`, `partial-assignee`, `partial-comment`, `partial-comments`, `partial-index-comments`, `partial-list`, `partial-list-quicksearch`, `partial-list-wrap`, `partial-massupdate`, `partial-searchform`, `partial-select-label`, `partial-select-subtask`, `partial-show-selected-label`, `partial-subtasks`, `partial-voter-list`, `partial-voters`, `view` |
| `wiki/` | 6 | 3 | `compare`, `edit`, `empty`, `history`, `shell`, `view` |
| `code/` | 14 | 4 | `branches`, `compare`, `compare-svn`, `diff`, `history`, `nohead-hg`, `nohead`, `nohead-svn`, `partial-comment-thread`, `partial-signature`, `svn-diff`, `tags`, `thread-wrapper`, `view` |
| `pullrequest/` | 14 | 4 | `changes`, `clone`, `create`, `edit`, `fork`, `list`, `partial-branch`, `partial-info`, `partial-merge-result`, `partial-search`, `partial-state`, `partial-state-page`, `partial-timeline`, `view` |
| `search/` | 8 | 4 | `partial-items`, `partial-milestones`, `partial-projects`, `partial-search`, `partial-users`, `result-organization`, `result-project`, `result-site` |
| `organization/` | 17 | 5 | `audit`, `board-list`, `create`, `delete-form`, `header`, `issue-list`, `list`, `members`, `menu`, `oauth-app-form`, `oauth-app-secret`, `oauth-apps`, `oauth-policy`, `partial-settingmenu`, `pullrequest-list`, `setting`, `view` |
| `site/` | 15 | 6 | `audit`, `data`, `diagnostic`, `issue-list`, `lfs`, `lost-password`, `mail`, `mass-mail`, `oauth-apps`, `post-list`, `project-list`, `security`, `sso`, `update`, `user-list` |
| `audit/` | 1 | 7 | `list` |
| `migration/` | 1 | 7 | `home` |
| `oauth/` | 1 | 7 | `partial-apps` |
| `oauth2/` | 1 | 7 | `consent` |
| `user/` | 33 | 7 | `device`, `edit-audit`, `edit-emails`, `edit-gpg`, `edit-gpg-new`, `edit`, `edit-linked-accounts`, `edit-notifications`, `edit-oauth-app-form`, `edit-oauth-app-secret`, `edit-oauth-apps`, `edit-password`, `edit-ssh`, `edit-ssh-new`, `edit-token-edit`, `edit-token`, `edit-token-new`, `edit-two-factor-codes`, `edit-two-factor`, `edit-two-factor-key`, `edit-two-factor-setup`, `files`, `login`, `login-two-factor`, `partial-edit-tabmenu`, `partial-issues`, `partial-projectlist`, `partial-pull-requests`, `partial-token-form`, `reset-password`, `signup`, `verified`, `view` |
| `welcome/` | 2 | 7 | `restart`, `secret` |
| **합계** | **230** | | |

## 확인하지 못한 것

- **마크업 전수 대조**: Bootstrap 구성 요소 열은 계약과 옛 클래스 표지에 근거한 설계안이다. 템플릿 230개를 줄 단위로 읽어 현재 클래스를 대조하지 않았다. 옛 표지가 전혀 없는 47개 템플릿 가운데 조각과 이메일이 대부분이다.
- **swap-safe 최종 판정**: 스크립트의 문서와 창 수준 핸들러, 타이머를 읽어 낸 예비 판정이다. `detectChange.js`, `project.Global.js`, `project.Home.js` 등 일부 모듈은 호출마다 등록하는 핸들러가 있는지 줄 단위로 따라가지 않았다. 확정은 `ui-htmx-plan.md` 5절 3항의 시험으로 한다.
- 일부 화면의 보조 구성 요소(예: `organization/view`의 탭 유무, `site/issue-list`와 `site/project-list`의 탭과 모달, `site/diagnostic`의 점검 소요 시간)는 계약만 읽고 소스에서 다시 열어 보지 않았다. 표에는 "미확인"을 붙였거나 설계안으로 적었다.
- 실제 앱을 띄우지 않았고 화면을 캡처하지 않았다(읽기 전용 조사).
