---
title: v1.6에서 그대로 유지한 결함 목록
type: architecture
sources:
  - ../refactoring-backlog.md
  - v16-defect-catalog.md
updated: 2026-11-04
---

# v1.6에서 그대로 유지한 결함 목록

`refactoring-backlog.md`(R-nn, 131건)와 [v16-defect-catalog.md](v16-defect-catalog.md)(C###, 189건)에서 **v2가 v1.6과 같게 재현하고 있는 결함**만 모았다. 두 원본은 일부가 겹친다(카탈로그의 "기록" 열이 R-nn을 가리킨다). 개선은 선생님의 승인을 받은 뒤에만 한다. 이 페이지는 두 원본에서 생성했으므로 원본이 바뀌면 다시 만든다.

| 구분 | 건수 |
|---|---|
| 재현 목록(R) 중 그대로 재현 | 103 |
| 재현 목록(R) 중 일부만 재현 | 8 |
| 재현 목록(R) 중 고쳤거나 해당 없음 | 20 |
| 카탈로그(C) 중 재현 | 88 |
| 카탈로그(C) 중 해결·개선·일부 재현·기타 | 72 |
| 카탈로그(C) 중 해당 없음 | 29 |


## 1. 재현 목록(R)에서 그대로 재현하는 결함

| 번호 | 위치 | 결함 |
|---|---|---|
| R-01 | 로그인 | 솔트나 패스워드가 비어 있는 계정으로 로그인하면 예외가 발생해 HTTP 500이 된다 |
| R-02 | 전역 | CSRF 보호가 없다. 폼과 JS가 토큰을 보내지 않는다 |
| R-05 | remember-me | 쿠키 값이 `로그인ID:해시된 패스워드`라서 쿠키가 유출되면 패스워드 없이 로그인된다. 패스워드를 바꿔도 해시가 바뀌므로 쿠키는 무효가 된다 |
| R-06 | 로그인 | `redirectUrl`을 검증하지 않아 `//example.com` 같은 값으로 외부 주소로 이동시킬 수 있다(열린 리다이렉트) |
| R-08 | AccessControl | 컨테이너가 프로젝트가 아닌 전역 리소스(예: USER)이면 `isResourceCreatable`이 `container.getProject()`의 예외로 실패한다 |
| R-09 | AccessControl | 프로젝트 매니저 판정이 "코드(CODE)는 삭제할 수 없다" 규칙보다 먼저 평가되어 매니저는 코드도 삭제할 수 있다 |
| R-10 | AccessControl | 존재하지 않는 이슈를 ISSUE_POST 리소스로 검사하면 담당자 조회에서 NullPointerException이 난다 |
| R-11 | 프로젝트 메뉴 | 단축키 `Q`(설정 화면)가 "로그인 ID가 `manager`일 때"만 켜진다. 프로젝트 역할 이름과 로그인 ID를 혼동했다 |
| R-12 | 프로젝트 검사 | 마일스톤, 이슈 라벨, 라벨 분류, 커밋 코멘트를 번호만으로 찾아 경로의 프로젝트와 달라도 통과한다. 숫자가 아닌 식별자는 500이다 |
| R-13 | 프로젝트 생성 | 이름 예약어(`.`, `..`, `.git`) 비교의 `ignoreCase` 분기가 거꾸로라 기본 설정에서 대소문자를 무시한다(`.GIT`도 거부) |
| R-14 | README 상대 경로 변환 | 일반 링크 정규식이 앞 문자 하나를 요구해 문서 맨 앞 링크를 놓치고, 컨텍스트 경로를 쓰면 소유자 앞 `/`가 빠진다(`//yonaalice/...`) |
| R-15 | 마크다운 자동 링크, 이슈 링크 변환 | (1) 다른 프로젝트의 이슈 참조는 링크 문구에 접두어와 문구가 두 번 붙는다 (2) 링크 문구를 치환 문자열로 쓰므로 제목에 `$`, `\`가 있으면 예외 또는 잘못된 문구 (3) 읽을 수 없는 이슈 링크를 만나면 나머지 링크 변환을 중단 (4) 이슈 번호가 Long… |
| R-16 | 마크다운 결과 캐시 | 캐시 적중 시 이전 결과를 돌려주면서 새로 만들어 저장(변경이 한 번 늦게 반영), 프로젝트 문맥 유무와 무관하게 같은 키 공유, 보는 사람의 권한에 따른 결과가 전역 공유 |
| R-17 | 첨부 파일 종류 감지 | 텍스트 파일의 문자셋을 Tika가 읽고 난 스트림 나머지로 감지해서 65536바이트 이하 파일은 항상 UTF-8 |
| R-18 | 프로젝트 삭제 | (2026-10-27 재정정) 프로젝트 로고 같은 프로젝트 자체의 첨부는 지우지 않아 고아 데이터가 남는다. 프로젝트와 풀 리퀘스트의 구독 기록은 `ResourcePersistAdapter.postDelete`가 지우므로 v2도 지운다(이전 "구독 기록은 지우지 않는다… |
| R-19 | 조직 탈퇴와 이름 변경 | (1) 탈퇴 검사 조건이 거꾸로라 관리자는 혼자여도 탈퇴하고 일반 구성원은 관리자가 혼자일 때 탈퇴 못 함 (2) 이름 변경 여부는 대소문자 구별, 중복 검사는 무시라 대소문자만 바꾸면 자기 자신과 겹침 |
| R-20 | 임시 첨부 정리 | 정리 조건이 `createdDate >= (오늘 0시 - 보관 기간)`(2026-10-26 정정: 기준은 지금이 아니라 오늘 0시)이라 오래된 것이 아니라 최근 보관 기간 안에 올린 임시 첨부를 지운다 |
| R-21 | 아바타 크기 검사 | 1MB를 넘는 이미지를 폼 오류로만 기록하고 실제로는 아바타로 옮긴다 |
| R-22 | 라벨 색 글자색 | 3자리 16진수 색(`#abc`)을 6자리로 늘리려다 문자열 전체를 반복해(`"abc" * 2`) 길이가 맞지 않아 흰색으로 처리된다. 어두운 3자리 색에도 글자색이 `dimgray`가 된다 |
| R-23 | 임시 첨부 옮기기 | `temporaryUploadFiles`를 멀티파트 본문에서만 읽는데 마일스톤 만들기 폼은 일반 폼이라 편집기에서 올린 첨부가 마일스톤으로 옮겨지지 않는다(다음에 수정할 때 사용자의 임시 첨부가 모두 딸려 온다) |
| R-24 | 이슈 삭제 | 이슈를 지워도 멘션 기록(`mention`)이 남아 고아 행이 된다 |
| R-25 | 이슈 보기의 추천자 대화상자 | 추천자 아바타를 그리면서 같은 집합에서 현재 사용자를 빼기 때문에, 대화상자 목록에서 내가 빠지고 나만 추천했으면 대화상자가 없다 |
| R-26 | 이슈 댓글 삭제 | 댓글을 지워도 멘션 기록(`mention`)이 남아 고아 행이 된다 |
| R-27 | 줄바꿈 변환 | `LineEnding.changeLineEnding(…, DOS)`가 `\n`을 `\n`으로 바꾸는 코드라 DOS 변환이 아무것도 하지 않는다. 저장소 파일 온라인 편집에서 CRLF 파일을 고쳐도 LF로 저장된다 |
| R-28 | 게시판 목록의 정렬 링크 | 라벨을 고른 채 정렬 링크를 누르면 링크의 `labelIds`에 배열 원소가 구분자 없이 붙는다(Twirl `BaseScalaTemplate`이 배열을 `fill`로 출력, 바이트코드 확인). 라벨 하나는 정상이고 둘 이상(3과 5)이면 `35` 한 값이 되어 다른 라… |
| R-29 | 게시판 라벨 변경 | 라벨을 바꿀 때마다 `posting.save()`가 불려 제목 머리말 개수(`TitleHead`)가 한 번씩 늘어난다 |
| R-30 | 게시판 보기의 라벨 변경 | 라벨을 고르면 요청은 성공하지만 `$.ajax(...).done(...).error(...)`의 `.error`가 jQuery 3에 없어 브라우저 콘솔에 `TypeError`가 난다 |
| R-31 | 사용자 메뉴의 즐겨찾기 탭 | 내 프로젝트도 조직도 없으면 즐겨찾기 프로젝트가 있어도 탭 전체가 "No results"이다(`etc-favorites`는 빈 `ul`이고 줄이 `ul` 바깥에 이어진다) |
| R-32 | 알림 메일의 "마일스톤 없음" 문구 | 마일스톤 변경 알림에서 마일스톤이 없을 때의 문구를 받는 사람의 언어가 아니라 서버 기본 언어로 만든다 |
| R-33 | 알림 메일의 일반 텍스트 본문 | 끝에 붙는 안내 문장("Yona 에서 자세히 보거나 혹은 이 메일에 직접 회신하실 수도 있습니다.")이 한국어로 고정이고 메일에 답장이 되지 않아도 붙는다 |
| R-34 | 프로젝트 이전 요청 메일 | 받는 사람(To)의 이름이 `Yobi`로 고정이고, 받는 사람이 사용자인지 조직인지에 따라 같은 메일에 Bcc만 달라진다 |
| R-35 | 홈 알림 목록의 본문 언어 | 본문을 로그인한 사용자의 언어가 아니라 서버 기본 언어로 만든다 |
| R-36 | 외부 API 프로젝트 만들기 | `projectCreatedDate`를 받아 두고도 `Project.create`가 만든 날짜를 지금으로 덮어써서 가져온 프로젝트의 날짜가 보존되지 않는다 |
| R-37 | 외부 API 라벨 만들기 | 입력의 `isExclusive`를 읽기만 하고 쓰지 않아 분류가 항상 단일 선택이 아닌 상태로 만들어진다 |
| R-38 | 코드 브라우저 폴더 목록 | 폴더 이동 주소에 `#cb-…` 앵커를 붙이려는 조건이 `" folder"`(앞 공백)와 비교해 항상 거짓이라 앵커가 붙지 않는다 |
| R-39 | 코드 브라우저 이진 파일 안내 | 파일 크기를 문자열 값으로 읽는 도우미(`fieldText`)가 숫자 노드에는 비어 있어 크기가 표시되지 않는다 |
| R-40 | 커밋 이력의 쪽 나누기 | `page * size + size`개를 읽어 마지막 `size`개를 남기므로, 전체 커밋이 두 쪽보다 적으면 마지막 쪽이 앞 쪽과 겹친다(예: 30개일 때 1쪽은 가장 오래된 25개) |
| R-41 | 파일 보기의 댓글 수 | `countOnCommit`에 경로를 `"/"+path`로 넘겨, 코드 범위 스레드는 경로가 항상 달라 세지 않고 파일 단위 스레드만 센다 |
| R-42 | 풀 리퀘스트 병합 검사의 결함 | (1) 보낼 커밋이 없으면 상태를 `MERGED`로 바꾼 뒤 "상태가 MERGED가 아니면" 이벤트를 쌓는 조건을 걸어 병합 이벤트와 알림이 남지 않는다. (2) 검사 중 예외가 나면 병합 중 표시(`isMerging`)를 끄지 않아 화면이 계속 "병합 중"이다. (3… |
| R-43 | 풀 리퀘스트 화면의 잘못된 인수 | (1) 상태 조각의 충돌 해결 안내에서 저장소 주소에 로그인 ID를 넣을지 정하려고 `ProjectUser.isMember(프로젝트 번호, 사용자 번호)`를 인수를 뒤바꿔 호출해 대개 로그인 ID가 들어가지 않는다. (2) 수정에서 브랜치를 바꿔 다른 열린 요청과 겹치… |
| R-44 | 코드 리뷰 댓글의 결함 | (1) 새 코드 리뷰 댓글 알림 본문은 코드 범위 스레드의 첫 댓글에만 차이 조각을 넣고, 새로 추가하거나 지운 파일은 차이 조각이 없어 파일 이름 줄 뒤에 댓글만 나온다. (2) 차이 조각의 `> @@ -시작, 개수 +시작, 개수 @@` 머리줄은 쉼표 뒤에 공백이 들… |
| R-45 | 웹훅의 결함 | (1) 풀 리퀘스트 새 커밋 웹훅은 받는 프로젝트를 요청 폼의 임시 값(`toProjectId`)으로 찾아 병합 검사에서는 값이 없어 어느 웹훅에도 보내지 않는다. 닫기와 다시 열기도 보내지 않는다. (2) 푸시 본문의 커밋 시각은 12시간제(`hh`)라 오전과 오후를… |
| R-46 | 외부 저장소 가져오기의 결함 | (1) 조직 소유로 가져오면 만든 사람에게 매니저 역할을 주지 않는다(소유자가 사용자일 때만 준다). (2) 구독과 방문 기록을 남기지 않는다. (3) 가져올 주소를 그대로 JGit에 넘겨 `file:` 주소나 서버 내부 주소도 가져올 수 있다. 로그인한 누구나 서버… |
| R-47 | 코드 리뷰 화면 스크립트의 오류 | `code.Diff` 모듈이 `a[href=#reviewcards-…]`(따옴표 없는 속성 값)로 리뷰 카드 탭을 찾는데 v1.6이 함께 싣는 jQuery 3.3.1은 이를 문법 오류로 본다. 주소에 `#thread-`나 `#comment-` 앵커가 있어 스레드를 찾아… |
| R-48 | 사용자 페이지와 내 파일, 탈퇴의 결함 | (1) 사용자 페이지 이슈 조회는 초안(draft) 이슈를 거르지 않아 목록 건수(스트림 상자의 이슈 수)에 초안이 들어간다. 다만 이슈 탭은 `OPEN`과 `CLOSED`만 그리므로 초안의 제목은 어느 탭에도 보이지 않는다(2026-10-27 공식 v1.16.0 실행… |
| R-49 | 내 이슈와 사용자 페이지 이슈 줄의 결함 | (1) 내 이슈 검색은 읽기 권한을 따지지 않는다. 프로젝트에서 빠졌거나 비공개로 바뀐 뒤에도 내가 담당하거나 쓴 이슈가 그대로 보인다. (2) 작성자, 담당자, 댓글 조건은 값(`authorId`, `assigneeId`, `commenterId`)을 보지 않고 현재… |
| R-50 | 검색의 결함 | (1) 프로젝트 결과 조각에는 쪽 이동 요소가 없어 첫 20개만 보인다. (2) 게시글 댓글의 작성자 없음 문구 키 `posting.noAuthor`가 메시지 파일에 없어 키 이름이 그대로 보인다. (3) 키워드 강조는 클라이언트가 `innerHTML`을 정규식으로 바… |
| R-51 | 도움말과 통계의 결함 | (1) 도움말 답변의 `/info` 링크는 전용 라우트가 없어 로그인 ID가 `info`인 사용자 페이지로 해석되어 404이다. (2) 도움말 본문은 언어와 관계없이 한국어이다. (3) 통계 화면은 데이터 없이 "Under Construction"만 보이고 제목 키 `… |
| R-52 | 사이트 관리 사용자 관리의 결함 | (1) 없는 사용자 번호로 삭제하면 오류(500)이다. (2) 관리자 권한 부여와 해제에는 자기 보호가 없어 자신이나 기본 `admin`의 권한도 거둘 수 있다. (3) 잠금 전환은 삭제된 사용자도 활성으로 되돌리는데 지운 이름과 이메일은 복구되지 않는다. (4) 삭제… |
| R-53 | 사이트 관리 목록과 프로젝트 삭제의 결함 | (1) 프로젝트 목록의 `pageNum`은 0부터인데 쪽 번호 위젯은 1부터 보내서 기본 화면이 첫 쪽이고 위젯의 1쪽이 실제로는 둘째 쪽이다. (2) 프로젝트 검색에는 정렬이 없다. (3) 프로젝트 삭제는 모델의 `delete()`만 불러 저장소 디렉터리와 첨부 파일… |
| R-54 | 사이트 관리 메일의 결함 | (1) 메일 쓰기에서 본문이 비어 있으면 오류(500)이다. 화면은 본문을 필수로 하지 않는다. (2) 보내지 못해도(메일 서버 없음, 거절) 성공 안내가 나온다. 실패를 알리는 경로가 없다. (3) 설정 안내의 링크 `/admin/mailconf`는 문구에 자리표시자… |
| R-55 | 업데이트 확인과 진단의 결함 | (1) 백그라운드 확인은 `check.use` 설정을 보지 않고 돈다. (2) 알림 끄기는 서버 전체에 적용되고 사용자별이 아니며 서버를 다시 시작하면 켜진다. (3) 화면의 확인은 요청 스레드에서 원격 저장소를 읽어 저장소가 느리면 화면이 늦게 열린다. 시간 제한이… |
| R-56 | 데이터 내보내기와 가져오기의 결함 | (1) 컬럼 목록이 손으로 적혀 있어 엔티티의 일부 컬럼이 빠진다: 사용자의 게스트 여부, 영문 이름, 토큰, 이슈의 초안 여부와 가중치와 부모, 프로젝트의 일부 컬럼 등은 내보내지지 않고 가져오면 기본값이 된다. (2) 즐겨찾기, 웹훅, 소셜 로그인 정보, 사용자 설… |
| R-57 | 최초 기동 화면의 결함 | (1) 로그인 ID가 비어 있으면 같은 오류가 두 번 쌓인다(화면에는 한 번씩만 보이게 묶었다). (2) 이름은 검사하지 않고 그대로 저장한다. (3) 기본 이메일을 그대로 두면 이미 쓰는 이메일이라는 오류가 난다. (4) 재시작 전에는 서버가 계속 안내 화면만 보이고… |
| R-58 | 이전 화면의 결함 | (1) 토큰 교환 응답을 JSON이 아닌 폼 형식으로만 해석하는 정규식(`access_token=([^&]+)`)이고 상태 코드를 보지 않는다. (2) OAuth `state` 값을 쓰지 않아 요청 위조에 약하다. (3) 화면에 옛 서버(`yobi.navercorp.c… |
| R-59 | 소셜 로그인의 결함 | (1) 소셜 세션만 있고 로컬 세션이 없는 채로 로그인 화면에 오면 이동할 주소가 없을 때 v1.6은 오류가 난다. v2는 홈으로 보낸다. (2) 인증 직후 로그아웃으로 보내는 경우(잠김, 이메일 없음)의 안내는 로그아웃 요청이 먼저 소비해서 화면에 보이지 않는다… |
| R-60 | LDAP 인증의 결함 | (1) 프로토콜 설정은 `ldap.protocol`이 아니라 접두어 없는 최상위 `protocol` 키를 읽는다. 기본 설정 파일은 `ldap.protocol`로 적어 두었으므로 `ldaps`로 바꿔도 반영되지 않는다. v2도 최상위 `yona.protocol`을 읽는… |
| R-61 | 메일 수신의 결함 | (1) v1.6은 첨부를 저장하는 `Attachment.save`가 HTTP 요청 문맥이 없는 메일 스레드에서 항상 실패한다. v2는 정상으로 저장한다(D-25). (2) 한 메일을 프로젝트 여러 곳으로 보내면 `original_email.message_id` 유일 제… |
| R-62 | Subversion의 결함 | (1) 이력은 시작과 끝 리비전을 모두 포함해 한 쪽에 `limit + 1`개가 나오고 다음 쪽의 첫 리비전과 겹친다. (2) 폴더 목록과 파일 내용은 리비전을 알려 줘도 항상 최신(HEAD)에서 읽는다. (3) 코드 내려받기(zip)는 본문이 빈 `200` 응답이다… |
| R-64 | 조직 이슈 목록과 풀 리퀘스트 목록 | (1) 이슈 줄의 라벨 링크는 `/소유자/프로젝트/issues?state=open&labelIds=ID`이다. 결함은 아니다(R-49 정정 참고). (2) 작성자, 담당자, 멘션 조건은 값이 아니라 현재 사용자를 쓴다. (3) 풀 리퀘스트는 보내는 쪽 프로젝트도 보이는… |
| R-65 | 리뷰 목록 | (1) 상태나 정렬 방향이 잘못되면 500이고 정렬 기준은 검증 없이 질의에 붙는다. (2) 열림 탭의 활성 판정이 대소문자를 구별해 `state=open`처럼 소문자면 어느 탭도 활성이 아니다. (3) 엑셀은 첫 댓글과 같은 글을 단 응답도 제목 줄로 쓴다. (4)… |
| R-66 | 저장소 초기화(`GET /_init`) | 로그인 없이 누구나 호출하면 모든 프로젝트의 저장소를 지우고 빈 저장소로 다시 만든다. 개발용 경로가 운영에 그대로 열려 있다. 한 프로젝트가 실패해도 나머지는 계속한다(오류는 로그에만 남는다) |
| R-67 | 이슈 엑셀의 머리글 | 댓글 작성 시각 열의 메시지 키 `common.comment.created`가 번들에 없어 머리글에 키 이름이 그대로 나온다 |
| R-69 | 조직 이슈 목록 화면의 스크립트 | `yobi.issue.List.js`가 로드 마지막에 `_initShowSubtasks()`(`yona.showSubtask.js`)를 호출하는데, 이 화면은 그 스크립트를 불러오지 않아 페이지를 열 때마다 `_initShowSubtasks is not defined`… |
| R-70 | 변경 화면(`yobi.code.Diff.js`) | 리뷰 스레드가 하나도 없으면 `div.review-list`가 그려지지 않는데 `_setReviewListHeight`가 그 요소의 `position().top`을 읽어 화면을 열 때와 스크롤할 때마다 `Cannot read properties of undefined… |
| R-71 | 변경 화면의 리뷰 카드 선택 | `$("a[href=#reviewcards-" + state + "]")`처럼 따옴표 없는 속성 선택자를 써서 jQuery 3에서 `Syntax error, unrecognized expression` 오류가 나고, 해당 스레드가 있는 탭으로 전환되지 않는다. `yon… |
| R-72 | 러시아어 번들의 `project.you.are.watching` | `conf/messages.ru-RU:649`가 `Вы смотрите {-} проект.`라서 `{-}`가 올바른 `MessageFormat` 패턴이 아니다. 프로젝트 머리말(`project/header`)이 이 메시지를 인자와 함께 부르므로 러시아어 화면(`Acc… |
| R-74 | `ProjectApp.changeVCS` | v1.6은 `@Transactional`이 없어 저장소 생성이 실패하면 README 해제와 포크 분리는 이미 반영되고 저장소만 지워진 중간 상태로 남는다. v2도 서비스에 트랜잭션을 걸지 않아 같은 중간 상태가 남는다 |
| R-75 | 번역(`IssueApi.translate`) | 번역 서비스 응답에 `translatedText`가 없으면 `String.join`이 문자열 `null`을 이어 붙여 번역문에 "null"이 섞인다 |
| R-77 | 외부 이슈 API 수정, 상태, 라벨 | 저장할 때마다 `save()`가 제목 머리말 개수를 한 번 더 늘린다(R-29와 같은 원인) |
| R-78 | 프로젝트 생성 화면(`yobi.project.New.js`) | 코드 체크를 해제하면 `welReviewerCountSettingPanel` 등이 초기화되지 않아 `undefined.hide()`로 TypeError가 난다. VCS를 바꾸면 두 분기 모두 풀 리퀘스트 체크를 checked로 강제한다. JS가 v1.6과 같아 v2 화… |
| R-79 | 사용자 설정 토큰 재발급(`token_reset`) | GET 요청만으로 토큰이 재발급된다. v2도 같다 |
| R-80 | `MarkdownApp.render`, 이슈 번역 | 마크다운 미리보기에 프로젝트 읽기 권한 검사가 없고, 번역은 댓글 id만으로 조회해 비공개 프로젝트 내용이 번역으로 노출된다 |
| R-82 | REST 프로젝트 생성(`ProjectApi`) | SITEMANAGER 역할 행이 두 번 INSERT된다(`Project.create` 안과 밖). 웹 생성은 1행이다. v2도 REST 경로에서 2행을 넣는다(웹 경로는 1행) |
| R-83 | 조직 검색의 `HIDE_PROJECT_LISTING` | 조건이 `!isMemberOf // !isAdminOf`라 한 사용자가 조직당 역할 행 하나뿐이어서 항상 400이다(`SearchApp.java:127-131`). v2도 같다 |
| R-86 | 첨부 업로드의 파일 종류 감지 | `Attachment.save`가 인자 `fileName`이 아니라 아직 비어 있는 필드 `name`으로 감지해 파일 이름 힌트 없이 내용만으로 MIME을 정한다. 예를 들어 `.css` 파일도 내용이 평문이면 `text/plain`이다. v2도 같게 내용만으로 감지한… |
| R-88 | 이슈, 게시글 방문 기록 | `User.visits(Issue)`, `visits(Posting)`이 `lastVisitedProjectId`에 이슈 또는 게시글의 `id`를 대입한다. 프로젝트 번호 자리에 다른 번호가 들어가서, 이슈를 본 뒤 같은 프로젝트를 다시 열면 "직전 방문과 같다"고 보… |
| R-89 | `Config.getSystemEmailAddress`의 사이트 관리자… | SMTP 주소가 없을 때 `new SiteAdmin().admin`의 메일을 쓰려 하지만 새로 만든 객체의 `admin`은 항상 `null`이라 언제나 `yona@yona.io`이다. 사용처는 충돌 검사용 임시 병합 커밋의 작성자 한 곳이다. v2도 항상 `yona@y… |
| R-91 | 온라인 커밋의 줄바꿈 판정 | `fileLineEnding`이 첫 항목만 보고 이후 줄의 줄바꿈을 보지 않는다 |
| R-92 | 한글 UTF-8 텍스트의 종류 | Tika 1.2의 텍스트 판정이 비ASCII 비율 10% 이상이면 `application/octet-stream`이라 한글 텍스트 첨부를 열면 내려받기가 된다. SVG(선언 없음)는 `text/plain`, WebP는 octet-stream |
| R-94 | 엑셀 내려받기 링크 | `URI.getQuery()`로 퍼센트 인코딩을 풀어 다시 조립해 `%2B`가 `+`로, `%26`이 `&`로 바뀌고 값 없는 `pageNum`이 빠지며 `&&`가 남는다. 잘못된 주소(`%`, `/`)는 500 |
| R-96 | 검색어의 `%`, `_` | Ebean 3.3.4 `icontains`는 키워드를 이스케이프하지 않아 `%`, `_`가 와일드카드이다(`_`만 넣으면 모든 비어 있지 않은 값에 맞는다) |
| R-98 | 외부 API 작성자 | 모르는 이메일의 작성자를 익명 사용자(-1)로 저장하고 사용자를 만들지 않는다(`findByEmail`이 `NullUser`를 돌려주어 생성 분기에 닿지 않는다) |
| R-99 | 이메일 형식 | Play `@Constraints.Email`의 정규식은 `\b...\b`라서 로컬 부분이 단어 문자가 아닌 문자(`+`, `.`, `-`)로 시작하는 주소를 거부한다 |
| R-100 | 최초 관리자 솔트 | 최초 관리자 설정의 솔트를 `Arrays.toString` 형식(`[12, -3, ...]`)으로 저장한다. 일반 가입은 Base64이다 |
| R-101 | 구독 프로젝트 정렬 | 사이드바의 구독 프로젝트는 Ebean 메모리 정렬이라 대소문자를 구분한다(`Zeta`가 `alpha`보다 앞) |
| R-102 | 하위 이슈의 댓글, 공감 링크 | 하위 이슈가 다른 프로젝트에 있으면 링크가 부모 프로젝트의 같은 번호 이슈를 가리킨다(`commentAndVoterPairDisplay(child, parent.project)`) |
| R-103 | 게시판 정렬 링크의 라벨 | 라벨 둘 이상을 고르면 `labelIds`에 번호가 구분 없이 붙는다(R-28 정정) |
| R-104 | 최근 본 이슈의 이름표 | 컨텍스트 경로로 배포하면 주소 앞에 경로가 붙어 `프로젝트 #번호` 이름표가 어긋난다(`RecentIssue.getNumber`가 위치로 자른다) |
| R-106 | 세션 사용자의 권한 판정 기억 | 사이트 관리자, 프로젝트 매니저, 조직 역할 판정이 로그인 동안 유지되어 승격과 해임이 반영되지 않는다. 항목이 최대 2000명을 넘어 밀려나면 세션이 무효가 된다 |
| R-107 | 잠긴 계정의 로그인 유지 쿠키 | 잠기거나 지워진 계정도 `yobi.token` 쿠키가 유효하면 로그인 상태이다 |
| R-110 | `GET /user/isEmailExist` | `@BodyParser.Of(BodyParser.Json.class)`가 붙어 있다. 공식 v1.16.0을 실행해 보니 내용 형식이 없거나 JSON이 아니면 본문을 해석하지 않고 `{"isExist":...}`를 돌려주며, 내용 형식이 `application/json`… |
| R-111 | 라벨 삭제, 추천 등 이슈를 `update()`로 저장하는 모든 경로 | `AbstractPosting.update()`가 저장 때마다 댓글 수 재계산과 멘션 행 맞춤을 한다. 라벨 삭제는 `issue.save()`라 제목 머리말 빈도도 올린다 |
| R-112 | 이슈 수정의 제목 머리말 개수 | 새 제목을 먼저 더하고 옛 제목을 나중에 빼며, 빼는 쪽 프로젝트는 이미 바뀐 프로젝트라 옮겨도 옛 프로젝트의 개수가 줄지 않는다 |
| R-113 | 초안 게시와 프로젝트 이동을 함께 할 때 | 새 이슈 알림(`afterNewIssue`)을 두 번 부른다. 알림 이벤트는 한 건으로 합쳐지지만 웹훅이 두 번 나간다 |
| R-114 | 버전 문자열 파서와 humanize | `java-semver`의 관대한 접두 파싱, `humanize` 1.2.2의 `binaryPrefix` 표기 |
| R-116 | 게시판 글의 댓글 수 | 글의 `comments` 목록이 댓글 저장 전에 읽혀 있어, 댓글을 달 때 저장하는 `numOfComments`가 실제보다 하나 적다(첫 댓글만 맞다). 글 목록의 숫자가 어긋나다가 댓글을 지우거나 글을 고치면 바로잡힌다. 이슈는 해당 없다(실제 서버에서 확인) |
| R-119 | 인증 헤더 처리 | `Authorization` 헤더의 방식 이름을 보지 않고 앞 여섯 글자를 건너뛰어 읽는다(`basic`, `Bearer`도 같은 자격으로 읽힌다). 여섯 글자보다 짧으면 문자열 범위 오류로 500이다 |
| R-120 | 구성원 역할 바꾸기의 입력 | `POST /organizations/:name/member/:id/edit`와 `POST /:user/:project/member/:id/edit`: 역할 번호가 없으면 널 참조로 500, 숫자가 아니면 조직은 `unknownRole` 안내(200)이고 프로젝트는 5… |
| R-125 | 멘션 목록에 권한 검사가 없음 | `GET /:user/:project/mentionList`는 `@AnonymousCheck`뿐이라 익명도 비공개·보호 프로젝트의 멤버와 이슈 후보를 받는다. 없는 프로젝트는 `mentionType`이 `user`나 `issue`이면 널 참조로 500이다 |
| R-126 | 프로젝트 이름 캐시가 예전 이름을 남김 | `CacheStore.projectMap`은 이름을 바꿔도 예전 이름의 항목을 지우지 않아 한 번 연 이름은 프로젝트의 이름이 바뀐 뒤에도 같은 프로젝트를 연다. 같은 이름의 새 프로젝트를 만들면 `newProject`가 항목을 덮어쓴다. 서버를 다시 띄우면 사라진다 |
| R-127 | 이슈 일괄 변경의 입력 | `POST /:user/:project/issues`는 `state`가 열거형 이름이 아니면 400 `{"state":["Invalid value"]}`, 빈 값은 통과, 없는 이슈 번호는 `issue.refresh()` 예외로 500이다 |
| R-128 | 커밋이 아닌 대상을 가리키는 태그 | 트리나 블롭을 가리키는 태그(`git tag t <tree>`)가 있으면 그 태그의 커밋 이력(`/commits/<태그>/`)이 500이고, 코드 화면은 v1.6이 500, v2가 404이다. 주석 태그와 태그의 태그는 정상(200)이다 |

## 2. 재현 목록(R)에서 일부만 재현하는 결함

| 번호 | 위치 | 결함 | v2의 처리 |
|---|---|---|---|
| R-07 | 기본 로그인 페이지 | 로그인하지 않은 사용자가 `POST /-_-api/v1/user/defultLoginPage`를 호출해도 공식 v1.16.0은 오류가 아니라 받은 `path`를 담은 200 JSON을 돌려주고(익명 사용자 설정을 사용자 없이 저장하려 한다) 소스만 읽고 500이라 적었… | 200 JSON을 같게 돌려주되 사용자 없는 설정 행은 저장하지 않는다 (A-161) |
| R-68 | 프로젝트 홈의 이력, 대시보드 탭 | (1) 대시보드의 담당자별, 라벨별 수는 하위 이슈를 포함하지만 비율의 분모는 하위 이슈를 뺀 열린 이슈 수라 100%를 넘을 수 있다. (2) 이력 항목의 주소는 프로젝트의 실제 소유자가 아니라 요청 주소에 쓴 소유자(대소문자 포함)를 쓴다. (3) 이슈 이력은 초안… | (1)~(3) 재현함, (4) 재현하지 않음(A-24) |
| R-84 | 임시 업로드 정리, import, welcome 화면 | `import`에서 TRUNCATE 뒤 값이 배열이 아니면 테이블만 비워지고 행이 채워지지 않는다(결정 5로 v2도 같게 테이블만 비운다). 업데이트 확인과 IMAP 폴링은 `application.secret`이 기본값이 아닐 때만 등록된다(v2 비밀 키가 비어 있어도… | 일부 재현하지 않음(D3, A-92) |
| R-87 | 5차 검증의 결함 후보 189건 | 항목별 v1.6 소스 줄, 기록 위치, v2 판정은 [v16-defect-catalog.md](architecture/v16-defect-catalog.md)에 있다. 기록이 없던 실제 결함(★ 105건)은 이 목록이 기록이며, v2는 같게 재현하거나(JS, 템플릿… | 재현함 / 일부 재현하지 않음(목록의 v2 판정 열) |
| R-121 | 첨부 올리기의 잘못된 요청 | 멀티파트가 아닌 `POST /files`(본문 없음, 폼 인코딩)는 `request().body().asMultipartFormData()`가 `null`이라 `NullPointerException`으로 `500`이다. 로그인하지 않은 요청도 `authorEmail`로… | v2는 멀티파트가 아니면 `400`이다(R-118과 같은 부류). 명의 지정은 v1.6대로 허용한다 |
| R-122 | 알림 둘 이상일 때의 표시 순서 | 알림이 둘 이상 남아 있을 때 표시 순서가 페이지 종류와 실행마다 다르다(`GET /nope`는 경고, 실패 순이고 403 화면과 라우트 없는 방식의 404 화면은 실패, 경고 순이다). 규칙을 찾지 못했다 | 넣은 순서로 둔다. (이전에 "HEAD 요청 차이"라고 적었던 항목은 잘못이었다. 같은 경로들이 익명 GET에서도 달랐고 아래 R-123과 A-172에서 고쳤다) |
| R-123 | 사이드바 `hash` 누락 | `GET /user/sidebar`는 `hash` 질의가 없으면 `layout_framed`의 `request.getQueryString("hash").get`이 `NoSuchElementException`이라 500이다(`hash=` 빈 값은 정상). 익명의 즐겨찾기… | v2는 `hash` 누락과 익명 즐겨찾기 이슈 목록을 500으로 재현한다. 프로젝트, 조직 목록은 안정된 상태인 200(빈 목록)으로 둔다 |
| R-124 | 메일로 받은 글의 결함 | (1) 제목이 빈 메일로 만든 이슈는 제목이 `null`(문자열이 아닌 널)이라 이슈 보기의 제목이 `null`로 나오고, 같은 프로젝트의 이슈 목록 화면은 500이다. (2) 제목이 255자를 넘는 메일은 저장에 실패하지만 이슈 번호를 두 개 쓴다(재시도). (3)… | (1) v2는 빈 제목으로 만들고 목록은 정상이다(재현하면 메일 한 통으로 이슈 목록 전체가 500이 되어 재현하지 않았다. 선생님 확인이 필요하다). (2)(3)(4)(5)는 재현하지 않는다… |

## 3. 카탈로그(C)에서 재현하는 결함 (영역별)


### 보안

| 번호 | 결함 | v1.6 소스 | 닿는 경로 |
|---|---|---|---|
| C013★ | 기본 로그인 페이지의 `path`를 검증 없이 저장하고 `index()`가 그대로 `redirect`. 토큰이 없는 POST라 외부 요청으로 설정 가능(열린 리다이렉트) | UserApp.java:1373-1381, Application.java:49-50 | 희귀(화면은 현재 경로만 보냄. 직접 만든 요청이나 CSRF) |
| C027 | 프로젝트 라벨 `'<span ...>' + sName + ' </span>'`에 입력값 이스케이프가 없음(자기 입력 XSS) | yobi.site.MassMail.js:155 | 희귀(사이트 관리자 본인 입력) |
| C056★ | 댓글 수정이 `isModifiedByOthers`를 권한 검사보다 먼저 해, 로그인만 하면 틀린 `original`로 409 응답에서 본문을 읽음 | IssueApi.java:615-621 | 희귀(직접 만든 요청) |
| C091 | MarkdownApp.render가 본문/프로젝트 null 검사와 프로젝트 READ 권한 검사가 없음 | MarkdownApp.java:30-37 | 희귀(미리보기 요청을 직접 만들 때). 권한 부분은 정상 화면에서도 호출은 가능 |
| C092 | 번역 API가 댓글을 id만으로 조회해 비공개 프로젝트 내용이 노출 | api/IssueApi.java:1003-1008 | 희귀 |
| C093★ | `updateIssueComment`의 충돌 검사(원문 반환)가 UPDATE 권한 검사보다 먼저라 권한 없는 사용자도 원문을 받음 | api/IssueApi.java:615-621 | 희귀 |
| C135 | 내 이슈 목록에서 assigneeId=-1이면 프로젝트 가시성 필터 없이 전체 프로젝트의 미배정 이슈가 나옴 | SearchCondition.java:202-206,229-253, IssueApp.jav… | 희귀(직접 쿼리) |
| C147★ | newIssueFormByComment가 댓글의 프로젝트 읽기 권한을 검사하지 않아 다른(비공개) 프로젝트 댓글 본문이 폼에 실림 | IssueApp.java:374-387 | 희귀(타 프로젝트 댓글 id를 직접 지정) |
| C155 | deleteComment가 commitId/경로 프로젝트와 댓글의 일치를 검사하지 않음(번호만으로 조회). 후보가 말한 "없는 id면 NPE"는 결함 아님: IsAllowedAction이 null이면 404를 돌려줌 | CodeHistoryApp.java:250-260, Resource.java:240-241… | 희귀 |
| C169 | `GET /user/editform/token_reset`이 토큰을 재생성하고 CSRF 방어가 없음 | UserApp.java:897-904, conf/routes:136 | 정상(화면의 다시 만들기 링크) |

### 데이터

| 번호 | 결함 | v1.6 소스 | 닿는 경로 |
|---|---|---|---|
| C079★ | `addEOL("")`이 `"\n"`을 붙여 빈 내용이 개행 한 줄 파일이 됨 | LineEnding.java:43-54 | 정상(온라인 편집에서 내용을 비울 때) |
| C081★ | `PullRequest.save()`가 호출자의 number를 무시하고 최댓값+1을 매겨 동시 생성에 약함 | PullRequest.java:687-704 | 희귀(동시 생성) |
| C082★ | `Comment.update()`는 부모를 재저장하지 않음(`save/delete`는 함) | Comment.java:77-99 | 정상(댓글 수는 수정으로 바뀌지 않아 영향 거의 없음) |
| C130 | 외부 API 프로젝트 생성에서 SITEMANAGER 역할 행이 Project.create 안과 밖에서 2번 INSERT됨(findByIds가 SITEMANAGER 제외) | ProjectApi.java:152, Project.java:146-147, Project… | 희귀(외부 REST 프로젝트 생성) |
| C162 | 임시 업로드 정리가 `ge("createdDate", 기준)`이라 오래된 것이 아니라 최근 USER 첨부를 지움 | Attachment.java:457-461 | 정상(주기 작업) |

### 서버

| 번호 | 결함 | v1.6 소스 | 닿는 경로 |
|---|---|---|---|
| C001 | LDAP 바인드는 성공했지만 검색 결과가 없거나 둘 이상이면 `authenticate`가 null이고 `ldapUser.getEmail()`에서 NPE(500) | UserApp.java:1159-1162 | 희귀(LDAP 서버/디렉터리 이상) |
| C016 | `UserApp.leave`가 `deleteMember`의 403을 버리고 항상 프로필로 redirect하며 직접 호출이라 `@With(DefaultProjectCheckAction)`도 적용 안 됨 | UserApp.java:993-996, ProjectApp.java:1006-1017 | 정상(소유자가 탈퇴 링크 클릭) |
| C031★ | `findIndexes`가 빈 키워드에서 `index + 0`으로 무한 루프 | SearchResult.java:92-96 (`makeSnippets` :54-) | 희귀(빈 문자열은 차단됨. 공백만 있는 키워드는 비어 있지 않아 무한 루프가 아님) |
| C034★ | `admin` 사용자가 없으면 `updateDefaultSiteAdmin`에서 NPE | Global.java:301 -> SiteAdmin.java:45-46 | 희귀(첫 기동 화면에서 admin 행이 없을 때만) |
| C038 | `setMergedStateOfPullRequest`가 상태를 MERGED로 바꾼 직후 `state != MERGED`를 검사해 알림/이벤트가 안 쌓임 | PullRequestActor.java:49-54, PullRequestMergeResul… | 정상(푸시로 보낼 커밋이 모두 반영된 풀 리퀘스트) |
| C046★ | 일괄 수정에서 삭제 성공이 `updatedItems`에 안 잡혀, 삭제만 성공하고 일부 거부면 403 | IssueApp.java:408-417, 440-442 | 정상(목록에서 여러 건 선택 삭제, 일부는 삭제 권한 없음) |
| C047 | 조직/전역 검색의 author/assignee가 요청 값을 무시하고 현재 사용자 사용 | SearchCondition.java:184-188, 202-210 | 정상 |
| C048★ | 멘션/공유/즐겨찾기 조건이 익명이면 조용히 무시되어 전체 반환 | SearchCondition.java:265,276,295 | 희귀(익명이 조직 이슈 목록에 이 쿼리를 직접 붙일 때) |
| C049★ | 프로젝트 오버로드는 `mentionId`로 필터하지 않고 서브태스크 숨김 판정에만 사용 | SearchCondition.java:347-420 (415행) | 희귀(프로젝트 이슈 목록에 `mentionId`를 직접 붙일 때) |
| C058 | `isExclusive = ...isBoolean()`을 계산해 놓고 쓰지 않으며, 응답은 새 카테고리 기본값 | ProjectApi.java:483,510 | 희귀(외부 API, 화면은 안 부름) |
| C062★ | 오류 문구를 번역 키가 아니라 문장(`'이름' has no permission`)으로 만들어 번역되지 않음 | ProjectApi.java:137, ImportApp.java:192, ProjectAp… | 정상(조직 관리자가 아닌 사용자가 조직 소유로 만들 때) |
| C072★ | 검증은 `trim`한 URL, 클론은 원문(공백 포함) URL을 씀 | ImportApp.java:75,88,201 | 정상(주소 뒤 공백을 붙여 넣을 때). 클론 성공 여부는 JGit 동작이라 미확인 |
| C080 | SVN WebDAV `HEAD`가 읽기 목록에 없어 UPDATE 권한 요구 | SvnApp.java:188-197 | 정상(읽기 전용 사용자가 HEAD를 보낼 때) |
| C088 | 이슈 링크 문구에 `linkText += prefix + linkText`와 `prefix + linkText`로 접두와 문구가 중복 | AutoLinkRenderer.java:253-261 | 정상(타 프로젝트 이슈 참조) |
| C089 | `appendReplacement`에 제목이 든 문자열을 그대로 써 `$`, `\`가 있으면 예외 | AutoLinkRenderer.java:215 | 정상(제목에 `$`, `\`가 든 이슈 참조) |
| C090 | `extractIssueLink`가 권한 거부 시 true를 돌려 호출부가 `break`로 이후 링크 변환을 중단 | Markdown.java:152,187-189 | 정상 |
| C096★ | `EventType.valueOf(notificationType)`가 잘못된 값이면 IllegalArgumentException(500) | WatchProjectApp.java:49-50 | 희귀 |
| C097 | 조직 신청 취소 검증이 `isGuest`만 봐서 이미 멤버인 사용자는 400 | EnrollOrganizationApp.java:96-107 | 정상 |
| C128★ | 조직 검색의 일부 400이 본문 없는 `badRequest()` | SearchApp.java:118-122, 127-131 | 희귀(직접 만든 요청, 숨김 설정 켜면 정상 사용도 해당) |
| C136 | 임시 첨부 정리 쿼리가 `ge`라 최근 보관 기간 안의 것을 지움 | Attachment.java:458-461 | 정상(스케줄러가 자동 실행) |
| C137★ | `Authorization.split("token")[1]`이라 토큰 값 안에 "token" 문자열이 있으면 잘림(Base64 SHA-256 토큰이라 확률 약 4e-8) | UserApi.java:57,136,300,313 | 희귀 |
| C140★ | extractDepartmentPart가 `[`, `(` 순으로 이미 잘린 문자열에서 다시 찾아, `(`가 `[`보다 앞서면 `(` 부분이 버려짐 | User.java:1166-1177 | 정상(이름에 둘 다 있을 때) |
| C142★ | 로그인 폼이 절대 URL인 Referer를 경로 문자열 `/users/loginform`과 비교해 항상 다름. 로그인 화면 자신이 redirectUrl이 됨 | UserApp.java:138-143 | 정상(실패한 로그인 뒤 등) |
| C150 | FileDiff.updateRange에서 lineA와 lineB가 같은 Edit에 맞으면 Edit이 두 번 추가됨 | playRepository/FileDiff.java:230-252 (후보의 경로 FileD… | 해당 없음(죽은 코드) |
| C151 | 없는 이슈에서 담당자 검사 시 `byId(...).assignee` NPE | AccessControl.java:398-405(후보의 :401이 해당 줄) | 희귀 |
| C154 | C136과 같은 항목(`ge`로 최근 파일 삭제) | Attachment.java:458-461 | 정상(스케줄러) |
| C157 | validateForLeave 조건이 거꾸로(관리자는 혼자여도 탈퇴, 일반 구성원은 관리자 1명이면 탈퇴 불가) | OrganizationApp.java:297-311 | 정상 |
| C167 | 가입 거절(REJECT) 이벤트의 title이 null이 되는 switch 누락 | NotificationEvent.java:1236-1250 | 닿지 않음 |
| C183 | PR 재오픈이 리뷰어를 초기화하지 않고 병합 액터가 새 커밋을 찾을 때만 초기화 | PullRequest.java:667-670; PullRequestActor.java:39… | 정상 |
| C187★ | multipart/related에서 `start`도 Content-ID도 없는 자식이 `StringUtils.equals(null,null)`로 모두 루트가 되어 병합됨 | CreationViaEmail.java:388-390 | 정상(Content-ID 없는 related 메일) |

### 화면

| 번호 | 결함 | v1.6 소스 | 닿는 경로 |
|---|---|---|---|
| C053 | `fileType.eq(" folder")`(앞 공백, 참조 비교)로 항상 거짓이라 앵커가 안 붙음 | views/code/partial_view_folder.scala.html:31 | 정상 |
| C148★ | Organization.getVisibleProjects는 대소문자 구분 정렬, getSortedByProjectName은 무시 정렬이라 불일치 | Organization.java:82-90,133-138, myOrganizationLis… | 정상(표시 순서) |
| C152★ | `"error.fobidden"` 오타 키가 어느 messages 파일에도 없어 로그인 화면 제목에 키가 그대로 나옴(5개 번들 모두 0건) | ErrorViews.java:47 | 정상(로그아웃 상태로 없는/비공개 프로젝트 접근) |
| C159★ | `-ms-sbox-izing` 접두 오타 | app/assets/stylesheets/less/_mixins.less:176 | 정상(무해, 브라우저가 무시) |
| C160★ | `fonts/material-icon..ttf` 점 두 개 오타(woff2/woff가 먼저 선택돼 ttf 폴백만 영향) | app/assets/stylesheets/usermenu.less:25 (후보의 _user… | 정상(구형 브라우저 ttf 폴백만) |

### JS

| 번호 | 결함 | v1.6 소스 | 닿는 경로 |
|---|---|---|---|
| C003★ | `requestAs`의 `get` 분기가 선언하지 않은 `el`을 읽어 ReferenceError | jquery.requestAs.js:49 | \ |
| C004★ | `_onSuccessSubmit`이 응답 검증 실패 때 `_onErrorSubmit(nSubmitId, oRes)`로 네임스페이스를 빠뜨려 네임스페이스 핸들러가 `errorUpload`를 못 받음 | yobi.Files.js:225-227 (`_onErrorSubmit` 시그니처 :247) | 정상(업로드 응답에 name/url이 없을 때) |
| C005★ | 업로드 실패 시 임시 주석을 `nSubmitId + ".png"`로 지우는데 삽입은 `nSubmitId`(접미 없음)라 주석이 남는다 | yobi.Attachments.js:350 vs :533 (`_getTempLinkText… | 정상(붙여넣기 업로드 실패) |
| C006★ | `_onSuccessUpload`가 응답 검증 전에 `oRes.id`를 목록에 등록하고, `_onErrorUpload(nSubmitId, oRes)`로 호출하지만 함수는 `htData` 하나를 받아 `htData.nSubmitId`가 undefined | yobi.Attachments.js:282-285 (`_onErrorUpload` :341… | 정상(업로드 응답 이상 시) |
| C007★ | 업로드마다 `.on("click", ".btn-delete", deleteAttachment)`를 다시 걸어 삭제 핸들러가 누적 | yona.CommentAttachmentsUpdate.js:136-137 | 정상(한 댓글 폼에서 두 번 이상 업로드 후 삭제) |
| C008★ | `splitIds.includes(fileId)`가 문자열 배열과 숫자 id를 비교해 항상 거짓, 같은 id가 중복으로 붙을 수 있음 | yona.CommentAttachmentsUpdate.js:154 | 정상(같은 파일 id가 다시 오는 경우만 영향, 영향 작음) |
| C009★ | `if (!data && !data.receivers)`가 `&&`라 data가 null이면 TypeError, data가 있고 receivers만 없으면 통과해 `sortByName(undefined)` | yona.ReceiverList.js:33 | 희귀(서버가 항상 객체를 돌려줌) |
| C010★ | `getPlainText`가 인자 대신 전역 `window.event`를 씀 | yobi.Files.js:588 | 정상(표 붙여넣기. 전역 event가 있는 브라우저에서는 동작) |
| C011★ | `htLabels.hasOwnProperty()`에 인자가 없어 항상 false, 라벨 버튼 비활성화 의도가 동작하지 않음 | yobi.issue.MassUpdate.js:190 | 정상(버튼이 항상 활성) |
| C022★ | `_beautifyFileSize`가 `welShowfile`(소문자 f)을 읽어 TypeError. 요소는 `welShowFile` | yobi.code.Browser.js:359 (정의 :72) | 정상(이진 파일 보기) |
| C024★ | 삭제 링크를 누를 때마다 `#deleteBtn` click 핸들러를 추가해 취소 후 다른 구성원을 지우면 이전 구성원 URL로도 DELETE | yobi.organization.Member.js:165-172 | 정상(삭제 확인 창을 닫고 다른 구성원 삭제. 의도하지 않은 구성원 삭제 가능) |
| C025★ | 비밀번호/확인 keyup이 `htVar.oDelay` 하나를 공유해 한쪽이 다른 쪽 검사를 취소 | yobi.user.SignUp.js:99-117 | 정상(영향 작음) |
| C026★ | 문서에 붙이지 않은 `$('<form>')`에 `submit()`을 호출해 최신 브라우저는 제출하지 않으므로 `mailto:` 메일 창이 안 열림 | yobi.site.MassMail.js:107-116 | 희귀(사이트 관리자 화면) |
| C032 | 코드/PR 체크 해제 때 초기화되지 않은 `welReviewerCountSettingPanel`을 호출해 TypeError | yobi.project.New.js:198-200, 210 | 정상(프로젝트 생성/가져오기 화면) |
| C042★ | `--`/`++`로 시작하는 줄(예: 내용이 `-`/`+`로 시작하는 삭제/추가 줄)을 파일 헤더 분기로 보내 `return`해 렌더링 안 되고 줄 번호도 어긋남 | yobi.code.SvnDiff.js:350-360 | 정상(Subversion 프로젝트 커밋/비교 화면) |
| C043★ | 헝크 헤더 파싱 실패 시 경고만 하고 `aHunkRange[1]`로 TypeError | yobi.code.SvnDiff.js:383-395 | 희귀(형식이 어긋난 헝크 헤더) |
| C064★ | 번역 `.done` 콜백의 `$(this)`가 버튼이 아니라 ajax 설정 객체여서 버튼이 비활성화되지 않음 | views/board/view.scala.html:271,290 (issue/view.sc… | 정상(번역 단추. `.one('click')`이라 재클릭은 막혀 해는 작음) |
| C074★ | 검증 실패 분기가 `_onErrorUpload(nSubmitId, oRes)`로 호출하지만 수신 함수는 `htData` 하나라 `htData.oRes.status`에서 TypeError | yobi.Attachments.js:285,341 | 희귀(업로드가 200이지만 응답에 name/url이 없을 때) |
| C077★ | 알림 `.more` 토글이 링크/이미지 클릭 판정보다 먼저 실행돼 링크를 눌러도 토글됨 | notifications.scala.html:82-91 | 정상 |
| C101★ | 첨부 이전 위임 서비스 함수가 undefined를 반환하는데 `.then` 호출 -> TypeError | yona.Migration.js:123, 128, 685-687 | 정상(이전 화면 사용 중 해당 경로 진입) |
| C102★ | `endLoadingBar(counter++)` 후위 증가라 counter가 늘지 않아 타임아웃 불가 | yona.Migration.js:489 | 정상(가져오기 수가 기대값과 어긋날 때) |
| C113★ | (a) Browser.js:359 `welShowfile` 오타(정의는 `welShowFile`, :72) (b) MassUpdate.js:174 `htLabel` 전역 누출(:166 선언에 없음, :230에만 있음), :190 `hasOwnProperty()` 인자… | 열거한 파일:줄 | (a)(c) 정상, (b) 정상(일괄 수정 사용), (d) 희귀(형식 틀린 헝크 헤더) |
| C174★ | `[data-action=delete]` 클릭마다 `#deleteBtn` click 핸들러를 새로 등록해 누적, 확인 한 번에 DELETE가 여러 번 나감 | yobi.organization.Member.js:161-175 | 정상 |
| C181★ | `_onSuccessUpload`가 `_onErrorUpload(nSubmitId, oRes)`로 위치 인자를 넘기지만 함수는 객체 하나를 받아 `htData.nSubmitId`가 undefined, 진행 항목이 안 지워지고 오류 안내가 깨짐 | yobi.Attachments.js:285,341-342 | 희귀(서버 응답에 name/url이 없을 때) |

### 화면(메시지)

| 번호 | 결함 | v1.6 소스 | 닿는 경로 |
|---|---|---|---|
| C184 | 영어/러시아어 `viaEmail.error.cannotCreate`가 `{1}~{3}`을 쓰지만 인자는 `{0}`부터 3개라 어긋남, `{3}` 미치환 | conf/messages:1105; messages.ru-RU:998; CreationVi… | 정상(메일 수신 권한 오류 회신) |

### 화면(템플릿)

| 번호 | 결함 | v1.6 소스 | 닿는 경로 |
|---|---|---|---|
| C020★ | `urlToList(project, state)`가 `state` 인자를 무시하고 항상 `"open"`을 써서 닫힌 이슈/하위 이슈의 라벨 링크도 열림 목록으로 감 | my_partial_list.scala.html:42,74, partial_view_chi… | 정상(닫힌 이슈의 라벨 클릭) |
| C021★ | 일반 목록이 비면 `else` 안에서만 그려지는 초안 목록이 안 그려져, 초안만 있는 프로젝트의 이슈 화면은 초안이 안 보임 | partial_list_wrap.scala.html:61-86 | 정상(열린 이슈 없이 초안만 있는 프로젝트) |
| C028 | 게시글 댓글 검색 결과의 작성자 없음 문구 키 `posting.noAuthor`가 메시지 파일에 없음 | partial_post_comments.scala.html:45 (메시지는 `common… | 희귀(작성자 이름이 비어 있는 게시글 댓글) |
| C086 | 상대 링크 치환에서 `application.context`가 있으면 `"/" + root + owner`가 `//ctxowner`가 됨 | Markdown.java:359-375 | 정상(컨텍스트 경로를 쓰는 배포) |
| C087★ | 정규식 `(.*)\)`가 탐욕이라 한 줄에 상대 링크가 둘이면 합쳐짐 | Markdown.java:363,372-373 | 정상 |
| C122 | `ProjectUser.isMember(userId, projectId)`에 인수 순서를 뒤바꿔 전달 | partial_state.scala.html:30, ProjectUser.java:122-… | 정상 |
| C123★ | 이벤트 li는 `id="comment-N"`인데 날짜 링크는 `#event-N` | partial_pull_request_event.scala.html:56,60 | 정상 |
| C124 | 프로젝트 검색 결과에 페이지 이동 요소가 없어 21건째부터 UI로 접근 불가 | search/partial_projects.scala.html (전체, 쪽 이동 없음) | 정상 |
| C125 | `posting.noAuthor` 키가 메시지 파일에 없음 | search/partial_post_comments.scala.html:45 | 정상(작성자 없는 게시글 댓글) |
| C168★ | 사용자 페이지의 닫힘 이슈 탭 빈 메시지 조건이 전체 `issues.isEmpty`라 닫힌 이슈만 없어도(또는 열림 탭에서 열린 이슈만 없어도) 메시지가 안 나옴. 파일은 `user/view.scala.html`(candidate의 view.scala.html:143) | user/view.scala.html:131-135,142-147 | 정상 |
| C171★ | 조직 설정 폼이 검증 실패(이름 중복) 시 제출 값이 아니라 DB 값을 다시 표시. candidate의 setting.scala.html:58,68은 `organization/setting.scala.html`(project/setting도 같은 모양, 단 projec… | organization/setting.scala.html:58,68; Organizatio… | 정상 |
| C179★ | `Q` 단축키가 로그인 ID `manager`일 때만 켜지고, 그때 출력되는 `"Q": …`는 앞 쉼표가 없어 단축키 맵이 문법 오류 | projectMenu.scala.html:148-151 (다른 항목은 `,"M"` 식으로… | 희귀(로그인 ID가 `manager`인 사용자) |
| C180 | 폴더 판정 `eq(" folder")`(앞 공백)라 항상 거짓, 폴더 링크에 `#cb-` 앵커가 안 붙음 | partial_view_folder.scala.html:31 | 정상 |

## 4. 고치거나 해당 없는 재현 목록(R) 항목

| 번호 | 위치 | v2의 처리 |
|---|---|---|
| R-03 | 로그아웃 | 홈(`/`)으로 이동한다. v1.6의 오류까지 재현하지는 않는다. **선생님 승인(2026-10-22)** |
| R-04 | API 토큰 | 같게 재현했으나 X-5(A-206)에서 잠기거나 삭제된 사용자의 토큰을 거절하도록 바꿨다(의도된 차이, differences-from-v16.md) |
| R-63 | 라벨 붙이기(`POST /:user/:project/labels`) | 결함 아님(정정) |
| R-73 | `IssueApp.editIssue`, `deleteIssue` | 재현하지 않음(선생님 승인 2026-10-26, P-07) |
| R-76 | `PullRequestApp.accept` | v2도 병합 중 표시를 먼저 저장하고 트랜잭션을 걸지 않아 같은 상태가 남는다. **개선함(선생님 승인 2026-11-02)**: 병합 조건을 확인한 뒤에 표시를 저장하고, 병합기가 예외를 던지면… |
| R-81 | `10.sql` USER/USER_AVATAR 백필 | 재현하지 않음(해당 없음) |
| R-85 | `ProjectApp.addCommitAuthor` | 재현하지 않음 |
| R-90 | `UserApi.newUser`의 로그인 ID 중복 | 재현하지 않음 |
| R-93 | 이름 없는 Ogg 첨부 | v2는 오류 없이 `audio/ogg` (A-113) |
| R-95 | 접근 로그 | 재현하지 않는다 (A-111) |
| R-97 | (해소) Git POST 본문 한도 | 해당 없음 |
| R-105 | 이슈 참조 링크의 치환 문자열 | v2는 `quoteReplacement`로 오류 없이 처리 (A-125) |
| R-108 | 이슈 수정(`editIssue`)의 입력 처리 순서 | v2는 오류 없이 권한에 따라 303 또는 403을 돌려준다 |
| R-109 | `MarkdownApp.render`의 본문 읽기 | v2는 두 형태 모두 오류 없이 처리한다 |
| R-115 | 코드 보기와 원본 보기의 큰 파일 | v2는 스트림으로 읽어 오류 없이 처리한다 (A-149) |
| R-117 | 즐겨찾기 이슈 목록 | v2는 이슈의 작성자 이름으로 정상 JSON을 돌려준다 |
| R-118 | 토큰 API의 잘못된 입력 | v2는 각각 404, 400, 403으로 낸다. 정상 입력의 응답은 같다 |
| R-129 | 줄 댓글을 단 뒤 `#comment-번호`로 이동할 때 | 선생님 지시(2026-10-29)로 선택자에 따옴표를 붙여 고쳤다(차이 장부 기록) |
| R-130 | 사이트 데이터 가져오기에서 이슈 댓글 테이블이 항상 실패 | **개선했다(선생님 승인, 2026-11-01).** 가져올 때 `project_id`를 댓글이 가리키는 이슈의 프로젝트에서 채운다(`DerivedColumn`). 내보내기 파일은 v1.6과 같게… |
| R-131 | REST 이슈 가져오기(`IssueApi.saveIssues`)의 시각 | **고쳤다(선생님 지시 2026-11-03, D-83)**: 만든 시각이 없거나 읽을 수 없으면 지금 시각, 고친 시각이 없으면 만든 시각으로 채우고 ISO-8601도 받는다. 게시글 가져오기는 이… |
