"""(bulk1을 키운 것) 이슈 300개, 글 180개, 댓글 수백 개를 만들고 목록의 쪽 나누기, 정렬, 필터, 검색, 통계, 마일스톤, 사용자 화면을 여러 조건으로 읽는다.
data2 상태에서 한 번만 실행한다(데이터를 많이 만든다). 사용자 번호는 `<<uid_tester>>` 같은 값으로 쓴다."""
from harness import Step
from data1 import login, project

NORMALIZE_NUMERIC_KEYS = True
B = "/tester/proj1"
ACTORS = ["anon", "tester", "other", "admin"]
TITLES = ["버그 수정", "[기능] 추가", "문서 작성", "성능 개선", "테스트 보강", "리팩터링", "UI 개선", "보안 점검"]


def mpl(*pairs):
    """같은 이름의 값을 여러 번 보내는 멀티파트 본문이다."""
    return [(k, (None, str(v))) for k, v in pairs]


def issue_step(i):
    actor = ("tester", "other", "third")[i % 3]
    pairs = [("title", f"{TITLES[i % len(TITLES)]} {i}"), ("body", f"본문 {i}\n\n" + ("@tester 확인\n" if i % 4 == 0 else "") + ("@other 의견\n" if i % 5 == 0 else "") + "끝"), ("isDraft", "true" if i % 17 == 0 else "false")]
    if i % 3 == 0:
        pairs.append(("assigneeLoginId", "other"))
    elif i % 3 == 1:
        pairs.append(("assigneeLoginId", "tester"))
    if i % 2 == 0:
        pairs.append(("milestoneId", "1"))
    if i % 6 == 0:
        pairs.append(("dueDate", f"2030-0{(i % 9) + 1}-1{i % 9}"))
    for label, mod in (("<<lab_bug>>", 2), ("<<lab_feature>>", 3), ("<<lab_ui>>", 5)):
        if i % mod == 0:
            pairs.append(("labelIds", label))
    return Step(actor, "POST", f"{B}/issues/latest", files=mpl(*pairs), note=f"이슈 {i}")


def steps():
    s = []
    s += [login("tester"), login("other"), login("third"), login("admin", "admin1234")]
    s.append(Step("admin", "POST", "/sites/unwatchUpdate", note="업데이트 알림 숨기기"))
    # v1.6(Ebean, H2)는 시퀀스 번호를 20개씩 미리 받는데 두 번째 묶음이 뒤섞여 나온다(33..40 다음에 21..32). 이슈, 댓글, 글의 번호가 만든 순서와 달라져
    # 번호순 목록이 v2와 어긋나므로, 버리는 프로젝트에 항목을 먼저 만들어 번호를 소진한 뒤 프로젝트를 지운다
    s.append(project("tester", "tester", "burn1", "PRIVATE"))
    for i in range(1, 61):
        s.append(Step("tester", "POST", "/tester/burn1/issues/latest", files=mpl(("title", f"소진 {i}"), ("body", "x"), ("isDraft", "false")), note=f"소진 이슈 {i}"))
    for i in range(1, 61):
        s.append(Step("tester", "POST", "/tester/burn1/issue/1/comments", files=mpl(("contents", f"소진 댓글 {i}")), note=f"소진 댓글 {i}"))
    for i in range(1, 61):
        s.append(Step("tester", "POST", "/tester/burn1/posts", files=mpl(("title", f"소진 글 {i}"), ("body", "x"), ("issueTemplate", ""), ("branch", ""), ("path", ""), ("lineEnding", "UNDEFINED")), note=f"소진 글 {i}"))
    for i in range(1, 21):
        s.append(Step("tester", "POST", "/tester/burn1/post/1/comment", files=mpl(("contents", f"소진 글 댓글 {i}")), note=f"소진 글 댓글 {i}"))
    s.append(Step("tester", "DELETE", "/tester/burn1/delete", note="소진 프로젝트 삭제"))
    s.append(Step("tester", "GET", f"{B}/issue/labels", headers={"Accept": "application/json"}, capture={
        "lab_bug": ("jsonfind", "name=bug:id"), "lab_feature": ("jsonfind", "name=feature:id"), "lab_ui": ("jsonfind", "name=ui:id")}, note="라벨 번호 읽기"))
    # 만들기: 이슈 4번부터 43번까지
    for i in range(1, 301):
        s.append(issue_step(i))
    for i in range(1, 301):
        n = 3 + i  # 기존 이슈 3개(초안 포함) 뒤의 번호
        if i % 3 == 0:
            s.append(Step(("other", "tester", "third")[i % 3], "POST", f"{B}/issue/{n}/comments", files=mpl(("contents", f"댓글 {i}-1 @tester")), note=f"댓글 {i}-1"))
        if i % 4 == 0:
            s.append(Step("tester", "POST", f"{B}/issue/{n}/comments", files=mpl(("contents", f"댓글 {i}-2")), note=f"댓글 {i}-2"))
            s.append(Step("other", "POST", f"{B}/issue/{n}/comments", files=mpl(("contents", f"댓글 {i}-3 `코드`")), note=f"댓글 {i}-3"))
        if i % 7 == 0:
            s.append(Step("tester", "POST", f"{B}/issue/{n}/comments", files=mpl(("contents", "닫습니다"), ("withStateTransition", "close")), note=f"닫기 {i}"))
        if i % 9 == 0:
            s.append(Step("other", "POST", f"{B}/issue/{n}/vote", note=f"추천 {i}"))
    for i in range(1, 181):
        actor = ("tester", "other", "third")[i % 3]
        extra = [("notice", "on")] if i % 11 == 0 else []
        s.append(Step(actor, "POST", f"{B}/posts", files=mpl(("title", f"글 {i} {TITLES[i % len(TITLES)]}"), ("body", f"글 본문 {i}"), ("issueTemplate", ""), ("branch", ""), ("path", ""), ("lineEnding", "UNDEFINED"), *extra), note=f"글 {i}"))
    # 읽기
    tester, other = "<<uid_tester>>", "<<uid_other>>"
    issue_urls = [f"{B}/issues"] + [f"{B}/issues?pageNum={n}" for n in (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14)] + [
        f"{B}/issues?state=all", f"{B}/issues?state=all&pageNum=2", f"{B}/issues?state=closed", f"{B}/issues?state=closed&pageNum=2", f"{B}/issues?state=open&pageNum=3",
        f"{B}/issues?orderBy=createdDate&orderDir=asc", f"{B}/issues?orderBy=numOfComments&orderDir=desc", f"{B}/issues?orderBy=updatedDate&orderDir=desc&pageNum=2",
        f"{B}/issues?orderBy=dueDate&orderDir=asc", f"{B}/issues?orderBy=weight&orderDir=desc", f"{B}/issues?orderBy=id&orderDir=asc&state=all",
        f"{B}/issues?filter=버그", f"{B}/issues?filter=기능&state=all", f"{B}/issues?filter=없는검색어", f"{B}/issues?filter=%EB%AC%B8%EC%84%9C&pageNum=1",
        f"{B}/issues?milestoneId=1", f"{B}/issues?milestoneId=1&state=all&pageNum=2", f"{B}/issues?milestoneId=none", f"{B}/issues?milestoneId=99",
        f"{B}/issues?assigneeId={tester}", f"{B}/issues?assigneeId={other}&state=all", f"{B}/issues?assigneeId=0", f"{B}/issues?authorId={tester}", f"{B}/issues?authorId={other}&pageNum=2",
        f"{B}/issues?commenterId={tester}", f"{B}/issues?commenterId={other}&state=all", f"{B}/issues?mentionId={tester}", f"{B}/issues?commentedCheck=true", f"{B}/issues?commentedCheck=false",
        f"{B}/issues?labelIds=<<lab_bug>>", f"{B}/issues?labelIds=<<lab_bug>>&labelIds=<<lab_feature>>", f"{B}/issues?labelIds=<<lab_ui>>&state=all", f"{B}/issues?labelIds=99999",
        f"{B}/issues?format=json", f"{B}/issues?format=json&pageNum=2", f"{B}/issues?format=json&state=all&filter=버그", f"{B}/issues?format=xls", f"{B}/issues?pageNum=0", f"{B}/issues?pageNum=-1", f"{B}/issues?pageNum=999",
        f"{B}/issues?pageNum=abc", f"{B}/issues?orderBy=nonexistent", f"{B}/issues?orderDir=sideways", f"{B}/issues?state=bogus", f"{B}/issues/excel", f"{B}/issues/excel?state=all",
        f"{B}/issue/10", f"{B}/issue/20", f"{B}/issue/100", f"{B}/issue/250", f"{B}/issue/303", f"{B}/issue/304", f"{B}/issue/3",
    ]
    post_urls = [f"{B}/posts"] + [f"{B}/posts?pageNum={n}" for n in (1, 2, 3, 4, 5, 6, 7, 8, 9)] + [
        f"{B}/posts?filter=글", f"{B}/posts?filter=성능&pageNum=1", f"{B}/posts?orderBy=createdDate&orderDir=asc", f"{B}/posts?orderBy=numOfComments&orderDir=desc", f"{B}/posts?pageNum=999",
        f"{B}/post/10", f"{B}/post/100", f"{B}/post/180", f"{B}/post/181",
    ]
    search_urls = []
    for kind in ("issue", "post", "user", "project", "milestone", "issue_comment", "post_comment", "review", "auto", "bogus"):
        for word in ("버그", "글", "tester", "proj1", "마일스톤", "댓글", "없는"):
            search_urls.append(f"/search?searchType={kind}&keyword={word}")
    search_urls += ["/search", "/search?keyword=버그", "/search?searchType=issue", "/search?searchType=issue&keyword=버그&pageNum=2", "/search?searchType=issue&keyword=%EB%B2%84%EA%B7%B8&pageNum=999",
                    "/search?searchType=issue&keyword=%25", "/search?searchType=issue&keyword=_"]
    for kind in ("issue", "post", "milestone", "issue_comment", "post_comment", "auto"):
        search_urls += [f"{B}/search?searchType={kind}&keyword=버그", f"{B}/search?searchType={kind}&keyword=글&pageNum=2", f"/tester/priv1/search?searchType={kind}&keyword=비공개"]
    search_urls += [f"/organizations/org1/search?searchType={k}&keyword=버그" for k in ("issue", "post", "user", "project", "auto")]
    other_urls = [
        f"{B}/statistics", f"{B}/statistics?branch=master", f"{B}/milestones", f"{B}/milestones?state=all", f"{B}/milestones?state=closed", f"{B}/milestones?orderBy=dueDate",
        f"{B}/milestone/1", f"{B}/milestone/2", f"{B}/milestone/1?state=all", f"{B}/milestone/1?state=closed&pageNum=2",
        "/tester", "/tester?daysAgo=1", "/tester?daysAgo=30", "/tester?selected=issues", "/tester?selected=posts", "/tester?selected=comments", "/tester?selected=bogus", "/tester?daysAgo=-1",
        "/other", "/other?selected=issues", "/user/issues", "/user/issues?state=all", "/user/issues?state=closed&pageNum=2", "/user/issues?format=json", "/user/issues?pageNum=2",
        "/projects", "/projects?pageNum=2", "/projects?filter=proj", "/projects?sort=name", "/orgs", "/users?query=t", "/", "/notifications",
    ]
    for actor in ACTORS:
        for u in issue_urls + post_urls + search_urls + other_urls:
            s.append(Step(actor, "GET", u, note=f"{u} [{actor}]"))
    return s
