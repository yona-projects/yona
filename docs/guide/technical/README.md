# 기술 문서

legacy Yona의 `docs/ko/technical/` + `docs/technical/`(두 세트를 합침, 실제로는 겹치지 않는
서로 다른 11개 + 5개 문서였다)를 yona 기준으로 옮긴 기술 참고 문서. 성격이 다른 두 부류로
나뉜다.

## 지금도 그대로 적용되는 문서 (코드로 확인)

- [access-control.md](access-control.md) — 권한 규칙(비즈니스 로직, 프레임워크 무관)
- [javascript-module-guide.md](javascript-module-guide.md) — `yobi.*` JS 모듈 패턴
- [javascript-naming-convention.md](javascript-naming-convention.md) — JS 네이밍 규칙
- [views-naming-guide.md](views-naming-guide.md) — 템플릿 파일 명명 규칙
- [pagination.md](pagination.md) — Range 헤더 기반 페이지네이션
- [uploader-client.md](uploader-client.md) — 첨부파일 업로드 JS 클라이언트
- [uploader-server-internal.md](uploader-server-internal.md) — 첨부파일 업로드 서버 API
- [webhook-server-internal.md](webhook-server-internal.md) — 웹훅 등록/발동
- [label-typeahead.md](label-typeahead.md) — 라벨 자동완성 API
- [markdown.md](markdown.md) — 마크다운 렌더링 방식
- [mailbox.md](mailbox.md) — IMAP 메일함 처리 알고리즘
- [watch.md](watch.md) — Watch/알림 대상 결정 알고리즘
- [name-validation.md](name-validation.md) — 이름 검증 설계 지침

## legacy와 아키텍처 자체가 달라진 부분을 설명하는 문서

- [current-user.md](current-user.md) — Play 세션/토큰 방식 → Spring Security 세션/Remember-Me
- [validation-with-annotation.md](validation-with-annotation.md) — 애노테이션 기반 권한 검사
  → 컨트롤러 내 직접 호출
- [view-hierarchy.md](view-hierarchy.md) — `.scala.html` include 트리(파일명만 기계적으로
  치환, 전수 재검증은 아직 안 함 — `docs/TEMPLATE_BACKLOG.md`가 더 신뢰할 수 있는 소스)
