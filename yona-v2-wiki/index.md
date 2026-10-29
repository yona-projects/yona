# Yona v2 Wiki 인덱스

규약은 [CLAUDE.md](CLAUDE.md), 변경 이력은 [log.md](log.md)를 본다. v1.6 분석은 `~/yona-convert/yona-v1.6-wiki`를 참조한다.

- [결정 기록](decisions.md): 기술 스택과 정책 결정의 선택지, 권장안과 이유, 결정자
- [이식 로드맵과 진행 상태](roadmap.md): 단계별 진행과 비워 둔 연결 고리

## 아키텍처 (`architecture/`)

- [설정 키 대응표](architecture/configuration-keys.md): `application.*` -> `yona.*`
- [v1.6과 달라지는 아키텍처 결정](architecture/differences-from-v16.md): 기술 스택 전환과 그 이유 추가 개발 항목의 의도된 차이 장부 포함(D-32)
- [v1.6 소스 결함 후보 전체 목록](architecture/v16-defect-catalog.md): 5차 검증의 결함 후보 189건과 v2 판정
- [v1.6 위키 9~11차 갱신분의 v2 대조 결과](architecture/v16-sync-9-11.md): 바뀐 127개 페이지의 대조, 고친 항목, 결정 대기, 판정하지 못한 항목
- [v1.6 위키 127쪽 전수 줄 대조](architecture/v16-sync-ledger.md): 장부 2,267줄의 판정 집계, 불일치 92줄의 처리, 판정하지 못한 9줄
- [직접 만든 요청에서만 닿는 차이](architecture/hand-crafted-request-differences.md): A-24에 따라 재현하지 않는 v1.6 동작 목록
- [기술 스택 결정 기록](architecture/technology-decisions.md): 쿼리 계층, 스키마 관리, 검증 DB, 테스트 스택과 테스트 DB 운영 방식

## 웹 (`web/`)

- [정적 자원, LESS 빌드, 다국어 메시지](web/static-assets-and-i18n.md): `/assets/`, favicon, Less 1.7.5 빌드, Play 메시지 형식
- [최근 방문 기록](domain/recent-visits.md)
- [마일스톤 진행 현황](domain/milestone-status.md)
- [계정 설정과 첨부 올리기](web/account.md): 프로필, 비밀번호, 토큰, 이메일, 첨부
- [이전(Migration) 화면](web/migration.md): GitHub로 옮길 프로젝트 데이터 JSON과 토큰 교환(D-23 접근 기준)
- [Subversion](web/subversion.md): SVNKit 저장소, 코드 브라우저, 이력과 커밋 코멘트, WebDAV(HTTP) 접근
- [Git 태그 화면](web/tags.md): 태그 목록, 삭제, 브랜치 목록에서 태그 제외, 선택 목록 구역(X-18, D-30)
- [풀 리퀘스트의 담당자와 라벨](web/pull-request-assignee-label.md): 담당자 1명(여러 명으로 확장 가능), 라벨, 알림과 웹훅, 목록 필터(X-14, D-34)
- [풀 리퀘스트 리뷰 판정](web/pull-request-review.md): Comment, Approve, Request changes를 하나의 리뷰 모델로 합치고 v1.6 리뷰 완료를 흡수, 병합 조건과 기존 데이터 보정, 리뷰 요청(X-12 D-35, X-27 D-37)
- [이메일 수신(IMAP)](web/mailbox.md): 받은 메일로 이슈와 댓글 만들기, IMAP 연결과 UID 기록, 진단
- [LDAP 인증](web/ldap.md): LDAP 로그인, 로컬 사용자 동기화, Git 기본 인증
- [소셜 로그인](web/social-login.md): GitHub, Google OAuth 로그인, 계정 연결, 소셜 로그아웃, `/restricted`
- [최초 기동 화면](web/welcome.md): `yona.secret`이 기본값일 때 기본 관리자 계정 설정과 재시작 안내
- [데이터 내보내기와 가져오기](subsystems/data-exchange.md): 47개 테이블 JSON, 교체 규칙, DB별 처리
- [사이트 관리](web/site-admin.md): `/sites/userList` 등 사용자 관리와 사이트 관리 틀
- [도움말, 통계, UI 견본](web/help-statistics.md): `/_help`, `/:user/:project/statistics`, `/_UIKit`
- [검색](web/search.md): `/search`, 조직·프로젝트 범위, 여덟 종류
- [내 이슈](web/my-issues.md): `/user/issues`, 프로젝트를 가리지 않는 이슈 검색
- [사용자 페이지와 내 파일](web/user-page.md): `/:user`(프로필, 이슈·풀 리퀘스트·프로젝트 탭), `/user/files`, `/info/leave`
- [이슈 라벨과 라벨 분류](web/issue-labels.md): 관리 화면, JSON, 분류, 복사, 색 스타일 시트
- [라벨 JSON API](web/label-api.md): 전역 라벨, 분류 검색과 프로젝트 라벨 붙이기, 떼기
- [조직 이슈 목록과 풀 리퀘스트 목록](web/organization-lists.md): 조직 메뉴의 Issue, Pull Request 탭
- [프로젝트 리뷰 목록](web/review-list.md): 프로젝트 메뉴의 Review 탭과 엑셀 내려받기
- [저장소 초기화](web/repository-init.md): `GET /_init`(모든 저장소를 지우고 다시 만드는 v1.6 개발용 경로)
- [공용 편집기 부품과 멘션 후보](web/editor-components.md): 편집기, 업로더, 첨부 목록, 멘션 후보, 폼 본문 크기 한도
- [이슈 보기, 다음 상태, 삭제](web/issue-view.md): 보기 화면, 타임라인, 댓글 표시, 다음 상태, 삭제
- [diff 엔진](subsystems/diff.md): 커밋과 저장소 사이의 파일별 차이, 헝크
- [풀 리퀘스트 병합](subsystems/pull-request-merge.md): JGit 병합기, 병합 검사 처리기, 실행기, 이벤트, 알림, 관련 작성자
- [푸시 뒤 일](subsystems/post-receive.md): 이슈 언급 이벤트, 풀 리퀘스트 확인과 삭제, 새 커밋 알림
- [웹훅 전송](subsystems/webhooks.md): 이벤트별 본문, 형식(간단, 슬랙, 구글 챗, 지속적 통합), 스레드, 헤더
- [코드 브라우저](web/code-browser.md): 폴더와 파일 보기, 메타데이터 JSON, 원본, 내려받기
- [저장소 가져오기](web/import.md): `/_import`로 외부 Git 저장소를 가져와 프로젝트 만들기
- [웹훅 설정](web/webhooks.md): 웹훅 목록, 등록, 삭제
- [코드 리뷰 댓글](web/review-comments.md): 커밋과 풀 리퀘스트의 리뷰 댓글, 스레드 열기와 닫기, 삭제, 알림
- [풀 리퀘스트 보기](web/pull-request-view.md): 보기, 상태 조각, 타임라인, 변경 내용, 리뷰
- [풀 리퀘스트 동작](web/pull-request-actions.md): 받아들이기, 닫기와 열기, 수정, 보낸 브랜치 삭제와 복원
- [풀 리퀘스트 만들기](web/pull-request-create.md): 폼, 병합 결과 조각, 만들기, 병합 검사 시작
- [풀 리퀘스트 목록](web/pull-request-list.md): 열림, 닫힘, 보냄 목록, 검색, 최근 푸시 브랜치
- [포크](web/fork.md): 포크 폼, 복제 안내 화면, 복제(저장소 bare 복제)
- [커밋 보기와 비교](web/commit-view.md): 커밋 diff, 코드 리뷰 스레드 표시, 리뷰 카드, 커밋 구독, 두 커밋 비교
- [Git smart-HTTP 서비스](subsystems/git-http.md): 복제, 푸시, Basic 인증, 푸시 훅
- [외부 프로젝트, 게시판 REST API](web/external-project-api.md): 프로젝트, 라벨, 마일스톤, 내보내기, 구독자, 글, 글 댓글
- [외부 이슈 REST API](web/external-issue-api.md): 이슈 조회, 등록, 수정, 상태, 댓글, 라벨, 글 가져오기
- [알림 서브시스템](subsystems/notifications.md): 활동 → 알림 이벤트, 수신자, 합치기, 메일 발송(알림 메일, 시스템 메일), 홈 알림 목록, 프로젝트 구독과 알림 설정
- [사용자 메뉴와 사이드바](web/user-menu.md): `usermenuTabContentList`, `sidebar`, `go`, 프로젝트·조직 즐겨찾기
- [게시판](web/board.md): 목록, 보기, 작성(온라인 커밋 포함), 수정, 삭제, 댓글, 조직 게시판
- [이슈 화면 JSON API](web/issue-api.md): 담당자, 공유자, 가중치, 변경 감지, 체크리스트 갱신
- [구독과 이슈 즐겨찾기](web/watch-and-favorite.md): `/watch`, `/unwatch`, `favoriteIssues`
- [이슈 일괄 변경](web/issue-mass-update.md): `POST issues`, 상태·담당자·마일스톤·라벨·마감일·삭제
- [이슈 댓글과 추천](web/issue-comments.md): 댓글 등록, 수정, 삭제, 이슈와 댓글 추천
- [이슈 수정](web/issue-edit.md): 수정 폼, 변경 이력, 초안 게시, 프로젝트 이동
- [이슈 등록](web/issue-create.md): 등록 폼, 등록, 자동 완성 API, 타임라인 이벤트
- [이슈 목록과 검색](web/issue-list.md): 목록, 검색 조건, PJAX와 JSON, 엑셀, 일괄 변경 막대
- [마일스톤](web/milestones.md): 목록, 만들기, 수정, 삭제, 열기와 닫기, 보기
- [가입, 인증, 비밀번호 재설정](web/signup-and-password.md)
- [조직](web/organization.md): 생성, 홈, 목록, 설정, 구성원, 삭제, 가입 신청
- [프로젝트 구성원](web/project-members.md): 목록, 추가, 역할, 탈퇴
- [프로젝트 이전과 저장소 종류 변경](web/project-transfer.md)
- [프로젝트 삭제](web/project-delete.md): 삭제 범위와 순서
- [프로젝트 설정](web/project-setting.md): 폼, 저장, 이름 변경, 로고
- [첨부 파일](web/attachments.md): 저장, 내려받기, 권한
- [프로젝트 홈](web/project-home.md): README, 설명 수정, 구성원, 마일스톤 요약
- [마크다운 렌더링](web/markdown.md): commonmark-java, v1.6 marked.js와의 비교 테스트
- [프로젝트 생성](web/project-create.md): `/projectform`, `POST /projects`, 검증, 표시 이름
- [프로젝트 목록](web/project-list.md): `/projects`, 검색, 쪽 나누기, JSON, `TemplateHelper`
- [프로젝트 검사 애너테이션](web/project-checks.md): `@IsAllowed`, `@IsCreatable`, `@GuestProhibit` 등과 URL로 리소스 찾기
- [프로젝트 레이아웃](web/project-layout.md): 프로젝트 틀, 메뉴, 헤더, 프로젝트 안의 오류 화면, `ProjectViewContext`
- [오류 화면](web/error-pages.md): `ErrorViews`, 404/403/400/500, 예외 처리
- [홈 화면](web/home-page.md): 소개, 환영 표, 내 활동 탭, 기본 로그인 페이지, 알림 조회
- [레이아웃과 로그인 화면](web/layout-and-login-page.md): Scala -> Thymeleaf 변환 규칙, 공통 모델, 로그인 화면, `/messages.js`

## 도메인 (`domain/`)

- [프로젝트 조회와 메뉴 개수](domain/project-service.md): `ProjectService`
- [v1.6 스키마와 JPA 엔티티 매핑](domain/schema-mapping.md): 기준 스키마 추출, 3개 DB 검증 테스트, 패키지 구성, 매핑 규칙, v1.6과 달라진 점

## 서브시스템 (`subsystems/`)

- [Git 저장소 서비스](subsystems/git-repository.md): JGit, 저장소 위치와 생성, 삭제
- [초기 데이터 적재](subsystems/initial-data.md): 관리자 계정, 사이트 관리자, 역할 7종
- [접근 제어](subsystems/access-control.md): `@AnonymousCheck`, 앞으로 옮길 `AccessControl`
- [인증](subsystems/authentication.md): v1.6 호환 패스워드 해시와 로컬 로그인 판정 규칙

## 기타

- [v1.6 결함 재현 목록](refactoring-backlog.md): v1.6의 결함을 그대로 재현한 부분과 개선 후보
- [실제 git·svn 클라이언트 검증 기록](architecture/real-client-verification.md): 한글 경로, 큰 저장소, 인증 흐름을 실제 클라이언트로 확인한 결과
- [architecture/v16-release-differential-test.md](architecture/v16-release-differential-test.md): 공식 v1.16.0을 띄워 v2와 응답을 대조하는 시험의 구성, 정규화, 찾아 고친 것
- [architecture/magpie-integration.md](architecture/magpie-integration.md): Apache Magpie가 요구하는 이슈·PR 계약과 Yona가 제공하는 것의 대조, 빠진 API
- [architecture/next-vs-next2-source-closeness.md](architecture/next-vs-next2-source-closeness.md): next와 next2를 v1.6 소스와 대조한 라우트, 메시지, 정적 자원, 테이블 일치율
- [architecture/v16-remaining-differences.md](architecture/v16-remaining-differences.md): v1.6과 아직 다른 점의 전체 목록. 고칠 수 없는 것, 의도해서 둔 것, v1.6 오류를 v2가 정상 처리하는 것(요청 단위 표), 권한 차이, 메일 수신 차이
- [개인용 API 토큰 설계](architecture/api-token.md): Classic 범위 토큰, 해시 저장, 옛 토큰 이관, D-20 대체(X-5, D-36, 구현 완료)
- [일반 REST 1차: 프로젝트 단위 조회](architecture/api-project-reads.md): 프로젝트, 구성원, 담당자 후보, 라벨, 마일스톤, 이슈 목록과 검색(X-19, D-40)
- [브랜치 보호 규칙](architecture/branch-protection.md): 리뷰어 수 강제를 대체하는 브랜치별 승인 수 규칙(X-32, D-42)
- [풀 리퀘스트 병합 방식](architecture/pull-request-merge-methods.md): 병합 커밋, 스쿼시, 리베이스, 프로젝트 설정, 병합 전 가능 여부(X-28, D-41)
- [풀 리퀘스트 REST API](architecture/api-pull-requests.md): 조회와 쓰기, 리뷰와 임시 댓글, 권한과 오류 규칙, 병합 방식 필드 예약(X-15, D-39)
- [세션 경로와 API 경로의 분리](architecture/api-auth-split.md): 세션, 토큰 둘 다 받던 경로를 세션 전용으로 바꾸고 `/api/v1` 토큰 전용 경로를 만든다, v1.6과의 차이 목록(X-5, D-36, 설계 제안 확인 대기)
