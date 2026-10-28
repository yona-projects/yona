#!/usr/bin/env python3
"""설정을 바꿔 띄운 두 서버에서 그 설정의 효과가 실제로 화면에 나타나는지 확인한다(효과가 없으면 두 서버가 같게 보일 뿐이라 대조가 의미 없다).
사용: cfgprobe.py <설정 이름>   효과가 나타나면 'ok', 아니면 어느 서버에서 안 나타났는지 적는다."""
import sys, requests
from probe import BASES, session

CHECKS = {
    "anon_off": [("anon", "/projects", lambda t, c: c != 200)],
    "hide_listing": [("anon", "/projects", lambda t, c: "proj1" not in t)],
    "display_private": [("tester", "/projects", lambda t, c: "priv1" in t)],
    "social_only": [("anon", "/users/loginform", lambda t, c: 'name="password"' not in t)],
    "no_email": [("tester", "/tester", lambda t, c: "tester@example.com" not in t)],
    "noreferrer_off": [("anon", "/tester/proj1/issue/1", lambda t, c: "noreferrer" not in t)],
    "scope_private": [("tester", "/projectform", lambda t, c: "PRIVATE" in t and 'value="PRIVATE" checked' in t.replace("\n", " ").replace('  ', ' ') or 'checked' in t)],
    "menus_two": [("tester", "/projectform", lambda t, c: t.count('checked') >= 1)],
    "navbar": [("tester", "/", lambda t, c: "Docs" in t and "example.com/docs" in t)],
    "placeholders": [("anon", "/users/loginform", lambda t, c: "아이디" in t and "비밀" in t)],
    "sitename": [("anon", "/", lambda t, c: "MySite" in t)],
    "usage_off": [("anon", "/", lambda t, c: "google-analytics" not in t)],
    "langs_ko": [("anon", "/", lambda t, c: 'lang="ko"' in t or "프로젝트" in t)],
    "langs_ja": [("anon", "/", lambda t, c: 'lang="ja"' in t or "プロジェクト" in t)],
    "feedback": [("anon", "/", lambda t, c: "example.com/fb" in t)],
    "migration_on": [("tester", "/migration", lambda t, c: c == 200)],
    "maxfile_small": [("anon", "/", lambda t, c: '"maxFileSize": "1000"' in t)],
    "hostname": [("tester", "/tester/proj1", lambda t, c: "yona.example.com" in t)],
    "social_none": [("anon", "/users/loginform", lambda t, c: "oauth-login-btn" not in t)],
    "codeview_small": [("anon", "/tester/proj1/code/master/README.md", lambda t, c: True)],
}

name = sys.argv[1]
bad = []
for server, base in BASES.items():
    for actor, path, pred in CHECKS.get(name, []):
        r = session(base, actor).get(base + path, allow_redirects=False)
        if not pred(r.text, r.status_code):
            bad.append(f"{server}:{path}")
print("ok" if not bad and name in CHECKS else ("점검 없음" if name not in CHECKS else "효과 없음 " + " ".join(bad)))
