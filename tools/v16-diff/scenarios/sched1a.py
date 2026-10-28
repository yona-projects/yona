"""오래된 알림 정리 뒤 화면: 알림 목록과 홈에서 `NEW_ISSUE` 알림이 사라졌는지 행위자마다 읽는다."""
from harness import Step
from data1 import login


def steps():
    s = [login("tester"), login("other"), login("third"), login("admin", "admin1234")]
    for a in ("tester", "other", "third", "admin"):
        for p in ("/", "/notifications", "/user/issues", "/tester/proj1/issues"):
            s.append(Step(a, "GET", p, note=f"{p} [{a}]"))
    return s
