<!-- SPDX-License-Identifier: Apache-2.0
     https://www.apache.org/licenses/LICENSE-2.0 -->

# Yona — lifecycle label taxonomy

The **generic** label taxonomy that every Yona-backed tracker shares. These labels drive the state machine the skills reconcile; their spellings and meanings are project-agnostic and are the same as in the [GitHub taxonomy](../github/labels.md), so a project can change its tracker without renaming labels.

Project-specific labels, in particular the **scope labels** that pin a tracker to a product family, live in the adopting project's directory.

## How Yona labels differ from GitHub labels

- A Yona label belongs to a **category** (`lifecycle` below). A label is addressed by its name in the recipes and by its numeric `id` in the API; the recipes resolve one to the other.
- A category can be **exclusive** (only one of its labels at a time) or not. The lifecycle labels replace each other as the issue advances, so the project may mark the `lifecycle` category exclusive in the web UI (project settings, labels). The label recipes work either way: `issue_edit_labels` adds and removes in one write, so the exclusive rule is never violated half-way. Closing-disposition labels belong in a separate category, because they combine with lifecycle labels.
- Colors are CSS colors (`#d73a4a`). A label cannot be created without a category.

## Lifecycle labels

Category `lifecycle`.

| Label | Meaning | Added at process step | Removed at process step |
|---|---|---|---|
| `needs triage` | Freshly filed; assessment not yet started. | 1 | 5 |
| *scope label* | Project-specific scope pin. Exactly one is set after triage. | 5 | never |
| `cve allocated` | A CVE has been reserved for the issue. | 6 | never |
| `pr created` | A public fix pull request has been opened on the upstream repository but has not yet merged. | 10 | 11 (replaced by `pr merged`) |
| `pr merged` | The fix pull request has merged upstream; no release carrying the fix has shipped yet. | 11 | 12 (replaced by `fix released`) |
| `fix released` | A release carrying the fix has shipped; the advisory has not been sent yet. | 12 | 13 (replaced by `announced - emails sent`) |
| `announced - emails sent` | The public advisory has been sent to the project's mailing lists. The issue **stays open** until the release manager completes the last step. | 13 | never |
| `announced` | The public advisory URL has been captured in the tracking issue and the CVE record regenerated. | 14 | never |

## Closing-disposition labels

Category `disposition`. Applied when a tracker leaves the lifecycle without producing a CVE. Mutually exclusive: a tracker closes with exactly one of `invalid`, `not CVE worthy`, `duplicate`, `wontfix` (meanings as in the GitHub taxonomy).

## Secondary labels

| Label | Meaning |
|---|---|
| `security issue` | Flags the issue as security-related for filters. On GitHub the issue template applies it; Yona has no issue form, so the skill that creates the issue adds it with `issue_edit_labels`. |

## Checking which labels are missing

A skill never creates a label silently. This read-only recipe prints the taxonomy labels the project does not have yet (an empty output means none is missing), so the skill can propose `label_create` for each one and wait for a yes:

<!-- recipe:labels_missing -->
```bash
yona label list -R <upstream> --json labelName \
  | jq -r --argjson wanted '["needs triage","cve allocated","pr created","pr merged","fix released","announced - emails sent","announced","invalid","not CVE worthy","duplicate","wontfix","security issue"]' \
      '[.[].labelName] as $have | $wanted[] | select(. as $w | $have | index($w) | not)'
```

## Maintenance

The `security-issue-sync` skill is the authority on label transitions: on every run it detects the current state (labels, body fields, fix pull request state, release state) and proposes the transitions the process requires. Adding a new generic lifecycle label is a **process change** that is proposed, reviewed and merged together with the new label in the tracker (`label_create` in [`operations.md`](operations.md#create-2)).
