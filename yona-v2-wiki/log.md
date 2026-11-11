

## [2026-11-11] fix | A-461과 A-463 결정 28건의 구현 (A-464)

선생님 결정(project-F21 유지, issue-F24는 메뉴가 꺼지면 접근 차단, 나머지 권장안 수용)에 따라 시험 먼저로 구현했다. 로그인과 기기 코드 잠금과 DB 저장, OAuth 동의 폼 CSRF, 컨텍스트 경로, 한글 아이디와 이름 규칙, 메뉴를 끈 기능의 주소 차단, 관리자 자기 보호, 이전 화면 정리, 고정 문구의 메시지 키 전환 등이다. 시작 시점부터 실패하던 `DataTablesTest`(X-43의 `api_token_scope` 표가 목록에 없었다)와 병합 때 드러난 회귀 시험 셋(`ExternalPullRequestApiTest`의 비공개 프로젝트 준비, `MigrationPageTest`의 영어 화면 한국어 주석, `SiteSecretTest`)도 고쳤다. 남은 것은 welcome-F3(비밀번호 정책 값)과 도움말 본문의 네 언어 번역이다.

