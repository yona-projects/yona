"""조직 소유 한글 이름 프로젝트(corp1/한글저장소)에서 포크(other/한글포크)를 만든다. pr2.sh가 이 시나리오를 부른 뒤 포크에 브랜치를 올린다. data2 상태에서 한 번만 실행한다."""
from harness import Step
from data1 import login


def steps():
    s = [login("tester"), login("other"), login("third"), login("admin", "admin1234")]
    s += [
        Step("other", "POST", "/corp1/한글저장소/fork", data={"owner": "other", "name": "한글포크", "projectScope": "PUBLIC"}, note="포크 만들기"),
        Step("other", "POST", "/corp1/한글저장소/clone", data={"owner": "other", "name": "한글포크", "projectScope": "PUBLIC"}, note="복제 실행"),
        Step("other", "GET", "/other/한글포크", note="포크 홈"),
        Step("anon", "GET", "/corp1/한글저장소/forks", note="포크 목록"),
    ]
    return s
