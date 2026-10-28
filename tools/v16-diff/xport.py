#!/usr/bin/env python3
"""데이터 내보내기/가져오기 상호 시험. 1) data1 상태에서 두 서버의 내보내기 파일을 구조로 견준다(표, 열, 값 형식, 행 수).
2) base 상태로 되돌린 두 서버에 서로의 파일을 가져오게 한다(v1.6에는 v2의 파일, v2에는 v1.6의 파일). 이어서 pages1을 돌려 가져온 데이터로 만든 화면이 원래와 같은지 본다.
사용: xport.py export | cross | all   (상태를 되돌리는 일은 xport.sh가 한다)"""
import json, os, re, sys
import requests

BASES = {"v1.6": "http://127.0.0.1:19200", "v2": "http://127.0.0.1:19300"}
OUT = "/home/jiho/yona-convert/yona-diff-work"


def admin(base):
    s = requests.Session()
    r = s.post(base + "/users/login", data={"loginIdOrEmail": "admin", "password": "admin1234"}, allow_redirects=False)
    return s


def export(name, base):
    r = admin(base).get(base + "/sites/export")
    path = f"{OUT}/export-{name}.json"
    open(path, "wb").write(r.content)
    return r, json.loads(r.content.decode("utf-8"))


def kind(v):
    if v is None:
        return "null"
    if isinstance(v, bool):
        return "bool"
    if isinstance(v, int):
        return "int"
    if isinstance(v, float):
        return "float"
    if isinstance(v, str):
        if re.fullmatch(r"\d{4}-\d\d-\d\d \d\d:\d\d:\d\d(\.\d+)?", v):
            return "datetime:" + ("ms" if "." in v else "s")
        if re.fullmatch(r"\d{4}-\d\d-\d\dT.*", v):
            return "iso"
        if re.fullmatch(r"\d{4}-\d\d-\d\d", v):
            return "date"
        return "str"
    return type(v).__name__


def compare_exports(a, b):
    problems = []
    if list(a.keys()) != list(b.keys()):
        problems.append(f"표 순서/목록: v1.6 {list(a.keys())} != v2 {list(b.keys())}")
    for t in a:
        if t not in b:
            problems.append(f"{t}: v2에 없음")
            continue
        va, vb = a[t], b[t]
        if t.endswith("_SEQ"):
            continue
        if len(va) != len(vb):
            problems.append(f"{t}: 행 수 v1.6={len(va)} v2={len(vb)}")
        if va and vb:
            ca, cb = list(va[0].keys()), list(vb[0].keys())
            if ca != cb:
                problems.append(f"{t}: 열 v1.6={ca} v2={cb}")
            kinds_a = {k: {kind(r.get(k)) for r in va} for k in ca}
            kinds_b = {k: {kind(r.get(k)) for r in vb} for k in cb}
            for k in ca:
                if k in kinds_b and kinds_a[k] != kinds_b[k] and kinds_a[k] - {"null"} != kinds_b[k] - {"null"}:
                    problems.append(f"{t}.{k}: 값 형식 v1.6={sorted(kinds_a[k])} v2={sorted(kinds_b[k])}")
    for t in b:
        if t not in a:
            problems.append(f"{t}: v1.6에 없음")
    return problems


def do_export():
    ra, a = export("v1.6", BASES["v1.6"])
    rb, b = export("v2", BASES["v2"])
    print("머리글:", ra.headers.get("Content-Type"), "|", rb.headers.get("Content-Type"))
    probs = compare_exports(a, b)
    print(f"내보내기 구조 대조: 문제 {len(probs)}건")
    for p in probs[:80]:
        print("  -", p)


def do_cross():
    for name, base, src in (("v1.6", BASES["v1.6"], "v2"), ("v2", BASES["v2"], "v1.6")):
        data = open(f"{OUT}/export-{src}.json", "rb").read()
        s = admin(base)
        r = s.post(base + "/sites/import", files={"data": ("d.json", data, "application/json")}, allow_redirects=False)
        print(f"{name}에 {src}의 파일 가져오기: {r.status_code} {r.headers.get('Location')} {r.text[:120].strip()!r}")


if __name__ == "__main__":
    mode = sys.argv[1] if len(sys.argv) > 1 else "all"
    if mode in ("export", "all"):
        do_export()
    if mode == "cross":
        do_cross()
