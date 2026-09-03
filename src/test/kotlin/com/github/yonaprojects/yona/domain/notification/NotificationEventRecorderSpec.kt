package com.github.yonaprojects.yona.domain.notification

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

// yona models/NotificationEvent.java의 add()/addWithoutSkipEvent() 대응 (P1-27).
@Transactional
class NotificationEventRecorderSpec @Autowired constructor(
    private val recorder: NotificationEventRecorder,
    private val notificationEventRepository: NotificationEventRepository,
    private val notificationMailRepository: NotificationMailRepository,
    private val userRepository: UserRepository,
    // yona-wiki P3-01(Observability) 계측 지점 1 검증용 — 실제 애플리케이션 컨텍스트가 관리하는
    // MeterRegistry 빈을 그대로 주입받아 record() 호출 후 카운터가 실제로 증가하는지 확인한다.
    private val meterRegistry: MeterRegistry
) : AbstractIntegrationTest() {

    init {
        describe("NotificationEventRecorder.record") {
            beforeEach {
                notificationMailRepository.deleteAll()
                notificationEventRepository.deleteAll()
                userRepository.deleteAll()
            }

            fun eventOf(sender: User, receiver: User, resourceId: String, oldValue: String?, newValue: String?) =
                NotificationEvent(
                    title = "이슈 상태 변경",
                    senderId = sender.id,
                    created = Instant.now(),
                    resourceType = ResourceType.ISSUE_POST,
                    resourceId = resourceId,
                    eventType = EventType.ISSUE_STATE_CHANGED,
                    oldValue = oldValue,
                    newValue = newValue,
                    receivers = mutableSetOf(receiver)
                )

            it("record() 호출 시 yona.notification.events 카운터가 eventType/resourceType 태그로 증가해야 한다") {
                val sender = userRepository.save(User(loginId = "sender-metric", name = "발신자", email = "sm@yona.io"))
                val receiver = userRepository.save(User(loginId = "receiver-metric", name = "수신자", email = "rm@yona.io"))
                val before = meterRegistry.counter(
                    "yona.notification.events",
                    "eventType", EventType.ISSUE_STATE_CHANGED.name,
                    "resourceType", ResourceType.ISSUE_POST.name
                ).count()

                recorder.record(eventOf(sender, receiver, "metric-1", "OPEN", "CLOSED"))

                meterRegistry.counter(
                    "yona.notification.events",
                    "eventType", EventType.ISSUE_STATE_CHANGED.name,
                    "resourceType", ResourceType.ISSUE_POST.name
                ).count() shouldBe before + 1.0
            }

            it("저장되면 NotificationMail 마커도 함께 생성해야 한다") {
                val sender = userRepository.save(User(loginId = "sender1", name = "발신자1", email = "s1@yona.io"))
                val receiver = userRepository.save(User(loginId = "receiver1", name = "수신자1", email = "r1@yona.io"))

                val saved = recorder.record(eventOf(sender, receiver, "1", "OPEN", "CLOSED"))

                saved shouldBe notificationEventRepository.findById(saved!!.id!!).orElse(null)
                notificationMailRepository.findByNotificationEvent(saved!!) shouldBe notificationMailRepository.findAll().first()
            }

            it("수신자가 없으면 저장하지 않아야 한다") {
                val sender = userRepository.save(User(loginId = "sender2", name = "발신자2", email = "s2@yona.io"))
                val event = NotificationEvent(
                    title = "제목", senderId = sender.id, created = Instant.now(),
                    resourceType = ResourceType.ISSUE_POST, resourceId = "2",
                    eventType = EventType.ISSUE_STATE_CHANGED, newValue = "CLOSED"
                )

                val saved = recorder.record(event)

                saved shouldBe null
                notificationEventRepository.count() shouldBe 0
            }

            it("30초 내 같은 사용자가 같은 리소스에 같은 타입 이벤트를 연속 발생시키면 A→C로 병합해야 한다") {
                val sender = userRepository.save(User(loginId = "sender3", name = "발신자3", email = "s3@yona.io"))
                val receiver = userRepository.save(User(loginId = "receiver3", name = "수신자3", email = "r3@yona.io"))

                recorder.record(eventOf(sender, receiver, "3", "OPEN", "CLOSED"))
                val second = recorder.record(eventOf(sender, receiver, "3", "CLOSED", "REJECTED"))

                notificationEventRepository.count() shouldBe 1
                second!!.oldValue shouldBe "OPEN"
                second.newValue shouldBe "REJECTED"
                notificationMailRepository.count() shouldBe 1
            }

            it("30초 내 정확히 원상복구되면(A→B→A) 두 이벤트 모두 상쇄해야 한다") {
                val sender = userRepository.save(User(loginId = "sender4", name = "발신자4", email = "s4@yona.io"))
                val receiver = userRepository.save(User(loginId = "receiver4", name = "수신자4", email = "r4@yona.io"))

                recorder.record(eventOf(sender, receiver, "4", "OPEN", "CLOSED"))
                val second = recorder.record(eventOf(sender, receiver, "4", "CLOSED", "OPEN"))

                second shouldBe null
                notificationEventRepository.count() shouldBe 0
                notificationMailRepository.count() shouldBe 0
            }

            it("skipWaypoint=false면 중간 지점은 남기고 정확히 되돌아오는 경우만 상쇄해야 한다") {
                val sender = userRepository.save(User(loginId = "sender5", name = "발신자5", email = "s5@yona.io"))
                val receiver = userRepository.save(User(loginId = "receiver5", name = "수신자5", email = "r5@yona.io"))

                recorder.record(eventOf(sender, receiver, "5", "", "sharer1"), skipWaypoint = false)
                val second = recorder.record(eventOf(sender, receiver, "5", "sharer1", ""), skipWaypoint = false)

                second shouldBe null
                notificationEventRepository.count() shouldBe 0
            }

            // skipWaypoint=false의 else-if 조건이 false인 경우(정확한 원상복구가 아닌 중간 지점) —
            // 병합/상쇄 없이 두 이벤트가 그대로 각각 저장돼야 한다.
            it("skipWaypoint=false이고 정확히 되돌아오는 게 아니면 두 이벤트 모두 남아야 한다") {
                val sender = userRepository.save(User(loginId = "sender6", name = "발신자6", email = "s6@yona.io"))
                val receiver = userRepository.save(User(loginId = "receiver6", name = "수신자6", email = "r6@yona.io"))

                recorder.record(eventOf(sender, receiver, "6", "", "sharer1"), skipWaypoint = false)
                val second = recorder.record(eventOf(sender, receiver, "6", "sharer1", "sharer2"), skipWaypoint = false)

                second shouldNotBe null
                notificationEventRepository.count() shouldBe 2
            }

            // 직전 이벤트가 있어도(lastEvent != null) eventType이 다르면 무관한 이벤트로 취급해
            // 병합/상쇄 없이 별도로 저장돼야 한다 — 3항 AND 조건의 두 번째 피연산자 false 분기.
            it("직전 이벤트가 있어도 eventType이 다르면 병합하지 않고 각각 저장해야 한다") {
                val sender = userRepository.save(User(loginId = "sender7", name = "발신자7", email = "s7@yona.io"))
                val receiver = userRepository.save(User(loginId = "receiver7", name = "수신자7", email = "r7@yona.io"))

                recorder.record(eventOf(sender, receiver, "7", "OPEN", "CLOSED"))
                val differentTypeEvent = NotificationEvent(
                    title = "다른 타입 이벤트", senderId = sender.id, created = Instant.now(),
                    resourceType = ResourceType.ISSUE_POST, resourceId = "7",
                    eventType = EventType.ISSUE_ASSIGNEE_CHANGED, oldValue = "a", newValue = "b",
                    receivers = mutableSetOf(receiver)
                )
                val second = recorder.record(differentTypeEvent)

                second shouldNotBe null
                notificationEventRepository.count() shouldBe 2
            }

            // 직전 이벤트가 있고 eventType은 같아도 senderId가 다르면 무관한 이벤트로 취급해야 한다 —
            // 3항 AND 조건의 세 번째 피연산자 false 분기.
            it("직전 이벤트가 있어도 senderId가 다르면 병합하지 않고 각각 저장해야 한다") {
                val sender1 = userRepository.save(User(loginId = "sender8a", name = "발신자8a", email = "s8a@yona.io"))
                val sender2 = userRepository.save(User(loginId = "sender8b", name = "발신자8b", email = "s8b@yona.io"))
                val receiver = userRepository.save(User(loginId = "receiver8", name = "수신자8", email = "r8@yona.io"))

                recorder.record(eventOf(sender1, receiver, "8", "OPEN", "CLOSED"))
                val second = recorder.record(eventOf(sender2, receiver, "8", "CLOSED", "REJECTED"))

                second shouldNotBe null
                notificationEventRepository.count() shouldBe 2
            }

            // skipWaypoint=true 병합 시 findByNotificationEvent(lastEvent)?.let{} 의 null 분기 —
            // lastEvent에 연결된 메일이 이미 삭제돼 없는 상태에서 병합이 일어나도 예외 없이 진행돼야 한다.
            it("skipWaypoint=true 병합 시 직전 이벤트의 메일이 이미 없어도 예외 없이 병합해야 한다") {
                val sender = userRepository.save(User(loginId = "sender9", name = "발신자9", email = "s9@yona.io"))
                val receiver = userRepository.save(User(loginId = "receiver9", name = "수신자9", email = "r9@yona.io"))

                recorder.record(eventOf(sender, receiver, "9", "OPEN", "CLOSED"))
                notificationMailRepository.deleteAll()

                val second = recorder.record(eventOf(sender, receiver, "9", "CLOSED", "REJECTED"))

                second shouldNotBe null
                notificationEventRepository.count() shouldBe 1
            }

            // 원상복구(skipWaypoint=true, A->B->A) 상쇄 시 findByNotificationEvent(lastEvent)?.let{}
            // 의 null 분기 — 마찬가지로 메일이 이미 없어도 예외 없이 상쇄돼야 한다.
            it("정확히 원상복구되어 상쇄될 때 직전 이벤트의 메일이 이미 없어도 예외 없이 상쇄해야 한다") {
                val sender = userRepository.save(User(loginId = "sender10", name = "발신자10", email = "s10@yona.io"))
                val receiver = userRepository.save(User(loginId = "receiver10", name = "수신자10", email = "r10@yona.io"))

                recorder.record(eventOf(sender, receiver, "10", "OPEN", "CLOSED"))
                notificationMailRepository.deleteAll()

                val second = recorder.record(eventOf(sender, receiver, "10", "CLOSED", "OPEN"))

                second shouldBe null
                notificationEventRepository.count() shouldBe 0
            }

            // skipWaypoint=false의 else-if 복합조건(event.oldValue == lastEvent.newValue &&
            // event.newValue == lastEvent.oldValue)에서 좌변부터 이미 false인 경우(단락평가) —
            // 위쪽 "정확히 되돌아오는 게 아니면" 테스트는 좌변이 true인 채로 우변만 false였으므로
            // 좌변 자체가 false인 조합은 이 테스트에서 별도로 커버한다.
            it("skipWaypoint=false이고 첫 값부터 직전 이벤트와 이어지지 않으면 두 이벤트 모두 남아야 한다") {
                val sender = userRepository.save(User(loginId = "sender11", name = "발신자11", email = "s11@yona.io"))
                val receiver = userRepository.save(User(loginId = "receiver11", name = "수신자11", email = "r11@yona.io"))

                recorder.record(eventOf(sender, receiver, "11", "", "sharer1"), skipWaypoint = false)
                val second = recorder.record(eventOf(sender, receiver, "11", "unrelated", "sharer3"), skipWaypoint = false)

                second shouldNotBe null
                notificationEventRepository.count() shouldBe 2
            }
        }
    }
}
