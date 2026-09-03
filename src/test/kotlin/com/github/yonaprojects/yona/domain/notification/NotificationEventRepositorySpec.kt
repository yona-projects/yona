package com.github.yonaprojects.yona.domain.notification

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.enumeration.EventType
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Transactional
class NotificationEventRepositorySpec @Autowired constructor(
    private val notificationEventRepository: NotificationEventRepository,
    private val userRepository: UserRepository
) : AbstractIntegrationTest() {

    init {
        describe("NotificationEventRepository") {
            beforeEach {
                notificationEventRepository.deleteAll()
                userRepository.deleteAll()
            }

            it("알림 이벤트를 정상적으로 생성하고 조회할 수 있어야 한다") {
                // Given
                val userA = userRepository.save(User(loginId = "usera", name = "사용자A", email = "usera@yona.io"))
                val userB = userRepository.save(User(loginId = "userb", name = "사용자B", email = "userb@yona.io"))

                val event = NotificationEvent(
                    title = "새로운 댓글이 등록되었습니다.",
                    senderId = userA.id,
                    created = Instant.now(),
                    resourceType = ResourceType.ISSUE_COMMENT,
                    resourceId = "123",
                    eventType = EventType.NEW_COMMENT,
                    newValue = "댓글 내용"
                )
                event.receivers.add(userB)

                // When
                val savedEvent = notificationEventRepository.save(event)

                // Then
                savedEvent.id shouldNotBe null
                
                val foundEvent = notificationEventRepository.findById(savedEvent.id!!).orElse(null)
                foundEvent shouldNotBe null
                foundEvent.title shouldBe "새로운 댓글이 등록되었습니다."
                foundEvent.receivers.size shouldBe 1
                foundEvent.receivers.first().loginId shouldBe "userb"
                foundEvent.resourceType shouldBe ResourceType.ISSUE_COMMENT
                foundEvent.eventType shouldBe EventType.NEW_COMMENT
            }
        }
    }
}
