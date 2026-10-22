# Yona v2 Wiki 인덱스

규약은 [CLAUDE.md](CLAUDE.md), 변경 이력은 [log.md](log.md)를 본다. v1.6 분석은 `~/yona-convert/yona-v1.6-wiki`를 참조한다.

- [결정 기록](decisions.md): 기술 스택과 정책 결정의 선택지, 권장안과 이유, 결정자
- [이식 로드맵과 진행 상태](roadmap.md): 단계별 진행과 비워 둔 연결 고리

## 아키텍처 (`architecture/`)

- [설정 키 대응표](architecture/configuration-keys.md): `application.*` -> `yona.*`
- [v1.6과 달라지는 아키텍처 결정](architecture/differences-from-v16.md): 기술 스택 전환과 그 이유
- [기술 스택 결정 기록](architecture/technology-decisions.md): 쿼리 계층, 스키마 관리, 검증 DB, 테스트 스택과 테스트 DB 운영 방식

## 웹 (`web/`)

- [정적 자원, LESS 빌드, 다국어 메시지](web/static-assets-and-i18n.md): `/assets/`, favicon, Less 1.7.5 빌드, Play 메시지 형식
- [최근 방문 기록](domain/recent-visits.md)
- [마일스톤 진행 현황](domain/milestone-status.md)
- [계정 설정과 첨부 올리기](web/account.md): 프로필, 비밀번호, 토큰, 이메일, 첨부
- [이슈 라벨과 라벨 분류](web/issue-labels.md): 관리 화면, JSON, 분류, 복사, 색 스타일 시트
- [공용 편집기 부품과 멘션 후보](web/editor-components.md): 편집기, 업로더, 첨부 목록, 멘션 후보, 폼 본문 크기 한도
- [이슈 보기, 다음 상태, 삭제](web/issue-view.md): 보기 화면, 타임라인, 댓글 표시, 다음 상태, 삭제
- [코드 브라우저](web/code-browser.md): 폴더와 파일 보기, 메타데이터 JSON, 원본, 내려받기
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
