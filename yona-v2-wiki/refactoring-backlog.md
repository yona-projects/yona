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
| R-36 | 외부 API 프로젝트 만들기 | `projectCreatedDate`를 받아 두고도 `Project.create`가 만든 날짜를 지금으로 덮어써서 가져온 프로젝트의 날짜가 보존되지 않는다 | 같게 재현한다 | `ProjectApi.java:newProject`, `Project.java:142-149` |
| R-37 | 외부 API 라벨 만들기 | 입력의 `isExclusive`를 읽기만 하고 쓰지 않아 분류가 항상 단일 선택이 아닌 상태로 만들어진다 | 같게 재현한다 | `ProjectApi.java:createLabelNode` |
| R-38 | 코드 브라우저 폴더 목록 | 폴더 이동 주소에 `#cb-…` 앵커를 붙이려는 조건이 `" folder"`(앞 공백)와 비교해 항상 거짓이라 앵커가 붙지 않는다 | 같게 재현한다 | `partial_view_folder.scala.html:makeFileItem` |
| R-39 | 코드 브라우저 이진 파일 안내 | 파일 크기를 문자열 값으로 읽는 도우미(`fieldText`)가 숫자 노드에는 비어 있어 크기가 표시되지 않는다 | 같게 재현한다 | `partial_view_file.scala.html`, `TemplateHelper.CodeBrowser.fieldText` |
| R-40 | 커밋 이력의 쪽 나누기 | `page * size + size`개를 읽어 마지막 `size`개를 남기므로, 전체 커밋이 두 쪽보다 적으면 마지막 쪽이 앞 쪽과 겹친다(예: 30개일 때 1쪽은 가장 오래된 25개) | 같게 재현한다 | `GitRepository.java:getHistory` |
| R-41 | 파일 보기의 댓글 수 | `countOnCommit`에 경로를 `"/"+path`로 넘겨, 코드 범위 스레드는 경로가 항상 달라 세지 않고 파일 단위 스레드만 센다 | 같게 재현한다 | `partial_view_file.scala.html:47`, `CommentThread.java:198` |
| R-42 | 풀 리퀘스트 병합 검사의 결함 | (1) 보낼 커밋이 없으면 상태를 `MERGED`로 바꾼 뒤 "상태가 MERGED가 아니면" 이벤트를 쌓는 조건을 걸어 병합 이벤트와 알림이 남지 않는다. (2) 검사 중 예외가 나면 병합 중 표시(`isMerging`)를 끄지 않아 화면이 계속 "병합 중"이다. (3) 풀 리퀘스트의 `updated`는 어디서도 채우지 않는다. (4) 폼의 받는 브랜치 기본값은 받는 프로젝트가 아니라 현재 프로젝트의 기본 브랜치이다. (5) 병합 상태 알림 문구 키 `notification.type.pullrequest.merged.<상태>`는 영어와 일본어 메시지에 없어 키 이름이 그대로 보인다. (6) 커밋 목록은 `PRIOR`였다 돌아온 커밋을 되살리지 않고 새 `CURRENT` 행을 만든다 | 같게 재현한다 | `PullRequestActor.java:12-60`, `PullRequestApp.java:128-144`, `PullRequestMergeResult.java:49-90`, `NotificationEvent.java:154-157` |
| R-43 | 풀 리퀘스트 화면의 잘못된 인수 | (1) 상태 조각의 충돌 해결 안내에서 저장소 주소에 로그인 ID를 넣을지 정하려고 `ProjectUser.isMember(프로젝트 번호, 사용자 번호)`를 인수를 뒤바꿔 호출해 대개 로그인 ID가 들어가지 않는다. (2) 수정에서 브랜치를 바꿔 다른 열린 요청과 겹치면 수정 폼 주소를 받는 프로젝트가 아니라 보내는 프로젝트로 만들어, 포크가 보낸 요청이면 다른 프로젝트의 같은 번호 요청으로 간다. (3) 수정은 제목이 바뀌어도 병합 검사를 다시 하지 않는다. (4) 풀 리퀘스트 수정 폼의 병합 결과, 멘션, 마크다운 주소는 받는 프로젝트가 아니라 보내는 프로젝트 기준이다 | 같게 재현한다 | `partial_state.scala.html:12-18`, `PullRequestApp.java:520-560`, `edit.scala.html` |
| R-44 | 코드 리뷰 댓글의 결함 | (1) 새 코드 리뷰 댓글 알림 본문은 코드 범위 스레드의 첫 댓글에만 차이 조각을 넣고, 새로 추가하거나 지운 파일은 차이 조각이 없어 파일 이름 줄 뒤에 댓글만 나온다. (2) 차이 조각의 `> @@ -시작, 개수 +시작, 개수 @@` 머리줄은 쉼표 뒤에 공백이 들어간다. (3) 커밋 댓글은 `prevCommitId`를 `null`로, 풀 리퀘스트의 커밋 하나에 단 댓글은 빈 문자열로 둔다. (4) 풀 리퀘스트의 새 댓글이 풀 리퀘스트 `commentThreads`에 더해지는 것과 달리 커밋의 스레드는 풀 리퀘스트와 연결되지 않는다 | 같게 재현한다 | `NotificationEvent.java:275-340`, `CodeHistoryApp.java`, `PullRequestApp.java:566-640` |
| R-45 | 웹훅의 결함 | (1) 풀 리퀘스트 새 커밋 웹훅은 받는 프로젝트를 요청 폼의 임시 값(`toProjectId`)으로 찾아 병합 검사에서는 값이 없어 어느 웹훅에도 보내지 않는다. 닫기와 다시 열기도 보내지 않는다. (2) 푸시 본문의 커밋 시각은 12시간제(`hh`)라 오전과 오후를 구별하지 못하고 커밋 주소 끝에 공백이 붙는다. (3) 인증 헤더 값 `token <토큰> `의 끝에 공백이 붙는다. (4) 슬랙 첨부의 항목에는 제목이 빈 담당자 항목이 들어간다 | 같게 재현한다 | `NotificationEvent.java:601-681`, `Webhook.java:297-420,560-640` |
| R-46 | 외부 저장소 가져오기의 결함 | (1) 조직 소유로 가져오면 만든 사람에게 매니저 역할을 주지 않는다(소유자가 사용자일 때만 준다). (2) 구독과 방문 기록을 남기지 않는다. (3) 가져올 주소를 그대로 JGit에 넘겨 `file:` 주소나 서버 내부 주소도 가져올 수 있다. 로그인한 누구나 서버 안의 다른 저장소(비공개 포함)를 읽을 수 있는 보안 결함이다. (4) 실패해서 다시 그릴 때 메뉴 선택은 늘 모두 켜진 상태로 그려진다. (5) 이름 규칙 오류가 하나라도 있으면 이름에 `project.name.alert`를 더 붙인다 | 같게 재현한다. (3)은 보안에 관한 것이라 선생님께 정책(허용 주소 제한)을 여쭙는다 | `ImportApp.java:52-216`, `importing.scala.html` |
| R-47 | 코드 리뷰 화면 스크립트의 오류 | `code.Diff` 모듈이 `a[href=#reviewcards-…]`(따옴표 없는 속성 값)로 리뷰 카드 탭을 찾는데 v1.6이 함께 싣는 jQuery 3.3.1은 이를 문법 오류로 본다. 주소에 `#thread-`나 `#comment-` 앵커가 있어 스레드를 찾아 탭을 바꾸려 할 때 콘솔에 `Syntax error, unrecognized expression`이 나고 탭이 바뀌지 않는다. 리뷰 카드 영역이 없는 화면에서 `offset().top`을 읽는 오류도 난다 | JS는 v1.6 파일 그대로이므로 같게 재현한다 | `public/javascripts/service/yobi.code.Diff.js:219-235,115,282` |
| R-48 | 사용자 페이지와 내 파일, 탈퇴의 결함 | (1) 사용자 페이지 이슈 탭은 초안(draft) 이슈를 거르지 않아, 다른 사람이 쓴 초안의 제목이 그 프로젝트를 읽을 수 있는 누구에게나 보인다. (2) `GET /info/leave/:user/:project`는 상태를 바꾸는 GET이라 링크만 눌러도 탈퇴되고, 소유자라서 거부되어도(`deleteMember`의 403) 결과를 보지 않아 같은 사용자 페이지로 이동한다. (3) 이슈·풀 리퀘스트 기간 조회 이름이 `findOpendPullRequestsByDaysAgo`이지만 상태를 가리지 않는다. (4) 사용자 페이지는 없는 사용자에게도 `daysAgo` 쿠키를 먼저 저장하고 404를 돌려준다. (5) 쿠키 값이 숫자가 아니면 오류(500)이다. (6) `/user/files`의 `pageNum`이 숫자가 아니거나 0 이하이면 오류(500)이다 | 같게 재현한다 | `UserApp.java:709-807,992-996`, `Issue.java:524-529`, `PullRequest.java:219-225` |
| R-49 | 내 이슈와 사용자 페이지 이슈 줄의 결함 | (1) 내 이슈 검색은 읽기 권한을 따지지 않는다. 프로젝트에서 빠졌거나 비공개로 바뀐 뒤에도 내가 담당하거나 쓴 이슈가 그대로 보인다. (2) 작성자, 담당자, 댓글 조건은 값(`authorId`, `assigneeId`, `commenterId`)을 보지 않고 현재 사용자를 쓴다. 빠른 검색 항목의 활성 표시만 값이 내 번호일 때 켜진다. (3) 마일스톤 선택과 라벨 조건은 무시하지만 마일스톤 값이 있으면 폼에 그대로 남는다. (4) 이슈 줄의 라벨 링크는 기본값을 생략하는 역방향 라우터 뒤에 `&labelIds=`를 이어 붙여 `/소유자/프로젝트/issues&labelIds=번호`가 되어 열리지 않는다(사용자 페이지와 내 이슈 모두) | 같게 재현한다 | `IssueApp.java:68-111`, `SearchCondition.java:229-253`, `my_partial_list.scala.html`, `partial_issues.scala.html` |
| R-50 | 검색의 결함 | (1) 프로젝트 결과 조각에는 쪽 이동 요소가 없어 첫 20개만 보인다. (2) 게시글 댓글의 작성자 없음 문구 키 `posting.noAuthor`가 메시지 파일에 없어 키 이름이 그대로 보인다. (3) 키워드 강조는 클라이언트가 `innerHTML`을 정규식으로 바꿔 키워드가 `amp`, `lt` 같은 엔티티나 태그 속성과 겹치면 마크업이 깨진다. (4) 사이트가 프로젝트 목록을 숨겨도 이슈, 게시글 등은 공개 프로젝트의 것이 그대로 검색된다. (5) 전체와 조직 범위의 이슈, 게시글, 댓글은 권한을 잃은 프로젝트에 있는 내 글과 내가 담당한 이슈도 찾는다. (6) 전체 범위의 사용자 검색에는 권한 필터가 없어 익명도 모든 활성 사용자의 이름과 로그인 ID를 검색한다. (7) 조직 범위에서 프로젝트 목록 숨김이 켜져 있으면 조직 구성원 역할과 관리자 역할을 모두 가진 사용자만 통과한다 | 같게 재현한다 | `SearchApp.java`, `Search.java:120-129,361-370,498-526`, `partial_projects.scala.html`, `partial_post_comments.scala.html:45`, `partial_search.scala.html:189-200` |
| R-51 | 도움말과 통계의 결함 | (1) 도움말 답변의 `/info` 링크는 전용 라우트가 없어 로그인 ID가 `info`인 사용자 페이지로 해석되어 404이다. (2) 도움말 본문은 언어와 관계없이 한국어이다. (3) 통계 화면은 데이터 없이 "Under Construction"만 보이고 제목 키 `statistics`가 메시지 파일에 없어 키 이름이 그대로 제목에 나온다. 프로젝트 메뉴도 없다. (4) `/_UIKit`에는 로그인 검사가 없다 | 같게 재현한다 | `toc.scala.html`, `statistics.scala.html`, `HelpApp.java`, `StatisticsApp.java` |
| R-52 | 사이트 관리 사용자 관리의 결함 | (1) 없는 사용자 번호로 삭제하면 오류(500)이다. (2) 관리자 권한 부여와 해제에는 자기 보호가 없어 자신이나 기본 `admin`의 권한도 거둘 수 있다. (3) 잠금 전환은 삭제된 사용자도 활성으로 되돌리는데 지운 이름과 이메일은 복구되지 않는다. (4) 삭제한 사용자의 `isLocked`가 참이라 사용자 페이지에 BLOCKED가 붙는다. (5) 삭제는 프로젝트 구성원 행만 지우고 조직 구성원, 가입 신청(조직), 즐겨찾기, 구독은 그대로 둔다. (6) 사이트 관리자 목록의 제외 기준이 사용자 번호가 아니라 관리자 행 번호 1이다 | 같게 재현한다 | `SiteApp.java:163-275`, `User.java:399-433,694-720` |

