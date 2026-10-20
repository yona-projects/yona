package com.github.yonaprojects.yona.domain.event

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.User

// GitPostReceiveEvent/HgPostReceiveEvent의 SVN 대응. 한 번의 HTTP 커밋(MERGE)이 만든 revision 구간.
data class SvnPostCommitEvent(
    val project: Project,
    val user: User,
    val fromRevision: Long,
    val toRevision: Long
)
