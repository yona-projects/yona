"""비밀 키를 설정 파일에 쓰지 못하는 경우: v1.6는 외부 설정 파일(`-Dconfig.file`)이면 쓰기를 거절하고, v2는 쓰기 폴더가 막혀 있으면 실패한다. 둘 다 재시작 안내에
"직접 고치라"는 문구가 붙는지 대조한다. `first3.sh`가 이 상태로 서버를 띄운다."""
from harness import Step


def steps():
    ok = {"loginId": "admin", "name": "관리자", "email": "root@example.com", "password": "newpass1", "retypedPassword": "newpass1"}
    return [
        Step("anon", "GET", "/", note="설정 화면"),
        Step("anon", "POST", "/", data=ok, note="정상 입력(쓰기 실패)"),
        Step("anon", "GET", "/", note="실패 뒤 홈"),
        Step("anon", "GET", "/users/loginform", note="실패 뒤 로그인 화면"),
        Step("anon", "POST", "/", data=ok, note="실패 뒤 다시 POST"),
    ]
