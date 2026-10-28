"""upd1b 뒤 저장소를 지운 다음 업데이트 화면을 읽는다(확인 실패 시 오류 문구가 어떻게 나오는지 본다)."""
from harness import Step
from data1 import login


def steps():
    s = [login("admin", "admin1234")]
    for a in ("anon", "tester", "admin"):
        s.append(Step(a, "GET", "/sites/update", note=f"저장소 없음 업데이트 화면 [{a}]"))
    s.append(Step("admin", "GET", "/sites/setting", note="저장소 없음 설정"))
    return s
