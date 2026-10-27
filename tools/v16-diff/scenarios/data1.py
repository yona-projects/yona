"""사용자, 프로젝트, 마일스톤, 이슈, 댓글, 게시글, 조직을 만드는 흐름이다. 두 서버에 같은 순서로 보내므로 번호도 같다. 기준 상태(base)에서 한 번만 실행한다."""
from harness import Step


def mp(**fields):
    """파일 없이 멀티파트 본문을 만든다."""
    return {k: (None, str(v)) for k, v in fields.items()}


def login(actor, pw="pass1234"):
    return Step(actor, "POST", "/users/login", data={"loginIdOrEmail": actor, "password": pw}, note=f"{actor} 로그인")


def project(actor, owner, name, scope="PUBLIC", vcs="GIT"):
    return Step(actor, "POST", "/projects", data={"owner": owner, "name": name, "projectScope": scope, "vcs": vcs, "code": "true", "issue": "true",
                                                    "pullRequest": "true", "review": "true", "milestone": "true", "board": "true"}, note=f"프로젝트 {name}")


def steps():
    s = []
    for u in ("tester", "other", "third"):
        s.append(Step("anon", "POST", "/users/signup", data={"loginId": u, "name": u.capitalize(), "email": f"{u}@example.com", "password": "pass1234", "retypedPassword": "pass1234"}, note=f"가입 {u}"))
    s.append(login("admin", "admin1234"))
    s += [login("tester"), login("other"), login("third")]
    s += [
        project("tester", "tester", "proj1"),
        project("tester", "tester", "priv1", "PRIVATE"),
        project("tester", "tester", "prot1", "PROTECTED"),
        project("tester", "tester", "svn1", "PUBLIC", "Subversion"),
        project("other", "other", "other1"),
        Step("tester", "POST", "/organizations/new", data={"name": "org1", "descr": "첫 조직"}, note="조직"),
        Step("tester", "POST", "/organizations/org1/members", data={"loginId": "other"}, note="조직 구성원 추가"),
        Step("tester", "POST", "/tester/proj1/members", data={"loginId": "other"}, note="프로젝트 구성원 추가"),
        Step("tester", "POST", "/tester/priv1/members", data={"loginId": "other"}, note="비공개 구성원"),
        Step("tester", "POST", "/tester/proj1/milestones", data={"title": "마일스톤1", "contents": "첫 마일스톤", "state": "OPEN", "dueDate": "2030-01-01"}, note="마일스톤1"),
        Step("tester", "POST", "/tester/proj1/milestones", data={"title": "마일스톤2", "contents": "닫을 것", "state": "OPEN", "dueDate": "2031-02-03"}, note="마일스톤2"),
        Step("tester", "POST", "/tester/proj1/milestone/2/close", note="마일스톤 닫기"),
        Step("tester", "POST", "/tester/proj1/issue/labels", data={"category": "Type", "name": "bug", "color": "#d9534f"}, note="라벨 bug"),
        Step("tester", "POST", "/tester/proj1/issue/labels", data={"category": "Type", "name": "feature", "color": "#5cb85c"}, note="라벨 feature"),
        Step("tester", "POST", "/tester/proj1/issue/labels", data={"category": "Area", "name": "ui", "color": "#0275d8"}, note="라벨 ui"),
        # 이슈
        Step("tester", "POST", "/tester/proj1/issues/latest", files=mp(title="첫 이슈", body="내용입니다 @other 확인 부탁", assigneeLoginId="other", isDraft="false", dueDate=""), note="이슈1"),
        Step("other", "POST", "/tester/proj1/issues/latest", files=mp(title="[버그] 두번째 이슈", body="**굵게** 그리고 `코드`\n\n- 목록1\n- 목록2\n\n#1 참조", isDraft="false", milestoneId="1"), note="이슈2 머리말"),
        Step("tester", "POST", "/tester/proj1/issues/latest", files=mp(title="초안 이슈", body="초안", isDraft="true"), note="초안"),
        Step("tester", "POST", "/tester/priv1/issues/latest", files=mp(title="비공개 이슈", body="비공개", isDraft="false"), note="비공개 이슈"),
        Step("tester", "POST", "/tester/proj1/issue/1/comments", files=mp(contents="첫 댓글 @other"), note="댓글1"),
        Step("other", "POST", "/tester/proj1/issue/1/comments", files=mp(contents="답글입니다"), note="댓글2"),
        Step("other", "POST", "/tester/proj1/issue/1/comments", files=mp(contents="닫습니다", withStateTransition="close"), note="상태 전환 댓글"),
        Step("tester", "GET", "/tester/proj1/issue/2/nextstate", note="다음 상태"),
        Step("tester", "POST", "/tester/proj1/issue/1/vote", note="추천"),
        Step("other", "POST", "/tester/proj1/issue/1/vote", note="추천 other"),
        Step("tester", "POST", "/tester/proj1/issue/1/comment/1/vote", note="댓글 추천"),
        Step("other", "POST", "/watch?resource.type=project&resource.id=1", note="구독"),
        Step("other", "POST", "/tester/proj1/watch", note="프로젝트 구독"),
        Step("tester", "POST", "/tester/proj1/unwatch", note="프로젝트 구독 해제"),
        # 게시판
        Step("tester", "POST", "/tester/proj1/posts", files=mp(title="공지입니다", body="게시글 본문 @other", notice="on", issueTemplate="", branch="", path="", lineEnding="UNDEFINED"), note="공지 글"),
        Step("other", "POST", "/tester/proj1/posts", files=mp(title="일반 글", body="내용", issueTemplate="", branch="", path="", lineEnding="UNDEFINED"), note="일반 글"),
        Step("other", "POST", "/tester/proj1/post/1/comment", files=mp(contents="글 댓글"), note="글 댓글"),
        Step("tester", "POST", "/tester/proj1/post/1/comment", files=mp(contents="두번째 글 댓글"), note="글 댓글2"),
        # 설정
        Step("tester", "POST", "/tester/proj1/webhooks", data={"payloadUrl": "http://127.0.0.1:9/hook", "secret": "s", "webhookType": "SIMPLE", "gitPush": "on"}, note="웹훅"),
        Step("tester", "PUT", "/tester/proj1", data={"overview": "수정한 개요"}, note="개요 수정"),
        Step("tester", "POST", "/noti/toggle/1/NEW_ISSUE", note="알림 설정"),
    ]
    return s
