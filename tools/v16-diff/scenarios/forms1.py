"""폼과 변경 요청(POST, PUT, DELETE, PATCH)을 여러 사용자로 보낸다. 검증 오류, 권한 오류, 성공을 모두 담는다. data2 상태에서 한 번만 실행한다.
실행 순서가 중요하다: 권한이 없는 요청 -> 오류 -> 성공 -> 파괴적인 요청 순이다."""
from harness import Step
from data1 import mp, login


def mpf(**fields):
    """멀티파트 본문에 비어 있는 로고 파일 부분(`logoPath`)을 더한다. 실제 설정 폼이 그렇게 보낸다."""
    parts = mp(**fields)
    parts["logoPath"] = ("", b"", "application/octet-stream")
    return parts


B = "/tester/proj1"
ACTORS = ["anon", "third", "other", "tester", "admin"]


def sweep(method, path, label, capture=None, **kw):
    """모든 행위자로 같은 요청을 보낸다(권한 행렬). `capture`의 이름에는 행위자 이름이 붙는다(`ms_tester`)."""
    return [Step(a, method, path, note=f"{label} [{a}]", capture=({f"{k}_{a}": v for k, v in capture.items()} if capture else {}), **kw) for a in ACTORS]


def steps():
    s = []
    for u in ("admin", "tester", "other", "third"):
        s.append(login(u, "admin1234" if u == "admin" else "pass1234"))
    s.append(Step("admin", "POST", "/sites/unwatchUpdate", note="업데이트 알림 숨기기"))
    s += [
        Step("tester", "GET", f"{B}/issue/labels", headers={"Accept": "application/json"}, capture={
            "lab_bug": ("jsonfind", "name=bug:id"), "lab_ui": ("jsonfind", "name=ui:id"), "cat_type": ("jsonfind", "name=bug:categoryId"), "cat_area": ("jsonfind", "name=ui:categoryId")},
            note="라벨 번호 읽기"),
    ]

    # ---- 로그인과 가입 ----
    s += [
        Step("anon", "POST", "/users/login", data={"loginIdOrEmail": "tester", "password": "wrong"}, note="로그인 비밀번호 틀림"),
        Step("anon", "POST", "/users/login", data={"loginIdOrEmail": "nobody", "password": "x"}, note="없는 사용자"),
        Step("anon", "POST", "/users/login", data={"loginIdOrEmail": "", "password": ""}, note="빈 로그인"),
        Step("anon", "POST", "/users/login", data={"loginIdOrEmail": "tester@example.com", "password": "pass1234", "rememberMe": "true"}, note="이메일로 로그인"),
        Step("anon", "POST", "/users/signup", data={"loginId": "", "name": "", "email": "", "password": "", "retypedPassword": ""}, note="가입 모두 빈 값"),
        Step("anon", "POST", "/users/signup", data={"loginId": "tester", "name": "Dup", "email": "dup@example.com", "password": "pass1234", "retypedPassword": "pass1234"}, note="가입 중복 아이디"),
        Step("anon", "POST", "/users/signup", data={"loginId": "newbie", "name": "New", "email": "tester@example.com", "password": "pass1234", "retypedPassword": "pass1234"}, note="가입 중복 이메일"),
        Step("anon", "POST", "/users/signup", data={"loginId": "newbie", "name": "New", "email": "new@example.com", "password": "pass1234", "retypedPassword": "different"}, note="가입 비밀번호 불일치"),
        Step("anon", "POST", "/users/signup", data={"loginId": "bad id!", "name": "New", "email": "new@example.com", "password": "pass1234", "retypedPassword": "pass1234"}, note="가입 잘못된 아이디"),
        Step("anon", "POST", "/users/signup", data={"loginId": "admin", "name": "New", "email": "new2@example.com", "password": "pass1234", "retypedPassword": "pass1234"}, note="가입 예약어"),
        Step("anon", "POST", "/users/signup", data={"loginId": "newbie", "name": "New", "email": "new@example.com", "password": "pass1234", "retypedPassword": "pass1234"}, note="가입 성공"),
        Step("anon", "POST", "/lostPassword", data={"loginId": "nobody", "email": "x@y.z"}, note="비밀번호 분실 없는 사용자"),
        Step("anon", "POST", "/lostPassword", data={"loginId": "tester", "email": "wrong@example.com"}, note="비밀번호 분실 이메일 불일치"),
        Step("anon", "POST", "/lostPassword", data={"loginId": "tester", "email": "tester@example.com"}, note="비밀번호 분실 요청"),
        Step("anon", "POST", "/resetPassword", data={"loginId": "tester", "hash": "bad", "password": "new12345", "retypedPassword": "new12345"}, note="재설정 잘못된 해시"),
    ]

    # ---- 사용자 정보 ----
    s += [
        Step("anon", "POST", "/user/edit", data={"name": "x"}, note="정보 수정 익명"),
        Step("third", "POST", "/user/edit", data={"name": "", "email": ""}, note="정보 수정 빈 값"),
        Step("third", "POST", "/user/edit", data={"name": "Third Renamed", "email": "third@example.com"}, note="정보 수정"),
        Step("third", "POST", "/user/editform/password", data={"loginId": "third", "oldPassword": "bad", "password": "newpass12", "retypedPassword": "newpass12"}, note="비밀번호 변경 옛 비밀번호 틀림"),
        Step("third", "POST", "/user/editform/password", data={"loginId": "third", "oldPassword": "pass1234", "password": "short", "retypedPassword": "short"}, note="비밀번호 변경 짧음"),
        Step("third", "POST", "/user/editform/password", data={"loginId": "third", "oldPassword": "pass1234", "password": "newpass12", "retypedPassword": "mismatch"}, note="비밀번호 변경 불일치"),
        Step("third", "POST", "/user/editform/password", data={"loginId": "third", "oldPassword": "pass1234", "password": "pass1234", "retypedPassword": "pass1234"}, note="비밀번호 변경 같은 값"),
        Step("third", "POST", "/user/email", data={"email": ""}, note="이메일 추가 빈 값"),
        Step("third", "POST", "/user/email", data={"email": "tester@example.com"}, note="이메일 추가 남의 이메일"),
        Step("third", "POST", "/user/email", data={"email": "third2@example.com"}, note="이메일 추가"),
        Step("third", "POST", "/user/email/sendValidationEmail/1", note="인증 메일 남의 이메일"),
        Step("third", "PUT", "/user/email/setAsMain/1", note="대표 이메일 남의 것"),
        Step("third", "DELETE", "/user/email/delete/1", note="이메일 삭제 남의 것"),
        Step("third", "POST", "/user/resetVisitedList", note="최근 방문 지우기"),
        Step("anon", "POST", "/user/resetVisitedList", note="최근 방문 지우기 익명"),
        Step("third", "POST", "/user/editform/notifications", data={"NEW_ISSUE": "on"}, note="알림 설정"),
        Step("third", "POST", "/user/editform/token", note="토큰 재발급"),
        Step("third", "POST", "/user/editform/nope", note="없는 탭"),
        Step("third", "POST", "/-_-api/v1/user/defultLoginPage", data={"path": "/user/issues"}, note="기본 페이지"),
        Step("third", "POST", "/-_-api/v1/user/defultLoginPage", data={"path": ""}, note="기본 페이지 빈 값"),
    ]

    # ---- 프로젝트 만들기와 설정 ----
    pf = {"projectScope": "PUBLIC", "vcs": "GIT", "code": "true", "issue": "true", "pullRequest": "true", "review": "true", "milestone": "true", "board": "true"}
    s += [
        Step("anon", "POST", "/projects", data={**pf, "owner": "tester", "name": "x"}, note="프로젝트 익명"),
        Step("third", "POST", "/projects", data={**pf, "owner": "third", "name": ""}, note="프로젝트 이름 없음"),
        Step("third", "POST", "/projects", data={**pf, "owner": "third", "name": "bad name"}, note="프로젝트 잘못된 이름"),
        Step("third", "POST", "/projects", data={**pf, "owner": "third", "name": ".git"}, note="프로젝트 예약어"),
        Step("third", "POST", "/projects", data={**pf, "owner": "tester", "name": "stolen"}, note="남의 소유자로 프로젝트"),
        Step("third", "POST", "/projects", data={**pf, "owner": "third", "name": "t1", "projectScope": "BAD"}, note="프로젝트 잘못된 범위"),
        Step("third", "POST", "/projects", data={**pf, "owner": "third", "name": "t1", "vcs": "Mercurial"}, note="프로젝트 잘못된 저장소 종류"),
        Step("third", "POST", "/projects", data={**pf, "owner": "third", "name": "t1"}, note="프로젝트 성공"),
        Step("third", "POST", "/projects", data={**pf, "owner": "third", "name": "t1"}, note="프로젝트 중복"),
        Step("third", "POST", "/projects", data={**pf, "owner": "third", "name": "T1"}, note="프로젝트 대소문자 중복"),
        Step("third", "POST", "/projects", data={**pf, "owner": "third", "name": "t2", "projectScope": "PRIVATE", "vcs": "Subversion"}, note="비공개 svn 프로젝트"),
        Step("third", "POST", "/projects", data={**pf, "owner": "org1", "name": "orgproj"}, note="조직 프로젝트 구성원 아님"),
        Step("tester", "POST", "/projects", data={**pf, "owner": "org1", "name": "orgproj"}, note="조직 프로젝트"),
    ]
    s += sweep("PUT", B, "개요 수정", data={"overview": "새 개요"})
    menus = dict(code="true", issue="true", pullRequest="true", review="true", milestone="true", board="true")
    s += sweep("POST", f"{B}/setting", "설정", files=mpf(id="1", name="proj1", overview="설정 개요", projectScope="PUBLIC", isUsingReviewerCount="false", defaultReviewerCount="1", **menus))
    s += [
        Step("tester", "POST", f"{B}/setting", files=mpf(**{**{"id": "1", "name": "", "overview": "x", "projectScope": "PUBLIC"}, **menus}), note="설정 이름 없음"),
        Step("tester", "POST", f"{B}/setting", files=mpf(**{**{"id": "1", "name": "bad name!", "overview": "x", "projectScope": "PUBLIC"}, **menus}), note="설정 잘못된 이름"),
        Step("tester", "POST", f"{B}/setting", files=mpf(**{**{"id": "1", "name": "priv1", "overview": "x", "projectScope": "PUBLIC"}, **menus}), note="설정 중복 이름"),
        Step("tester", "POST", f"{B}/setting", files=mpf(**{**{"id": "1", "name": "proj1", "overview": "x" * 600, "projectScope": "PUBLIC"}, **menus}), note="설정 긴 개요"),
        Step("tester", "POST", f"{B}/setting", files=mpf(**{**{"id": "1", "name": "proj1", "overview": "x", "projectScope": "PUBLIC", "isUsingReviewerCount": "true", "defaultReviewerCount": "0"}, **menus}), note="리뷰어 0명"),
        Step("tester", "POST", f"{B}/setting", files=mpf(**{**{"id": "1", "name": "proj1", "overview": "x", "projectScope": "PUBLIC", "isUsingReviewerCount": "true", "defaultReviewerCount": "abc"}, **menus}), note="리뷰어 숫자 아님"),
    ]

    # ---- 구성원 ----
    s += sweep("POST", f"{B}/members", "구성원 추가", data={"loginId": "third"})
    s += [
        Step("tester", "POST", f"{B}/members", data={"loginId": ""}, note="구성원 빈 값"),
        Step("tester", "POST", f"{B}/members", data={"loginId": "nobody"}, note="구성원 없는 사용자"),
        Step("tester", "POST", f"{B}/members", data={"loginId": "tester"}, note="구성원 자기 자신"),
        Step("tester", "POST", f"{B}/members", data={"loginId": "other"}, note="구성원 이미 있음"),
        Step("tester", "POST", f"{B}/member/<<uid_other>>/edit", data={"id": "1"}, note="구성원 역할 번호 형식"),
        Step("tester", "POST", f"{B}/member/<<uid_other>>/edit", data={"id": "1"}, note="구성원 역할 변경"),
        Step("other", "POST", f"{B}/member/<<uid_other>>/edit", data={"id": "1"}, note="구성원 역할 변경 권한 없음"),
        Step("tester", "POST", f"{B}/member/<<uid_other>>/edit", data={"id": "99"}, note="구성원 알 수 없는 역할"),
        Step("tester", "POST", f"{B}/member/<<uid_other>>/edit", data={}, note="구성원 역할 번호 없음"),
        Step("tester", "POST", f"{B}/member/<<uid_other>>/edit", data={"id": "abc"}, note="구성원 역할 번호 글자"),
        Step("tester", "GET", f"{B}/members", note="구성원 목록 알 수 없는 역할 뒤"),
        Step("tester", "POST", f"{B}/member/<<uid_other>>/edit", data={"id": "2"}, note="구성원 역할 되돌리기"),
        Step("anon", "GET", "/nobody/nothing/mentionList", note="멘션 목록 없는 프로젝트"),
        Step("anon", "GET", "/nobody/nothing/mentionList?mentionType=user", note="멘션 목록 없는 프로젝트 user"),
        Step("anon", "GET", "/tester/priv1/mentionList?mentionType=user", note="멘션 목록 비공개 익명 user"),
        Step("anon", "GET", "/tester/priv1/mentionList?mentionType=issue&query=1", note="멘션 목록 비공개 익명 issue"),
        Step("tester", "DELETE", f"{B}/member/999/delete", note="구성원 삭제 없는 번호"),
        Step("third", "DELETE", f"{B}/member/<<uid_other>>/delete", note="구성원 삭제 권한 없음"),
        Step("tester", "DELETE", f"{B}/member/<<uid_other>>/delete", note="구성원 삭제"),
        Step("tester", "POST", f"{B}/members", data={"loginId": "third"}, note="구성원 다시 추가"),
        Step("other", "GET", "/info/leave/tester/proj1", note="탈퇴"),
    ]

    # ---- 마일스톤 ----
    s += sweep("POST", f"{B}/milestones", "마일스톤 만들기", data={"title": "권한시험", "contents": "x", "state": "OPEN", "dueDate": "2030-05-05"},
               capture={"ms": ("location", r"/milestone/(\d+)")})
    s += [
        Step("tester", "POST", f"{B}/milestones", data={"title": "", "contents": "x", "state": "OPEN"}, note="마일스톤 제목 없음"),
        Step("tester", "POST", f"{B}/milestones", data={"title": "마일스톤1", "contents": "x", "state": "OPEN"}, note="마일스톤 중복 제목"),
        Step("tester", "POST", f"{B}/milestones", data={"title": "날짜", "contents": "x", "state": "OPEN", "dueDate": "not-a-date"}, note="마일스톤 잘못된 날짜"),
        Step("tester", "POST", f"{B}/milestones", data={"title": "상태", "contents": "x", "state": "WRONG"}, note="마일스톤 잘못된 상태"),
        Step("tester", "POST", f"{B}/milestones", data={"title": "긴" * 300, "contents": "x", "state": "OPEN"}, note="마일스톤 긴 제목"),
        Step("tester", "POST", f"{B}/milestone/1/edit", data={"title": "마일스톤1 수정", "contents": "수정", "state": "OPEN", "dueDate": "2031-01-01"}, note="마일스톤 수정"),
        Step("tester", "POST", f"{B}/milestone/1/edit", data={"title": "마일스톤2", "contents": "수정", "state": "OPEN"}, note="마일스톤 수정 중복 제목"),
        Step("tester", "POST", f"{B}/milestone/99/edit", data={"title": "x", "contents": "x", "state": "OPEN"}, note="마일스톤 수정 없는 것"),
        Step("third", "POST", f"{B}/milestone/1/close", note="마일스톤 닫기 권한 없음"),
        Step("tester", "POST", f"{B}/milestone/1/close", note="마일스톤 닫기"),
        Step("tester", "POST", f"{B}/milestone/1/open", note="마일스톤 열기"),
        Step("tester", "POST", f"{B}/milestone/99/close", note="마일스톤 닫기 없는 것"),
        Step("tester", "DELETE", f"{B}/milestone/99/delete", note="마일스톤 삭제 없는 것"),
        Step("other", "DELETE", f"{B}/milestone/<<ms_tester>>/delete", note="마일스톤 삭제 구성원"),
        Step("tester", "DELETE", f"{B}/milestone/<<ms_tester>>/delete", note="마일스톤 삭제"),
    ]

    # ---- 이슈 ----
    s += sweep("POST", f"{B}/issues/latest", "이슈 만들기", files=mp(title="권한시험 이슈", body="본문", isDraft="false"))
    s += [
        Step("tester", "POST", f"{B}/issues/latest", files=mp(title="", body="본문", isDraft="false"), note="이슈 제목 없음"),
        Step("tester", "POST", f"{B}/issues/latest", files=mp(title="x" * 300, body="본문", isDraft="false"), note="이슈 긴 제목"),
        Step("tester", "POST", f"{B}/issues/latest", files=mp(title="날짜", body="본문", isDraft="false", dueDate="bad"), note="이슈 잘못된 날짜"),
        Step("tester", "POST", f"{B}/issues/latest", files=mp(title="담당자", body="본문", isDraft="false", assigneeLoginId="nobody"), note="이슈 없는 담당자"),
        Step("tester", "POST", f"{B}/issues/latest", files=mp(title="마일스톤", body="본문", isDraft="false", milestoneId="99"), note="이슈 없는 마일스톤"),
        Step("tester", "POST", f"{B}/issues/latest", files=mp(title="라벨", body="본문", isDraft="false", labelIds="<<lab_bug>>"), note="이슈 라벨"),
        Step("tester", "POST", f"{B}/issues/latest", files=mp(title="이동", body="본문", isDraft="false", targetProjectId="2"), note="이슈 다른 프로젝트로"),
        Step("tester", "POST", f"{B}/issues/latest", files=mp(title="상위", body="본문", isDraft="false", parentIssueId="1"), note="하위 이슈"),
        Step("tester", "POST", f"{B}/issues/latest", files=mp(title="공유", body="본문", isDraft="false", sharer="other"), note="이슈 공유자"),
        Step("tester", "POST", f"{B}/issue/1/edit", files=mp(title="첫 이슈 수정", body="내용 수정", isDraft="false", authorId="<<uid_tester>>", isPublish="false", notificationMail="false", state="OPEN", assigneeLoginId="other"), note="이슈 수정"),
        Step("other", "POST", f"{B}/issue/1/edit", files=mp(title="남이 수정", body="내용", isDraft="false", authorId="<<uid_tester>>", isPublish="false", notificationMail="false", state="OPEN", assigneeLoginId="other"), note="이슈 수정 구성원"),
        Step("third", "POST", f"{B}/issue/1/edit", files=mp(title="남이 수정", body="내용", isDraft="false", authorId="<<uid_tester>>", isPublish="false", notificationMail="false", state="OPEN", assigneeLoginId="other"), note="이슈 수정 권한 없음"),
        Step("tester", "POST", f"{B}/issue/1/edit", files=mp(title="", body="내용", isDraft="false", authorId="<<uid_tester>>", isPublish="false", notificationMail="false", state="OPEN", assigneeLoginId="other"), note="이슈 수정 제목 없음"),
        Step("tester", "POST", f"{B}/issue/99/edit", files=mp(title="x", body="x", isDraft="false", authorId="<<uid_tester>>", isPublish="false", notificationMail="false", state="OPEN", assigneeLoginId="other"), note="이슈 수정 없는 번호"),
        Step("tester", "POST", f"{B}/issue/3/edit", files=mp(title="초안 게시", body="초안", isDraft="false", authorId="<<uid_tester>>", isPublish="false", notificationMail="false", state="OPEN", assigneeLoginId="other"), note="초안 게시"),
    ]
    s += sweep("POST", f"{B}/issue/1/comments", "이슈 댓글", files=mp(contents="권한시험 댓글"), capture={"c1": ("location", r"#comment-(\d+)")})
    s += [
        Step("tester", "POST", f"{B}/issue/1/comments", files=mp(contents=""), note="댓글 빈 값"),
        Step("tester", "POST", f"{B}/issue/99/comments", files=mp(contents="x"), note="댓글 없는 이슈"),
        Step("tester", "POST", f"{B}/issue/1/comments", files=mp(contents="닫기", withStateTransition="close"), note="댓글과 닫기"),
        Step("tester", "POST", f"{B}/issue/1/comments", files=mp(contents="다시 열기", withStateTransition="open"), note="댓글과 열기"),
        Step("tester", "POST", f"{B}/issue/1/comments", files=mp(contents="@other @nobody 멘션", parentCommentId="<<c1_tester>>"), note="한 줄 댓글"),
        Step("tester", "POST", f"{B}/issue/1/comments/<<c1_tester>>", files=mp(contents="댓글 수정"), note="댓글 수정"),
        Step("other", "POST", f"{B}/issue/1/comments/<<c1_tester>>", files=mp(contents="남의 댓글 수정"), note="댓글 수정 남의 것"),
        Step("tester", "POST", f"{B}/issue/1/comments/999", files=mp(contents="x"), note="댓글 수정 없는 것"),
        Step("tester", "POST", f"{B}/issue/1/vote", note="이슈 추천"),
        Step("anon", "POST", f"{B}/issue/1/vote", note="이슈 추천 익명"),
        Step("tester", "POST", f"{B}/issue/1/comment/<<c1_tester>>/vote", note="댓글 추천"),
        Step("tester", "GET", f"{B}/issue/1/nextstate", note="다음 상태"),
        Step("tester", "POST", f"{B}/issues", data={"issues[0].id": "1", "issues[0].number": "1", "action": "state", "state": "closed"}, note="일괄 상태"),
        Step("tester", "POST", f"{B}/issues", data={"issues[0].id": "99"}, note="일괄 없는 이슈"),
        Step("tester", "POST", f"{B}/issues", data={}, note="일괄 빈 값"),
        Step("third", "POST", f"{B}/issues", data={"issues[0].id": "1", "state": "closed"}, note="일괄 권한 없음"),
        Step("other", "DELETE", f"{B}/issue/2/comment/999/delete", note="댓글 삭제 없는 것"),
        Step("tester", "DELETE", f"{B}/issue/1/comment/<<c1_tester>>/delete", note="댓글 삭제"),
        Step("third", "DELETE", f"{B}/issue/1/delete", note="이슈 삭제 권한 없음"),
        Step("tester", "DELETE", f"{B}/issue/99/delete", note="이슈 삭제 없는 것"),
    ]

    # ---- 라벨 ----
    s += [
        Step("tester", "POST", f"{B}/issue/labels", data={"categoryName": "", "labelName": "", "labelColor": ""}, note="라벨 빈 값"),
        Step("tester", "POST", f"{B}/issue/labels", data={"categoryName": "Type", "labelName": "bug", "labelColor": "#d9534f"}, note="라벨 중복"),
        Step("tester", "POST", f"{B}/issue/labels", data={"categoryName": "Type", "labelName": "x" * 300, "labelColor": "#d9534f"}, note="라벨 긴 이름"),
        Step("tester", "POST", f"{B}/issue/labels", data={"categoryName": "Type", "labelName": "badcolor", "labelColor": "red"}, note="라벨 잘못된 색"),
        Step("third", "POST", f"{B}/issue/labels", data={"categoryName": "Type", "labelName": "nope", "labelColor": "#fff"}, note="라벨 권한 없음"),
        Step("tester", "POST", f"{B}/issue/labels", data={"categoryName": "Type", "labelName": "docs", "labelColor": "#123456"}, note="라벨 만들기"),
        Step("tester", "PUT", f"{B}/issue/label/<<lab_bug>>", data={"categoryName": "Type", "labelName": "bug2", "labelColor": "#654321"}, note="라벨 수정"),
        Step("tester", "PUT", f"{B}/issue/label/99999", data={"categoryName": "Type", "labelName": "x", "labelColor": "#654321"}, note="라벨 수정 없는 것"),
        Step("tester", "POST", f"{B}/issue/label/categories", data={"name": "NewCat", "isExclusive": "true"}, note="분류 만들기"),
        Step("tester", "POST", f"{B}/issue/label/categories", data={"name": "NewCat", "isExclusive": "true"}, note="분류 중복"),
        Step("tester", "POST", f"{B}/issue/label/categories", data={"name": ""}, note="분류 빈 값"),
        Step("tester", "PUT", f"{B}/issue/label/category/<<cat_type>>", data={"name": "TypeRenamed", "isExclusive": "false"}, note="분류 수정"),
        Step("tester", "POST", f"{B}/copyLabels", data={"fromProjectId": "2"}, note="라벨 복사"),
        Step("tester", "POST", f"{B}/issue/label/<<lab_ui>>/delete", note="라벨 삭제"),
        Step("tester", "DELETE", f"{B}/issue/label/category/99999", note="분류 삭제 없는 것"),
        Step("third", "DELETE", f"{B}/issue/label/category/<<cat_area>>", note="분류 삭제 권한 없음"),
        Step("tester", "POST", f"{B}/labels", data={"label": "my-tag"}, note="프로젝트 태그 붙이기"),
        Step("tester", "POST", f"{B}/labels/1", note="프로젝트 태그 떼기"),
    ]

    # ---- 게시판 ----
    pb = dict(issueTemplate="", branch="", path="", lineEnding="UNDEFINED")
    s += sweep("POST", f"{B}/posts", "글쓰기", files=mp(title="권한시험 글", body="본문", **pb))
    s += [
        Step("tester", "POST", f"{B}/posts", files=mp(title="", body="본문", **pb), note="글 제목 없음"),
        Step("tester", "POST", f"{B}/posts", files=mp(title="본문없음", body="", **pb), note="글 본문 없음"),
        Step("tester", "POST", f"{B}/posts", files=mp(title="공지", body="공지", notice="on", **pb), note="공지 글"),
        Step("other", "POST", f"{B}/posts", files=mp(title="공지 시도", body="공지", notice="on", **pb), note="구성원의 공지 글"),
        Step("tester", "POST", f"{B}/posts", files=mp(title="파일로", body="# 문서", **{**pb, "path": "docs/new.md", "branch": "master"}), note="글을 파일로 커밋"),
        Step("tester", "POST", f"{B}/post/1/edit", files=mp(title="수정된 글", body="수정", **pb), note="글 수정"),
        Step("other", "POST", f"{B}/post/1/edit", files=mp(title="남이 수정", body="수정", **pb), note="글 수정 남의 것"),
        Step("tester", "POST", f"{B}/post/99/edit", files=mp(title="x", body="x", **pb), note="글 수정 없는 것"),
        Step("tester", "POST", f"{B}/post/1/comment", files=mp(contents="글 댓글 권한"), note="글 댓글"),
        Step("anon", "POST", f"{B}/post/1/comment", files=mp(contents="익명"), note="글 댓글 익명"),
        Step("tester", "POST", f"{B}/post/1/comment", files=mp(contents=""), note="글 댓글 빈 값"),
        Step("tester", "POST", f"{B}/post/1/comment/1", files=mp(contents="글 댓글 수정"), note="글 댓글 수정"),
        Step("other", "POST", f"{B}/post/1/comment/1", files=mp(contents="남의 글 댓글 수정"), note="글 댓글 수정 남의 것"),
        Step("tester", "DELETE", f"{B}/post/1/comment/1/delete", note="글 댓글 삭제"),
        Step("tester", "DELETE", f"{B}/post/1/comment/999/delete", note="글 댓글 삭제 없는 것"),
        Step("other", "DELETE", f"{B}/post/2/delete", note="글 삭제 본인"),
        Step("third", "DELETE", f"{B}/post/1/delete", note="글 삭제 권한 없음"),
    ]

    # ---- 웹훅, 구독, 즐겨찾기 ----
    s += [
        Step("tester", "POST", f"{B}/webhooks", data={"payloadUrl": "", "secret": "s"}, note="웹훅 빈 주소"),
        Step("tester", "POST", f"{B}/webhooks", data={"payloadUrl": "ftp://x", "secret": "s", "gitPush": "on"}, note="웹훅 잘못된 주소"),
        Step("tester", "POST", f"{B}/webhooks", data={"payloadUrl": "http://127.0.0.1:9/h2", "secret": "s2", "webhookType": "SLACK", "issues": "on"}, note="웹훅 만들기"),
        Step("tester", "GET", f"{B}/webhooks", capture={"wh": ("body", r"(?s).*webhooks/(\d+)")}, note="웹훅 번호 읽기"),
        Step("third", "POST", f"{B}/webhooks", data={"payloadUrl": "http://127.0.0.1:9/h3"}, note="웹훅 권한 없음"),
        Step("tester", "DELETE", f"{B}/webhooks/99", note="웹훅 삭제 없는 것"),
        Step("tester", "DELETE", f"{B}/webhooks/<<wh>>", note="웹훅 삭제"),
        Step("third", "POST", f"{B}/watch", note="프로젝트 구독"),
        Step("third", "POST", f"{B}/unwatch", note="프로젝트 구독 해제"),
        Step("anon", "POST", f"{B}/watch", note="프로젝트 구독 익명"),
        Step("third", "POST", "/watch?resource.type=issue_post&resource.id=1", note="이슈 구독"),
        Step("third", "POST", "/unwatch?resource.type=issue_post&resource.id=1", note="이슈 구독 해제"),
        Step("third", "POST", "/watch?resource.type=nothing&resource.id=1", note="구독 잘못된 종류"),
        Step("third", "POST", "/noti/toggle/1/NEW_ISSUE", note="알림 토글"),
        Step("third", "POST", "/noti/toggle/1/NOPE", note="알림 토글 잘못된 종류"),
        Step("third", "POST", "/-_-api/v1/favoriteProjects/1", note="프로젝트 즐겨찾기"),
        Step("third", "POST", "/-_-api/v1/favoriteProjects/99", note="프로젝트 즐겨찾기 없는 것"),
        Step("third", "POST", "/-_-api/v1/favoriteProjects/abc", note="프로젝트 즐겨찾기 숫자 아님"),
        Step("third", "POST", "/-_-api/v1/favoriteIssues/1", note="이슈 즐겨찾기"),
        Step("third", "POST", "/-_-api/v1/favoriteOrganizations/1", note="조직 즐겨찾기"),
    ]

    # ---- 조직 ----
    s += [
        Step("third", "POST", "/organizations/new", data={"name": "", "descr": ""}, note="조직 빈 값"),
        Step("third", "POST", "/organizations/new", data={"name": "org1", "descr": "중복"}, note="조직 중복"),
        Step("third", "POST", "/organizations/new", data={"name": "tester", "descr": "사용자와 같은 이름"}, note="조직 사용자 이름과 같음"),
        Step("third", "POST", "/organizations/new", data={"name": "bad name", "descr": "x"}, note="조직 잘못된 이름"),
        Step("third", "POST", "/organizations/new", data={"name": "org2", "descr": "두번째 조직"}, note="조직 만들기"),
        Step("anon", "POST", "/organizations/new", data={"name": "org3", "descr": "익명"}, note="조직 익명"),
        Step("third", "POST", "/organizations/org1/setting", files=mpf(**{"id": "1", "name": "org1", "descr": "권한 없음"}), note="조직 설정 권한 없음"),
        Step("tester", "POST", "/organizations/org1/setting", files=mpf(**{"id": "1", "name": "org1", "descr": "수정된 설명"}), note="조직 설정"),
        Step("tester", "POST", "/organizations/org1/setting", files=mpf(**{"id": "1", "name": "org2", "descr": "중복 이름"}), note="조직 설정 중복 이름"),
        Step("tester", "POST", "/organizations/org1/members", data={"loginId": "third"}, note="조직 구성원 추가"),
        Step("tester", "POST", "/organizations/org1/members", data={"loginId": "third"}, note="조직 구성원 다시 추가"),
        Step("tester", "POST", "/organizations/org1/members", data={"loginId": "nobody"}, note="조직 구성원 없는 사용자"),
        Step("third", "POST", "/organizations/org1/members", data={"loginId": "other"}, note="조직 구성원 추가 권한 없음"),
        Step("tester", "POST", "/organizations/org1/member/<<uid_third>>/edit", data={"id": "6"}, note="조직 구성원 역할"),
        Step("tester", "DELETE", "/organizations/org1/member/99/delete", note="조직 구성원 삭제 없는 것"),
        Step("third", "DELETE", "/organizations/org1/member/leave", note="조직 탈퇴"),
        Step("other", "POST", "/organizations/org2/enroll", note="조직 가입 신청"),
        Step("other", "POST", "/organizations/org2/cancel/enroll", note="조직 가입 취소"),
        Step("other", "POST", "/organizations/nope/enroll", note="조직 가입 없는 조직"),
    ]

    # ---- 첨부 ----
    s += [
        Step("tester", "POST", "/files", files={"filePath": ("a.txt", b"attachment text", "text/plain")}, capture={"att": ("body", r"/files/(\d+)")}, note="첨부 올리기"),
        Step("tester", "POST", "/files", files={"filePath": ("한글 파일.png", b"\x89PNG\r\n\x1a\nfake", "image/png")}, note="첨부 한글 이름"),
        Step("anon", "POST", "/files", files={"filePath": ("a.txt", b"x", "text/plain")}, note="첨부 익명"),
        Step("tester", "POST", "/files", note="첨부 파일 없음"),
        Step("tester", "GET", "/files", note="첨부 목록"),
        Step("anon", "GET", "/files/<<att>>", note="첨부 받기 익명"),
        Step("tester", "GET", "/files/<<att>>", note="첨부 받기"),
        Step("tester", "GET", "/files/<<att>>?action=download", note="첨부 내려받기"),
        Step("tester", "GET", "/files/999", note="첨부 없는 것"),
        Step("other", "POST", "/files/<<att>>", note="첨부 삭제 남의 것"),
        Step("tester", "POST", "/files/<<att>>", note="첨부 삭제"),
    ]

    # ---- 관리자 ----
    s += [
        Step("tester", "POST", "/sites/toggleAccountLock?loginId=third&state=ACTIVE", note="계정 잠금 권한 없음"),
        Step("admin", "POST", "/sites/toggleAccountLock?loginId=third&state=ACTIVE", note="계정 잠금"),
        Step("admin", "POST", "/sites/toggleAccountLock?loginId=third&state=LOCKED", note="계정 잠금 해제"),
        Step("admin", "POST", "/sites/toggleAccountLock?loginId=nobody&state=ACTIVE", note="계정 잠금 없는 사용자"),
        Step("admin", "POST", "/sites/toggleGuestMode?loginId=third&state=ACTIVE", note="게스트 전환"),
        Step("admin", "POST", "/sites/toggleGuestMode?loginId=third&state=GUEST", note="게스트 해제"),
        Step("admin", "POST", "/sites/toggleSiteAdminRole/third", note="관리자 지정"),
        Step("admin", "POST", "/sites/toggleSiteAdminRole/third", note="관리자 해제"),
        Step("admin", "POST", "/sites/toggleSiteAdminRole/admin", note="관리자 자기 해제"),
        Step("tester", "POST", "/sites/mail", data={"to": "x@y.z", "subject": "s", "body": "b"}, note="메일 권한 없음"),
        Step("admin", "POST", "/sites/mail", data={"to": "tester@example.com", "subject": "시험", "body": "본문"}, note="메일 보내기"),
        Step("admin", "POST", "/sites/mail", data={"to": "", "subject": "", "body": ""}, note="메일 빈 값"),
        Step("admin", "POST", "/sites/mailList", data={"loginIds": "tester,other"}, note="메일 대상 목록"),
        Step("admin", "POST", "/sites/setAttachmentToUserAvatar", data={"attachmentId": "99", "loginId": "tester"}, note="아바타 없는 첨부"),
    ]

    # ---- 위험한 요청(마지막) ----
    s += [
        Step("third", "PUT", "/tester/priv1/transfer", data={"destination": "third"}, note="이전 권한 없음"),
        Step("tester", "PUT", "/tester/prot1/transfer", data={"destination": "nobody"}, note="이전 없는 사용자"),
        Step("tester", "PUT", "/tester/prot1/transfer", data={"destination": "other"}, note="이전"),
        Step("third", "POST", "/tester/proj1/changeVCS", data={"vcs": "Subversion"}, note="저장소 종류 변경 권한 없음"),
        Step("third", "DELETE", "/tester/priv1/delete", note="프로젝트 삭제 권한 없음"),
        Step("tester", "DELETE", "/tester/priv1/delete", note="프로젝트 삭제"),
        Step("tester", "DELETE", "/tester/priv1/delete", note="프로젝트 삭제 이미 없음"),
        Step("tester", "DELETE", "/organizations/org1", note="조직 삭제"),
        Step("third", "DELETE", "/organizations/org2", note="조직 삭제 본인"),
        Step("admin", "DELETE", "/sites/project/delete/99", note="사이트 프로젝트 삭제 없는 것"),
        Step("admin", "DELETE", "/sites/user/delete99", note="사이트 사용자 삭제 없는 것"),
    ]
    return s
