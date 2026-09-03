package com.github.yonaprojects.yona.domain.mail

import com.github.yonaprojects.yona.domain.support.PropertyName
import com.github.yonaprojects.yona.domain.support.PropertyService
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jakarta.activation.DataHandler
import jakarta.mail.Folder
import jakarta.mail.FolderClosedException
import jakarta.mail.Message
import jakarta.mail.MessagingException
import jakarta.mail.Part
import jakarta.mail.Session
import jakarta.mail.event.MessageCountEvent
import jakarta.mail.event.MessageCountListener
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import jakarta.mail.util.ByteArrayDataSource
import org.eclipse.angus.mail.imap.IMAPFolder
import org.eclipse.angus.mail.imap.IMAPStore
import org.springframework.scheduling.TaskScheduler
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import jakarta.mail.internet.InternetAddress
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.Properties
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ScheduledFuture

// yona mailbox/CreationViaEmail.java의 MIME 파트 트리 순회(processPart 등) 대응 (P1-29).
// 실제 jakarta.mail 객체를 구성해 IMAP 서버 연결 없이도 파싱 로직을 검증한다.
class ImapMailboxPollerSpec : DescribeSpec({
    val propertyService = mockk<PropertyService>(relaxed = true)
    val taskScheduler = mockk<TaskScheduler>(relaxed = true)
    val meterRegistry = SimpleMeterRegistry()
    val poller = ImapMailboxPoller(
        incomingMailProcessingService = mockk(relaxed = true),
        propertyService = propertyService,
        taskScheduler = taskScheduler,
        host = "localhost",
        user = "yona",
        password = "secret",
        useSsl = true,
        folderName = "inbox",
        pollingIntervalMs = 300000L,
        meterRegistry = meterRegistry
    )
    val session = Session.getDefaultInstance(Properties())

    // store/folder/idleThread/pollingTask는 전부 private var라 테스트 클래스에서 직접 대입할 수 없다.
    // 실제 IMAP 서버 없이도 폴더/스토어를 목으로 주입해 폴링·리스너 로직을 검증하려고 리플렉션으로
    // 필드를 직접 세팅한다 - start()/connect()가 실제 네트워크 I/O를 하는 것과 별개로, 그 이후의
    // 순수 분기 로직(재접속 판단, 메시지 처리, 워터마크 갱신 등)은 이렇게 단위테스트할 수 있다.
    fun ImapMailboxPoller.setField(name: String, value: Any?) {
        val field = ImapMailboxPoller::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.set(this, value)
    }

    fun ImapMailboxPoller.getField(name: String): Any? {
        val field = ImapMailboxPoller::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.get(this)
    }

    // private 메서드를 리플렉션으로 호출한다. InvocationTargetException은 원래 예외를 벗겨서 던진다.
    fun ImapMailboxPoller.callPrivate(name: String, paramTypes: Array<Class<*>> = emptyArray(), vararg args: Any?): Any? {
        val method = ImapMailboxPoller::class.java.getDeclaredMethod(name, *paramTypes)
        method.isAccessible = true
        return try {
            method.invoke(this, *args)
        } catch (e: InvocationTargetException) {
            throw e.targetException ?: e
        }
    }

    // 매 테스트마다 독립된 인스턴스 + 목을 새로 만든다 - DescribeSpec은 기본적으로 스펙 인스턴스를
    // 재사용하므로(SingleInstance), 리플렉션으로 private 상태를 건드리는 테스트가 서로 오염되지 않도록
    // 공유 poller/session이 아니라 매번 새 인스턴스를 만들어 쓴다.
    fun freshPoller(
        host: String = "localhost",
        useSsl: Boolean = true,
        propertyService: PropertyService = mockk(relaxed = true),
        taskScheduler: TaskScheduler = mockk(relaxed = true),
        incomingMailProcessingService: IncomingMailProcessingService = mockk(relaxed = true),
        meterRegistry: SimpleMeterRegistry = SimpleMeterRegistry()
    ) = ImapMailboxPoller(
        incomingMailProcessingService = incomingMailProcessingService,
        propertyService = propertyService,
        taskScheduler = taskScheduler,
        host = host,
        user = "yona",
        password = "secret",
        useSsl = useSsl,
        folderName = "inbox",
        pollingIntervalMs = 300000L,
        meterRegistry = meterRegistry
    )

    // yona MailboxService.java:176-188 Diagnostic checkOne() 대응 (P1-137).
    describe("ImapMailboxPoller.healthCheckMessage") {
        it("start()가 호출되지 않아 idleThread가 초기화되지 않았으면 미초기화 메시지를 반환해야 한다") {
            poller.healthCheckMessage() shouldBe "The Email Receiver is not initialized"
        }
    }

    describe("ImapMailboxPoller.toInboundEmailMessage") {
        it("text/plain 단일 파트 메일은 본문을 그대로 추출하고 첨부파일은 없어야 한다") {
            val message = MimeMessage(session)
            message.setFrom("gildong@example.com")
            message.setRecipients(Message.RecipientType.TO, "yona+dlab/hive@example.com")
            message.subject = "제목"
            message.setText("안녕하세요, 본문입니다.", "UTF-8")
            message.saveChanges()

            val result = poller.toInboundEmailMessage(message)

            result.textBody shouldBe "안녕하세요, 본문입니다."
            result.attachments.size shouldBe 0
        }

        it("multipart/mixed 메일에서 첨부파일을 추출해야 한다") {
            val message = MimeMessage(session)
            message.setFrom("gildong@example.com")
            message.setRecipients(Message.RecipientType.TO, "yona+dlab/hive@example.com")
            message.subject = "첨부파일 테스트"

            val textPart = MimeBodyPart()
            textPart.setText("본문입니다.", "UTF-8")

            val attachmentPart = MimeBodyPart()
            attachmentPart.setFileName("hello.txt")
            attachmentPart.setContent("attachment content", "text/plain")
            attachmentPart.setDisposition(Part.ATTACHMENT)

            val multipart = MimeMultipart("mixed")
            multipart.addBodyPart(textPart)
            multipart.addBodyPart(attachmentPart)
            message.setContent(multipart)
            message.saveChanges()

            val result = poller.toInboundEmailMessage(message)

            result.textBody shouldBe "본문입니다."
            result.attachments.size shouldBe 1
            result.attachments[0].fileName shouldBe "hello.txt"
            String(result.attachments[0].bytes, Charsets.UTF_8) shouldBe "attachment content"
        }

        it("첨부파일이 여러 개면 전부 추출해야 한다") {
            val message = MimeMessage(session)
            message.setFrom("gildong@example.com")
            message.setRecipients(Message.RecipientType.TO, "yona+dlab/hive@example.com")
            message.subject = "여러 첨부파일"

            val textPart = MimeBodyPart()
            textPart.setText("본문", "UTF-8")

            val attachment1 = MimeBodyPart()
            attachment1.setFileName("a.txt")
            attachment1.setContent("A", "text/plain")
            attachment1.setDisposition(Part.ATTACHMENT)

            val attachment2 = MimeBodyPart()
            attachment2.setFileName("b.txt")
            attachment2.setContent("B", "text/plain")
            attachment2.setDisposition(Part.ATTACHMENT)

            val multipart = MimeMultipart("mixed")
            multipart.addBodyPart(textPart)
            multipart.addBodyPart(attachment1)
            multipart.addBodyPart(attachment2)
            message.setContent(multipart)
            message.saveChanges()

            val result = poller.toInboundEmailMessage(message)

            result.attachments.map { it.fileName }.toSet() shouldBe setOf("a.txt", "b.txt")
        }

        it("text/html뿐인 메일은 태그를 벗기지 않고 원본 HTML을 그대로 보존하고 isHtml=true여야 한다(P1-47)") {
            val message = MimeMessage(session)
            message.setFrom("gildong@example.com")
            message.setRecipients(Message.RecipientType.TO, "yona+dlab/hive@example.com")
            message.subject = "HTML 메일"
            message.setContent("<p>안녕하세요 <b>굵게</b></p>", "text/html; charset=UTF-8")
            message.saveChanges()

            val result = poller.toInboundEmailMessage(message)

            result.isHtml shouldBe true
            result.textBody shouldBe "<p>안녕하세요 <b>굵게</b></p>"
        }

        it("인라인 이미지 첨부의 Content-ID를 추출해야 한다(P1-47)") {
            val message = MimeMessage(session)
            message.setFrom("gildong@example.com")
            message.setRecipients(Message.RecipientType.TO, "yona+dlab/hive@example.com")
            message.subject = "인라인 이미지"

            val htmlPart = MimeBodyPart()
            htmlPart.setContent("<p>사진: <img src=\"cid:image1\"></p>", "text/html; charset=UTF-8")

            val imagePart = MimeBodyPart()
            imagePart.fileName = "photo.png"
            imagePart.dataHandler = DataHandler(
                ByteArrayDataSource("fake-image-bytes".toByteArray(), "image/png")
            )
            imagePart.setContentID("<image1>")
            imagePart.setDisposition(Part.INLINE)

            val multipart = MimeMultipart("related")
            multipart.addBodyPart(htmlPart)
            multipart.addBodyPart(imagePart)
            message.setContent(multipart)
            message.saveChanges()

            val result = poller.toInboundEmailMessage(message)

            result.isHtml shouldBe true
            result.textBody shouldBe "<p>사진: <img src=\"cid:image1\"></p>"
            result.attachments.size shouldBe 1
            result.attachments[0].contentId shouldBe "image1"
        }
    }

    // yona EmailHandler.handleNewMessages()의 "lastUIDValidity == uidValidity && lastSeenUID != null"
    // 조건 대응 (P1-55). 이 조건이 성립할 때만 UID 구간 조회로 새 메일을 찾는다.
    describe("ImapMailboxPoller.shouldFetchByUidRange") {
        it("이전 기록이 전혀 없으면(최초 실행) false여야 한다") {
            poller.shouldFetchByUidRange(lastUidValidity = null, lastSeenUid = null, currentUidValidity = 100L) shouldBe false
        }

        it("uidValidity가 이전과 다르면(메일함이 재생성됨) false여야 한다") {
            poller.shouldFetchByUidRange(lastUidValidity = 99L, lastSeenUid = 5L, currentUidValidity = 100L) shouldBe false
        }

        it("lastSeenUid가 없으면 uidValidity가 같아도 false여야 한다") {
            poller.shouldFetchByUidRange(lastUidValidity = 100L, lastSeenUid = null, currentUidValidity = 100L) shouldBe false
        }

        it("uidValidity가 같고 lastSeenUid가 있으면 true여야 한다") {
            poller.shouldFetchByUidRange(lastUidValidity = 100L, lastSeenUid = 5L, currentUidValidity = 100L) shouldBe true
        }
    }

    // yona MailboxService.updateLastSeenUID()의 "uid <= lastSeenUID면 갱신하지 않는다" 대응 (P1-55).
    describe("ImapMailboxPoller.advancedSeenUid") {
        it("기존 워터마크가 없으면 새 uid를 그대로 반환해야 한다") {
            poller.advancedSeenUid(currentSeenUid = null, candidateUid = 7L) shouldBe 7L
        }

        it("새 uid가 기존보다 크면 새 uid를 반환해야 한다") {
            poller.advancedSeenUid(currentSeenUid = 5L, candidateUid = 7L) shouldBe 7L
        }

        it("새 uid가 기존보다 작거나 같으면 null(갱신 불필요)을 반환해야 한다") {
            poller.advancedSeenUid(currentSeenUid = 7L, candidateUid = 7L) shouldBe null
            poller.advancedSeenUid(currentSeenUid = 7L, candidateUid = 3L) shouldBe null
        }
    }

    describe("ImapMailboxPoller.healthCheckMessage 추가 분기") {
        it("idleThread가 있지만 살아있지 않으면(스레드 종료됨) 미실행 메시지를 반환해야 한다") {
            val p = freshPoller()
            p.setField("idleThread", Thread { })

            p.healthCheckMessage() shouldBe "The Email Receiver is not running"
        }

        it("idleThread가 살아있으면 null을 반환해야 한다") {
            val p = freshPoller()
            val latch = CountDownLatch(1)
            val alive = Thread { latch.await() }.apply { isDaemon = true; start() }
            p.setField("idleThread", alive)
            try {
                p.healthCheckMessage() shouldBe null
            } finally {
                latch.countDown()
                alive.join(2000)
            }
        }
    }

    describe("ImapMailboxPoller.stop") {
        it("folder/store가 둘 다 없으면(start() 실패 등) 아무 것도 하지 않고 반환해야 한다") {
            val p = freshPoller()

            p.stop() // 예외 없이 반환되면 성공
        }

        it("정상적으로 폴링 태스크/폴더/스토어를 정리해야 한다") {
            val p = freshPoller()
            val mockFolder = mockk<IMAPFolder>(relaxed = true)
            val mockStore = mockk<IMAPStore>(relaxed = true)
            val mockTask = mockk<ScheduledFuture<*>>(relaxed = true)
            p.setField("folder", mockFolder)
            p.setField("store", mockStore)
            p.setField("pollingTask", mockTask)

            p.stop()

            verify(exactly = 1) { mockTask.cancel(false) }
            verify(exactly = 1) { mockFolder.close(false) }
            verify(exactly = 1) { mockStore.close() }
            p.getField("isStopping") shouldBe true
        }

        it("종료 처리 중 예외가 발생해도 삼키고 경고만 남겨야 한다") {
            val p = freshPoller()
            val mockFolder = mockk<IMAPFolder>(relaxed = true)
            val mockStore = mockk<IMAPStore>(relaxed = true)
            every { mockFolder.close(false) } throws MessagingException("close 실패")
            p.setField("folder", mockFolder)
            p.setField("store", mockStore)

            p.stop() // 예외가 전파되지 않으면 성공
        }
    }

    // connect()/reopenFolder()는 실제 소켓 연결까지 시도하는 순수 글루 코드다(클래스 상단 KDoc에도
    // 명시). "mail.invalid"는 RFC 2606이 네거티브 테스트용으로 예약해 둔, 절대 존재할 수 없는
    // 도메인이라 DNS 조회부터 결정적/즉시 실패한다 - 이를 이용해 "접속을 실제로 시도하다가 실패하는"
    // 경로(useSsl 분기, 재접속 판단 분기)까지는 실제 IMAP 서버 없이도 검증한다. 다만 "접속 성공" 이후의
    // 코드(folder = opened 대입, handleNewMessagesAndStartListener() 진입)는 실제 IMAP 서버(또는
    // GreenMail 같은 임베디드 서버, 이 프로젝트엔 의존성으로 없음) 없이는 재현할 방법이 없다 - 아래
    // handleNewMessagesAndStartListener 이하 테스트들은 폴더/스토어를 리플렉션으로 직접 주입해 그
    // 이후의 순수 분기 로직만 별도로 검증한다.
    describe("ImapMailboxPoller.connect / reopenFolder (실제 접속 시도 경로)") {
        it("useSsl=true면 imaps 프로토콜로 접속을 시도하다 실패해야 한다") {
            val p = freshPoller(host = "mail.invalid", useSsl = true)

            val ex = runCatching { p.callPrivate("reopenFolder") }.exceptionOrNull()

            ex shouldNotBe null
        }

        it("useSsl=false면 imap 프로토콜로 접속을 시도하다 실패해야 한다") {
            val p = freshPoller(host = "mail.invalid", useSsl = false)

            val ex = runCatching { p.callPrivate("reopenFolder") }.exceptionOrNull()

            ex shouldNotBe null
        }

        it("store가 있지만 연결이 끊겨 있으면 재접속을 시도하다 실패해야 한다") {
            val p = freshPoller(host = "mail.invalid")
            val disconnectedStore = mockk<IMAPStore>(relaxed = true)
            every { disconnectedStore.isConnected } returns false
            p.setField("store", disconnectedStore)

            val ex = runCatching { p.callPrivate("reopenFolder") }.exceptionOrNull()

            ex shouldNotBe null
        }

        it("store가 이미 연결돼 있으면 재접속 없이 폴더만 다시 열어야 한다") {
            val p = freshPoller()
            val connectedStore = mockk<IMAPStore>(relaxed = true)
            val reopenedFolder = mockk<IMAPFolder>(relaxed = true)
            every { connectedStore.isConnected } returns true
            every { connectedStore.getFolder("inbox") } returns reopenedFolder
            p.setField("store", connectedStore)

            val result = p.callPrivate("reopenFolder")

            result shouldBe reopenedFolder
            verify(exactly = 1) { reopenedFolder.open(Folder.READ_ONLY) }
            verify(exactly = 0) { connectedStore.connect(any(), any(), any()) }
        }

        it("start()는 접속 실패 시 예외를 삼키고 folder를 세팅하지 않은 채 반환해야 한다") {
            val p = freshPoller(host = "mail.invalid")

            p.start() // 예외가 전파되지 않으면 성공

            p.getField("folder") shouldBe null
        }
    }

    describe("ImapMailboxPoller.handleNewMessagesAndStartListener") {
        it("folder가 없으면(연결 실패 등) 아무 것도 하지 않아야 한다") {
            val p = freshPoller()

            p.callPrivate("handleNewMessagesAndStartListener") // 예외 없이 반환되면 성공
        }

        it("IDLE을 지원하지 않으면 신규 메일 처리 후 폴링으로 폴백해야 한다") {
            val taskScheduler = mockk<TaskScheduler>(relaxed = true)
            val propertyService = mockk<PropertyService>(relaxed = true)
            val p = freshPoller(taskScheduler = taskScheduler, propertyService = propertyService)
            val mockFolder = mockk<IMAPFolder>(relaxed = true)
            val mockStore = mockk<IMAPStore>(relaxed = true)
            every { mockFolder.store } returns mockStore
            every { mockStore.hasCapability("IDLE") } returns false
            every { mockFolder.uidValidity } returns 1L
            every { propertyService.getLong(PropertyName.MAILBOX_LAST_UID_VALIDITY) } returns null
            every { propertyService.getLong(PropertyName.MAILBOX_LAST_SEEN_UID) } returns null
            p.setField("folder", mockFolder)

            p.callPrivate("handleNewMessagesAndStartListener")

            verify(exactly = 1) { taskScheduler.scheduleWithFixedDelay(any(), any<Instant>(), any<Duration>()) }
        }

        it("신규 메일 처리(handleNewMessages) 중 예외가 발생해도 리스너 시작은 계속 시도해야 한다") {
            val propertyService = mockk<PropertyService>(relaxed = true)
            val p = freshPoller(propertyService = propertyService)
            val mockFolder = mockk<IMAPFolder>(relaxed = true)
            val mockStore = mockk<IMAPStore>(relaxed = true)
            every { mockFolder.uidValidity } throws MessagingException("uidValidity 조회 실패")
            every { mockFolder.store } returns mockStore
            every { mockStore.hasCapability("IDLE") } returns false
            p.setField("folder", mockFolder)

            p.callPrivate("handleNewMessagesAndStartListener") // 예외 없이(폴링 폴백까지) 반환되면 성공
        }

        it("IDLE을 지원하면 신규 메일 처리 후 리스너를 정상적으로 시작해야 한다") {
            val propertyService = mockk<PropertyService>(relaxed = true)
            val p = freshPoller(propertyService = propertyService)
            val mockFolder = mockk<IMAPFolder>(relaxed = true)
            val mockStore = mockk<IMAPStore>(relaxed = true)
            every { mockFolder.store } returns mockStore
            every { mockStore.hasCapability("IDLE") } returns true
            every { mockFolder.uidValidity } returns 1L
            every { propertyService.getLong(any()) } returns null
            // idle()은 곧바로 실패시켜 스레드가 빠르게 종료되게 한다(스레드 자체의 동작은 startEmailListener
            // 전용 테스트에서 이미 검증했다 - 여기서는 handleNewMessagesAndStartListener()가 startEmailListener()
            // 성공 경로를 실제로 타는지만 확인한다).
            every { mockFolder.idle() } throws MessagingException("바로 종료용")
            p.setField("folder", mockFolder)

            p.callPrivate("handleNewMessagesAndStartListener")

            val thread = p.getField("idleThread") as Thread
            thread.join(3000)
            thread.isAlive shouldBe false
        }
    }

    describe("ImapMailboxPoller.handleNewMessages / handleMessages / handleMessage (전체 경로)") {
        it("UID 구간의 신규 메일을 UID 오름차순으로 처리하고 워터마크를 전진시켜야 한다") {
            val incomingMailProcessingService = mockk<IncomingMailProcessingService>(relaxed = true)
            val propertyService = mockk<PropertyService>(relaxed = true)
            val testMeterRegistry = SimpleMeterRegistry()
            val p = freshPoller(propertyService = propertyService, incomingMailProcessingService = incomingMailProcessingService, meterRegistry = testMeterRegistry)
            val mockFolder = mockk<IMAPFolder>(relaxed = true)

            val msg1 = MimeMessage(session).apply {
                setFrom("a@example.com"); setRecipients(Message.RecipientType.TO, "yona@example.com")
                subject = "1"; setText("본문1"); saveChanges()
            }
            val msg2 = MimeMessage(session).apply {
                setFrom("b@example.com"); setRecipients(Message.RecipientType.TO, "yona@example.com")
                subject = "2"; setText("본문2"); saveChanges()
            }

            every { propertyService.getLong(PropertyName.MAILBOX_LAST_UID_VALIDITY) } returns 100L
            every { propertyService.getLong(PropertyName.MAILBOX_LAST_SEEN_UID) } returns 5L
            every { mockFolder.uidValidity } returns 100L
            every { mockFolder.uidNext } returns 20L
            every { mockFolder.getMessagesByUID(6L, 20L) } returns arrayOf(msg1, msg2)
            // 일부러 UID 역순으로 반환해도(msg1=8, msg2=7) 오름차순(msg2 먼저) 처리되는지 검증한다.
            every { mockFolder.getUID(msg1) } returns 8L
            every { mockFolder.getUID(msg2) } returns 7L

            p.callPrivate("handleNewMessages", arrayOf(IMAPFolder::class.java), mockFolder)

            verify(exactly = 1) { incomingMailProcessingService.process(match { it.subject == "2" }) }
            verify(exactly = 1) { incomingMailProcessingService.process(match { it.subject == "1" }) }
            verify(exactly = 1) { propertyService.set(PropertyName.MAILBOX_LAST_SEEN_UID, 8L) }
            verify(exactly = 1) { propertyService.set(PropertyName.MAILBOX_LAST_UID_VALIDITY, 100L) }
            // yona-wiki P3-01(Observability) 계측 지점 4 검증 — 메시지 2건 처리 + 폴링 사이클 1회 기록,
            // uidValidity가 그대로(100L)였으므로 재동기화는 발생하지 않아야 한다.
            testMeterRegistry.counter("yona.mailbox.messages.processed").count() shouldBe 2.0
            testMeterRegistry.timer("yona.mailbox.poll.duration").count() shouldBe 1L
            testMeterRegistry.counter("yona.mailbox.uid.resync").count() shouldBe 0.0
        }

        it("이전 기록이 없으면(uidValidity만 다르거나 최초) UID 구간 조회 없이 uidValidity만 갱신해야 한다") {
            val propertyService = mockk<PropertyService>(relaxed = true)
            val p = freshPoller(propertyService = propertyService)
            val mockFolder = mockk<IMAPFolder>(relaxed = true)
            every { propertyService.getLong(PropertyName.MAILBOX_LAST_UID_VALIDITY) } returns null
            every { propertyService.getLong(PropertyName.MAILBOX_LAST_SEEN_UID) } returns null
            every { mockFolder.uidValidity } returns 100L

            p.callPrivate("handleNewMessages", arrayOf(IMAPFolder::class.java), mockFolder)

            verify(exactly = 0) { mockFolder.getMessagesByUID(any(), any()) }
            verify(exactly = 1) { propertyService.set(PropertyName.MAILBOX_LAST_UID_VALIDITY, 100L) }
        }

        // yona-wiki P3-01(Observability) 계측 지점 4 검증 — lastUidValidity가 있는데 현재 uidValidity와
        // 다르면(메일함 재생성 등으로 UID가 더 이상 유효하지 않음) 재동기화 카운터가 증가해야 한다.
        it("lastUidValidity가 현재 uidValidity와 다르면 재동기화 카운터가 증가해야 한다") {
            val propertyService = mockk<PropertyService>(relaxed = true)
            val testMeterRegistry = SimpleMeterRegistry()
            val p = freshPoller(propertyService = propertyService, meterRegistry = testMeterRegistry)
            val mockFolder = mockk<IMAPFolder>(relaxed = true)
            every { propertyService.getLong(PropertyName.MAILBOX_LAST_UID_VALIDITY) } returns 100L
            every { propertyService.getLong(PropertyName.MAILBOX_LAST_SEEN_UID) } returns 5L
            every { mockFolder.uidValidity } returns 200L

            p.callPrivate("handleNewMessages", arrayOf(IMAPFolder::class.java), mockFolder)

            testMeterRegistry.counter("yona.mailbox.uid.resync").count() shouldBe 1.0
        }

        it("메일 처리(process) 중 예외가 발생해도 워터마크는 전진시켜야 한다") {
            val propertyService = mockk<PropertyService>(relaxed = true)
            val incomingMailProcessingService = mockk<IncomingMailProcessingService>()
            every { incomingMailProcessingService.process(any()) } throws RuntimeException("처리 실패")
            val p = freshPoller(propertyService = propertyService, incomingMailProcessingService = incomingMailProcessingService)
            val mockFolder = mockk<IMAPFolder>(relaxed = true)
            val msg = MimeMessage(session).apply {
                setFrom("a@example.com"); setRecipients(Message.RecipientType.TO, "yona@example.com")
                subject = "제목"; setText("본문"); saveChanges()
            }
            every { propertyService.getLong(PropertyName.MAILBOX_LAST_UID_VALIDITY) } returns 100L
            every { propertyService.getLong(PropertyName.MAILBOX_LAST_SEEN_UID) } returns 5L
            every { mockFolder.uidValidity } returns 100L
            every { mockFolder.uidNext } returns 100L
            every { mockFolder.getMessagesByUID(any(), any()) } returns arrayOf(msg)
            every { mockFolder.getUID(msg) } returns 6L

            p.callPrivate("handleNewMessages", arrayOf(IMAPFolder::class.java), mockFolder)

            verify(exactly = 1) { propertyService.set(PropertyName.MAILBOX_LAST_SEEN_UID, 6L) }
        }

        it("새 uid가 기존 워터마크 이하면 갱신하지 않아야 한다") {
            val propertyService = mockk<PropertyService>(relaxed = true)
            val incomingMailProcessingService = mockk<IncomingMailProcessingService>(relaxed = true)
            val p = freshPoller(propertyService = propertyService, incomingMailProcessingService = incomingMailProcessingService)
            val mockFolder = mockk<IMAPFolder>(relaxed = true)
            val msg = MimeMessage(session).apply {
                setFrom("a@example.com"); setRecipients(Message.RecipientType.TO, "yona@example.com")
                subject = "제목"; setText("본문"); saveChanges()
            }
            every { propertyService.getLong(PropertyName.MAILBOX_LAST_UID_VALIDITY) } returns 100L
            every { propertyService.getLong(PropertyName.MAILBOX_LAST_SEEN_UID) } returns 10L
            every { mockFolder.uidValidity } returns 100L
            every { mockFolder.uidNext } returns 100L
            every { mockFolder.getMessagesByUID(any(), any()) } returns arrayOf(msg)
            every { mockFolder.getUID(msg) } returns 6L

            p.callPrivate("handleNewMessages", arrayOf(IMAPFolder::class.java), mockFolder)

            verify(exactly = 0) { propertyService.set(PropertyName.MAILBOX_LAST_SEEN_UID, any<Long>()) }
        }

        it("워터마크 갱신(updateLastSeenUid) 중 예외가 발생해도 삼키고 경고만 남겨야 한다") {
            val propertyService = mockk<PropertyService>(relaxed = true)
            val incomingMailProcessingService = mockk<IncomingMailProcessingService>(relaxed = true)
            val p = freshPoller(propertyService = propertyService, incomingMailProcessingService = incomingMailProcessingService)
            val mockFolder = mockk<IMAPFolder>(relaxed = true)
            val msg = MimeMessage(session).apply {
                setFrom("a@example.com"); setRecipients(Message.RecipientType.TO, "yona@example.com")
                subject = "제목"; setText("본문"); saveChanges()
            }
            every { propertyService.getLong(PropertyName.MAILBOX_LAST_UID_VALIDITY) } returns 100L
            every { propertyService.getLong(PropertyName.MAILBOX_LAST_SEEN_UID) } returns 5L
            every { mockFolder.uidValidity } returns 100L
            every { mockFolder.uidNext } returns 100L
            every { mockFolder.getMessagesByUID(any(), any()) } returns arrayOf(msg)
            // 메시지가 1건뿐이면 sortedBy { }는 비교할 대상이 없어 selector(getUID)를 아예 호출하지
            // 않는다(Kotlin sortedBy는 compareBy를 통해 비교 시점에만 selector를 호출) - 따라서
            // updateLastSeenUid() 내부의 유일한 getUID 호출만 실패시키면 그 예외 처리 분기를 겨냥할 수 있다.
            every { mockFolder.getUID(msg) } throws MessagingException("getUID 실패")

            p.callPrivate("handleNewMessages", arrayOf(IMAPFolder::class.java), mockFolder) // 예외가 전파되지 않으면 성공
        }
    }

    describe("ImapMailboxPoller.startEmailListener") {
        it("IDLE을 지원하지 않으면 예외를 던져야 한다") {
            val p = freshPoller()
            val folder = mockk<IMAPFolder>(relaxed = true)
            val store = mockk<IMAPStore>(relaxed = true)
            every { folder.store } returns store
            every { store.hasCapability("IDLE") } returns false

            val ex = runCatching { p.callPrivate("startEmailListener", arrayOf(IMAPFolder::class.java), folder) }.exceptionOrNull()

            ex.shouldBeInstanceOf<UnsupportedOperationException>()
        }

        it("IDLE을 지원하면 리스너 스레드를 시작하고, idle() 중 일반 예외가 발생하면 스레드가 종료돼야 한다") {
            val p = freshPoller()
            val folder = mockk<IMAPFolder>(relaxed = true)
            val store = mockk<IMAPStore>(relaxed = true)
            every { folder.store } returns store
            every { store.hasCapability("IDLE") } returns true
            every { folder.idle() } throws MessagingException("연결 끊김")
            val listenerSlot = slot<MessageCountListener>()
            every { folder.addMessageCountListener(capture(listenerSlot)) } just Runs

            p.callPrivate("startEmailListener", arrayOf(IMAPFolder::class.java), folder)

            val thread = p.getField("idleThread") as Thread
            thread.join(3000)
            thread.isAlive shouldBe false

            // MessageCountListener - IDLE로 push된 신규 메일을 handleMessages()로 넘기는 리스너도
            // 함께 검증한다(messagesRemoved는 no-op이라 예외 없이 반환되면 충분하다).
            val event = MessageCountEvent(folder, MessageCountEvent.ADDED, true, arrayOf())
            listenerSlot.captured.messagesAdded(event)
            listenerSlot.captured.messagesRemoved(event)
        }

        it("IDLE로 push된 메일 처리(handleMessages) 중 예기치 못한 예외가 발생해도 삼키고 경고만 남겨야 한다") {
            val p = freshPoller()
            val folder = mockk<IMAPFolder>(relaxed = true)
            val store = mockk<IMAPStore>(relaxed = true)
            every { folder.store } returns store
            every { store.hasCapability("IDLE") } returns true
            every { folder.idle() } throws MessagingException("리스너 등록 확인용 - idle 자체는 호출되지 않음")
            val listenerSlot = slot<MessageCountListener>()
            every { folder.addMessageCountListener(capture(listenerSlot)) } just Runs

            p.callPrivate("startEmailListener", arrayOf(IMAPFolder::class.java), folder)
            (p.getField("idleThread") as Thread).join(3000)

            // 메시지가 2건 이상이어야 handleMessages()의 sortedBy { getUID(it) }가 실제로 selector를
            // 호출한다(1건뿐이면 비교 대상이 없어 getUID가 아예 호출되지 않는다) - 그 비교 시점에 예외를
            // 던져 messagesAdded()의 catch(Exception) 분기를 겨냥한다.
            val msg1 = MimeMessage(session).apply { setText("1") }
            val msg2 = MimeMessage(session).apply { setText("2") }
            every { folder.getUID(any()) } throws MessagingException("정렬 중 실패")

            val event = MessageCountEvent(folder, MessageCountEvent.ADDED, true, arrayOf(msg1, msg2))
            listenerSlot.captured.messagesAdded(event) // 예외가 전파되지 않으면 성공
        }

        it("idle() 중 FolderClosedException이 발생했는데 이미 종료 중이면 재접속 없이 스레드를 종료해야 한다") {
            val p = freshPoller()
            val folder = mockk<IMAPFolder>(relaxed = true)
            val store = mockk<IMAPStore>(relaxed = true)
            every { folder.store } returns store
            every { store.hasCapability("IDLE") } returns true
            // idle() 호출 시점에 isStopping을 먼저 true로 만들어 "종료 중이라 재접속하지 않고 break"하는
            // 분기(catch (FolderClosedException) { if (isStopping) break ... })를 결정론적으로 재현한다.
            every { folder.idle() } answers {
                p.setField("isStopping", true)
                throw FolderClosedException(folder, "닫힘")
            }

            p.callPrivate("startEmailListener", arrayOf(IMAPFolder::class.java), folder)

            val thread = p.getField("idleThread") as Thread
            thread.join(3000)
            thread.isAlive shouldBe false
        }

        it("idle() 중 FolderClosedException이 발생하면 폴더를 다시 열고 계속 시도해야 한다") {
            val p = freshPoller()
            val folder = mockk<IMAPFolder>(relaxed = true)
            val store = mockk<IMAPStore>(relaxed = true)
            val reopenedFolder = mockk<IMAPFolder>(relaxed = true)
            every { folder.store } returns store
            every { store.hasCapability("IDLE") } returns true
            every { folder.idle() } throws FolderClosedException(folder, "닫힘")
            every { store.isConnected } returns true
            every { store.getFolder("inbox") } returns reopenedFolder
            every { reopenedFolder.idle() } throws MessagingException("재오픈 후 다시 실패 - 스레드 종료 유도")
            p.setField("store", store)

            p.callPrivate("startEmailListener", arrayOf(IMAPFolder::class.java), folder)

            val thread = p.getField("idleThread") as Thread
            thread.join(3000)
            thread.isAlive shouldBe false
            verify(exactly = 1) { reopenedFolder.open(Folder.READ_ONLY) }
            p.getField("folder") shouldBe reopenedFolder
        }

        it("idle() 중 FolderClosedException 후 재접속마저 실패하면 스레드를 종료해야 한다") {
            val p = freshPoller(host = "mail.invalid")
            val folder = mockk<IMAPFolder>(relaxed = true)
            val store = mockk<IMAPStore>(relaxed = true)
            every { folder.store } returns store
            every { store.hasCapability("IDLE") } returns true
            every { folder.idle() } throws FolderClosedException(folder, "닫힘")
            // store를 세팅하지 않아(null) reopenFolder()가 connect()를 다시 시도하다 실패하도록 유도한다.

            p.callPrivate("startEmailListener", arrayOf(IMAPFolder::class.java), folder)

            val thread = p.getField("idleThread") as Thread
            thread.join(5000)
            thread.isAlive shouldBe false
        }
    }

    describe("ImapMailboxPoller.startEmailPolling") {
        it("폴더가 열려 있으면 재접속 없이 바로 신규 메시지를 조회해야 한다") {
            val taskScheduler = mockk<TaskScheduler>(relaxed = true)
            val propertyService = mockk<PropertyService>(relaxed = true)
            val p = freshPoller(taskScheduler = taskScheduler, propertyService = propertyService)
            val folder = mockk<IMAPFolder>(relaxed = true)
            every { folder.isOpen } returns true
            every { folder.uidValidity } returns 1L
            every { propertyService.getLong(any()) } returns null
            p.setField("folder", folder)
            val runnableSlot = slot<Runnable>()
            every {
                taskScheduler.scheduleWithFixedDelay(capture(runnableSlot), any<Instant>(), any<Duration>())
            } returns mockk<ScheduledFuture<*>>(relaxed = true)

            p.callPrivate("startEmailPolling")
            runnableSlot.captured.run()

            verify(exactly = 1) { propertyService.set(PropertyName.MAILBOX_LAST_UID_VALIDITY, 1L) }
            verify(exactly = 0) { folder.close(any()) }
        }

        it("폴더가 닫혀 있으면 재접속 후 신규 메시지를 조회해야 한다") {
            val taskScheduler = mockk<TaskScheduler>(relaxed = true)
            val propertyService = mockk<PropertyService>(relaxed = true)
            val p = freshPoller(taskScheduler = taskScheduler, propertyService = propertyService)
            val closedFolder = mockk<IMAPFolder>(relaxed = true)
            val reopenedFolder = mockk<IMAPFolder>(relaxed = true)
            val store = mockk<IMAPStore>(relaxed = true)
            every { closedFolder.isOpen } returns false
            every { store.isConnected } returns true
            every { store.getFolder("inbox") } returns reopenedFolder
            every { reopenedFolder.uidValidity } returns 2L
            every { propertyService.getLong(any()) } returns null
            p.setField("folder", closedFolder)
            p.setField("store", store)
            val runnableSlot = slot<Runnable>()
            every {
                taskScheduler.scheduleWithFixedDelay(capture(runnableSlot), any<Instant>(), any<Duration>())
            } returns mockk<ScheduledFuture<*>>(relaxed = true)

            p.callPrivate("startEmailPolling")
            runnableSlot.captured.run()

            verify(exactly = 1) { reopenedFolder.open(Folder.READ_ONLY) }
            p.getField("folder") shouldBe reopenedFolder
        }

        it("폴링 처리 중 예외가 발생해도 삼키고 경고만 남겨야 한다") {
            val taskScheduler = mockk<TaskScheduler>(relaxed = true)
            val propertyService = mockk<PropertyService>(relaxed = true)
            val p = freshPoller(taskScheduler = taskScheduler, propertyService = propertyService)
            val folder = mockk<IMAPFolder>(relaxed = true)
            every { folder.isOpen } returns true
            every { propertyService.getLong(PropertyName.MAILBOX_LAST_UID_VALIDITY) } throws RuntimeException("조회 실패")
            p.setField("folder", folder)
            val runnableSlot = slot<Runnable>()
            every {
                taskScheduler.scheduleWithFixedDelay(capture(runnableSlot), any<Instant>(), any<Duration>())
            } returns mockk<ScheduledFuture<*>>(relaxed = true)

            p.callPrivate("startEmailPolling")
            runnableSlot.captured.run() // 예외가 전파되지 않으면 성공
        }
    }

    describe("ImapMailboxPoller.toInboundEmailMessage 나머지 분기") {
        it("MimeMessage가 아닌 Message는 messageId 등이 전부 빈 값이어야 한다") {
            val p = freshPoller()
            val raw = mockk<Message>(relaxed = true)
            every { raw.from } returns null
            every { raw.allRecipients } returns null
            every { raw.subject } returns "제목"
            every { raw.isMimeType(any()) } returns false
            every { raw.fileName } returns null

            val result = p.toInboundEmailMessage(raw)

            result.messageId shouldBe ""
            result.fromAddress shouldBe ""
            result.fromName shouldBe ""
            result.recipientAddresses shouldBe emptyList()
            result.inReplyTo shouldBe null
            result.references shouldBe null
            result.attachments shouldBe emptyList()
        }

        it("수신자가 전혀 없으면(allRecipients=null) 빈 목록이어야 한다") {
            val message = MimeMessage(session)
            message.setFrom("gildong@example.com")
            message.subject = "수신자 없음"
            message.setText("본문")
            message.saveChanges()

            poller.toInboundEmailMessage(message).recipientAddresses shouldBe emptyList()
        }

        it("From 헤더가 없으면 발신자 이름/주소가 빈 문자열이어야 한다") {
            val message = MimeMessage(session)
            message.setRecipients(Message.RecipientType.TO, "yona@example.com")
            message.subject = "발신자 없음"
            message.setText("본문")
            message.saveChanges()

            val result = poller.toInboundEmailMessage(message)
            result.fromAddress shouldBe ""
            result.fromName shouldBe ""
        }

        it("In-Reply-To/References 헤더가 있으면 그대로 추출해야 한다") {
            val message = MimeMessage(session)
            message.setFrom("gildong@example.com")
            message.setRecipients(Message.RecipientType.TO, "yona@example.com")
            message.subject = "스레딩"
            message.setText("본문")
            message.setHeader("In-Reply-To", "<parent@example.com>")
            message.setHeader("References", "<root@example.com> <parent@example.com>")
            message.saveChanges()

            val result = poller.toInboundEmailMessage(message)
            result.inReplyTo shouldBe "<parent@example.com>"
            result.references shouldBe "<root@example.com> <parent@example.com>"
        }

        it("멀티파트의 모든 파트가 빈 본문이면 최종적으로 빈 문자열을 반환해야 한다") {
            val message = MimeMessage(session)
            message.setFrom("gildong@example.com")
            message.setRecipients(Message.RecipientType.TO, "yona@example.com")
            message.subject = "빈 본문"

            val part1 = MimeBodyPart().apply { setText("", "UTF-8") }
            val part2 = MimeBodyPart().apply { setText("   ", "UTF-8") }
            val multipart = MimeMultipart("alternative")
            multipart.addBodyPart(part1)
            multipart.addBodyPart(part2)
            message.setContent(multipart)
            message.saveChanges()

            val result = poller.toInboundEmailMessage(message)
            result.textBody shouldBe ""
            result.isHtml shouldBe false
        }

        it("본문 추출 중 예외가 발생하면 빈 본문으로 대체해야 한다") {
            val p = freshPoller()
            val badPart = mockk<Message>(relaxed = true)
            every { badPart.from } returns null
            every { badPart.allRecipients } returns null
            every { badPart.subject } returns "제목"
            every { badPart.isMimeType("text/plain") } returns true
            every { badPart.content } throws MessagingException("본문 읽기 실패")
            every { badPart.fileName } returns null

            val result = p.toInboundEmailMessage(badPart)
            result.textBody shouldBe ""
            result.isHtml shouldBe false
        }

        it("첨부파일 추출 중 예외가 발생하면 빈 목록으로 대체해야 한다") {
            val p = freshPoller()
            val badPart = mockk<Message>(relaxed = true)
            every { badPart.from } returns null
            every { badPart.allRecipients } returns null
            every { badPart.subject } returns "제목"
            every { badPart.isMimeType(any()) } returns false
            every { badPart.fileName } returns "bad.txt"
            every { badPart.inputStream } throws IOException("첨부파일 읽기 실패")

            val result = p.toInboundEmailMessage(badPart)
            result.attachments shouldBe emptyList()
        }
    }

    describe("ImapMailboxPoller.stop 나머지 조합") {
        it("folder만 없고 store는 남아있으면 store만 정리한다") {
            val p = freshPoller()
            val mockStore = mockk<IMAPStore>(relaxed = true)
            p.setField("store", mockStore)
            // folder는 세팅하지 않음(null)

            p.stop()

            verify(exactly = 1) { mockStore.close() }
        }

        it("store만 없고 folder는 남아있으면 folder만 정리한다") {
            val p = freshPoller()
            val mockFolder = mockk<IMAPFolder>(relaxed = true)
            p.setField("folder", mockFolder)
            // store는 세팅하지 않음(null)

            p.stop()

            verify(exactly = 1) { mockFolder.close(false) }
        }
    }

    describe("ImapMailboxPoller.handleMessage - message.subject 자체가 예외를 던지는 경우") {
        it("메일 처리 실패 로그에 쓸 subject 조회도 실패하면 null로 대체하고 예외를 삼켜야 한다") {
            val propertyService = mockk<PropertyService>(relaxed = true)
            val p = freshPoller(propertyService = propertyService)
            val folder = mockk<IMAPFolder>(relaxed = true)
            val badMessage = mockk<Message>(relaxed = true)
            every { badMessage.subject } throws MessagingException("subject 조회 실패")
            every { folder.getUID(badMessage) } returns 1L

            p.callPrivate("handleMessage", arrayOf(IMAPFolder::class.java, Message::class.java), folder, badMessage)
            // 예외가 전파되지 않으면 성공 (toInboundEmailMessage도, catch 블록의 runCatching도 둘 다 실패)
        }
    }

    describe("ImapMailboxPoller.startEmailListener - 시작 직후 이미 종료 중인 경우") {
        it("스레드가 시작되기 전부터 isStopping이 true면 idle()을 한 번도 호출하지 않고 즉시 종료해야 한다") {
            val p = freshPoller()
            val folder = mockk<IMAPFolder>(relaxed = true)
            val store = mockk<IMAPStore>(relaxed = true)
            every { folder.store } returns store
            every { store.hasCapability("IDLE") } returns true
            p.setField("isStopping", true)

            p.callPrivate("startEmailListener", arrayOf(IMAPFolder::class.java), folder)

            val thread = p.getField("idleThread") as Thread
            thread.join(3000)
            thread.isAlive shouldBe false
            verify(exactly = 0) { folder.idle() }
        }
    }

    describe("ImapMailboxPoller.startEmailPolling - 폴더 자체가 없는 경우") {
        it("folder가 세팅된 적이 없으면(null) 재접속 후 신규 메시지를 조회해야 한다") {
            val taskScheduler = mockk<TaskScheduler>(relaxed = true)
            val propertyService = mockk<PropertyService>(relaxed = true)
            val p = freshPoller(taskScheduler = taskScheduler, propertyService = propertyService)
            val reopenedFolder = mockk<IMAPFolder>(relaxed = true)
            val store = mockk<IMAPStore>(relaxed = true)
            every { store.isConnected } returns true
            every { store.getFolder("inbox") } returns reopenedFolder
            every { reopenedFolder.uidValidity } returns 3L
            every { propertyService.getLong(any()) } returns null
            p.setField("store", store)
            // folder는 세팅하지 않음(null)
            val runnableSlot = slot<Runnable>()
            every {
                taskScheduler.scheduleWithFixedDelay(capture(runnableSlot), any<Instant>(), any<Duration>())
            } returns mockk<ScheduledFuture<*>>(relaxed = true)

            p.callPrivate("startEmailPolling")
            runnableSlot.captured.run()

            verify(exactly = 1) { reopenedFolder.open(Folder.READ_ONLY) }
            p.getField("folder") shouldBe reopenedFolder
        }
    }

    describe("ImapMailboxPoller.toInboundEmailMessage 세부 null 분기") {
        it("MimeMessage이지만 Message-ID 헤더가 없으면(messageID=null) 빈 문자열이어야 한다") {
            val p = freshPoller()
            val mime = mockk<MimeMessage>(relaxed = true)
            every { mime.messageID } returns null
            every { mime.subject } returns "제목"
            every { mime.from } returns null
            every { mime.allRecipients } returns null
            every { mime.isMimeType(any()) } returns false
            every { mime.fileName } returns null
            every { mime.getHeader(any()) } returns null

            p.toInboundEmailMessage(mime).messageId shouldBe ""
        }

        it("subject 헤더가 없으면 빈 문자열이어야 한다") {
            val message = MimeMessage(session)
            message.setFrom("gildong@example.com")
            message.setRecipients(Message.RecipientType.TO, "yona@example.com")
            message.setText("본문")
            message.saveChanges()

            poller.toInboundEmailMessage(message).subject shouldBe ""
        }

        it("From 헤더에 표시 이름(personal)이 있으면 그 이름을 fromName으로 써야 한다") {
            val message = MimeMessage(session)
            message.setFrom(InternetAddress("gildong@example.com", "홍길동", "UTF-8"))
            message.setRecipients(Message.RecipientType.TO, "yona@example.com")
            message.subject = "표시 이름"
            message.setText("본문")
            message.saveChanges()

            val result = poller.toInboundEmailMessage(message)
            result.fromName shouldBe "홍길동"
        }

        it("From 주소 자체가 비어있으면(address=null) fromAddress/fromName 모두 빈 문자열이어야 한다") {
            val p = freshPoller()
            val addressless = mockk<Message>(relaxed = true)
            every { addressless.from } returns arrayOf(InternetAddress())
            every { addressless.allRecipients } returns null
            every { addressless.subject } returns "제목"
            every { addressless.isMimeType(any()) } returns false
            every { addressless.fileName } returns null

            val result = p.toInboundEmailMessage(addressless)
            result.fromAddress shouldBe ""
            result.fromName shouldBe ""
        }
    }

    describe("ImapMailboxPoller.extractAttachments 세부 분기") {
        it("파일명이 공백뿐이면(null은 아님) 첨부파일로 취급하지 않아야 한다") {
            val message = MimeMessage(session)
            message.setFrom("gildong@example.com")
            message.setRecipients(Message.RecipientType.TO, "yona@example.com")
            message.subject = "빈 파일명"

            val textPart = MimeBodyPart().apply { setText("본문", "UTF-8") }
            val blankNamePart = MimeBodyPart().apply {
                fileName = "   "
                setContent("data", "application/octet-stream")
                setDisposition(Part.ATTACHMENT)
            }
            val multipart = MimeMultipart("mixed")
            multipart.addBodyPart(textPart)
            multipart.addBodyPart(blankNamePart)
            message.setContent(multipart)
            message.saveChanges()

            poller.toInboundEmailMessage(message).attachments shouldBe emptyList()
        }

        it("MimePart가 아닌 Part는 Content-ID 없이(null) 첨부파일로 추출해야 한다") {
            val p = freshPoller()
            // Message(추상 클래스)는 MimePart를 구현하지 않으므로, 이 목은 extractAttachments()의
            // "(part as? MimePart)" 캐스팅이 null이 되는 경로를 자연스럽게 재현한다(MimeMessage와 달리).
            val message = mockk<Message>(relaxed = true)
            every { message.from } returns null
            every { message.allRecipients } returns null
            every { message.subject } returns "제목"
            every { message.isMimeType(any()) } returns false
            every { message.fileName } returns "plain.bin"
            every { message.inputStream } returns "content".byteInputStream()
            every { message.contentType } returns "application/octet-stream"

            val result = p.toInboundEmailMessage(message)
            result.attachments.size shouldBe 1
            result.attachments[0].contentId shouldBe null
        }

        it("Content-Type이 없으면(null) application/octet-stream으로 대체해야 한다") {
            val p = freshPoller()
            val message = mockk<Message>(relaxed = true)
            every { message.from } returns null
            every { message.allRecipients } returns null
            every { message.subject } returns "제목"
            every { message.isMimeType(any()) } returns false
            every { message.fileName } returns "notype.bin"
            every { message.inputStream } returns "data".byteInputStream()
            every { message.contentType } returns null

            val result = p.toInboundEmailMessage(message)
            result.attachments.size shouldBe 1
            result.attachments[0].contentType shouldBe "application/octet-stream"
        }
    }

    describe("ImapMailboxPoller.extractBody 세부 분기") {
        it("text/plain으로 표시됐지만 실제 content가 String이 아니면 빈 문자열로 대체해야 한다") {
            val p = freshPoller()
            val message = mockk<Message>(relaxed = true)
            every { message.from } returns null
            every { message.allRecipients } returns null
            every { message.subject } returns "제목"
            every { message.isMimeType("text/plain") } returns true
            every { message.isMimeType("multipart/*") } returns false
            every { message.isMimeType("text/html") } returns false
            every { message.content } returns Date() // String이 아닌 임의의 객체
            every { message.fileName } returns null

            val result = p.toInboundEmailMessage(message)
            result.textBody shouldBe ""
            result.isHtml shouldBe false
        }

        it("text/html으로 표시됐지만 실제 content가 String이 아니면 빈 문자열로 대체해야 한다") {
            val p = freshPoller()
            val message = mockk<Message>(relaxed = true)
            every { message.from } returns null
            every { message.allRecipients } returns null
            every { message.subject } returns "제목"
            every { message.isMimeType("text/plain") } returns false
            every { message.isMimeType("multipart/*") } returns false
            every { message.isMimeType("text/html") } returns true
            every { message.content } returns Date() // String이 아닌 임의의 객체
            every { message.fileName } returns null

            val result = p.toInboundEmailMessage(message)
            result.textBody shouldBe ""
            // isMimeType("text/html")로 판정된 이상 캐스팅 실패와 무관하게 isHtml=true는 그대로 유지된다.
            result.isHtml shouldBe true
        }
    }
})
