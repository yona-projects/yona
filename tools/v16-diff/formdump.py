#!/usr/bin/env python3
"""서버 화면의 폼과 입력란을 보여 준다. 사용: formdump.py <기준주소> <경로> [아이디 비밀번호]"""
import sys
import requests
from bs4 import BeautifulSoup

base, path = sys.argv[1].rstrip("/"), sys.argv[2]
s = requests.Session()
if len(sys.argv) > 4:
    s.post(base + "/users/login", data={"loginIdOrEmail": sys.argv[3], "password": sys.argv[4]}, allow_redirects=False)
r = s.get(base + path)
print(r.status_code, r.headers.get("content-type"))
soup = BeautifulSoup(r.text, "lxml")
for f in soup.find_all("form"):
    print("FORM", f.get("method"), f.get("action"), f.get("enctype") or "")
    for i in f.find_all(["input", "select", "textarea", "button"]):
        if i.name == "select":
            print("   select", i.get("name"), [o.get("value") for o in i.find_all("option")][:6])
        else:
            print("  ", i.name, i.get("name"), i.get("type"), repr(i.get("value"))[:40] if i.get("value") is not None else "")
