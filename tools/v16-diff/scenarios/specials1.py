"""특수 문자(HTML, 따옴표, 역슬래시, 퍼센트, 해시, 물음표, 세미콜론, 이모지, 한글)가 든 데이터를 여러 곳에 만들고 그 데이터가 나오는 모든 화면을 읽는다.
화면마다 자바스크립트 속 값, 속성 값, 본문 글, 주소의 이스케이프가 v1.6과 같은지 본다. data2 상태에서 한 번만 실행한다(`mkspecial.py`로 저장소를 먼저 올린다)."""
import urllib.parse

from harness import Step
from data1 import login, mp

SP = '<b>X</b> & "q" \'s\' \\ / % + # ? ; : 😀 한글 <script>alert(1)</script>'
SP_SHORT = 'a<i>&"\'\\%+#?;😀'
B = "/tester/sp1"
ACTORS = ["anon", "tester", "other"]


def q(s):
    return urllib.parse.quote(s, safe="")


def steps():
    s = [login("tester"), login("other"), login("admin", "admin1234")]
    s.append(Step("admin", "POST", "/sites/unwatchUpdate", note="업데이트 알림 숨기기"))
    # 사용자 이름, 프로젝트, 설정
    s += [
        Step("tester", "POST", "/user/edit", data={"name": SP_SHORT, "email": "tester@example.com", "oldPassword": "", "password": "", "retypedPassword": ""}, note="이름에 특수 문자"),
        Step("anon", "POST", "/projects", data={"owner": "tester", "name": "sp1", "projectScope": "PUBLIC", "vcs": "GIT", "code": "true", "issue": "true", "pullRequest": "true", "review": "true", "milestone": "true", "board": "true"}, note="프로젝트(익명)"),
        Step("tester", "POST", "/projects", data={"owner": "tester", "name": "sp1", "projectScope": "PUBLIC", "vcs": "GIT", "code": "true", "issue": "true", "pullRequest": "true", "review": "true", "milestone": "true", "board": "true"}, note="프로젝트 만들기"),
        Step("tester", "GET", f"{B}/settingform", capture={"pid": ("body", r'name="id" value="(\d+)"')}, note="프로젝트 번호 읽기"),
        Step("tester", "POST", f"{B}/setting", files={**mp(id="<<pid>>", name="sp1", overview=SP, projectScope="PUBLIC", isUsingReviewerCount="false", defaultReviewerCount="1", code="true", issue="true", pullRequest="true", review="true", milestone="true", board="true"),
                                                      "logoPath": ("", b"", "application/octet-stream")}, note="설정에 개요 특수 문자"),
    ]
    # 라벨, 마일스톤, 이슈, 글, 댓글
    s += [
        Step("tester", "POST", f"{B}/issue/labels", data={"categoryName": SP_SHORT, "labelName": SP_SHORT, "labelColor": "#d9534f"}, capture={"lab": ("body", r'"id":"(\d+)"')}, note="라벨 특수 문자"),
        Step("tester", "POST", f"{B}/milestones", data={"title": SP, "contents": SP, "state": "OPEN", "dueDate": "2030-01-01"}, capture={"ms": ("location", r"/milestone/(\d+)")}, note="마일스톤 특수 문자"),
        Step("tester", "POST", f"{B}/issues/latest", files=mp(title=SP, body=SP + "\n\n[링크](http://example.org/?a=1&b=2) `코드 <i>` ```\n<b>펜스</b>\n```", assigneeLoginId="other", isDraft="false", dueDate="", labelIds="<<lab>>", milestoneId="<<ms>>"), note="이슈 특수 문자"),
        Step("tester", "POST", f"{B}/issues/latest", files=mp(title="[" + SP_SHORT + "] 머리말 제목", body="두번째", isDraft="false", dueDate=""), note="이슈 머리말 특수 문자"),
        Step("other", "POST", f"{B}/issue/1/comments", files=mp(contents=SP), note="댓글 특수 문자"),
        Step("tester", "POST", f"{B}/posts", files=mp(title=SP, body=SP, issueTemplate="", branch="", path="", lineEnding="UNDEFINED"), note="글 특수 문자"),
        Step("other", "POST", f"{B}/post/1/comment", files=mp(contents=SP), note="글 댓글 특수 문자"),
    ]
    # 읽기: 프로젝트와 모든 화면
    pages = [
        "", "/issues", "/issues?state=all", f"/issues?filter={q(SP_SHORT)}", f"/issues?labelIds=<<lab>>", "/issues?milestoneId=<<ms>>", "/issue/1", "/issue/2", "/issue/1/editform" if False else "/issue/1",
        "/newIssueForm", "/posts", f"/posts?filter={q(SP_SHORT)}", "/post/1", "/post/1/editform", "/milestones", "/milestone/<<ms>>", "/milestone/<<ms>>/editform", "/issue/labels", "/settingform", "/members", "/webhooks",
        "/code", "/commits", "/branches", "/statistics", "/watchers", "/search?keyword=" + q(SP_SHORT), "/pullRequests", "/newPullRequestForm", "/reviews",
    ]
    for a in ACTORS:
        for p in pages:
            s.append(Step(a, "GET", B + p, note=f"{p or '홈'} [{a}]"))
        for p in ("/tester", "/tester?selected=issues", "/search?keyword=" + q(SP_SHORT), "/search?searchType=issue&keyword=" + q(SP_SHORT), "/search?searchType=post&keyword=" + q(SP_SHORT),
                  "/search?searchType=milestone&keyword=" + q(SP_SHORT), "/search?searchType=user&keyword=" + q(SP_SHORT), "/user/issues", "/notifications", "/", "/user/usermenuTabContentList",
                  "/user/editform", "/projects?filter=sp"):
            s.append(Step(a, "GET", p, note=f"{p[:60]} [{a}]"))
    # 코드 화면: 가지, 태그, 파일 이름에 특수 문자 (mkspecial.py가 올린 저장소)
    for branch in ("master", q('br<b>&"\'#%+;x')):
        for path in ("", "/" + q("dir <b>&'\"%+#;") , "/" + q("dir <b>&'\"%+#;") + "/" + q("file <i>&'\".txt")):
            s.append(Step("anon", "GET", f"{B}/code/{branch}{path}", note=f"코드 {branch[:20]}{path[:30]}"))
    s += [
        Step("anon", "GET", f"{B}/rawcode/master/" + q("dir <b>&'\"%+#;") + "/" + q("file <i>&'\".txt"), note="원본 특수 문자 파일"),
        Step("anon", "GET", f"{B}/commits/master/" + q("dir <b>&'\"%+#;") + "/" + q("file <i>&'\".txt"), note="파일 이력 특수 문자"),
        Step("anon", "GET", f"{B}/compare/master..{q('br<b>&\"\'#%+;x')}", note="비교 특수 문자 가지"),
        Step("anon", "GET", f"{B}/code/{q('tag<b>&\"\'#%+;x')}", note="태그 특수 문자"),
        Step("tester", "GET", f"{B}/branches", note="가지 [tester]"),
    ]
    return s
