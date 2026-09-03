package com.github.yonaprojects.yona.web

import java.time.Instant

data class HistoryDto(
    var who: String = "",
    var userPageUrl: String = "#",
    var userAvatarUrl: String = "/images/default-avatar-64.png",
    var whenInstant: Instant = Instant.now(),
    var where: String = "",
    var what: String = "", // "commit", "issue", "post", "pullrequest" 등
    var how: String = "",
    var shortTitle: String = "",
    var url: String = ""
)
