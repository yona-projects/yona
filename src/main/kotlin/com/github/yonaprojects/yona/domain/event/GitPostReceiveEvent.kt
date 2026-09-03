package com.github.yonaprojects.yona.domain.event

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.User
import org.eclipse.jgit.transport.ReceiveCommand

data class GitPostReceiveEvent(
    val project: Project,
    val user: User,
    val commands: List<ReceiveCommand>
)
