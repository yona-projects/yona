# v1.6 결함 재현 목록

v1.6을 그대로 따른다는 원칙에 따라 결함도 같게 재현하고, 개선 후보로 여기에 모은다. 개선은 선생님의 승인을 받은 뒤에만 한다.

| 번호 | 위치 | 결함 | v2의 처리 | 근거 |
|---|---|---|---|---|
| R-01 | 로그인 | 솔트나 패스워드가 비어 있는 계정으로 로그인하면 예외가 발생해 HTTP 500이 된다 | 같은 예외(`IllegalArgumentException`)를 던진다 | `UserApp.java:1027-1032`, `LocalAuthenticatorTest` |
| R-02 | 전역 | CSRF 보호가 없다. 폼과 JS가 토큰을 보내지 않는다 | CSRF를 끈다 | `SecurityConfig` |
| R-03 | 로그아웃 | Referer 헤더가 없으면 `redirect(null)`로 오류가 난다 | 홈(`/`)으로 이동한다. v1.6의 오류까지 재현하지는 않는다. **선생님 승인(2026-10-22)** | `UserApp.java:154-159` |
| R-04 | API 토큰 | 토큰으로 식별된 사용자는 계정 상태(LOCKED)를 확인하지 않아 잠긴 계정도 API를 쓸 수 있다 | 같게 재현한다 | `User.java:306-316` |
| R-05 | remember-me | 쿠키 값이 `로그인ID:해시된 패스워드`라서 쿠키가 유출되면 패스워드 없이 로그인된다. 패스워드를 바꿔도 해시가 바뀌므로 쿠키는 무효가 된다 | 같게 재현한다 | `UserApp.java:1227-1230` |
| R-06 | 로그인 | `redirectUrl`을 검증하지 않아 `//example.com` 같은 값으로 외부 주소로 이동시킬 수 있다(열린 리다이렉트) | 같게 재현한다 | `UserApp.java:244-252` |
| R-07 | 기본 로그인 페이지 | 로그인하지 않은 사용자가 `POST /-_-api/v1/user/defultLoginPage`를 호출하면 사용자 ID가 없어 오류(HTTP 500)가 난다 | 같게 재현한다(`IllegalArgumentException`) | `UserApp.java` `setDefaultLoginPage` |
| R-08 | AccessControl | 컨테이너가 프로젝트가 아닌 전역 리소스(예: USER)이면 `isResourceCreatable`이 `container.getProject()`의 예외로 실패한다 | 같게 재현한다(`UnsupportedOperationException`) | `AccessControl.java:99-102` |
| R-09 | AccessControl | 프로젝트 매니저 판정이 "코드(CODE)는 삭제할 수 없다" 규칙보다 먼저 평가되어 매니저는 코드도 삭제할 수 있다 | 같게 재현한다 | `AccessControl.java:244-248`, `:289-293` |
| R-10 | AccessControl | 존재하지 않는 이슈를 ISSUE_POST 리소스로 검사하면 담당자 조회에서 NullPointerException이 난다 | 같게 재현한다(`NoSuchElementException`) | `AccessControl.java:403-405` |
| R-11 | 프로젝트 메뉴 | 단축키 `Q`(설정 화면)가 "로그인 ID가 `manager`일 때"만 켜진다. 프로젝트 역할 이름과 로그인 ID를 혼동했다 | 같게 재현한다 | `projectMenu.scala.html`의 `case Some(role) if role.equals("manager")` |
| R-12 | 프로젝트 검사 | 마일스톤, 이슈 라벨, 라벨 분류, 커밋 코멘트를 번호만으로 찾아 경로의 프로젝트와 달라도 통과한다. 숫자가 아닌 식별자는 500이다 | 같게 재현한다 | `Resource.java:226-247` |
| R-13 | 프로젝트 생성 | 이름 예약어(`.`, `..`, `.git`) 비교의 `ignoreCase` 분기가 거꾸로라 기본 설정에서 대소문자를 무시한다(`.GIT`도 거부) | 같게 재현한다 | `ExConstraints.java:62-77` |
| R-14 | README 상대 경로 변환 | 일반 링크 정규식이 앞 문자 하나를 요구해 문서 맨 앞 링크를 놓치고, 컨텍스트 경로를 쓰면 소유자 앞 `/`가 빠진다(`//yonaalice/...`) | 같게 재현한다 | `Markdown.java:358-376` |
| R-15 | 마크다운 자동 링크, 이슈 링크 변환 | (1) 다른 프로젝트의 이슈 참조는 링크 문구에 접두어와 문구가 두 번 붙는다 (2) 링크 문구를 치환 문자열로 쓰므로 제목에 `$`, `\`가 있으면 예외 또는 잘못된 문구 (3) 읽을 수 없는 이슈 링크를 만나면 나머지 링크 변환을 중단 (4) 이슈 번호가 Long 범위를 넘으면 500 | 같게 재현한다 | `AutoLinkRenderer.java:272-285`, `Markdown.java:133-209` |
| R-16 | 마크다운 결과 캐시 | 캐시 적중 시 이전 결과를 돌려주면서 새로 만들어 저장(변경이 한 번 늦게 반영), 프로젝트 문맥 유무와 무관하게 같은 키 공유, 보는 사람의 권한에 따른 결과가 전역 공유 | 같게 재현한다 | `Markdown.java:219-262, 311-326` |
| R-17 | 첨부 파일 종류 감지 | 텍스트 파일의 문자셋을 Tika가 읽고 난 스트림 나머지로 감지해서 8192바이트 이하 파일은 항상 UTF-8 | 같게 재현한다 | `FileUtil.java:127-135` |
| R-18 | 프로젝트 삭제 | 첨부(로고, 이슈 첨부), 구독 기록, 알림 이벤트를 지우지 않아 고아 데이터가 남고, 고아 첨부에 접근하면 오류 | 같게 재현한다 | `Project.java:695-740` |
| R-19 | 조직 탈퇴와 이름 변경 | (1) 탈퇴 검사 조건이 거꾸로라 관리자는 혼자여도 탈퇴하고 일반 구성원은 관리자가 혼자일 때 탈퇴 못 함 (2) 이름 변경 여부는 대소문자 구별, 중복 검사는 무시라 대소문자만 바꾸면 자기 자신과 겹침 | 같게 재현한다 | `OrganizationApp.java:262-287, 395-410` |
| R-20 | 임시 첨부 정리 | 정리 조건이 `createdDate >= (지금 - 보관 기간)`이라 오래된 것이 아니라 최근 보관 기간 안에 올린 임시 첨부를 지운다 | 같게 재현한다 | `Attachment.java:458-463` |
| R-21 | 아바타 크기 검사 | 1MB를 넘는 이미지를 폼 오류로만 기록하고 실제로는 아바타로 옮긴다 | 같게 재현한다 | `UserApp.java:960-975` |

| R-22 | 라벨 색 글자색 | 3자리 16진수 색(`#abc`)을 6자리로 늘리려다 문자열 전체를 반복해(`"abc" * 2`) 길이가 맞지 않아 흰색으로 처리된다. 어두운 3자리 색에도 글자색이 `dimgray`가 된다 | 같게 재현한다 | `issueLabelColor.scala.html` |
| R-23 | 임시 첨부 옮기기 | `temporaryUploadFiles`를 멀티파트 본문에서만 읽는데 마일스톤 만들기 폼은 일반 폼이라 편집기에서 올린 첨부가 마일스톤으로 옮겨지지 않는다(다음에 수정할 때 사용자의 임시 첨부가 모두 딸려 온다) | 같게 재현한다 | `AbstractPostingApp.java:254-268`, `MilestoneApp.java:90-100,155-170` |
| R-24 | 이슈 삭제 | 이슈를 지워도 멘션 기록(`mention`)이 남아 고아 행이 된다 | 같게 재현한다 | `AbstractPosting.java:226-236` |
| R-25 | 이슈 보기의 추천자 대화상자 | 추천자 아바타를 그리면서 같은 집합에서 현재 사용자를 빼기 때문에, 대화상자 목록에서 내가 빠지고 나만 추천했으면 대화상자가 없다 | 같게 재현한다 | `partial_voters.scala.html`, `VoteApp.java:129-132` |
| R-26 | 이슈 댓글 삭제 | 댓글을 지워도 멘션 기록(`mention`)이 남아 고아 행이 된다 | 같게 재현한다 | `Comment.java:94-99` |
| R-27 | 줄바꿈 변환 | `LineEnding.changeLineEnding(…, DOS)`가 `\n`을 `\n`으로 바꾸는 코드라 DOS 변환이 아무것도 하지 않는다. 저장소 파일 온라인 편집에서 CRLF 파일을 고쳐도 LF로 저장된다 | 같게 재현한다 | `LineEnding.java:25-36` |
| R-28 | 게시판 목록의 정렬 링크 | 라벨을 고른 채 정렬 링크를 누르면 링크의 `labelIds`에 자바 배열의 `toString()`(`[Ljava.lang.String;@…`)이 들어가 숫자 변환 오류로 `500`이 난다 | **고쳐서 구현했다**(고른 라벨을 이어 받는다). v1.6 그대로 오류를 내려면 [decisions.md] A-50을 되돌린다 | `board/list.scala.html:19-27`, `LabelApp.java:128-139` |
| R-29 | 게시판 라벨 변경 | 라벨을 바꿀 때마다 `posting.save()`가 불려 제목 머리말 개수(`TitleHead`)가 한 번씩 늘어난다 | 같게 재현한다 | `BoardApi.java:42-63`, `AbstractPosting.java:113-125` |
| R-30 | 게시판 보기의 라벨 변경 | 라벨을 고르면 요청은 성공하지만 `$.ajax(...).done(...).error(...)`의 `.error`가 jQuery 3에 없어 브라우저 콘솔에 `TypeError`가 난다 | 같게 재현한다 | `board/view.scala.html`(라벨 `change` 핸들러) |
| R-31 | 사용자 메뉴의 즐겨찾기 탭 | 내 프로젝트도 조직도 없으면 즐겨찾기 프로젝트가 있어도 탭 전체가 "No results"이다(`etc-favorites`는 빈 `ul`이고 줄이 `ul` 바깥에 이어진다) | 같게 재현한다 | `index/myOrganizationList.scala.html:12` |
| R-32 | 알림 메일의 "마일스톤 없음" 문구 | 마일스톤 변경 알림에서 마일스톤이 없을 때의 문구를 받는 사람의 언어가 아니라 서버 기본 언어로 만든다 | 같게 재현한다 | `NotificationEvent.java:132-136` |
| R-33 | 알림 메일의 일반 텍스트 본문 | 끝에 붙는 안내 문장("Yona 에서 자세히 보거나 혹은 이 메일에 직접 회신하실 수도 있습니다.")이 한국어로 고정이고 메일에 답장이 되지 않아도 붙는다 | 같게 재현한다 | `NotificationMail.java:getPlainMessage` |
| R-34 | 프로젝트 이전 요청 메일 | 받는 사람(To)의 이름이 `Yobi`로 고정이고, 받는 사람이 사용자인지 조직인지에 따라 같은 메일에 Bcc만 달라진다 | 같게 재현한다 | `ProjectApp.java:sendTransferRequestMail` |
| R-35 | 홈 알림 목록의 본문 언어 | 본문을 로그인한 사용자의 언어가 아니라 서버 기본 언어로 만든다 | 같게 재현한다 | `partial_notifications.scala.html` (`getMessage` 기본 언어) |

