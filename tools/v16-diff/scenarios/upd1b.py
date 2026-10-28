"""upd1 뒤 저장소에 `v3.0.0` 태그를 더한 다음 업데이트 화면을 다시 읽는다(화면이 확인을 새로 하므로 새 버전이 바뀐다)."""
from harness import Step
from data1 import login


def steps():
    s = [login("admin", "admin1234"), login("tester")]
    for a in ("anon", "tester", "admin"):
        s += [Step(a, "GET", "/sites/update", note=f"새 태그 뒤 업데이트 화면 [{a}]"), Step(a, "GET", "/sites/setting", note=f"새 태그 뒤 설정 [{a}]"), Step(a, "GET", "/", note=f"새 태그 뒤 홈 [{a}]")]
    return s
