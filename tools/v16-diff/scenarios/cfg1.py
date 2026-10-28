"""설정 값에 따라 달라지는 화면과 동작을 읽는다(설정은 `cfg.sh`가 두 서버에 같은 뜻으로 넣는다). data2 상태에서 실행한다.
익명, 프로젝트 소유자(`tester`), 관리자가 공개 화면, 프로젝트·조직·사용자 화면, 가입과 로그인, 프로젝트 만들기 폼, 외부 링크가 든 이슈를 읽는다."""
from harness import Step
from data1 import login, mp

PAGES = [
    "/", "/projects", "/orgs", "/users/loginform", "/users/signupform", "/lostPassword", "/tester", "/tester/proj1", "/tester/proj1/issues", "/tester/proj1/issue/1",
    "/tester/proj1/posts", "/tester/proj1/post/1", "/tester/proj1/code", "/tester/priv1", "/org1", "/search?keyword=proj1", "/-_-api/v1/hello", "/_help", "/restricted",
    "/projectform", "/orgs/new", "/user/editform", "/user/issues", "/notifications", "/sites/setting", "/sites/userList", "/sites/mailList", "/migration", "/migration/projects",
    "/tester/proj1/settingform", "/tester/proj1/members", "/user/editform/projects", "/user/usermenuTabContentList", "/files",
]
LINK = "[외부 링크](http://example.org/page) 와 <http://example.net> 그리고 [내부](/tester/proj1)"


def steps():
    s = [login("tester"), login("admin", "admin1234")]
    for a in ("anon", "tester", "admin"):
        for p in PAGES:
            s.append(Step(a, "GET", p, note=f"{p} [{a}]"))
    # 가입과 로그인
    s += [
        Step("anon", "POST", "/users/signup", data={"loginId": "cfguser", "name": "Cfg", "email": "cfg@example.com", "password": "pass1234", "retypedPassword": "pass1234"}, note="가입"),
        Step("anon", "POST", "/users/login", data={"loginIdOrEmail": "cfguser", "password": "pass1234"}, note="새 사용자 로그인"),
        Step("anon", "POST", "/users/login", data={"loginIdOrEmail": "tester", "password": "pass1234"}, note="기존 사용자 로그인 시도"),
        Step("admin", "GET", "/sites/userList", note="가입 뒤 사용자 목록"),
        Step("admin", "GET", "/cfguser", note="가입한 사용자 화면"),
        Step("anon", "GET", "/users/loginform", note="로그인 화면 다시"),
    ]
    # 외부 링크가 든 이슈와 글
    s += [
        Step("tester", "POST", "/tester/proj1/issues", files=mp(title="설정 링크 이슈", body=LINK, state="OPEN"), capture={"iss": ("location", r"/issue/(\d+)")}, note="링크 이슈"),
        Step("tester", "POST", "/tester/proj1/posts", files=mp(title="설정 링크 글", body=LINK, issueTemplate="", branch="", path="", lineEnding="UNDEFINED"), capture={"pst": ("location", r"/post/(\d+)")}, note="링크 글"),
        Step("anon", "GET", "/tester/proj1/issue/<<iss>>", note="링크 이슈 보기 [anon]"),
        Step("tester", "GET", "/tester/proj1/issue/<<iss>>", note="링크 이슈 보기 [tester]"),
        Step("anon", "GET", "/tester/proj1/post/<<pst>>", note="링크 글 보기 [anon]"),
        Step("tester", "POST", "/projects", data={"owner": "tester", "name": "cfgproj", "projectScope": "PUBLIC", "vcs": "GIT"}, note="프로젝트 만들기(메뉴 기본 값)"),
        Step("tester", "GET", "/tester/cfgproj", note="만든 프로젝트 화면"),
        Step("tester", "GET", "/tester/cfgproj/settingform", note="만든 프로젝트 설정"),
    ]
    # 첨부 크기 한도
    s += [
        Step("tester", "POST", "/files", files={"filePath": ("big.txt", b"x" * 1500, "text/plain")}, note="1500바이트 첨부"),
    ]
    return s
