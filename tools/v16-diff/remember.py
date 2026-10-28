#!/usr/bin/env python3
"""'로그인 유지'(yobi.token 쿠키) 흐름을 두 서버에서 견준다: 쿠키 모양, 쿠키만으로 로그인, 위조, 존재하지 않는 사용자, 잠긴 사용자, 비밀번호를 바꾼 뒤, 로그아웃 때의 쿠키 지움.
data2 상태에서 실행한다(`servers.sh restore data2`)."""
import re, sys, json
import requests
from probe import BASES


def cookie_summary(r):
    out = []
    for c in r.raw.headers.getlist("Set-Cookie"):
        name = c.split("=", 1)[0]
        attrs = sorted(re.sub(r"=.*", "", a.strip()).lower() + ("=" + a.split("=", 1)[1].strip() if a.strip().lower().startswith(("max-age", "path", "samesite", "domain")) else "") for a in c.split(";")[1:])
        val = c.split("=", 1)[1].split(";")[0]
        if name in ("PLAY_FLASH", "PLAY_SESSION", "JSESSIONID"):
            continue  # 세션과 안내 쿠키는 서버 종류마다 이름이 다르다
        attrs = [a for a in attrs if not a.startswith("max-age")]
        out.append((name, "<빈 값>" if val == "" else "<값>", attrs))
    return out


def who(base, cookies):
    s = requests.Session()
    s.headers["Accept-Language"] = "en-US"
    r = s.get(base + "/user/editform", cookies=cookies, allow_redirects=False)
    r2 = s.get(base + "/", cookies=cookies, allow_redirects=False)
    return {"editform": r.status_code, "loc": r.headers.get("Location"), "logout_link": "users/logout" in r2.text, "setcookie": cookie_summary(r2) if r2.raw.headers.getlist("Set-Cookie") else [],
            "setcookie_edit": cookie_summary(r) if r.raw.headers.getlist("Set-Cookie") else []}


def run(base):
    res = {}
    s = requests.Session()
    r = s.post(base + "/users/login", data={"loginIdOrEmail": "third", "password": "pass1234", "rememberMe": "true"}, allow_redirects=False)
    res["로그인 유지 켬"] = {"status": r.status_code, "cookies": cookie_summary(r)}
    token = s.cookies.get("yobi.token")
    res["토큰 모양"] = re.sub(r"[0-9a-f]{20,}", "<해시>", token or "없음")
    res["쿠키만으로"] = who(base, {"yobi.token": token})
    s2 = requests.Session()
    r = s2.post(base + "/users/login", data={"loginIdOrEmail": "third", "password": "pass1234"}, allow_redirects=False)
    res["로그인 유지 끔"] = {"status": r.status_code, "cookies": cookie_summary(r)}
    r = s.get(base + "/users/logout", allow_redirects=False)
    res["로그아웃"] = {"status": r.status_code, "loc": r.headers.get("Location"), "cookies": cookie_summary(r)}
    if token:
        user, _, pw = token.partition(":")
        for label, tok in (("위조 해시", f"{user}:0000"), ("없는 사용자", f"nobody:{pw}"), ("콜론 셋", f"{user}:{pw}:x"), ("콜론 없음", user), ("빈 값", ""), ("대문자 ID", f"{user.upper()}:{pw}"),
                           ("이메일 ID", f"third@example.com:{pw}"), ("공백", f" {user}:{pw}"), ("퍼센트 인코딩", requests.utils.quote(f"{user}:{pw}")), ("다른 사용자 해시", f"tester:{pw}")):
            res["토큰 " + label] = who(base, {"yobi.token": tok})
        # 비밀번호를 바꾼 뒤 옛 토큰
        a = requests.Session()
        a.post(base + "/users/login", data={"loginIdOrEmail": "third", "password": "pass1234"}, allow_redirects=False)
        r = a.post(base + "/user/resetPassword", data={"loginId": "third", "oldPassword": "pass1234", "password": "newpw1234", "retypedPassword": "newpw1234"}, allow_redirects=False)
        res["비밀번호 변경"] = r.status_code
        res["비밀번호 변경 뒤 옛 토큰"] = who(base, {"yobi.token": token})
        # 잠긴 사용자
        t2 = requests.Session()
        t2.post(base + "/users/login", data={"loginIdOrEmail": "other", "password": "pass1234", "rememberMe": "true"}, allow_redirects=False)
        tok2 = t2.cookies.get("yobi.token")
        adm = requests.Session()
        adm.post(base + "/users/login", data={"loginIdOrEmail": "admin", "password": "admin1234"}, allow_redirects=False)
        res["잠금"] = adm.post(base + "/sites/toggleAccountLock/other", allow_redirects=False).status_code
        res["잠긴 사용자 토큰"] = who(base, {"yobi.token": tok2})
        adm.post(base + "/sites/toggleAccountLock/other", allow_redirects=False)
    return res


a, b = run(BASES["v1.6"]), run(BASES["v2"])
same = diff = 0
for k in a:
    if a[k] == b.get(k):
        same += 1
    else:
        diff += 1
        print(f"- {k}\n    v1.6={json.dumps(a[k], ensure_ascii=False)}\n    v2  ={json.dumps(b.get(k), ensure_ascii=False)}")
print(f"로그인 유지 대조: 같음 {same}, 다름 {diff} (전체 {same + diff})")
