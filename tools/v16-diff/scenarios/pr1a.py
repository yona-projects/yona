"""포크 만들기와 복제(풀 리퀘스트 준비). 이어서 pr1.sh가 포크에 커밋을 올린다. data2 상태에서 한 번만 실행한다."""
from harness import Step
from data1 import login


def steps():
    s = [login("tester"), login("other"), login("third"), login("admin", "admin1234")]
    s += [
        Step("anon", "GET", "/tester/proj1/newFork", note="포크 폼 익명"),
        Step("other", "GET", "/tester/proj1/newFork", note="포크 폼"),
        Step("other", "GET", "/tester/priv1/newFork", note="포크 폼 비공개"),
        Step("other", "POST", "/tester/proj1/fork", data={"owner": "other", "name": "", "projectScope": "PUBLIC"}, note="포크 이름 없음"),
        Step("other", "POST", "/tester/proj1/fork", data={"owner": "other", "name": "bad name", "projectScope": "PUBLIC"}, note="포크 잘못된 이름"),
        Step("anon", "POST", "/tester/proj1/fork", data={"owner": "other", "name": "forked", "projectScope": "PUBLIC"}, note="포크 익명"),
        Step("third", "POST", "/tester/priv1/fork", data={"owner": "third", "name": "forked", "projectScope": "PUBLIC"}, note="포크 읽을 수 없는 프로젝트"),
        Step("other", "POST", "/tester/proj1/fork", data={"owner": "other", "name": "proj1", "projectScope": "PUBLIC"}, note="포크 만들기"),
        Step("other", "POST", "/tester/proj1/fork", data={"owner": "other", "name": "proj1", "projectScope": "PUBLIC"}, note="포크 중복"),
        Step("other", "POST", "/tester/proj1/clone", data={"owner": "other", "name": "proj1", "projectScope": "PUBLIC"}, note="복제 실행"),
        Step("anon", "POST", "/tester/proj1/clone", data={"owner": "other", "name": "proj1", "projectScope": "PUBLIC"}, note="복제 익명"),
        Step("third", "POST", "/tester/nope/clone", data={"owner": "third", "name": "x", "projectScope": "PUBLIC"}, note="복제 없는 프로젝트"),
        Step("other", "GET", "/other/proj1", note="포크 홈"),
        Step("other", "GET", "/other/proj1/code", note="포크 코드"),
        Step("anon", "GET", "/tester/proj1/forks", note="포크 목록"),
    ]
    return s
