<!-- SPDX-License-Identifier: Apache-2.0
     https://www.apache.org/licenses/LICENSE-2.0 -->

# Yona — CLI operation catalogue

Shared reference for the `yona` CLI and `yona api` invocations the skills use against the project's Yona server.
Every verb of [`contract:tracker`](../tracker/README.md) and [`contract:change-request`](../change-request/README.md) is resolved below, plus the read-only contributor-activity verbs.

Placeholder convention used below:

- `<upstream>` — the Yona project slug `<owner>/<project>` from `<project manifest>.upstream_repo` (the issue tracker and the change requests live in the same project, so `<tracker>` is the same value).
- `<owner>` and `<project>` — the two halves of `<upstream>`.
- `<login>` — a Yona login ID. `<N>` — an issue or pull request number.
- `<since>`, `<end>`, `<until>` — a window: `since` inclusive, `end` / `until` exclusive. A date alone (`2026-02-01`) means 00:00 UTC; ISO-8601 (`2026-02-01T00:00:00Z`, `+09:00` allowed) is also accepted, and a value without a zone is read as UTC.
- `$YONA_HOST` — the server base URL. The server returns relative `url` fields; every permalink below is `${YONA_HOST%/}` plus that path.

All dates the server adds for this adapter (`closedAt`, `firstActivity`, `firstReviewAt`, discussion `date`) are UTC ISO-8601 with second precision (`2026-01-20T09:00:00Z`). The older `createdAt` / `updatedAt` fields use the server's time zone offset (`+0900` style); both are valid ISO-8601, so compare dates with a date parser, never as strings.

**Known limitation:** an issue that was imported through the REST API **by an older Yona server** without a `createdAt` has no creation time and is never returned by a window query (`--created-since` / `--created-until`). Current servers fill the time in on import, and the web UI and pull requests were never affected.

## Prelude

Every recipe below assumes this prelude. `yona_pages` runs a list command page by page (100 items per page) and prints one merged JSON array:

<!-- recipe:prelude -->
```bash
set -o pipefail
yona_pages() {
  local page=1 out
  while :; do
    out=$("$@" -L 100 --page "$page") || return 1
    echo "$out"
    [ "$(echo "$out" | jq 'length')" -lt 100 ] && break
    page=$((page + 1))
  done | jq -s 'add // []'
}
```

## Authentication

Every skill's Step 0 pre-flight must verify that `yona` is authenticated and can read `<upstream>`:

<!-- recipe:auth_check -->
```bash
yona auth status
yona api /-_-api/v1/owners/<owner>/projects/<project> | jq -r .name   # must print the project name; a 401/403/404 means stop
```

A non-zero exit on either command is a hard stop: the skill reports the failure and asks the user to run `yona auth login --server $YONA_HOST`, or to ask for membership of the project, rather than retrying. The authenticated user (`<viewer>`) is:

<!-- recipe:viewer -->
```bash
yona api /-_-api/v1/user | jq -r .loginId
```

## Contract: tracker

The read-only activity queries of [`contract:tracker`](../tracker/README.md). Activity means **comments**, the same basis as the GitHub adapter's `commenter:` search; label, state and assignee changes are not counted, and Yona has no component or priority field.

### `list_filed(<login>, <since>, <end>)`

<!-- recipe:list_filed -->
```bash
yona_pages yona issue list -R <upstream> --state all --author <login> --created-since <since> --created-until <end> \
  --json number,url,createdAt,closedAt,labels \
  | jq --arg server "${YONA_HOST%/}" 'map({id: (.number | tostring), permalink: ($server + .url), created: .createdAt, closed: .closedAt, labels: [.labels[].labelName]})'
```

### `list_triaged(<login>, <since>, <end>)`

Issues others filed that the person commented on, dated by the person's first comment in the window. `pushback_candidate` stays `null` unless the caller passes a phrase list and a maintainer roster (then add `&phrases=<phrases>&maintainers=<maintainers>`, both comma-separated and URL-encoded): the server scans the thread inside Yona and returns only the link of the first maintainer comment containing a phrase, never a comment body.

<!-- recipe:list_triaged -->
```bash
yona api "/-_-api/v1/owners/<owner>/projects/<project>/activity?actor=<login>&kind=triaged&since=<since>&until=<end>" \
  | jq --arg server "${YONA_HOST%/}" '[.items[] | {id: (.number | tostring), permalink: ($server + .url), first_activity: .firstActivity, labels: .labels, pushback_candidate: .pushbackCandidate}]'
```

### `list_commented(<login>, <since>, <end>)`

Issues and pull requests the person commented on (review-thread comments count for pull requests), dated by the first comment in the window:

<!-- recipe:list_commented -->
```bash
yona api "/-_-api/v1/owners/<owner>/projects/<project>/activity?actor=<login>&kind=commented&since=<since>&until=<end>" \
  | jq --arg server "${YONA_HOST%/}" '[.items[] | {id: (.number | tostring), permalink: ($server + .url), first_activity: .firstActivity, labels: .labels, pushback_candidate: .pushbackCandidate}]'
```

### `list_created(<since>, <until>)`

Every issue (`kind: issue`) and pull request (`kind: change`) opened in the window. `author_first_time` is `true` when it is the author's first issue or pull request in the project:

<!-- recipe:list_created -->
```bash
{
  yona_pages yona issue list -R <upstream> --state all --created-since <since> --created-until <until> --json number,url,createdAt,author,authorFirstTime \
    | jq --arg server "${YONA_HOST%/}" 'map({id: (.number | tostring), permalink: ($server + .url), kind: "issue", created: .createdAt, author: .author.loginId, author_first_time: .authorFirstTime})'
  yona_pages yona pr list -R <upstream> --state all --created-since <since> --created-until <until> --json number,url,createdAt,author,authorFirstTime \
    | jq --arg server "${YONA_HOST%/}" 'map({id: (.number | tostring), permalink: ($server + .url), kind: "change", created: .createdAt, author: .author.loginId, author_first_time: .authorFirstTime})'
} | jq -s 'add'
```

### `first_reply(<N>)`

The first maintainer reply, skipping bots. A maintainer is a project member with role `manager` or `member` (guests are not members). Yona has no bot flag: skip logins ending in `[bot]`, matching `dependabot` or `renovate`, and the optional `bot_logins` of the manifest (`<bot-logins>`, comma-separated, may be empty). Prints `null` when no maintainer replied.

<!-- recipe:first_reply -->
```bash
roster=$(yona api /-_-api/v1/owners/<owner>/projects/<project>/members | jq -c '[.members[] | select(.role == "manager" or .role == "member") | .loginId]')
yona api /-_-api/v1/owners/<owner>/projects/<project>/issues/<N>/comments \
  | jq --argjson roster "$roster" --arg server "${YONA_HOST%/}" --arg bots "<bot-logins>" \
      '($bots | split(",") | map(select(length > 0))) as $extra
       | [.result[] | select(.author.loginId as $a | $roster | index($a))
                    | select(.author.loginId | (test("\\[bot\\]$|dependabot|renovate") or (. as $l | $extra | index($l) != null)) | not)]
       | first // null
       | if . == null then null else {author: .author.loginId, created: .createdAt, url: ($server + "/<owner>/<project>/issue/<N>#comment-" + (.id | tostring)), body: .body} end'
```

## Contract: change-request

A Yona *pull request* is a request from a fork or branch to a target branch of the receiving project. Lifecycle mapping used by the recipes below: `merged` → `landed`, `closed` → `rejected`, otherwise `changes-requested` when an active review asked for changes, `approved` when the approval count reaches the required count, `under-review` when there is discussion, else `open`. `superseded` is never produced.

### `list_open(filter)`

`<filters>` is any combination of `--author <login>`, `--label <name>` (repeatable), `--review-requested <viewer>` (review-for-me), `--created-until <cutoff>` (age) and `--updated-before <cutoff>` (stale). The list is newest-updated first. The coarse state here is `approved` or `open`; use `status` for the full lifecycle value.

<!-- recipe:list_open -->
```bash
yona_pages yona pr list -R <upstream> --state open --sort updated <filters> --json number,title,author,createdAt,updatedAt,labels,url,approvals \
  | jq --arg server "${YONA_HOST%/}" 'map({id: (.number | tostring), title: .title, author: .author.loginId, created: .createdAt, updated: .updatedAt,
      state: (if .approvals.count > 0 and .approvals.count >= .approvals.required then "approved" else "open" end), labels: [.labels[].labelName], permalink: ($server + .url)})'
```

### `get(<N>)`

`diff` is the concatenated unified patch of every file; when any file has no patch (binary or too large) the contract allows a URL, so the recipe then returns the web changes page.

<!-- recipe:get -->
```bash
pr=$(yona pr view <N> -R <upstream> --json number,title,author,createdAt,updatedAt,state,labels,url,toBranch,mergeable,approvals) || exit 1
commits=$(yona api /-_-api/v1/owners/<owner>/projects/<project>/pullrequests/<N>/commits | jq '[.commits[] | {sha: .commitId, message: .message}]')
files=$(yona api /-_-api/v1/owners/<owner>/projects/<project>/pullrequests/<N>/files | jq '.files')
jq -n --argjson pr "$pr" --argjson commits "$commits" --argjson files "$files" --arg server "${YONA_HOST%/}" '
  {id: ($pr.number | tostring), title: $pr.title, author: $pr.author.loginId, created: $pr.createdAt, updated: $pr.updatedAt,
   state: (if $pr.state == "merged" then "landed" elif $pr.state == "closed" then "rejected" else "open" end),
   labels: [$pr.labels[].labelName], permalink: ($server + $pr.url), base: ($pr.toBranch | sub("^refs/heads/"; "")), commits: $commits,
   mergeable: (if $pr.mergeable == "clean" then "clean" elif $pr.mergeable == "conflicting" then "conflicting" else "unknown" end), checks: "none",
   diff: (if ($files | all(.patch != null and (.truncated | not))) then ($files | map("--- a/" + (.oldPath // .path) + "\n+++ b/" + .path + "\n" + .patch) | join("\n")) else ($server + $pr.url + "/changes") end)}'
```

### `get_discussion(<N>)`

`kind` is `comment` (review-thread comment), `review`, `approval` or `change-request`. Dismissed reviews and pending (unsubmitted) comments are not returned.

<!-- recipe:get_discussion -->
```bash
yona api /-_-api/v1/owners/<owner>/projects/<project>/pullrequests/<N>/discussion \
  | jq '{comments: [.comments[] | {author, date, body, kind}], participants: .participants, unresolved_count: .unresolvedCount}'
```

### `status(<N>)`

Yona has no CI, so `checks` is always `none`.

<!-- recipe:status -->
```bash
pr=$(yona pr view <N> -R <upstream> --json state,mergeable,approvals) || exit 1
disc=$(yona api /-_-api/v1/owners/<owner>/projects/<project>/pullrequests/<N>/discussion) || exit 1
jq -n --argjson pr "$pr" --argjson disc "$disc" '
  {state: (if $pr.state == "merged" then "landed" elif $pr.state == "closed" then "rejected"
           elif ([$disc.comments[] | select(.kind == "change-request")] | length) > 0 then "changes-requested"
           elif ($pr.approvals.count > 0 and $pr.approvals.count >= $pr.approvals.required) then "approved"
           elif ($disc.comments | length) > 0 then "under-review" else "open" end),
   checks: "none",
   mergeable: (if $pr.mergeable == "clean" then "clean" elif $pr.mergeable == "conflicting" then "conflicting" else "unknown" end)}'
```

### `post_review(<N>, <verdict>, <body>)`

`<verdict-flag>` is `--approve`, `--request-changes` or `--comment`. The skill confirms with the user before calling this; the adapter never gates. Write the body to `<body-file>` first (never inline a body that carries a handle or a quote):

<!-- recipe:post_review -->
```bash
yona pr review <N> -R <upstream> <verdict-flag> --body "$(cat <body-file>)"
```

### `land(<N>, <strategy>)`

`<strategy>` is `merge`, `squash` or `rebase`. The server merges and closes in one step. When the project does not allow the strategy the call fails with HTTP 400 `Merge method not allowed`; the adapter then retries once **without `--method`** (the project's default) and reports which strategy it used. `landed_ref` is the commit the target branch points at after the merge.

<!-- recipe:land -->
```bash
out=$(yona pr merge <N> -R <upstream> --method <strategy> --json mergeMethod,mergedCommitIdTo 2>/dev/null) \
  || out=$(yona pr merge <N> -R <upstream> --json mergeMethod,mergedCommitIdTo) || exit 1
echo "$out" | jq '{landed_ref: .mergedCommitIdTo, strategy_used: .mergeMethod}'
```

### `reject(<N>, <reason>)`

Closes without landing. The reason is posted as a comment first, then the pull request is closed. Write the reason to `<reason-file>`:

<!-- recipe:reject -->
```bash
yona pr comment <N> -R <upstream> --body "$(cat <reason-file>)" && yona pr close <N> -R <upstream>
```

## Contributor activity (read-only)

The Yona resolution of the read-only activity queries in [`contract:change-request`](../change-request/README.md#contributor-activity-queries-read-only).

### `list_authored(<login>, <since>, <end>, state)`

`state: any` is dated by creation; for `state: landed` replace the created window by `--state merged --merged-since <since> --merged-until <end>`. Without a person, drop `--author`.

<!-- recipe:list_authored -->
```bash
yona_pages yona pr list -R <upstream> --state all --author <login> --created-since <since> --created-until <end> \
  --json number,url,createdAt,author,state,mergedAt,labels,authorFirstTime \
  | jq --arg server "${YONA_HOST%/}" '{total: length, items: map({id: (.number | tostring), permalink: ($server + .url), author: .author.loginId,
      state: (if .state == "merged" then "landed" elif .state == "closed" then "rejected" else "open" end), created: .createdAt,
      landed_at: (if .state == "merged" then .mergedAt else null end), labels: [.labels[].labelName], author_first_time: .authorFirstTime})}'
```

### `list_reviews_given(<login>, <since>, <end>)`

`body_length` is the total length of the review bodies in the window and `inline_comment_count` counts review-thread comments; no body is returned.

<!-- recipe:list_reviews_given -->
```bash
yona api "/-_-api/v1/owners/<owner>/projects/<project>/activity/reviews?reviewer=<login>&since=<since>&until=<end>" \
  | jq --arg server "${YONA_HOST%/}" '{total: (.items | length), items: [.items[] | {change_id: (.pullRequestNumber | tostring), change_permalink: ($server + .url), reviewer: .reviewer,
      first_review_at: .firstReviewAt, body_length: .bodyLength, inline_comment_count: .inlineCommentCount, labels: .labels}]}'
```

### `list_authored_commits(<login>)`

Raises `NotApplicable`: Yona cannot search commits by author e-mail yet, so the consuming skill reports the stream as "not collected".

## Error handling

If any state-changing command (`post_review`, `land`, `reject`) fails, **stop**, report the failure verbatim and ask the user how to proceed. Never retry a write blindly. The only automatic retry is the strategy fallback of `land`.
