package com.github.yonaprojects.yona.domain.mail

import com.github.yonaprojects.yona.domain.support.PropertyName
import com.github.yonaprojects.yona.domain.support.PropertyService
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import jakarta.mail.Folder
import jakarta.mail.FolderClosedException
import jakarta.mail.Message
import jakarta.mail.Multipart
import jakarta.mail.Part
import jakarta.mail.Session
import jakarta.mail.event.MessageCountEvent
import jakarta.mail.event.MessageCountListener
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimePart
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.eclipse.angus.mail.imap.IMAPFolder
import org.eclipse.angus.mail.imap.IMAPStore
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.Properties
import java.util.concurrent.ScheduledFuture

/**
 * yona의 mailbox/MailboxService.java + mailbox/EmailHandler.java 대응.
 *
 * yona와 완전히 동일한 설계로 이식했다: IMAP `IDLE` 명령으로 실시간 push를 우선 시도하고, 서버가
 * IDLE을 지원하지 않을 때만 폴링(`polling-interval-ms`, 기본 5분)으로 폴백한다. 진행 상황은 메일함의
 * `\Seen` 플래그가 아니라 `Property`(yona `models/Property.java` 대응) 테이블에 저장하는
 * UID 워터마크(`MAILBOX_LAST_SEEN_UID`)+UID 유효성(`MAILBOX_LAST_UID_VALIDITY`)으로 별도 추적하며,
 * 폴더는 항상 `READ_ONLY`로 열어 메일함 자체(다른 IMAP 클라이언트가 보는 읽음 상태)는 절대 건드리지
 * 않는다 — 이전 버전(`\Seen` 플래그를 자체 북마크로 재활용)은 외부에서 먼저 읽힌 메일을 영구히
 * 스킵해버리는 위험이 있어 폐기했다.
 *
 * 실제 IMAP 서버 연결/스레드 관리는 순수 글루 코드라 단위테스트 대상에서 제외했다(이 저장소의 다른
 * *Config류 인프라 배선과 동일한 관례) — 다만 UID 구간 조회 여부 판단(`shouldFetchByUidRange`)과
 * 워터마크 전진 규칙(`advancedSeenUid`)은 순수 함수로 분리해 실제로 단위테스트한다.
 * `toInboundEmailMessage`(본문/첨부파일 MIME 파싱)도 기존과 동일하게 커버된다.
 */
@Component
@ConditionalOnProperty(prefix = "yona.mailbox.imap", name = ["enabled"], havingValue = "true")
class ImapMailboxPoller(
    private val incomingMailProcessingService: IncomingMailProcessingService,
    private val propertyService: PropertyService,
    private val taskScheduler: TaskScheduler,
    @Value("\${yona.mailbox.imap.host}") private val host: String,
    @Value("\${yona.mailbox.imap.user}") private val user: String,
    @Value("\${yona.mailbox.imap.password}") private val password: String,
    @Value("\${yona.mailbox.imap.ssl:true}") private val useSsl: Boolean,
    @Value("\${yona.mailbox.imap.folder:inbox}") private val folderName: String,
    @Value("\${yona.mailbox.imap.polling-interval-ms:300000}") private val pollingIntervalMs: Long,
    private val meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(ImapMailboxPoller::class.java)

    private var store: IMAPStore? = null
    private var folder: IMAPFolder? = null
    private var idleThread: Thread? = null
    private var pollingTask: ScheduledFuture<*>? = null
    @Volatile private var isStopping = false

    // yona MailboxService.start() 대응.
    @PostConstruct
    fun start() {
        try {
            store = connect()
            val opened = store!!.getFolder(folderName) as IMAPFolder
            opened.open(Folder.READ_ONLY)
            folder = opened
        } catch (e: Exception) {
            logger.error("IMAP 폴더를 여는 데 실패했습니다", e)
            return
        }

        handleNewMessagesAndStartListener()
    }

    // yona MailboxService.stop() 대응.
    @PreDestroy
    fun stop() {
        if (folder == null && store == null) {
            return
        }

        isStopping = true

        try {
            pollingTask?.cancel(false)
            folder?.close(false)
            store?.close()
        } catch (e: Exception) {
            logger.warn("IMAP 메일함 종료 중 오류가 발생했습니다", e)
        }
    }

    // yona MailboxService.java Diagnostic.register(new SimpleDiagnostic() { checkOne() })
    // 대응. idleThread가 null이면 아직 초기화되지 않은 것, isAlive가 false면 죽은 것.
    // (폴링 모드로 폴백한 경우 idleThread가 계속 null이라 이 체크는 IDLE 지원 서버에서만 유효 —
    // yona 원본도 동일한 한계를 가진다.)
    fun healthCheckMessage(): String? {
        val thread = idleThread
        return when {
            thread == null -> "The Email Receiver is not initialized"
            !thread.isAlive -> "The Email Receiver is not running"
            else -> null
        }
    }

    private fun connect(): IMAPStore {
        val protocol = if (useSsl) "imaps" else "imap"
        val props = Properties()
        props["mail.store.protocol"] = protocol

        val session = Session.getDefaultInstance(props)
        val newStore = session.getStore(protocol) as IMAPStore
        newStore.connect(host, user, password)
        return newStore
    }

    private fun reopenFolder(): IMAPFolder {
        val currentStore = store
        val reconnected = if (currentStore == null || !currentStore.isConnected) {
            connect().also { store = it }
        } else {
            currentStore
        }

        val reopened = reconnected.getFolder(folderName) as IMAPFolder
        reopened.open(Folder.READ_ONLY)
        return reopened
    }

    // yona MailboxService.handleNewMessagesAndStartListener() 대응.
    private fun handleNewMessagesAndStartListener() {
        val currentFolder = folder ?: return
        try {
            handleNewMessages(currentFolder)
        } catch (e: Exception) {
            logger.error("신규 메일 처리에 실패했습니다", e)
        }

        try {
            startEmailListener(currentFolder)
        } catch (e: Exception) {
            logger.info("IMAP 서버가 IDLE 명령을 지원하지 않아 폴링으로 대체합니다: ${e.message}")
            startEmailPolling()
        }
    }

    // yona EmailHandler.handleNewMessages(IMAPFolder) 대응. IDLE 콜백과 폴링 폴백(startEmailPolling())
    // 양쪽 경로가 모두 이 함수를 거치므로, 여기 하나만 계측해도 "폴링 사이클 소요시간"을 놓치지 않는다.
    private fun handleNewMessages(targetFolder: IMAPFolder) {
        val sample = Timer.start(meterRegistry)
        try {
            val lastUidValidity = propertyService.getLong(PropertyName.MAILBOX_LAST_UID_VALIDITY)
            val lastSeenUid = propertyService.getLong(PropertyName.MAILBOX_LAST_SEEN_UID)

            val uidValidity = targetFolder.uidValidity

            // UID validity가 이전과 달라졌다는 건 메일함이 재생성돼 기존 UID들이 더 이상 유효하지
            // 않다는 뜻이다(IMAP 스펙) — 이 경우 워터마크 기준 증분 조회를 포기하고 재동기화가 필요하다.
            if (lastUidValidity != null && lastUidValidity != uidValidity) {
                meterRegistry.counter("yona.mailbox.uid.resync").increment()
            }

            if (shouldFetchByUidRange(lastUidValidity, lastSeenUid, uidValidity)) {
                val messages = targetFolder.getMessagesByUID(lastSeenUid!! + 1, targetFolder.uidNext)
                handleMessages(targetFolder, messages.toList())
            }

            propertyService.set(PropertyName.MAILBOX_LAST_UID_VALIDITY, uidValidity)
        } finally {
            sample.stop(meterRegistry.timer("yona.mailbox.poll.duration"))
        }
    }

    // yona EmailHandler.handleNewMessages()의 "lastUIDValidity == uidValidity && lastSeenUID != null"
    // 조건 대응. 순수 함수로 분리해 단위테스트한다.
    internal fun shouldFetchByUidRange(lastUidValidity: Long?, lastSeenUid: Long?, currentUidValidity: Long): Boolean {
        return lastUidValidity != null && lastUidValidity == currentUidValidity && lastSeenUid != null
    }

    // yona EmailHandler.handleMessages(IMAPFolder, Message[]) 대응. 처리 중 장애가 나도 이미 처리된
    // 메일을 다시 놓치지 않도록 UID 오름차순으로 정렬해 순서대로 처리한다(yona 주석의 크래시 시나리오와
    // 동일한 이유).
    private fun handleMessages(targetFolder: IMAPFolder, messages: List<Message>) {
        val sorted = messages.sortedBy { targetFolder.getUID(it) }
        for (message in sorted) {
            handleMessage(targetFolder, message)
        }
    }

    // yona EmailHandler.handleMessage(IMAPMessage) 대응. 실제 리소스 생성 로직은
    // IncomingMailProcessingService에 전부 위임돼 있다 — 중복 메일 판별도 그쪽에서 이미 한다.
    private fun handleMessage(targetFolder: IMAPFolder, message: Message) {
        meterRegistry.counter("yona.mailbox.messages.processed").increment()
        try {
            val inbound = toInboundEmailMessage(message)
            val outcomes = incomingMailProcessingService.process(inbound)
            logger.info("메일 처리 결과: messageId=${inbound.messageId}, outcomes=$outcomes")
        } catch (e: Exception) {
            logger.error("메일 처리 실패: ${runCatching { message.subject }.getOrNull()}", e)
        } finally {
            // yona MailboxService.updateLastSeenUID()와 동일하게, 처리 성공/실패와 무관하게 워터마크를
            // 전진시킨다(재시도하지 않음) — legacy도 동일하게 동작한다.
            try {
                updateLastSeenUid(targetFolder, message)
            } catch (e: Exception) {
                logger.warn("워터마크(lastSeenUid) 갱신에 실패했습니다", e)
            }
        }
    }

    private fun updateLastSeenUid(targetFolder: IMAPFolder, message: Message) {
        val uid = targetFolder.getUID(message)
        val currentSeenUid = propertyService.getLong(PropertyName.MAILBOX_LAST_SEEN_UID)
        val advanced = advancedSeenUid(currentSeenUid, uid) ?: return
        propertyService.set(PropertyName.MAILBOX_LAST_SEEN_UID, advanced)
    }

    // yona MailboxService.updateLastSeenUID()의 "uid <= lastSeenUID면 갱신하지 않는다" 대응. 순수
    // 함수로 분리해 단위테스트한다. 갱신이 필요 없으면 null을 반환한다.
    internal fun advancedSeenUid(currentSeenUid: Long?, candidateUid: Long): Long? {
        if (currentSeenUid != null && candidateUid <= currentSeenUid) {
            return null
        }
        return candidateUid
    }

    // yona MailboxService.startEmailListener() 대응.
    private fun startEmailListener(targetFolder: IMAPFolder) {
        if (!(targetFolder.store as IMAPStore).hasCapability("IDLE")) {
            throw UnsupportedOperationException("IMAP 서버가 IDLE 명령을 지원하지 않습니다")
        }

        targetFolder.addMessageCountListener(object : MessageCountListener {
            override fun messagesAdded(e: MessageCountEvent) {
                try {
                    handleMessages(targetFolder, e.messages.toList())
                } catch (ex: Exception) {
                    logger.error("IDLE로 수신한 메일 처리 중 예기치 못한 오류가 발생했습니다", ex)
                }
            }

            override fun messagesRemoved(e: MessageCountEvent) {
                // no-op (yona와 동일)
            }
        })

        idleThread = Thread {
            logger.info("IMAP 메일 수신 스레드를 시작합니다")
            var currentFolder = targetFolder
            while (!isStopping) {
                try {
                    currentFolder.idle()
                } catch (e: FolderClosedException) {
                    if (isStopping) break
                    logger.info("IMAP 폴더가 닫혀 다시 엽니다")
                    try {
                        currentFolder = reopenFolder()
                        folder = currentFolder
                    } catch (reopenEx: Exception) {
                        logger.warn("IMAP 폴더를 다시 여는 데 실패했습니다; 중단합니다", reopenEx)
                        break
                    }
                } catch (e: Exception) {
                    logger.warn("IDLE 명령 실행에 실패했습니다; 중단합니다", e)
                    break
                }
            }
            logger.info("IMAP 메일 수신 스레드를 종료합니다")
        }.apply {
            isDaemon = true
            name = "imap-idle-thread"
            start()
        }
    }

    // yona MailboxService.startEmailPolling() 대응 — IDLE 미지원 서버에 대한 폴백.
    private fun startEmailPolling() {
        pollingTask = taskScheduler.scheduleWithFixedDelay(
            {
                try {
                    var currentFolder = folder
                    if (currentFolder == null || !currentFolder.isOpen) {
                        currentFolder = reopenFolder()
                        folder = currentFolder
                    }
                    handleNewMessages(currentFolder)
                } catch (e: Exception) {
                    logger.error("IMAP 폴링 중 오류가 발생했습니다", e)
                }
            },
            Instant.now(),
            Duration.ofMillis(pollingIntervalMs)
        )
    }

    internal fun toInboundEmailMessage(message: Message): InboundEmailMessage {
        val from = message.from?.filterIsInstance<InternetAddress>()?.firstOrNull()
        val recipients = message.allRecipients
            ?.filterIsInstance<InternetAddress>()
            ?.map { it.address }
            ?: emptyList()
        val mime = message as? MimeMessage
        val body = extractBody(message)

        return InboundEmailMessage(
            messageId = mime?.messageID ?: "",
            subject = message.subject ?: "",
            fromAddress = from?.address ?: "",
            fromName = from?.personal ?: (from?.address ?: ""),
            recipientAddresses = recipients,
            inReplyTo = mime?.getHeader("In-Reply-To")?.firstOrNull(),
            references = mime?.getHeader("References")?.firstOrNull(),
            textBody = body.content,
            isHtml = body.isHtml,
            attachments = extractAttachments(message)
        )
    }

    // yona CreationViaEmail.saveAttachments()가 순회하는 MIME 파트 트리 대응.
    // Content-ID도 함께 추출해 본문의 cid: 참조와 매칭할 수 있게 한다.
    private fun extractAttachments(part: Part): List<InboundAttachment> {
        return try {
            when {
                part.isMimeType("multipart/*") -> {
                    val multipart = part.content as Multipart
                    (0 until multipart.count).flatMap { extractAttachments(multipart.getBodyPart(it)) }
                }
                !part.fileName.isNullOrBlank() -> {
                    val bytes = part.inputStream.use { it.readBytes() }
                    val contentId = (part as? MimePart)?.contentID?.trim('<', '>')
                    listOf(InboundAttachment(part.fileName, part.contentType ?: "application/octet-stream", bytes, contentId))
                }
                else -> emptyList()
            }
        } catch (e: Exception) {
            logger.warn("첨부파일 추출 실패", e)
            emptyList()
        }
    }

    private data class ExtractedBody(val content: String, val isHtml: Boolean)

    // yona CreationViaEmail.processPart()/getContentOfBestPart() 대응(간소화 - 여러 표현 중 첫 번째로
    // 발견되는 비어있지 않은 파트를 그대로 쓴다). text/plain이 있으면 그걸 쓰고, text/html뿐이면
    // 태그를 벗겨 텍스트화하지 않고 원본 HTML을 그대로 보존한다(cid 치환·마크다운 렌더링은 상위에서 처리).
    private fun extractBody(part: Part): ExtractedBody {
        return try {
            when {
                part.isMimeType("text/plain") -> ExtractedBody(part.content as? String ?: "", false)
                part.isMimeType("multipart/*") -> {
                    val multipart = part.content as Multipart
                    (0 until multipart.count)
                        .map { extractBody(multipart.getBodyPart(it)) }
                        .firstOrNull { it.content.isNotBlank() } ?: ExtractedBody("", false)
                }
                part.isMimeType("text/html") -> ExtractedBody(part.content as? String ?: "", true)
                else -> ExtractedBody("", false)
            }
        } catch (e: Exception) {
            logger.warn("메일 본문 추출 실패", e)
            ExtractedBody("", false)
        }
    }
}
