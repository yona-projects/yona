#!/usr/bin/env python3
"""v1.6 배포본의 정적 자원(`*-assets.jar`의 `public/`)을 모두 두 서버에서 내려받아 본문과 주요 머리글(Content-Type, Cache-Control, ETag 존재, Content-Length)을 대조한다.
사용: assets.py [jar 경로]"""
import sys, zipfile, requests, hashlib, collections
BASES = {"v1.6": "http://127.0.0.1:19200", "v2": "http://127.0.0.1:19300"}
JAR = sys.argv[1] if len(sys.argv) > 1 else "/home/jiho/yona-convert/yona-v1.16-release/yona-1.16.0/lib/yona.yona-1.16.0-assets.jar"
names = [n for n in zipfile.ZipFile(JAR).namelist() if n.startswith("public/") and not n.endswith("/")]
diff = collections.Counter(); samples = collections.defaultdict(list)
for n in names:
    path = "/assets/" + n[len("public/"):]
    res = {}
    for k, b in BASES.items():
        r = requests.get(b + path, allow_redirects=False, timeout=60)
        res[k] = (r.status_code, hashlib.sha1(r.content).hexdigest(), r.headers.get("Content-Type"), r.headers.get("Cache-Control"), "ETag" in r.headers, "Last-Modified" in r.headers, r.headers.get("Content-Encoding"))
    a, b = res["v1.6"], res["v2"]
    for i, label in enumerate(("상태", "본문", "Content-Type", "Cache-Control", "ETag", "Last-Modified", "Content-Encoding")):
        if a[i] != b[i]:
            diff[label] += 1
            if len(samples[label]) < 12: samples[label].append((path, a[i], b[i]))
print(f"자원 {len(names)}개 대조")
for label, c in diff.most_common():
    print(f"  {label} 다름 {c}건")
    for s in samples[label]: print("     ", s)
if not diff: print("  모두 같음")
