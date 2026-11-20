
# 릴리스 자산(설치 묶음)을 찾아 `이름|주소|digest`로 돌려준다. 사용법: _bs_asset <JSON> <태그> <자산 이름>
_bs_asset() {
  printf '%s' "$1" | _yi_releases_flatten | awk -F'|' -v tag="$2" -v name="$3" '$1 == tag && $5 == name { print $5 "|" $8 "|" $7; exit }'
}

bootstrap_main() {
  local json version tag="" arg args=() asset url digest work
  for arg in "$@"; do
    case "$arg" in
      --version=*) version="${arg#--version=}"; args+=("$arg") ;;
      *) args+=("$arg") ;;
    esac
  done
  json="$(releases_fetch)" || { echo "Cannot reach the release list. Check the network." >&2; return 1; }
  if [ -n "${version:-}" ]; then
    case "$version" in v*) tag="$version" ;; *) tag="v$version" ;; esac
  else
    tag="$(printf '%s' "$json" | _yi_releases_flatten | awk -F'|' '$3 != "true" { print $1; exit }')"
  fi
  [ -n "$tag" ] || { echo "No release was found." >&2; return 1; }
  asset="$(_bs_asset "$json" "$tag" yona-install.tar.gz)"
  if [ -z "$asset" ]; then
    echo "Release $tag has no yona-install.tar.gz asset. Download the installer files of that release manually." >&2
    return 1
  fi
  url="$(printf '%s' "$asset" | cut -d'|' -f2)"
  digest="$(printf '%s' "$asset" | cut -d'|' -f3)"
  work="$(mktemp -d "${TMPDIR:-/tmp}/yona-bootstrap.XXXXXX")"
  echo "Downloading the installer of $tag: $url"
  curl -fL --retry 2 -o "$work/yona-install.tar.gz" "$url" || { echo "Download failed." >&2; rm -rf "$work"; return 1; }
  verify_digest "$work/yona-install.tar.gz" "$digest"
  case $? in
    0) echo "The sha256 check passed." ;;
    2) echo "Warning: no digest is available, so the installer could not be verified." ;;
    *) echo "The sha256 of the downloaded installer does not match the release. Aborting." >&2; rm -rf "$work"; return 1 ;;
  esac
  tar -xzf "$work/yona-install.tar.gz" -C "$work" || { rm -rf "$work"; return 1; }
  # 고른 릴리스의 설치 프로그램에 같은 인자를 넘긴다(버전이 어긋나지 않는다).
  "$work/yona-install/yona-install.sh" "${args[@]+"${args[@]}"}"
  local rc=$?
  rm -rf "$work"
  return "$rc"
}

# 파일을 다 받은 뒤에만 실행되도록 맨 마지막 줄에서 부른다(받다가 끊긴 반쪽이 실행되지 않는다).
bootstrap_main "$@"
