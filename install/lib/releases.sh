# shellcheck shell=bash
# GitHub 릴리스 목록을 읽어 설치할 버전을 고른다. `jq` 같은 도구에 기대지 않고 `sed`와 `awk`로 JSON을 읽는다
# (macOS와 최소 Linux에 `jq`가 없을 수 있다). 줄바꿈이 있는 JSON과 한 줄 JSON을 모두 읽는다.

# 릴리스 목록을 내려받는 주소이다. 시험이나 미러에서 `YONA_RELEASES_URL`로 바꾼다.
YI_RELEASES_URL_DEFAULT="https://api.github.com/repos/yona-projects/yona/releases?per_page=30"

# 릴리스 목록 JSON을 표준 출력으로 내려받는다. 사용법: releases_fetch
releases_fetch() {
  curl -fsSL -H "Accept: application/vnd.github+json" "${YONA_RELEASES_URL:-$YI_RELEASES_URL_DEFAULT}"
}

# JSON을 읽어 자산마다 한 줄(`태그|릴리스 이름|draft|prerelease|자산 이름|크기|digest|주소`)로 바꾼다.
# 문자열 안의 따옴표는 항상 `\"`이므로 `,"`와 `{"`로 키를 나누어도 본문 속 JSON 조각에 속지 않는다.
_yi_releases_flatten() {
  tr -d '\r\n' |
    sed -e 's/,[[:space:]]*"/\
"/g' -e 's/{[[:space:]]*"/\
"/g' |
    LC_ALL=C awk '
      function val(s) {
        sub(/^"[^"]*"[[:space:]]*:[[:space:]]*/, "", s)
        sub(/[],}[:space:]]+$/, "", s)
        sub(/^"/, "", s); sub(/"$/, "", s)
        return s
      }
      /^"tag_name"/ { tag = val($0); rname = ""; draft = "false"; pre = "false"; inassets = 0; next }
      /^"assets"[[:space:]]*:[[:space:]]*\[/ { inassets = 1; next }
      /^"tarball_url"/ { inassets = 0; next }
      !inassets && /^"name"/ { rname = val($0); next }
      !inassets && /^"draft"/ { draft = val($0); next }
      !inassets && /^"prerelease"/ { pre = val($0); next }
      inassets && /^"name"/ { aname = val($0); next }
      inassets && /^"size"/ { asize = val($0); next }
      inassets && /^"digest"/ { adigest = val($0); if (adigest == "null") adigest = ""; next }
      inassets && /^"browser_download_url"/ {
        printf "%s|%s|%s|%s|%s|%s|%s|%s\n", tag, rname, draft, pre, aname, asize, adigest, val($0)
        aname = ""; asize = ""; adigest = ""
        next
      }
    '
}

# 정렬에 쓰는 버전 키를 만든다. 숫자는 5자리로 채우고 정식은 `1`, 사전 배포는 `0-꼬리표`로 끝나 정식이 더 크다.
# 사용법: version_key <버전>
version_key() {
  printf '%s\n' "$1" | LC_ALL=C awk '
    function pad(d) { while (length(d) < 5) d = "0" d; return d }
    function pad_digits(s,   out) {
      out = ""
      while (match(s, /[0-9]+/)) {
        out = out substr(s, 1, RSTART - 1) pad(substr(s, RSTART, RLENGTH))
        s = substr(s, RSTART + RLENGTH)
      }
      return out s
    }
    {
      v = tolower($0); sub(/^v/, "", v)
      pre = ""
      i = index(v, "-")
      if (i > 0) { pre = substr(v, i + 1); v = substr(v, 1, i - 1) }
      n = split(v, p, ".")
      key = pad(p[1]) "." pad(n >= 2 ? p[2] : "0") "." pad(n >= 3 ? p[3] : "0") "."
      key = key (pre == "" ? "1" : "0-" pad_digits(pre))
      print key
    }'
}

# 두 버전을 견주어 `1`(앞이 큼), `0`(같음), `-1`(뒤가 큼)을 돌려준다. 사용법: version_cmp <버전1> <버전2>
version_cmp() {
  local a b
  a="$(version_key "$1")"
  b="$(version_key "$2")"
  LC_ALL=C awk -v a="$a" -v b="$b" 'BEGIN { print (a == b) ? 0 : ((a < b) ? -1 : 1) }'
}

# 태그의 표시 이름을 만든다. `-rc`가 있으면 `(rc)`, 그 밖의 사전 배포는 `(pre-release)`를 붙인다.
# 사용법: _yi_release_label <태그> <prerelease 값>
_yi_release_label() {
  local lower
  lower="$(yi_lower "$1")"
  case "$lower" in
    *-rc*) printf '%s (rc)' "$1" ;;
    *-*) printf '%s (pre-release)' "$1" ;;
    *)
      if [ "$2" = "true" ]; then printf '%s (pre-release)' "$1"; else printf '%s' "$1"; fi
      ;;
  esac
}

# 표준 입력의 릴리스 JSON에서 v2 이상 릴리스를 높은 버전부터 최대 N건(기본 4) 고른다.
# 초안(draft)과 실행 jar(`yona-*.jar`, `-plain.jar` 제외)가 없는 릴리스는 뺀다.
# 출력은 한 줄에 `태그<탭>표시 이름<탭>jar 주소<탭>크기<탭>digest`이다. 사용법: releases_list_v2 [N]
releases_list_v2() {
  local limit="${1:-4}" tag draft pre aname asize adigest url key
  _yi_releases_flatten | while IFS='|' read -r tag _ draft pre aname asize adigest url; do
    [ "$draft" = "true" ] && continue
    case "$aname" in
      yona-*-plain.jar) continue ;;
      yona-*.jar) ;;
      *) continue ;;
    esac
    case "$tag" in
      v[2-9]* | v[1-9][0-9]* | [2-9]* | [1-9][0-9]*) ;;
      *) continue ;;
    esac
    key="$(version_key "$tag")"
    printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$key" "$tag" "$(_yi_release_label "$tag" "$pre")" "$url" "$asize" "$adigest"
  done |
    LC_ALL=C sort -r -u -t "$(printf '\t')" -k2,2 |
    LC_ALL=C sort -r -t "$(printf '\t')" -k1,1 |
    head -n "$limit" |
    cut -f2-
}

# 태그로 릴리스 하나를 고른다(`v`가 있든 없든). 없으면 실패한다. 사용법: releases_pick <태그>
releases_pick() {
  local want="$1" found
  case "$want" in v*) ;; *) want="v$want" ;; esac
  found="$(releases_list_v2 1000 | awk -F'\t' -v want="$want" '$1 == want { print; exit }')"
  [ -n "$found" ] || return 1
  printf '%s\n' "$found"
}

# 파일의 sha256(소문자 16진수)을 돌려준다. `sha256sum`, `shasum`, `openssl` 순으로 있는 것을 쓴다.
sha256_of() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | cut -d' ' -f1
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | cut -d' ' -f1
  else
    openssl dgst -sha256 "$1" | sed 's/^.*= //'
  fi
}

# 파일을 `sha256:...` digest와 견준다. 맞으면 0, 틀리면 1, digest가 없거나 sha256이 아니라 검증하지 못하면 2이다.
# 사용법: verify_digest <파일> <digest>
verify_digest() {
  case "$2" in
    sha256:?*) ;;
    *) return 2 ;;
  esac
  [ "$(sha256_of "$1")" = "${2#sha256:}" ]
}

# 폐쇄 환경용이다. 폴더의 `yona-<버전>.jar`를 찾아 같은 형식의 줄로 돌려준다(`-plain.jar` 제외, 높은 버전부터).
# 같은 이름의 `.sha256` 파일(`해시  파일이름`)이 있으면 digest로 읽고 없으면 비워 둔다. 사용법: releases_local_list <폴더>
releases_local_list() {
  local dir="$1" jar base version tag size digest
  for jar in "$dir"/yona-*.jar; do
    [ -f "$jar" ] || continue
    case "$jar" in *-plain.jar) continue ;; esac
    base="$(basename "$jar" .jar)"
    version="${base#yona-}"
    tag="v$version"
    size="$(wc -c <"$jar" | tr -d ' ')"
    digest=""
    if [ -f "$jar.sha256" ]; then
      digest="sha256:$(cut -d' ' -f1 <"$jar.sha256" | head -1)"
    fi
    printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$(version_key "$tag")" "$tag" "$(_yi_release_label "$tag" false)" "$jar" "$size" "$digest"
  done | LC_ALL=C sort -r -t "$(printf '\t')" -k1,1 | cut -f2-
}
