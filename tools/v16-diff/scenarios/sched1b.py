"""임시 첨부 정리: 사용자가 올린 첨부(`USER` 컨테이너) 두 개와 이슈에 붙인 첨부 하나를 만든 뒤 정리 주기(20초)보다 오래 기다려 어느 것이 남는지 본다."""
from harness import Step
from data1 import login, mp


def steps():
    up = lambda name, note: Step("tester", "POST", "/files", files={"filePath": (name, b"temp file " + name.encode(), "text/plain")}, capture={"f_" + name[:1]: ("body", r'"id":\s*"?(\d+)')}, note=note)  # noqa: E731
    return [
        login("tester"),
        up("a-temp.txt", "임시 올리기 a"),
        up("b-temp.txt", "임시 올리기 b"),
        Step("tester", "GET", "/files/<<f_a>>", note="올린 직후 a 읽기"),
        Step("tester", "GET", "/user/editform/projects", delay=0, note="기다리기 전 화면"),
        Step("tester", "GET", "/files/<<f_a>>", delay=45, note="45초 뒤 a 읽기"),
        Step("tester", "GET", "/files/<<f_b>>", note="45초 뒤 b 읽기"),
        Step("tester", "GET", "/files", note="45초 뒤 목록"),
    ]
