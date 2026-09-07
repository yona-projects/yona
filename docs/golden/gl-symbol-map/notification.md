---
id: gl-notification
type: evidence
status: done
created: 2026-08-26
updated: 2026-08-26
relates_to: []
source: docs/golden/GOLDEN_PARITY_LEDGER.md — 이관 전 버전은 git 히스토리 참고
---

# 버킷 C — 공백 후보: `notification` 영역 (19개 심볼)

자동 분류 결과이며, 실제 공백인지는 사람이 개별 확인해야 한다(자동 승격 금지). 상세 방법론은 [[../methodology]] 참고.

| GL-ID | yona 파일:라인 | 선언 |
|---|---|---|
| GL-notification_INotificationEvent-001 | `app/notification/INotificationEvent.java:19` | `public interface INotificationEvent {` |
| GL-notification_INotificationEvent-012 | `app/notification/INotificationEvent.java:51` | `boolean resourceExists();` |
| GL-notification_INotificationEvent-013 | `app/notification/INotificationEvent.java:54` | `Set<User> findReceivers();` |
| GL-notification_MergedNotificationEvent-001 | `app/notification/MergedNotificationEvent.java:20` | `public class MergedNotificationEvent implements INotificationEvent {` |
| GL-notification_MergedNotificationEvent-005 | `app/notification/MergedNotificationEvent.java:29` | `public MergedNotificationEvent(@Nonnull INotificationEvent main,` |
| GL-notification_MergedNotificationEvent-006 | `app/notification/MergedNotificationEvent.java:36` | `public MergedNotificationEvent(@Nonnull INotificationEvent main) {` |
| GL-notification_MergedNotificationEvent-007 | `app/notification/MergedNotificationEvent.java:41` | `@Override` |
| GL-notification_MergedNotificationEvent-008 | `app/notification/MergedNotificationEvent.java:47` | `@Override` |
| GL-notification_MergedNotificationEvent-009 | `app/notification/MergedNotificationEvent.java:53` | `@Override` |
| GL-notification_MergedNotificationEvent-010 | `app/notification/MergedNotificationEvent.java:63` | `@Override` |
| GL-notification_MergedNotificationEvent-011 | `app/notification/MergedNotificationEvent.java:73` | `@Override` |
| GL-notification_MergedNotificationEvent-012 | `app/notification/MergedNotificationEvent.java:79` | `@Override` |
| GL-notification_MergedNotificationEvent-013 | `app/notification/MergedNotificationEvent.java:85` | `@Override` |
| GL-notification_MergedNotificationEvent-014 | `app/notification/MergedNotificationEvent.java:91` | `@Override` |
| GL-notification_MergedNotificationEvent-015 | `app/notification/MergedNotificationEvent.java:97` | `@Override` |
| GL-notification_MergedNotificationEvent-016 | `app/notification/MergedNotificationEvent.java:103` | `@Override` |
| GL-notification_MergedNotificationEvent-017 | `app/notification/MergedNotificationEvent.java:109` | `@Override` |
| GL-notification_MergedNotificationEvent-018 | `app/notification/MergedNotificationEvent.java:115` | `@Override` |
| GL-notification_MergedNotificationEvent-019 | `app/notification/MergedNotificationEvent.java:125` | `@Override` |
