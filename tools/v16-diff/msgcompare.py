#!/usr/bin/env python3
"""두 서버의 `/messages.js`를 언어별로 내려받아 키와 값을 모두 대조한다. 다른 키만 줄로 보여 준다."""
import json
import re

import requests

DEC = json.JSONDecoder()
for lang in ("en-US", "ko-KR", "ja-JP", "ru-RU", "uz-UZ", "fr-FR", "zh-CN"):
    got = []
    for base in ("http://127.0.0.1:19200", "http://127.0.0.1:19300"):
        text = requests.get(base + "/messages.js", headers={"Accept-Language": lang}).content.decode("utf-8")
        start = text.index("f.messages=") + len("f.messages=")
        got.append(DEC.raw_decode(text[start:])[0])
    a, b = got
    diff = sorted(k for k in set(a) | set(b) if a.get(k) != b.get(k))
    print(lang, len(a), len(b), "다른 키", len(diff), diff[:8])
