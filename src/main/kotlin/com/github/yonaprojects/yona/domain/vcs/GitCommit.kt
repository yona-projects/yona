package com.github.yonaprojects.yona.domain.vcs

import com.github.yonaprojects.yona.domain.user.User
import org.eclipse.jgit.revwalk.RevCommit
import java.util.*

class GitCommit(
    private val revCommit: RevCommit,
    private val userResolver: (String?, String?) -> User?
) : Commit() {

    override fun getId(): String {
        return revCommit.name
    }

    override fun getShortId(): String {
        return if (revCommit.name.length > 7) revCommit.name.substring(0, 7) else revCommit.name
    }

    override fun getMessage(): String? {
        return revCommit.fullMessage
    }

    override fun getShortMessage(): String {
        return revCommit.shortMessage ?: ""
    }

    override fun getAuthor(): User? {
        return userResolver(null, getAuthorEmail())
    }

    override fun getAuthorName(): String? {
        return revCommit.authorIdent?.name
    }

    override fun getAuthorEmail(): String? {
        return revCommit.authorIdent?.emailAddress
    }

    override fun getAuthorDate(): Date? {
        return revCommit.authorIdent?.`when`
    }

    override fun getAuthorTimezone(): TimeZone? {
        return revCommit.authorIdent?.timeZone
    }

    override fun getCommitterName(): String? {
        return revCommit.committerIdent?.name
    }

    override fun getCommitterEmail(): String? {
        return revCommit.committerIdent?.emailAddress
    }

    override fun getCommitterDate(): Date? {
        return revCommit.committerIdent?.`when`
    }

    override fun getCommitterTimezone(): TimeZone? {
        return revCommit.committerIdent?.timeZone
    }

    override fun getParentCount(): Int {
        return revCommit.parentCount
    }
}
