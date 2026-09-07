---
id: k6-notable-findings
type: finding
status: done
created: 2026-08-26
updated: 2026-08-26
relates_to: []
source: docs/golden/GOLDEN_PARITY_LEDGER.md — 이관 전 버전은 git 히스토리 참고
---

## K6에서 발견된 주목할 사항 (2026-08-26)

| 항목 | 내용 | 심각도 |
|---|---|---|
| `ResourcePersistAdapter.java` | legacy는 Ebean `postDelete` 훅으로 이슈/게시글 등 리소스 삭제 시 관련 `Watch`/`Unwatch` row를 자동 정리한다. yona의 `IssueServiceImpl.deleteIssueCascade()`/`PostingServiceImpl.deletePostingCascade()`/`ProjectServiceImpl.deleteProject()` 어디에도 `watchRepository`/`unwatchRepository` 삭제 호출이 없음 — **리소스 삭제 후 Watch/Unwatch에 고아 row가 남는 실질적 회귀 가능성** | 중간(데이터 정합성) |
| `AccessLogger.java` | Apache Combined Log Format 방식 HTTP 접근 로그(사용자/referer/UA/응답시간)가 yona 전체에 없음(필터/인터셉터/logback-access 설정 전무) | 낮음(운영/관측성) |
| `AttachmentCache.java` | 첨부파일 목록 인메모리 캐싱이 yona에 없음(`@Cacheable` 등 캐시 계층 부재) | 낮음(성능 최적화만, 기능 문제 없음) |
