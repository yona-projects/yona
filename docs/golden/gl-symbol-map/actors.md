---
id: gl-actors
type: evidence
status: done
created: 2026-08-26
updated: 2026-08-26
relates_to: []
source: docs/golden/GOLDEN_PARITY_LEDGER.md — 이관 전 버전은 git 히스토리 참고
---

# 버킷 C — 공백 후보: `actors` 영역 (21개 심볼)

자동 분류 결과이며, 실제 공백인지는 사람이 개별 확인해야 한다(자동 승격 금지). 상세 방법론은 [[../methodology]] 참고.

| GL-ID | yona 파일:라인 | 선언 |
|---|---|---|
| GL-actors_RelatedPullRequestMergingActor-001 | `app/actors/RelatedPullRequestMergingActor.java:29` | `public class RelatedPullRequestMergingActor extends PullRequestActor {` |
| GL-actors_RelatedPullRequestMergingActor-002 | `app/actors/RelatedPullRequestMergingActor.java:31` | `@Override` |
| GL-actors_RelatedPullRequestMergingActor-003 | `app/actors/RelatedPullRequestMergingActor.java:47` | `private void changeStateToMerging(List<PullRequest> pullRequests) {` |
| GL-actors_RelatedPullRequestMergingActor-004 | `app/actors/RelatedPullRequestMergingActor.java:55` | `private void processPullRequests(PullRequestEventMessage message, List<PullRequest> pullRequests) {` |
| GL-actors_PullRequestMergingActor-001 | `app/actors/PullRequestMergingActor.java:27` | `/**` |
| GL-actors_PullRequestMergingActor-002 | `app/actors/PullRequestMergingActor.java:33` | `@Override` |
| GL-actors_IssueReferredFromCommitEventActor-001 | `app/actors/IssueReferredFromCommitEventActor.java:35` | `/**` |
| GL-actors_IssueReferredFromCommitEventActor-002 | `app/actors/IssueReferredFromCommitEventActor.java:43` | `@Override` |
| GL-actors_IssueReferredFromCommitEventActor-003 | `app/actors/IssueReferredFromCommitEventActor.java:52` | `private void addIssueEvent(RevCommit commit, Project project, User user) {` |
| GL-actors_PostReceiveActor-001 | `app/actors/PostReceiveActor.java:39` | `/**` |
| GL-actors_PostReceiveActor-002 | `app/actors/PostReceiveActor.java:50` | `@Override` |
| GL-actors_PostReceiveActor-003 | `app/actors/PostReceiveActor.java:61` | `abstract void doReceive(PostReceiveMessage cap);` |
| GL-actors_PostReceiveActor-004 | `app/actors/PostReceiveActor.java:64` | `class CommitAndRefNames {` |
| GL-actors_PostReceiveActor-005 | `app/actors/PostReceiveActor.java:95` | `protected CommitAndRefNames commitAndRefNames(PostReceiveMessage message) {` |
| GL-actors_PostReceiveActor-007 | `app/actors/PostReceiveActor.java:116` | `protected Collection<? extends RevCommit> parseCommitsFrom(ReceiveCommand command, Project project) ` |
| GL-actors_CommitsNotificationActor-001 | `app/actors/CommitsNotificationActor.java:18` | `/**` |
| GL-actors_CommitsNotificationActor-002 | `app/actors/CommitsNotificationActor.java:24` | `@Override` |
| GL-actors_PullRequestActor-001 | `app/actors/PullRequestActor.java:29` | `public abstract class PullRequestActor extends UntypedActor {` |
| GL-actors_PullRequestActor-002 | `app/actors/PullRequestActor.java:32` | `protected void processPullRequestMerging(PullRequestEventMessage message, PullRequest pullRequest) {` |
| GL-actors_ValidationEmailSender-001 | `app/actors/ValidationEmailSender.java:33` | `/**` |
| GL-actors_ValidationEmailSender-002 | `app/actors/ValidationEmailSender.java:39` | `@Override` |
