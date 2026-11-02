<!-- SPDX-License-Identifier: Apache-2.0
     https://www.apache.org/licenses/LICENSE-2.0 -->

# `tools/yona/`

**Capability:** contract:tracker + contract:source-control + contract:change-request

**Kind:** implementation

**Vendor:** Yona

[Yona](https://github.com/yona-projects/yona) is a self-hosted code-hosting and project-management server (Git and Subversion repositories, issues, pull requests, code review).
This directory documents the **Yona** tool adapter: the recipes the skills run when the adopting project declares Yona as its tracker and change-request backend.
See [`tool.md`](tool.md) for the capability overview and [`operations.md`](operations.md) for the per-verb recipes.

The adapter is **doc-only** and uses a single tool family, the `yona` CLI (`yona <command>` and the raw `yona api` call, the same way the GitHub adapter uses `gh` and `gh api`).
It never calls `curl`, so it also works under the shipped secure setup.

## Coverage

Every verb of `contract:tracker` (`list_filed`, `list_triaged`, `list_commented`, `list_created`, `first_reply`) and of `contract:change-request` (`list_open`, `get`, `get_discussion`, `status`, `post_review`, `land`, `reject`) is implemented.
Of the contributor-activity verbs, `list_authored` and `list_reviews_given` are implemented; `list_authored_commits` raises `NotApplicable` (Yona has no commit search by author e-mail yet).
Issue-body schemas, lifecycle labels and project boards are **out of scope** for this adapter, so `issue-template.md`, `labels.md` and `project-board.md` are not provided.

## Prerequisites

- **Runtime:** Bash — this is a doc-only adapter; skills invoke the `yona` CLI and `git`, no local package.
- **CLIs:** `yona` **0.0.0-main or newer from the commit that added `--created-since`, `pr merge --method` and `--json mergedCommitIdTo`** (run `yona pr merge --help` and look for `--method`), `git` (source-control capability), `jq` (the recipes pipe `yona … --json` and `yona api` output through `jq`).
- **Credentials / auth:** `yona auth status` must show a logged-in user. The token is a personal access token (PAT) created in the Yona web UI. Scope `read:repo` is enough for every read verb; `post_review`, `land` and `reject` need scope `repo`. Prefer a dedicated bot account that is a member of the project, with only the scope the project needs.
- **Environment:** `YONA_HOST` must hold the server base URL (for example `https://yona.example.com`). It is used to turn the server's relative `url` fields into absolute permalinks.
- **Network:** the Yona host (API and Git remote); source-control recipes are offline except explicit `fetch` / `push`.

## Configuration

Adopters select Yona through `<project-config>/project.md`:

- `upstream_repo` and `tracker_repo`: the Yona project as `<owner>/<project>` (the two halves are written `<owner>` and `<project>` below).
- `change_request.backend: yona` and `tracker_type: yona` (with `url:` set to the server base URL and `project_key: <owner>/<project>`).
- Optional `bot_logins`: a comma-separated list of Yona logins that `first_reply` must skip (Yona has no bot flag).
