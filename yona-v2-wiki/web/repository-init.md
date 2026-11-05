---
title: 저장소 초기화
type: web
sources: [app/controllers/Application.java:106-126, app/playRepository/RepositoryService.java:71-74, conf/routes:34]
updated: 2026-10-25
---

# 저장소 초기화 (`GET /_init`)

v1.6의 개발용 경로이다. 2026-10-25 동등성 점검(9a)에서 v2에 없는 것으로 드러났고, 선생님이 v1.6 그대로 재현하기로 결정하셨다(D-27, R-66). 이후 2026-11-05에 접근을 같은 컴퓨터로 제한했다(A-366).

## 동작

- v1.6은 접근 제한이 없어 로그인하지 않아도 누구나 호출할 수 있었다. 2026-11-05 선생님 결정(R-66, A-366)으로 **서버와 같은 컴퓨터(루프백 주소 `127.0.0.1`, `::1`)에서 직접 온 요청만** 받고 그 밖에는 403이다. 프록시를 거쳤다는 헤더(`X-Forwarded-For`, `Forwarded`)가 있어도 받지 않는다. 같은 컴퓨터의 리버스 프록시 뒤에서는 연결 주소가 프록시의 루프백이 되어 바깥 요청도 루프백으로 보이기 때문이다. 로그인 여부는 보지 않는다.
- 모든 프로젝트에 대해 `RepositoryService.createRepository(project)`를 부른다. 이 함수는 저장소를 **지운 뒤** 프로젝트의 VCS(Git, Subversion)에 맞는 빈 저장소를 새로 만든다. 기존 커밋과 브랜치는 모두 사라진다.
- 한 프로젝트에서 예외가 나도(예: 알 수 없는 VCS) 오류만 남기고 다음 프로젝트로 넘어간다.
- 끝나면 홈(`/`)으로 보낸다(`303`).

## 구현

`RepositoryInitController`가 `GET /_init`을 처리한다. 테스트는 `RepositoryInitTest`이다.
