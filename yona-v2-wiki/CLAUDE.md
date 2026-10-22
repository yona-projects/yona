# Yona v2 LLM Wiki: 스키마

이 위키는 Andrej Karpathy의 [LLM Wiki 패턴](https://gist.github.com/karpathy/442a6bf555914893e9891c11519de94f)을 따른다.
LLM이 소스를 읽고 상호 링크된 마크다운 위키를 한 번 컴파일해 두고 계속 갱신한다.

## 3개 레이어

| 레이어 | 위치 | 규칙 |
|---|---|---|
| Raw source (불변) | Yona v1.6 소스(`app/`, `conf/`, `public/`)와 `~/yona-convert/yona-v1.6-wiki` | 읽기만 한다. 수정하지 않는다. |
| Wiki (LLM 소유) | 이 디렉터리 | 개발하면서 작성하고 갱신한다. |
| Schema | 이 파일 | 구조, 규약, 워크플로를 정의한다. |

## 탐색 파일
- `index.md`: 모든 페이지의 카탈로그(링크와 한 줄 요약). 질문 응답 시 가장 먼저 읽는다.
- `log.md`: append-only 시간순 기록. 항목 접두사는 `## [YYYY-MM-DD] ingest|query|lint|build | 제목`이다.

## 페이지 규약
- 언어는 한국어이고, 식별자, 경로, 코드는 원문을 유지한다.
- 파일 상단에 YAML frontmatter(`title`, `type`, `sources`, `updated`)를 둔다.
- v1.6 기준 사실은 `app/models/Project.java:123` 형식으로 출처를 단다. 확인하지 못한 것은 "미확인"으로 적는다.
- 새 페이지는 반드시 `index.md`에 등록한다(고아 페이지 금지).

## 개발 원칙
- v1.6을 그대로 따른다. DB 테이블, 기능, 동작, 요청·응답 계약, 템플릿, JS 구성을 임의로 바꾸지 않는다.
- 아키텍처 때문에 달라지는 부분은 `architecture/differences-from-v16.md`에 이유와 함께 기록한다.
- 개발은 TDD(Red, Green, Refactor)로 진행하고, 코드를 만들 때마다 대응하는 위키 페이지를 함께 갱신한다.
- 코드 주석은 한글로, 공개 API 문서(javadoc, mkdocs)로 쓸 수 있는 수준으로 작성한다.
- Gradle은 항상 샌드박스 바깥에서 실행한다.
