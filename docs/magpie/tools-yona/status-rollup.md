<!-- SPDX-License-Identifier: Apache-2.0
     https://www.apache.org/licenses/LICENSE-2.0 -->

# Status-rollup comment — Yona recipes

Every agent-authored status update on a tracker issue lands in **one single rollup comment** per tracker. Each pass appends a new *entry* to that comment instead of posting a fresh one, so the timeline shows one rollup plus the human discussion. The comment shape, the action labels, the entry body rules, the legacy-comment folding and the hard rules are defined in [`../github/status-rollup.md`](../github/status-rollup.md) and are the same on Yona; this file only gives the Yona recipes that replace the `github-rollup` tool.

## Differences to keep in mind

- A Yona comment is edited with `PATCH …/comments/<comment-id>`; there is no GitHub-style minimize, so a folded legacy comment is deleted (`DELETE …/comments/<comment-id>`) only after the append succeeded.
- Yona renders the `<details>` blocks of the shape like GitHub does, with the same rule: one blank line after `<summary>…</summary>` and before `</details>`, and no leading whitespace on any line of an entry.
- The rollup author must be the authenticated user or a **project member** (the [roster](operations.md#collaborator-lookup-roster)). A marker line in a comment by anyone else is ignored.

## Find the rollup

Prints the rollup comment (`id` and `body`), `null` when there is none, and fails when there are several (two rollups on one tracker is a bug: stop and ask the user which one to keep):

<!-- recipe:rollup_find -->
```bash
roster=$(yona api /-_-api/v1/owners/<owner>/projects/<project>/members | jq -c '[.members[].loginId]')
viewer=$(yona api /-_-api/v1/user | jq -r .loginId)
yona api /-_-api/v1/owners/<owner>/projects/<project>/issues/<N>/comments \
  | jq -c --argjson roster "$roster" --arg viewer "$viewer" '[.result[]
      | select(.body | startswith("<!-- apache-magpie: status-rollup v"))
      | select(.author.loginId as $a | $a == $viewer or ($roster | index($a)))]
      | if length > 1 then error("more than one status rollup on the tracker") else (first // null | if . == null then null else {id, body} end) end'
```

## Append an entry (creates the rollup when there is none)

Write the entry body (without the `<details>` envelope) to `<entry-file>` with the Write tool. The recipe adds the `<details><summary>YYYY-MM-DD · @user · <action></summary>` envelope and the ruler, appends to the existing rollup or creates it with the marker line, and prints the comment id:

<!-- recipe:rollup_append -->
```bash
viewer=$(yona api /-_-api/v1/user | jq -r .loginId)
entry=$(jq -rn --rawfile body <entry-file> --arg date "$(date -u +%F)" --arg user "$viewer" --arg action "<action>" \
  '"<details><summary>\($date) · @\($user) · \($action)</summary>\n\n\($body | rtrimstr("\n"))\n\n</details>"')
existing=$(yona api /-_-api/v1/owners/<owner>/projects/<project>/issues/<N>/comments \
  | jq -c --arg viewer "$viewer" '[.result[] | select(.body | startswith("<!-- apache-magpie: status-rollup v")) | select(.author.loginId == $viewer)]
      | if length > 1 then error("more than one status rollup on the tracker") else (first // null) end') || exit 1
if [ "$existing" = "null" ]; then
  jq -n --arg comment "<!-- apache-magpie: status-rollup v1 -->"$'\n'"$entry" '{comment: $comment}' \
    | yona api -X POST /-_-api/v1/owners/<owner>/projects/<project>/issues/<N>/comments --input - > /dev/null
else
  id=$(echo "$existing" | jq -r .id)
  jq -n --argjson old "$existing" --arg entry "$entry" '{body: ($old.body + "\n\n---\n\n" + $entry)}' \
    | yona api -X PATCH /-_-api/v1/owners/<owner>/projects/<project>/issues/<N>/comments/$id --input - > /dev/null
fi
yona api /-_-api/v1/owners/<owner>/projects/<project>/issues/<N>/comments | jq -r --arg viewer "$viewer" '[.result[] | select(.body | startswith("<!-- apache-magpie: status-rollup v")) | select(.author.loginId == $viewer)] | first | .id'
```

`<action>` is one of the action labels of the shared shape (`Import`, `Sync`, `Reformat`, `CVE allocated`, `Merge (kept)`, `Merge (dropped)`, `Fix PR`).

## Fold a legacy comment

Detection and the proposal step are as in the shared file. Folding is two steps, in this order: append the legacy body as an entry with `rollup_append` (dated by the legacy comment, so write its text, left-trimmed, to `<entry-file>` and pass its original date in the summary by editing the entry), and only when that succeeded delete the original:

<!-- recipe:rollup_delete_legacy -->
```bash
yona api -X DELETE /-_-api/v1/owners/<owner>/projects/<project>/issues/<N>/comments/<comment-id> | jq -r '.result.number // "deleted"'
```

## Hard rules

The hard rules of the shared file apply unchanged: never touch a human-authored comment, never delete a legacy comment before the append succeeded, never rewrite a folded entry, keep the order chronological, keep every line flush-left, and never create two rollups on one tracker.
