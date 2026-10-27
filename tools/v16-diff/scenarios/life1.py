"""프로젝트와 조직의 이름 바꾸기, 이전, 삭제 뒤에 옛 주소가 어떻게 응답하는지 본다. 이전 요청 메일의 확인 링크는 SMTP 수신기에서 읽는다.
data3 상태에서 한 번만 실행하며 mail 수신기가 켜져 있어야 한다(servers.sh start)."""
from harness import Step
from data1 import login, mp
from forms1 import mpf

ACTORS = ["anon", "tester", "other", "third", "admin"]
MENUS = dict(code="true", issue="true", pullRequest="true", review="true", milestone="true", board="true")
OLD = ["/tester/proj1", "/tester/proj1/issues", "/tester/proj1/issue/1", "/tester/proj1/code/master/README.md", "/tester/proj1/commits", "/tester/proj1/pullRequests",
       "/tester/proj1/posts", "/tester/proj1/milestones", "/tester/proj1/info/refs?service=git-upload-pack", "/-_-api/v1/owners/tester/projects/proj1/exports",
       "/tester/proj1x", "/tester/proj1x/issues", "/tester/proj1x/code/master/README.md"]


def sweep(paths, label):
    return [Step(a, "GET", p, note=f"{label} {p} [{a}]") for p in paths for a in ACTORS]


def steps():
    s = []
    s += [login("tester"), login("other"), login("third"), login("admin", "admin1234")]
    s.append(Step("admin", "POST", "/sites/unwatchUpdate", note="업데이트 알림 숨기기"))
    # 이름 바꾸기
    s += [
        Step("tester", "POST", "/tester/proj1/setting", files=mpf(id="1", name="proj1x", overview="이름 바꿈", projectScope="PUBLIC", isUsingReviewerCount="false", defaultReviewerCount="1", **MENUS), note="이름 바꾸기"),
        Step("tester", "POST", "/tester/proj1x/setting", files=mpf(id="1", name="proj1y", overview="또 바꿈", projectScope="PUBLIC", isUsingReviewerCount="false", defaultReviewerCount="1", **MENUS), note="이름 또 바꾸기"),
        Step("tester", "POST", "/tester/proj1y/setting", files=mpf(id="1", name="proj1x", overview="되돌림", projectScope="PUBLIC", isUsingReviewerCount="false", defaultReviewerCount="1", **MENUS), note="이름 되돌리기"),
    ]
    s += sweep(OLD, "이름 바꾼 뒤")
    s.append(Step("anon", "GET", "/tester/proj1y", note="중간 이름"))
    # 이전
    s += [
        Step("third", "PUT", "/tester/prot1/transfer?owner=third", note="이전 권한 없음"),
        Step("tester", "PUT", "/tester/prot1/transfer?owner=nobody", note="이전 없는 대상"),
        Step("tester", "PUT", "/tester/prot1/transfer?owner=tester", note="이전 같은 소유자"),
        Step("tester", "PUT", "/tester/prot1/transfer?owner=other", note="이전 요청"),
        Step("tester", "PUT", "/tester/prot1/transfer?owner=other", note="이전 다시 요청"),
        Step("tester", "PUT", "/tester/priv1/transfer?owner=org1", note="조직으로 이전 요청", delay=0),
        Step("tester", "GET", "/tester/prot1", delay=6, note="이전 요청 뒤",
             capture={"tid": ("mail", r"/project/transfer/(\d+)/"), "tkey": ("mail", r"/project/transfer/\d+/(\w+)")}),
        Step("anon", "GET", "/project/transfer/<<tid>>/<<tkey>>", note="이전 수락 익명"),
        Step("third", "GET", "/project/transfer/<<tid>>/<<tkey>>", note="이전 수락 권한 없음"),
        Step("other", "GET", "/project/transfer/<<tid>>/wrongkey", note="이전 수락 틀린 키"),
        Step("other", "GET", "/project/transfer/99999/x", note="이전 수락 없는 번호"),
        Step("other", "GET", "/project/transfer/<<tid>>/<<tkey>>", note="이전 수락"),
        Step("other", "GET", "/project/transfer/<<tid>>/<<tkey>>", note="이전 수락 다시"),
    ]
    s += sweep(["/tester/prot1", "/tester/prot1/issues", "/other/prot1", "/other/prot1/issues", "/other/prot1/code"], "이전 뒤")
    # 조직 이름
    s += [
        Step("tester", "POST", "/organizations/org1/setting", files=mpf(id="1", name="orgx", descr="이름 바꿈"), note="조직 이름 바꾸기"),
        Step("anon", "GET", "/organizations/org1", note="조직 옛 이름"),
        Step("anon", "GET", "/organizations/orgx", note="조직 새 이름"),
        Step("anon", "GET", "/org1", note="조직 옛 이름 프로젝트 경로"),
    ]
    # 삭제
    s += [
        Step("third", "GET", "/tester/other1/deleteform", note="삭제 화면 권한 없음"),
        Step("other", "GET", "/other/other1/deleteform", note="삭제 화면"),
        Step("third", "DELETE", "/other/other1/delete", note="삭제 권한 없음"),
        Step("other", "DELETE", "/other/other1/delete", note="삭제"),
        Step("other", "DELETE", "/other/other1/delete", note="삭제 다시"),
    ]
    s += sweep(["/other/other1", "/other/other1/issues", "/other/other1/code"], "삭제 뒤")
    s.append(Step("admin", "GET", "/sites/projectList", note="삭제 뒤 프로젝트 목록"))
    s.append(Step("anon", "GET", "/", note="홈"))
    return s
