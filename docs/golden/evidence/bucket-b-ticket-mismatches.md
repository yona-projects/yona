---
id: bucket-b-ticket-mismatches
type: evidence
status: done
created: 2026-08-26
updated: 2026-08-26
relates_to: []
source: docs/golden/GOLDEN_PARITY_LEDGER.md — 이관 전 버전은 git 히스토리 참고
---

## 버킷 B — 티켓 상태 불일치 / 고아 티켓

| yona 파일:라인 | 티켓 | 티켓 상태 | yona 파일:범위 |
|---|---|---|---|
| `src/main/kotlin/com/github/yonaprojects/yona/domain/board/PostingRepository.kt:21` | TASK-0244 | ORPHAN | `organization/group_board_list.scala.html:65-71` |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/issue/IssueRepository.kt:27` | TASK-0244 | ORPHAN | `organization/group_issue_search_partial.scala.html:72` |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/organization/OrganizationService.kt:47` | TASK-0244 | ORPHAN | `OrganizationApp.java:287-311` |
| `src/main/kotlin/com/github/yonaprojects/yona/domain/project/Project.kt:100` | 그룹11 #168 | ORPHAN | `Project.java:850` |
| `src/main/kotlin/com/github/yonaprojects/yona/web/BoardViewController.kt:246` | TASK-0263 | ORPHAN | `board/create.scala.html:100-106` |
| `src/main/kotlin/com/github/yonaprojects/yona/web/BoardViewController.kt:294` | TASK-0263 | ORPHAN | `board/edit.scala.html:59` |
| `src/main/kotlin/com/github/yonaprojects/yona/web/CodeViewController.kt:492` | 그룹2 #39 | ORPHAN | `code/svnDiff.scala.html:37-50` |
| `src/main/kotlin/com/github/yonaprojects/yona/web/IssueController.kt:72` | P2-12 | ORPHAN | `AccessControl.java:244-248` |
| `src/main/kotlin/com/github/yonaprojects/yona/web/IssueViewController.kt:223` | 그룹7 #119 | ORPHAN | `partial_list_wrap.scala.html:84-86` |
| `src/main/kotlin/com/github/yonaprojects/yona/web/IssueViewController.kt:333` | 그룹7 #127 | ORPHAN | `issue/view.scala.html:329-381` |
| `src/main/kotlin/com/github/yonaprojects/yona/web/IssueViewController.kt:523` | 그룹7 #125 | ORPHAN | `partial_select_subtask.scala.html:10` |
| `src/main/kotlin/com/github/yonaprojects/yona/config/TemplateHelper.kt:501` | TASK-0244 | ORPHAN | `models/OrganizationUser.java:62-68` |
| `src/test/kotlin/com/github/yonaprojects/yona/web/IssueControllerSpec.kt:572` | P2-12 | ORPHAN | `AccessControl.java:244-248` |
| `src/test/kotlin/com/github/yonaprojects/yona/web/IssueControllerSpec.kt:775` | P2-12 | ORPHAN | `AccessControl.java:244-248` |
| `src/test/kotlin/com/github/yonaprojects/yona/web/IssueControllerSpec.kt:888` | P2-12 | ORPHAN | `AccessControl.java:244-248` |
| `src/test/kotlin/com/github/yonaprojects/yona/web/IssueControllerSpec.kt:937` | P2-12 | ORPHAN | `AccessControl.java:244-248` |
| `src/test/kotlin/com/github/yonaprojects/yona/web/BoardViewControllerSpec.kt:253` | TASK-0263 | ORPHAN | `board/create.scala.html:100-106` |
