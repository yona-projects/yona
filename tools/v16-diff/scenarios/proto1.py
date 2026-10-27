"""Git 스마트 HTTP와 Subversion(WebDAV) 주소를 사용자와 자격 증명(없음, 맞음, 틀림)과 프로젝트 공개 범위의 조합으로 읽고 쓰기 요청을 보내 상태와 헤더를 대조한다.
data3 상태에서 한 번만 실행한다. 쓰기는 본문이 없는 사전 요청까지만 보내 저장소를 바꾸지 않는다."""
import base64

from harness import Step

CREDS = [
    ("anon", None),
    ("tester", ("tester", "pass1234")),
    ("other", ("other", "pass1234")),
    ("third", ("third", "pass1234")),
    ("admin", ("admin", "admin1234")),
    ("wrong", ("tester", "bad-password")),
    ("nouser", ("nobody", "x")),
]
PROJECTS = ["tester/proj1", "tester/priv1", "tester/prot1", "tester/none"]


def auth(cred):
    if cred is None:
        return {}
    token = base64.b64encode(f"{cred[0]}:{cred[1]}".encode()).decode()
    return {"Authorization": f"Basic {token}"}


def steps():
    s = []
    for actor, cred in CREDS:
        h = auth(cred)
        for proj in PROJECTS:
            for service in ("git-upload-pack", "git-receive-pack"):
                s.append(Step(actor, "GET", f"/{proj}/info/refs?service={service}", headers=dict(h), note=f"{service} 광고 {proj} [{actor}]"))
            s.append(Step(actor, "GET", f"/{proj}/info/refs", headers=dict(h), note=f"서비스 없는 광고 {proj} [{actor}]"))
            s.append(Step(actor, "POST", f"/{proj}/git-upload-pack", headers={**h, "Content-Type": "application/x-git-upload-pack-request"}, raw_body=b"0000", note=f"upload-pack {proj} [{actor}]"))
            s.append(Step(actor, "POST", f"/{proj}/git-receive-pack", headers={**h, "Content-Type": "application/x-git-receive-pack-request"}, raw_body=b"0000", note=f"receive-pack {proj} [{actor}]"))
        for proj in ("tester/svn1", "tester/none"):
            for method, path, extra in (
                ("OPTIONS", f"/svn/{proj}", {}), ("PROPFIND", f"/svn/{proj}", {"Depth": "0"}), ("GET", f"/svn/{proj}/trunk/README.md", {}),
                ("MKACTIVITY", f"/svn/{proj}/!svn/act/00000000-0000-0000-0000-000000000001", {}), ("PUT", f"/svn/{proj}/x", {}), ("DELETE", f"/svn/{proj}/x", {}),
            ):
                s.append(Step(actor, method, path, headers={**h, **extra}, note=f"svn {method} {proj} [{actor}]"))
    # 형식이 잘못된 인증
    for bad in ("Basic", "Basic !!!", "Basic Zm9v", "Bearer abc", "basic dGVzdGVyOnBhc3MxMjM0"):
        s.append(Step("anon", "GET", "/tester/priv1/info/refs?service=git-upload-pack", headers={"Authorization": bad}, note=f"잘못된 Authorization {bad}"))
        s.append(Step("anon", "OPTIONS", "/svn/tester/svn1", headers={"Authorization": bad}, note=f"svn 잘못된 Authorization {bad}"))
    return s
