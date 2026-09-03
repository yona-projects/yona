package com.github.yonaprojects.yona.domain.vcs

import com.github.yonaprojects.yona.domain.user.User
import org.eclipse.jgit.lib.Constants

data class GitBranch(
    val name: String,
    val headCommit: Commit,
    val user: User? = null
) {
    val shortName: String = name.removePrefix(Constants.R_HEADS)
}
