---
title: 저장소 초기화
type: web
sources: [app/controllers/Application.java:106-126, app/playRepository/RepositoryService.java:71-74, conf/routes:34]
updated: 2026-10-25
---

# 저장소 초기화 (`GET /_init`)

v1.6의 개발용 경로이다. 2026-10-25 동등성 점검(9a)에서 v2에 없는 것으로 드러났고, 선생님이 v1.6 그대로 재현하기로 결정하셨다(D-27, R-66).

## 동작

- 접근 제한이 없다. 로그인하지 않아도 호출할 수 있다(`Application` 클래스에 `@AnonymousCheck`가 없고 이 액션에도 없다).
- 모든 프로젝트에 대해 `RepositoryService.createRepository(project)`를 부른다. 이 함수는 저장소를 **지운 뒤** 프로젝트의 VCS(Git, Subversion)에 맞는 빈 저장소를 새로 만든다. 기존 커밋과 브랜치는 모두 사라진다.
- 한 프로젝트에서 예외가 나도(예: 알 수 없는 VCS) 오류만 남기고 다음 프로젝트로 넘어간다.
- 끝나면 홈(`/`)으로 보낸다(`303`).

## 구현

`RepositoryInitController`가 `GET /_init`을 처리한다. 테스트는 `RepositoryInitTest`이다.
