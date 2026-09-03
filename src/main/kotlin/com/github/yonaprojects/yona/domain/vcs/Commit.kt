package com.github.yonaprojects.yona.domain.vcs

import com.github.yonaprojects.yona.domain.user.User
import java.util.Date
import java.util.TimeZone

abstract class Commit {
    abstract fun getShortId(): String
    abstract fun getId(): String
    abstract fun getShortMessage(): String
    abstract fun getMessage(): String?
    abstract fun getAuthor(): User?
    abstract fun getAuthorName(): String?
    abstract fun getAuthorEmail(): String?
    abstract fun getAuthorDate(): Date?
    abstract fun getAuthorTimezone(): TimeZone?
    abstract fun getCommitterName(): String?
    abstract fun getCommitterEmail(): String?
    abstract fun getCommitterDate(): Date?
    abstract fun getCommitterTimezone(): TimeZone?
    abstract fun getParentCount(): Int
}
