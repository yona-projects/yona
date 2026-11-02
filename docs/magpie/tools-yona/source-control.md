<!-- SPDX-License-Identifier: Apache-2.0
     https://www.apache.org/licenses/LICENSE-2.0 -->

# Yona — source-control (VCS) capability

A Yona project that uses Git is an ordinary Git remote: `https://<host>/<owner>/<project>.git` over HTTP(S) (the PAT is the password, the login ID the user name) or the built-in SSH server.
Every operation the skills need (branch, commit, diff, log, fetch, push, worktree) is therefore the plain `git` binding documented in [`../github/source-control.md`](../github/source-control.md); nothing Yona-specific is required.

Two points differ from GitHub:

- A change request is created with `yona pr create -R <upstream> --title <t> --from <fork-owner>/<fork-project> --from-branch <branch> --to-branch <branch>`; there is no `gh pr create --web` browser step in the adapter.
- Subversion projects on Yona are not covered by this adapter.
