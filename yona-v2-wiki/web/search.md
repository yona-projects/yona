---
title: 검색
type: web
sources: [app/controllers/SearchApp.java, app/models/Search.java, app/models/SearchResult.java, app/models/enumeration/SearchType.java, app/views/search/*, app/views/common/navbar.scala.html]
updated: 2026-10-25
---

# 검색

코드: `domain/search/SearchService`(JPQL), `SearchSnippets`, `SearchType`; `web/search/SearchController`, `SearchRowAssembler`, `SearchRows`. 템플릿은 `search/result-site`, `result-organization`, `result-project`, `partial-search`, `partial-items`, `partial-milestones`, `partial-users`, `partial-projects`.

## 라우트와 검증

| 범위 | 라우트 | 검사 |
|---|---|---|
| 전체 | `GET /search` | `@AnonymousCheck` |
| 조직 | `GET /organizations/:name/search` | `@AnonymousCheck` |
| 프로젝트 | `GET /:user/:project/search` | `@AnonymousCheck`, `@IsAllowed(READ)` |

- 쿼리: `keyword`(비어 있으면 400, 공백만은 유효), `searchType`(비어 있거나 모르는 값은 400, 대소문자 구분), `pageNum`(1부터. 숫자가 아니거나 1보다 작으면 400, A-87). 한 쪽은 20개이다.
- 조직 범위의 이름, 검색어, 종류가 비면 **본문 없는** 400이다. 사이트가 프로젝트 목록을 숨기고(`hide-project-listing`) 조직 구성원 역할과 관리자 역할을 모두 갖지 않았으면 본문 없는 400이다(R-50). 조직이 없거나 종류가 `not available`이면 오류 화면 400이다.
- 프로젝트 범위의 종류가 `project`이거나 `not available`이면 400이다.

## 종류 정하기

모든 종류의 건수를 세고(프로젝트 범위는 프로젝트를 세지 않아 0), 종류가 `auto`이면 건수가 있는 첫 종류를 이슈, 사용자, 프로젝트, 게시글, 마일스톤, 이슈 댓글, 게시글 댓글, 코드 리뷰 순으로 고른다. 모두 0이면 이슈이다. 사용자가 고른 종류는 건수가 0이어도 그대로이다. 상단 검색창은 항상 `auto`를 보낸다.

## 조건

키워드는 필드 안에 통째로 들어 있는지(대소문자 무시)만 본다.

| 종류 | 필드 | 정렬 |
|---|---|---|
| 이슈, 게시글 | 제목, 본문 | 만든 시각 내림차순 |
| 마일스톤 | 제목, 내용 | 마감일 내림차순 |
| 이슈 댓글, 게시글 댓글, 코드 리뷰 댓글 | 내용 | 만든 시각 내림차순 |
| 사용자(활성만) | 이름, 로그인 ID | 이름 오름차순 |
| 프로젝트 | 개요, 이름 | 이름 오름차순 |

볼 수 있는 프로젝트(이슈, 게시글, 마일스톤, 댓글):
- 전체 범위: 익명은 공개만. 로그인 사용자는 공개, 내가 구성원인 프로젝트, 내 조직의 프로젝트 중 비공개가 아닌 것.
- 조직 범위: 그 조직의 프로젝트 중 익명은 공개만, 로그인 사용자는 내가 구성원인 것과 공개(내가 그 조직 구성원이면 공개와 보호). 
- 프로젝트 범위: 그 프로젝트(읽기 권한은 라우트에서 확인).
- 이슈, 게시글, 댓글은 전체와 조직 범위에서 내가 쓴 글(이슈는 담당한 것도)을 권한과 관계없이 더한다. 마일스톤에는 없다.
- 사용자: 전체는 필터 없음, 조직은 조직 구성원, 프로젝트는 프로젝트 구성원.
- 프로젝트(검색 대상): 익명은 공개(목록 숨김이면 없음). 로그인 사용자는 공개(목록 숨김이면 제외), 내 조직의 보호 프로젝트, 내가 구성원인 프로젝트. 조직 범위는 그 조직으로 한정한다.

## 화면

왼쪽 탭 여덟 개(프로젝트 범위는 프로젝트 탭 없음)에 건수 배지가 붙고 현재 종류는 `active`, 건수가 0이면 `empty`이다. 오른쪽에 검색 폼(`#searchInnerForm`, 범위별 주소, 숨은 `searchType`), 결과 제목(`search.result.title`, HTML), 목록이 있다. 탭을 누르면 `searchType`만 바꿔 폼을 제출한다. 키워드는 클라이언트가 `.search-content-body`, `.title` 안에서 `<strong class="keyword">`로 강조한다.

항목: 이슈, 게시글은 `#번호` 제목, 키워드 앞뒤 40자 발췌(본문보다 짧으면 `.....`), 프로젝트 링크(프로젝트 범위 제외), 작성자, 시각. 댓글은 `#번호 Re) 제목`이 댓글 앵커 링크이다. 코드 리뷰 댓글은 풀 리퀘스트 위이면 `#번호 Re) 제목`과 발췌이고, 커밋 위이면 발췌 전체가 스레드 링크이다. 마일스톤은 제목, 발췌, 마감일과 남은 기간이다. 사용자는 아바타, `이름 (@ID)`, 가입일이다. 프로젝트는 로고, `소유자/이름`, 포크 원본, 개요, 만든 시각, 마지막 푸시이며 쪽 이동이 없다(R-50). 게시글 댓글의 작성자 없음 문구는 `posting.noAuthor`가 그대로 보인다(R-50).
