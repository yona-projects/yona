package dev.yona.domain.vcs

import java.util.Date

/**
 * 푸시로 들어온 커밋 하나의 값이다. 저장소 종류와 무관하며, 새 커밋 알림, 푸시 웹훅, 이슈 언급 이벤트가 이 값만 쓴다
 * (Mercurial 도입을 위한 공통화, X-47). 작성자와 커미터를 따로 담는다.
 *
 * @property id 커밋 전체 번호
 * @property shortId 7자리 번호
 * @property shortMessage 메시지의 첫 문단(줄바꿈은 공백)
 * @property message 메시지 전체
 * @property commitTime 커밋 시각
 */
data class PushedCommit(
    val id: String, val shortId: String, val shortMessage: String, val message: String, val authorName: String, val authorEmail: String,
    val committerName: String, val committerEmail: String, val commitTime: Date
)
