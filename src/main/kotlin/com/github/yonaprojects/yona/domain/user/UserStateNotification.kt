package com.github.yonaprojects.yona.domain.user

// v1.6 filterInactiveUsers()/NotificationMail.sendNotification()는 state != ACTIVE만 뺐다. v1.6에서 사이트 관리자는 SiteAdmin 테이블,
// 게스트는 isGuest 불리언이라 state가 ACTIVE였으므로 알림을 받았다. next는 둘을 state(SITE_ADMIN/GUEST)로 표현하므로
// 같은 동작이 되려면 잠금·삭제된 계정만 제외해야 한다.
fun UserState.canReceiveNotifications(): Boolean = this != UserState.LOCKED && this != UserState.DELETED
