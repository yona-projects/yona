#!/usr/bin/env python3
"""v1.6 라우트 306개 중 어느 비교 시나리오도 지원하는 요청 방식으로 요청한 적 없는 라우트를 찾는다(시나리오는 서버 없이 Step 목록만 만든다)."""
import os, re, sys, glob, importlib
sys.path.insert(0, os.path.dirname(__file__)); sys.path.insert(0, os.path.join(os.path.dirname(__file__), "scenarios"))
ROUTES = os.path.join(os.path.dirname(__file__), "..", "..", "src", "test", "resources", "v16", "routes.tsv")
rows = [l.split("\t") for l in open(ROUTES, encoding="utf-8").read().splitlines() if l.strip()]

def rx(pattern):
    optional = pattern.endswith("/?")
    if optional:
        pattern = pattern[:-2]
    out = []
    for part in re.split(r"(:\w+|\*\w+|\$\w+<[^>]*>)", pattern):
        if part.startswith(":"):
            out.append("[^/]+")
        elif part.startswith("*"):
            out.append(".+")
        elif part.startswith("$"):
            out.append(re.search(r"<(.*)>", part).group(1))
        else:
            out.append(re.escape(part))
    return re.compile("^" + "".join(out) + ("/?" if optional else "") + "$")

compiled = [(m, pat, act, rx(pat)) for m, pat, act in rows]
hits = {i: set() for i in range(len(compiled))}
steps_seen = 0
for f in sorted(glob.glob(os.path.join(os.path.dirname(__file__), "scenarios", "*.py"))):
    name = os.path.basename(f)[:-3]
    if name in ("methods1",):  # 지원하지 않는 방식만 보내므로 제외
        continue
    try:
        mod = importlib.import_module(name)
        steps = mod.steps()
    except Exception as e:
        print("건너뜀", name, e); continue
    for st in steps:
        path = re.sub(r"<<\w+>>", "1", st.path).split("?")[0].split("#")[0]
        steps_seen += 1
        for i, (m, pat, act, r) in enumerate(compiled):
            if st.method.upper() == m and r.match(path):
                hits[i].add(name)
extra = os.path.join(os.path.dirname(__file__), "covered_by_clients.txt")
manual = set(l.strip() for l in open(extra, encoding="utf-8")) if os.path.exists(extra) else set()
missing = [(m, pat, act) for i, (m, pat, act, r) in enumerate(compiled) if not hits[i] and f"{m} {pat}" not in manual]
print(f"단계 {steps_seen}개, 라우트 {len(compiled)}개 중 요청한 적 없는 것 {len(missing)}개")
for m, pat, act in missing:
    print(f"{m:7} {pat:80} {act}")
