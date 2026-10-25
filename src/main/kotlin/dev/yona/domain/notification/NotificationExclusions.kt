package dev.yona.domain.notification

import org.springframework.stereotype.Component

/**
 * 알림에서 뺄 사용자를 요청 안에서 임시로 정한다. 받은 메일로 이슈나 댓글을 만들 때 그 메일을 이미 받은 사람(보낸 사람, 받는 사람, 참조)에게
 * 같은 내용의 알림 메일이 또 가지 않게 한다. v1.6 `CreationViaEmail.addEvent`가 이벤트의 수신자에서 이 사용자들을 빼고 저장하던 일에 해당한다.
 */
@Component
class NotificationExclusions {

    private val excluded = ThreadLocal<Set<Long>>()

    /** [userIds]를 뺀 채로 [block]을 실행한다. 끝나면 원래대로 되돌린다. */
    fun <T> without(userIds: Set<Long>, block: () -> T): T {
        val previous = excluded.get()
        excluded.set((previous ?: emptySet()) + userIds)
        try {
            return block()
        } finally {
            if (previous == null) excluded.remove() else excluded.set(previous)
        }
    }

    /** 지금 알림에서 빼야 하는 사용자 번호이다. */
    fun current(): Set<Long> = excluded.get() ?: emptySet()
}
