package com.github.yonaprojects.yona.domain.vcs

import com.github.yonaprojects.yona.domain.user.User
import org.tmatesoft.svn.core.SVNLogEntry
import java.util.Date
import java.util.TimeZone

class SvnCommit(
    private val entry: SVNLogEntry,
    private val userResolver: (String) -> User?
) : Commit() {

    private val idAsLong: Long
        get() = entry.revision

    override fun getMessage(): String? {
        return entry.message
    }

    override fun getAuthor(): User? {
        val authorName = getAuthorName() ?: return null
        return userResolver(authorName)
    }

    override fun getAuthorName(): String? {
        return entry.author
    }

    override fun getId(): String {
        return idAsLong.toString()
    }

    override fun getShortMessage(): String {
        val msg = getMessage()
        return if (!msg.isNullOrEmpty()) {
            val lines = msg.trim().split("\n")
            if (lines.isNotEmpty()) lines[0] else ""
        } else {
            ""
        }
    }

    override fun getAuthorEmail(): String? {
        return null
    }

    override fun getAuthorDate(): Date? {
        return entry.date
    }

    override fun getAuthorTimezone(): TimeZone? {
        return null
    }

    override fun getCommitterName(): String? {
        return getAuthorName()
    }

    override fun getCommitterEmail(): String? {
        return null
    }

    override fun getCommitterDate(): Date? {
        return getAuthorDate()
    }

    override fun getCommitterTimezone(): TimeZone? {
        return null
    }

    override fun getParentCount(): Int {
        return if (idAsLong > 1) 1 else 0
    }

    override fun getShortId(): String {
        return getId()
    }
}
