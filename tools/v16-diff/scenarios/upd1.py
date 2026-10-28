"""업데이트 확인: 태그가 있는 로컬 저장소(`upd.sh init`)를 보게 한 서버(`upd.sh on` 뒤 `servers.sh restore data2`)에서 관리자 화면의 새 버전 안내, 업데이트 화면,
알림 끄기를 행위자별로 읽는다. 시작 1초 뒤 첫 확인이 끝나 있어야 하므로 먼저 몇 초 기다린다.
`upd1b`는 저장소에 새 태그를 더한 뒤, `upd1c`는 저장소를 없앤 뒤 업데이트 화면을 다시 읽는다."""
from harness import Step
from data1 import login

ACTORS = ["anon", "tester", "admin"]


def common(tag):
    s = [login("tester"), login("admin", "admin1234")]
    for a in ACTORS:
        for p in ("/sites/update", "/sites/setting", "/", "/sites/userList", "/sites/diagnose"):
            s.append(Step(a, "GET", p, delay=3 if p == "/sites/update" and a == "anon" and tag == "a" else 0, note=f"{p} [{a}] {tag}"))
    return s


def steps():
    s = common("a")
    s += [
        Step("tester", "POST", "/sites/unwatchUpdate", note="알림 끄기 tester"),
        Step("admin", "GET", "/", note="tester가 끈 뒤 홈 [admin]"),
        Step("admin", "POST", "/sites/unwatchUpdate", note="알림 끄기 admin"),
    ]
    for a in ACTORS:
        for p in ("/", "/sites/update", "/sites/setting"):
            s.append(Step(a, "GET", p, note=f"{p} 끈 뒤 [{a}]"))
    return s
