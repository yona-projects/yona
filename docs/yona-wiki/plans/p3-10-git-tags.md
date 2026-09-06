---
type: plan
id: P3-10
title: "Git 태그(tag) 지원"
status: planned
priority: 8
depends_on: [p3-02-cli-and-rest-api]
blocks: []
source: 사용자 요청(2026-09-03) — "gh에 태그 기능이 있는걸로 아는데 yona도 구현할거거든"
created: 2026-09-03
updated: 2026-09-06
tags: [plan, p3, vcs, cli]
---

# Git 태그(tag) 지원

## 배경

사용자 요청(2026-09-03) — GitHub(`gh`)에 태그 기능이 있듯 yona에도 git 태그 지원을 넣을 계획.
아직 착수 전, TODO로만 등록한다(현재 라운드 작업 대상 아님 — [[p3-02-cli-and-rest-api]]의 `gh status`
갭 해소만 진행 중).

참고: `gh` 자체엔 독립된 `tag` 최상위 명령이 없다(태그는 `gh release`에 종속된 개념이거나 순수
`git tag`/`git push --tags`로 다룬다 — 2026-09-03 실측 확인, `gh --help`/`gh release --help`/`gh repo
--help`에 tag 서브커맨드 없음). yona가 무엇을 만들지는 착수 시점에 다시 정의해야 한다 — 최소한
아래를 확인하고 시작할 것:

- yona/legacy-yona에 태그 관련 기존 코드가 있는지(`Tag`/`TagController`/`git tag` 관련 JGit 호출)
  전수 확인 — [[p3-02-cli-and-rest-api]]의 감사표가 `gh release`를 "yona에 릴리즈/태그 배포 개념
  없음(전수 확인 — `Release`/`ReleaseController` 0건)"으로 이미 기록해뒀으므로, 태그 자체도 별도로
  다시 확인해야 한다(release와 태그는 다른 개념).
- 범위: 코드 브라우저에서 태그 목록/브라우징(브랜치 셀렉터 옆 태그 셀렉터), REST API(`/api/v1/
  projects/{owner}/{project}/tags` 등 — [[p3-02-cli-and-rest-api]]의 기존 리소스 세그먼트 패턴
  재사용), `yona-cli`의 `yona tag list/create/delete` 같은 서브커맨드.
- [[p3-02-cli-and-rest-api]]의 Fine-grained PAT 스코프 체계(`ApiTokenScopeGroup`)에 태그를 어느
  그룹(CODE?)으로 편입할지 결정 필요.

## 범위/설계는 착수 시점에 확정

이 문서는 지금은 TODO 등록용 스텁이다 — 착수 전 위 조사 항목부터 실측으로 채운 뒤 범위를 확정한다.
