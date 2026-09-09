package com.github.yonaprojects.yona.domain.event

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.vcs.HgBookmarkMove

// GitPostReceiveEvent(org.eclipse.jgit.transport.ReceiveCommand에 결합돼 재사용
// 불가)의 Mercurial 대응. HgBookmarkMove(도메인/vcs/HgPushHooks.kt)는 이미 VCS 무관한 형태(이름/
// old·new node hex/변경 타입)라 그대로 감싼다.
data class HgPostReceiveEvent(
    val project: Project,
    val user: User,
    val move: HgBookmarkMove
)
