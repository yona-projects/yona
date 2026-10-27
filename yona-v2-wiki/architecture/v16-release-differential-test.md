---
title: 공식 v1.16.0 실행 비교 시험
type: architecture
sources: [tools/v16-diff/, https://github.com/yona-projects/yona/releases/tag/v1.16.0]
updated: 2026-10-27
---

# 공식 v1.16.0 실행 비교 시험

v1.6의 소스를 읽어 맞추는 방식은 판단이 틀릴 수 있어(예: `isEmailExist`, `mentionListAtCommitDiff`, 이슈 댓글 수) 라운드마다 새 불일치가 나왔다.
그래서 공식 릴리스 `yona-h2-v1.16.0-bin.zip`(JDK 8, H2)을 그대로 띄우고 v2와 같은 요청을 보내 응답을 대조한다. 코드를 따로 빌드하지 않는다.

## 구성 (`tools/v16-diff/`)

| 파일 | 역할 |
|---|---|
| `servers.sh` | 두 서버(v1.6: 19200, v2: 19300) 시작, 종료, 데이터 저장(`snapshot`), 되돌리기(`restore`), v2 빌드(`build`). 프로세스는 pid 파일로만 다룬다 |
| `harness.py` | 같은 `Step` 목록을 두 서버에 보내 상태 코드, 주요 헤더, 본문(JSON은 구조, HTML은 정규화한 요소 목록)을 비교하고 보고서를 쓴다 |
| `scenarios/*.py` | `anon_basic`(익명 둘러보기), `data1`(사용자, 프로젝트, 이슈, 글 만들기), `pages1`(모든 사용자 x 화면 1200건), `repo1`(git 저장소 화면 366건), `md1`(마크다운 조각), `forms1`(폼과 변경 요청, 권한 행렬), `pr1a`/`pr1`(포크와 풀 리퀘스트, `pr1.sh`로 브랜치를 먼저 올린다), `svn1`(`svnprep.sh`로 8개 리비전을 올린 Subversion 프로젝트, data3 상태), `mail1`/`gitpush.sh`(커밋 코멘트, 비밀번호 재설정, 푸시 알림), `hook0`/`hook1`(웹훅 네 종류의 요청 본문), `life1`(이름 바꾸기, 이전, 삭제), `proto1`(Git, Subversion 인증 행렬), `misc1`(추천 취소, 기본 브랜치, 초기화, 이메일 확인, 정적 파일), `site1`(사이트 관리자의 내보내기, 가져오기, 메일 대상, 삭제), `api1`(`-_-api/v1` 토큰 API, `NORMALIZE_ENTITY_IDS`로 JSON `id`를 가린다) |
| `smtpsink.py`, `mailcompare.py`, `httpsink.py`, `hookcompare.py`, `msgcompare.py` | 두 서버가 보내는 알림 메일과 웹훅 요청, 언어별 메시지를 받아 짝지어 대조한다(`servers.sh start`가 수신기도 띄운다). 비교용 서버는 알림 메일 지연을 3초로 줄여 띄운다 |
| `clients.py` | 실제 `git`과 `svn` 클라이언트로 두 서버에 같은 일을 시켜 결과를 대조한다(35MB 푸시와 복제, 한글 경로, 인증 흐름, 태그, 브랜치 삭제, 얕은 복제, svn 가져오기, 이력, 잠금). `regress.sh`는 모든 시나리오를 다시 돌려 이전 차이 건수와 견준다 |
| `gitprep.sh`, `mkproject.py` | 같은 내용과 같은 시각의 git 저장소를 만들어 두 서버에 올린다. 커밋 해시가 같아 화면을 그대로 대조할 수 있다 |
| `mdcompare.py`, `mdfuzz.py` | 마크다운 미리보기를 파일과 무작위 조각으로 직접 대조한다 |
| `probe.py`, `show.py`, `formdump.py` | 요청 하나를 나란히 보여 주는 도구 |

상태는 `base`(최초 설정만), `data1`(데이터 생성 뒤), `data2`(저장소 올림 뒤)로 저장해 두고 시나리오마다 되돌려 쓴다.

## 정규화 (서버마다 다른 값은 같게 본다)

- 날짜, 상대 시간, 세션, 무작위 편집기 번호, `FileDiff.hashCode()`(enum 신원 해시)
- Ebean 시퀀스가 20개씩 번호를 받아 가므로 재시작 뒤 이슈, 댓글, 마일스톤의 내부 번호가 서버마다 다르다. 응답에서 값을 뽑아 `<<이름>>`으로 쓰고(`Step.capture`) 번호 모양의 문맥에서만 `<이름>`으로 바꿔 비교한다
- v1.6 H2의 사용자 번호는 33부터, v2는 2부터이다
- 속성 순서, 불리언 속성(`checked`와 `checked="checked"`), `method` 대소문자, 주소 안의 공백

## 알아 둘 점

- 릴리스와 소스 기준 커밋(`60a5ac406`)은 세 커밋 차이가 있다(A-153). 비교용 릴리스의 `marked.js`를 소스 기준으로 바꿔 쓴다
- v1.6 서버는 `application.update.check.use=false`여도 새 버전 알림을 보일 수 있다(관리자 화면의 `notification-badge`). 환경 차이이다
- 손으로 만든 요청은 v1.6에서 널 참조 500이 되는 일이 많다(필수 숨은 필드 누락). 그런 차이는 [hand-crafted-request-differences.md](hand-crafted-request-differences.md)에 따른다
- 순서가 정해지지 않은 목록(HashSet)과 둘 이상일 때의 flash 표시 순서는 같게 만들 수 없다

## 이 시험으로 찾아 고친 것 (요약, 2026-10-27)

- 헤더: `Cache-Control: no-cache`가 붙는 조건, JSON `charset`, 스프링의 RFD 방지 헤더 제거
- 화면: 빈 속성(`data-milestone-id=""`) 출력, 스크립트 글자(쉼표, 공백, 변수 대신 직접 찍기), `jAvascript` 오타, `data-mimeType`의 따옴표, 팔레트 색 `;`
- 링크: `*path` 역방향 라우트는 인코딩하지 않고 경로가 비면 끝에 `/`가 없다
- 동작: 게시판 글의 댓글 수(R-116), `isEmailExist`(R-110 정정), 익명 즐겨찾기 목록, 빈 저장소 안내 권한, 설정 POST의 내용 형식 제한 제거
- flash: 심은 요청과 다음 요청에서 모두 보이고 앞 값을 이어 받는다(A-157), `requestHeader.flash`를 읽는 조직 만들기
- 마크다운: commonmark를 버리고 `marked.js`를 Nashorn에서 그대로 실행(A-154). 160여 개 파일과 무작위 1900건이 모두 같다
- 라이브러리: jsoup 1.8.3, OWASP 20190610.1(A-155)

## 남은 것

- 풀 리퀘스트, 포크, 리뷰 화면 전체, Subversion 저장소 화면, `-_-api/v1` 토큰 API, 사이트 관리자 POST 흐름, 알림 메일의 마크다운
- 다음 시나리오는 조직과 사용자 변경 화면, 알림 메일 본문으로 만든다

## 응답 헤더 집합의 알려진 차이
v2 응답에는 v1.6에 없는 헤더가 붙는다: Spring Security의 `X-Content-Type-Options`, `X-Frame-Options: SAMEORIGIN`, `X-XSS-Protection`(기술 결정: Spring Security 필터 체인), 스프링의 `Content-Language`, 컨테이너의 `Connection`, `Keep-Alive`, 길이를 모르는 본문의 `Transfer-Encoding: chunked`(v1.6은 `Content-Length`). `Content-Type`의 `; charset` 공백 표기도 Tomcat이 다시 쓰므로 다르다. 비교 도구는 이 헤더들을 대조하지 않는다.

