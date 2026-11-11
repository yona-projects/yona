

## [2026-11-11] feat | Mercurial "기본으로 설정" 구현 (A-467, code-F7)

선생님 결정에 따라 아무 일도 하지 않던 단추를 구현했다. 기본으로 정한 북마크 이름을 `.hg/yona-default-branch`에 적고 기본 판정의 첫 후보로 삼는다. `@` 북마크로 구현하려던 처음 안은 `@`가 리비전에 고정되어 새 커밋을 따라가지 않는 결함이 있어 바꿨다(A-467). `VcsType.MERCURIAL`에 `DEFAULT_BRANCH`를 켜고 서버는 없는 이름을 400으로 답한다. 계약 문서(`code.md`, `code-vcs.md`, `project-settings.md`)를 맞췄다.
