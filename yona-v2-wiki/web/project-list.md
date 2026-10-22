---
title: 프로젝트 목록 (/projects)
type: web
sources: [app/controllers/ProjectApp.java:1052-1136, app/views/project/list.scala.html, app/controllers/LabelApp.java:128-139, app/utils/TemplateHelper.scala:98-115, app/utils/JodaDateUtil.java, app/models/User.java:441-445]
updated: 2026-10-22
---

# 프로젝트 목록

`GET /projects`를 옮겼다. 컨트롤러는 `ProjectListController`, 검색과 쪽 나누기는 `ProjectListService`, 화면은 `templates/project/list.html`이다.

## 동작

| 항목 | 규칙 |
|---|---|
| 접근 | 클래스에 `@AnonymousCheck`, 메서드에 `@GuestProhibit`(게스트는 홈으로). 사이트가 목록을 숨기면(`yona.hide-project-listing`) 403 화면 `error.auth.unauthorized.waringMessage` |
| 응답 형식 | `Accept`가 JSON을 더 원하면 JSON, 아니면 HTML. 항상 `Vary: Accept`. v1.6의 406 분기는 선택 함수가 항상 HTML로 대체해서 실행되지 않았고 v2도 같다 |
| 검색어(`filter`) | 소유자, 이름, 설명, 라벨 이름 중 하나에 포함(대소문자 무시) |
| 라벨 필터(`labelIds`) | 값이 여러 개이거나 쉼표로 이어도 된다. 해당 라벨이 붙은 프로젝트만 |
| 보이는 범위 | 보는 사람이 사이트 관리자이거나 `yona.display-private-repositories=true`이면 전체, 아니면 **공개(PUBLIC) 프로젝트만**. 구성원인 비공개 프로젝트도 목록에는 나오지 않는다 |
| 정렬 | 만든 날짜 내림차순 |
| 쪽 나누기 | 한 쪽 10개(`pageNum`은 1부터). 1보다 작으면 404. 전체 쪽 수는 화면 스크립트 `yobi.Pagination.update($("#pagination"), 쪽수)`가 쓴다 |
| JSON | `["소유자/이름", ...]`. 최대 1000개이고, 더 많으면 `Content-Range: items 1000/전체` 헤더를 붙인다 |

## 화면 항목

한 항목에는 로고(있을 때만), 이름 링크, 포크 원본 링크, 비공개 자물쇠, 라벨(분류를 CSS 클래스로 삼고 `/projects?labelIds=번호`로 링크), 설명, "by 소유자 at 시각"(`agoOrDateString`, 툴팁은 전체 날짜), 최근 코드 갱신 시각이 나온다. 공개 프로젝트는 오른쪽에 구성원 아바타(이름 순, 사이트 관리자 행 제외)와 구성원 수, 구독자 수를 보여 준다.

탭은 "PUBLIC Project list"와 "Group List"(`/orgs`)이다.

### v1.6에서 사실상 실행되지 않는 분기

템플릿에는 읽기 권한이 없는 프로젝트를 흐리게 보여 주는 분기("You do not have permission to view this project's information")가 있다. 조건은 `displayPrivateRepositories || 읽기 권한`의 반대인데, 이 설정이 꺼져 있으면 목록에는 읽을 수 있는 공개 프로젝트(또는 사이트 관리자가 보는 전체)만 있어서 조건이 거짓이 될 일이 없다. 충실한 이식을 위해 템플릿에는 남겨 두었다.

## 도우미 `helper` (`TemplateHelper`)

모든 템플릿에서 모델 이름 `helper`로 쓴다. v1.6 `utils.TemplateHelper`와 `JodaDateUtil`의 일부이다.

| 메서드 | 규칙 |
|---|---|
| `dateString(date)` | `yyyy-MM-dd h:mm:ss a`. v1.6처럼 JVM 기본 로캘로 오전/오후를 표기. 날짜가 없으면 빈 문자열 |
| `agoString(date)` | 1일 이상 `common.time.day`(복수형 규칙), 1시간 이상 `hour`, 1분 이상 `minute`, 1초 이상 `second`, 그보다 짧으면 `common.time.just` |
| `agoOrDateString(date)` | 8일 미만이면 `agoString`, 같은 해이면 `MM-dd`, 아니면 `yyyy-MM-dd` |
| `plural(key, count)` | 개수가 1이 아니면 키에 `s`를 붙여 고른 메시지에 개수를 문자열 인자로 넘긴다 |

시각은 주입하는 `Clock`을 쓰므로 테스트에서 고정할 수 있다. `TemplateHelperTest` 11건.

## 지연 로딩과 템플릿

`spring.jpa.open-in-view=false`라서 템플릿은 트랜잭션 밖에서 그려진다. 라벨과 구성원처럼 지연 로딩 연관은 서비스(`ProjectListService`)가 트랜잭션 안에서 읽어 `ProjectListItem`에 담아 넘긴다. 다른 화면 테스트는 메서드 전체가 하나의 트랜잭션이라 이런 실수를 숨기므로, 커밋된 데이터를 그리는 `CommittedDataRenderTest`를 따로 두었다. 새 화면을 옮길 때도 지연 로딩 연관을 템플릿이 직접 건드리지 않게 한다.

## 검증

`ProjectListTest` 20건(탭, 빈 목록, 검색 폼, 가시성 3가지, 검색 3가지, 정렬, 쪽 나누기, 항목, 로고, 포크, 라벨, 구성원과 구독자, JSON, Vary, 게스트), `ProjectListHiddenTest` 1건(목록 숨김 403), `ProjectListPrivateDisplayTest` 1건, `CommittedDataRenderTest` 1건.
