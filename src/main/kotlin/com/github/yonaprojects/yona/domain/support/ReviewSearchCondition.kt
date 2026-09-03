package com.github.yonaprojects.yona.domain.support

data class ReviewSearchCondition(
    var state: String = "OPEN",
    var authorId: Long? = null,
    var participantId: Long? = null,
    var orderBy: String = "createdDate",
    var orderDir: String = "desc",
    var filter: String = "",
    var pageNum: Int = 1
) {
    fun clone(): ReviewSearchCondition {
        return this.copy()
    }

    fun setState(state: String): ReviewSearchCondition {
        this.state = state
        return this
    }

    fun setAuthorId(authorId: Long?): ReviewSearchCondition {
        this.authorId = authorId
        return this
    }

    fun setParticipantId(participantId: Long?): ReviewSearchCondition {
        this.participantId = participantId
        return this
    }
}
