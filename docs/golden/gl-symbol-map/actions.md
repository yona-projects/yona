---
id: gl-actions
type: evidence
status: done
created: 2026-08-26
updated: 2026-08-26
relates_to: []
source: docs/golden/GOLDEN_PARITY_LEDGER.md — 이관 전 버전은 git 히스토리 참고
---

# 버킷 C — 공백 후보: `actions` 영역 (24개 심볼)

자동 분류 결과이며, 실제 공백인지는 사람이 개별 확인해야 한다(자동 승격 금지). 상세 방법론은 [[../methodology]] 참고.

| GL-ID | yona 파일:라인 | 선언 |
|---|---|---|
| GL-actions_GuestProhibitAction-001 | `app/actions/GuestProhibitAction.java:21` | `/**` |
| GL-actions_GuestProhibitAction-002 | `app/actions/GuestProhibitAction.java:31` | `@Override` |
| GL-actions_NullProjectCheckAction-001 | `app/actions/NullProjectCheckAction.java:39` | `/**` |
| GL-actions_NullProjectCheckAction-002 | `app/actions/NullProjectCheckAction.java:49` | `@Override` |
| GL-actions_IsOnlyGitAvailableAction-001 | `app/actions/IsOnlyGitAvailableAction.java:34` | `/**` |
| GL-actions_IsAllowedAction-001 | `app/actions/IsAllowedAction.java:39` | `/**` |
| GL-actions_IsAllowedAction-002 | `app/actions/IsAllowedAction.java:52` | `@Override` |
| GL-actions_AbstractProjectCheckAction-001 | `app/actions/AbstractProjectCheckAction.java:39` | `/**` |
| GL-actions_AbstractProjectCheckAction-002 | `app/actions/AbstractProjectCheckAction.java:50` | `@Override` |
| GL-actions_AbstractProjectCheckAction-003 | `app/actions/AbstractProjectCheckAction.java:99` | `protected abstract Promise<Result> call(Project project, Context context, PathParser parser)` |
| GL-actions_AnonymousCheckAction-001 | `app/actions/AnonymousCheckAction.java:36` | `/**` |
| GL-actions_AnonymousCheckAction-002 | `app/actions/AnonymousCheckAction.java:46` | `@Override` |
| GL-actions_IsCreatableAction-001 | `app/actions/IsCreatableAction.java:37` | `/**` |
| GL-actions_IsCreatableAction-002 | `app/actions/IsCreatableAction.java:47` | `@Override` |
| GL-actions_CodeAccessCheckAction-001 | `app/actions/CodeAccessCheckAction.java:18` | `public class CodeAccessCheckAction extends AbstractProjectCheckAction<Void> {` |
| GL-actions_CodeAccessCheckAction-002 | `app/actions/CodeAccessCheckAction.java:20` | `@Override` |
| GL-actions_DefaultProjectCheckAction-001 | `app/actions/DefaultProjectCheckAction.java:31` | `/**` |
| GL-actions_DefaultProjectCheckAction-002 | `app/actions/DefaultProjectCheckAction.java:40` | `@Override` |
| GL-actions_support_PathParser-001 | `app/actions/support/PathParser.java:30` | `/**` |
| GL-actions_support_PathParser-004 | `app/actions/support/PathParser.java:44` | `public PathParser(String path) {` |
| GL-actions_support_PathParser-005 | `app/actions/support/PathParser.java:52` | `public PathParser(String contextPath, String path) {` |
| GL-actions_support_PathParser-006 | `app/actions/support/PathParser.java:61` | `public PathParser(Http.Context context) {` |
| GL-actions_support_PathParser-010 | `app/actions/support/PathParser.java:81` | `public String toString() {` |
| GL-actions_support_PathParser-011 | `app/actions/support/PathParser.java:86` | `public String restOfPathExceptOwnerAndProjectName() {` |
