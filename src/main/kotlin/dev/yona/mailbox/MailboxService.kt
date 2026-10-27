package dev.yona.mailbox

import dev.yona.config.YonaProperties
import dev.yona.domain.enumeration.PropertyName
import dev.yona.domain.misc.PropertyService
import dev.yona.domain.user.UserRepository
import jakarta.annotation.PreDestroy
import jakarta.mail.FolderClosedException
import jakarta.mail.Folder
import jakarta.mail.Message
import jakarta.mail.MessagingException
import jakarta.mail.Session
import jakarta.mail.event.MessageCountAdapter
import jakarta.mail.event.MessageCountEvent
import jakarta.mail.internet.MimeMessage
import org.eclipse.angus.mail.imap.IMAPFolder
import org.eclipse.angus.mail.imap.IMAPStore
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.Properties
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * 설정한 IMAP 서버의 받은 편지함에서 새 메일을 가져와 [MailHandler]로 처리한다. v1.6 `mailbox.MailboxService`
 * (`app/mailbox/MailboxService.java`)에 해당한다.
 *
 * 시작할 때 폴더를 연다. 이전에 쓰던 폴더와 같으면(UID 유효값이 같으면) 마지막으로 본 UID 뒤의 메일을 가져와 처리한다. 처음 시작하거나
 * 폴더가 바뀌었으면 지난 메일은 처리하지 않고 UID 유효값만 기록한다. 그 뒤로는 IDLE 명령으로 새 메일을 바로 받고, 서버가 IDLE을 지원하지
 * 않으면 `yona.mailbox-polling-interval`(기본 5분)마다 가져온다. 처리한 메일의 UID가 지금까지의 마지막 UID보다 크면 속성
 * `MAILBOX_LAST_SEEN_UID`로 기록한다.
 *
 * 폴더를 열지 못하면 경고만 남기고 다시 시도하지 않는다(v1.6과 같다). 수신 대기 스레드가 죽으면 진단 화면이 알린다.
 */
@Service
class MailboxService(
    private val properties: YonaProperties,
    private val propertyService: PropertyService,
    private val handler: MailHandler,
    private val users: UserRepository
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val imap get() = properties.imap

    @Volatile private var store: IMAPStore? = null
    @Volatile private var folder: IMAPFolder? = null
    @Volatile private var idleThread: Thread? = null
    @Volatile private var polling: ScheduledExecutorService? = null
    @Volatile private var stopping = false

    @Volatile private var started = false

    /** 시작에 성공했으면 `true`이다. 한 번 시작하면 멈춰도 유지한다(진단은 멈춘 뒤에도 알려야 한다). */
    val running: Boolean get() = started

    /** 서버가 IDLE을 지원해도 쓰지 않고 폴링한다. 테스트가 폴링 경로를 확인하는 데 쓴다. */
    @Volatile var useIdle: Boolean = true

    /** 설정한 서버에 연결한다. 호스트, 사용자, 비밀번호가 하나라도 없으면 연결하지 못한다. */
    private fun connect(): IMAPStore {
        val missing = listOf("imap.host" to imap.host, "imap.user" to imap.user, "imap.password" to imap.password).filter { it.second.isEmpty() }.map { it.first }
        check(missing.isEmpty()) { "Cannot connect to the IMAP server because these are not configured: $missing" }
        val session = Session.getInstance(Properties().apply { setProperty("mail.store.protocol", if (imap.ssl) "imaps" else "imap") })
        val connected = session.getStore() as IMAPStore
        val port = imap.port
        if (port != null) connected.connect(imap.host, port, imap.user, imap.password) else connected.connect(imap.host, imap.user, imap.password)
        store = connected
        return connected
    }

    /** 폴더를 읽기 전용으로 연다. */
    private fun openFolder(connected: IMAPStore): IMAPFolder = (connected.getFolder(imap.folder) as IMAPFolder).also { it.open(Folder.READ_ONLY) }

    /** 서버에 연결해 폴더를 열고 새 메일 처리를 시작한다. 설정이 없거나 꺼져 있거나 열지 못하면 아무것도 하지 않는다. */
    fun start() {
        if (imap.host.isEmpty()) {
            log.info("Mailbox Service doesn't start because IMAP server is not configured.")
            return
        }
        if (!imap.use) return

        // `<imap 사용자>+...`로 시작하는 이메일을 가진 사용자는 메일 수신으로 프로젝트에 글을 올릴 수 있는 주소와 겹친다.
        val dangerous = users.findByEmailStartingWithIgnoreCase(imap.user + "+")
        if (dangerous.size == 1) log.warn("There is a user whose email is danger: {}", dangerous)
        if (dangerous.size > 1) log.warn("There are some users whose email is danger: {}", dangerous)

        stopping = false
        try {
            folder = openFolder(connect())
        } catch (e: Exception) {
            log.error("Failed to open IMAP folder", e)
            return
        }
        val begin = { handleNewMessagesAndListen() }
        if (properties.backgroundAsync) Executors.newSingleThreadExecutor { Thread(it, "mailbox-start").apply { isDaemon = true } }.also { it.execute(begin); it.shutdown() } else begin()
        started = true
    }

    private fun handleNewMessagesAndListen() {
        try {
            handleNewMessages(requireNotNull(folder))
        } catch (e: MessagingException) {
            log.error("Failed to handle new messages")
        }
        try {
            startListener()
        } catch (e: Exception) {
            startPolling()
        }
    }

    /** 이전에 쓰던 폴더와 같으면 마지막으로 본 UID 뒤의 메일을 처리하고, UID 유효값을 기록한다. */
    private fun handleNewMessages(target: IMAPFolder) {
        val lastValidity = propertyService.getLong(PropertyName.MAILBOX_LAST_UID_VALIDITY)
        val lastSeen = propertyService.getLong(PropertyName.MAILBOX_LAST_SEEN_UID)
        val validity = target.uidValidity
        if (lastValidity != null && lastValidity == validity && lastSeen != null) {
            // 폴더의 마지막 UID가 아니라 다음 UID를 쓴다. 마지막 UID는 lastSeen + 1보다 작을 수 있다.
            handleMessages(target, target.getMessagesByUID(lastSeen + 1, target.uidNext).filterNotNull())
        }
        propertyService.set(PropertyName.MAILBOX_LAST_UID_VALIDITY, validity)
    }

    /**
     * 메일을 UID 순서로 처리한다. 순서대로 처리하지 않으면 UID 1, 3, 2를 받아 3까지 처리한 뒤 비정상 종료했을 때 2를 영영 처리하지 못한다.
     */
    private fun handleMessages(target: IMAPFolder, messages: List<Message>) {
        val sorted = messages.sortedBy {
            try {
                target.getUID(it)
            } catch (e: MessagingException) {
                log.warn("Failed to compare uids of {} while sorting messages by the uid; There is some remote chance of loss of mail requests.", it)
                0L
            }
        }
        for (message in sorted) handler.handle(message as MimeMessage) { updateLastSeenUid(target.getUID(message)) }
    }

    /** 처리한 메일의 UID가 지금까지의 마지막 UID보다 클 때만 기록한다. */
    @Synchronized
    internal fun updateLastSeenUid(uid: Long) {
        val last = try {
            propertyService.getLong(PropertyName.MAILBOX_LAST_SEEN_UID)
        } catch (ignored: Exception) {
            null
        }
        if (last != null && uid <= last) return
        propertyService.set(PropertyName.MAILBOX_LAST_SEEN_UID, uid)
    }

    /** IDLE 명령으로 새 메일을 기다린다. 서버가 IDLE을 지원하지 않으면 예외이다. */
    private fun startListener() {
        val target = requireNotNull(folder)
        if (!useIdle || !(target.store as IMAPStore).hasCapability("IDLE")) throw UnsupportedOperationException("The imap server does not support IDLE command")
        target.addMessageCountListener(object : MessageCountAdapter() {
            override fun messagesAdded(e: MessageCountEvent) {
                try {
                    handleMessages(target, e.messages.toList())
                } catch (e1: Exception) {
                    log.error("Unexpected error occurs while handling messages", e1)
                }
            }
        })
        idleThread = Thread({
            log.info("Start the Email Receiving Thread")
            while (!stopping) {
                try {
                    // 폴더의 EXISTS 응답이 실제 개수보다 크면 메시지 수 리스너가 불린다.
                    requireNotNull(folder).idle()
                } catch (e: FolderClosedException) {
                    if (stopping) break
                    log.info("Reopen the imap folder")
                    try {
                        folder = reopenFolder()
                    } catch (e1: MessagingException) {
                        log.warn("Failed to reopen the imap folder; abort", e1)
                        break
                    }
                } catch (e: Exception) {
                    log.warn("Failed to run IDLE command; abort", e)
                    break
                }
            }
            log.info("Stop the Email Receiving Thread")
        }, "mailbox-idle").apply { isDaemon = true; start() }
    }

    private fun reopenFolder(): IMAPFolder {
        val current = store
        val connected = if (current == null || !current.isConnected) connect() else current
        return openFolder(connected)
    }

    /** IDLE을 쓸 수 없을 때 일정 간격으로 새 메일을 가져온다. */
    private fun startPolling() {
        polling = Executors.newSingleThreadScheduledExecutor { Thread(it, "mailbox-polling").apply { isDaemon = true } }.also {
            it.scheduleWithFixedDelay({ pollOnce() }, 0, properties.mailboxPollingInterval.toMillis(), TimeUnit.MILLISECONDS)
        }
    }

    /** 폴더를 (닫혀 있으면 다시 열어) 새 메일을 처리하고 닫는다. */
    @Synchronized
    fun pollOnce() {
        try {
            var target = folder
            if (target == null || !target.isOpen) {
                target = reopenFolder()
                folder = target
            }
            handleNewMessages(target)
        } catch (e: MessagingException) {
            log.error("Failed to poll emails", e)
            return
        }
        try {
            folder?.close(true)
        } catch (e: MessagingException) {
            log.error("Failed to close the IMAP folder", e)
        }
    }

    /** 수신을 멈추고 폴더와 연결을 닫는다. 시작하지 않았으면 아무것도 하지 않는다. */
    @PreDestroy
    fun stop() {
        if (folder == null && store == null && polling == null) return
        stopping = true
        // 이미 닫힌 폴더를 닫으면 IllegalStateException이다. 폴더와 연결을 따로 닫아 하나가 실패해도 나머지를 닫는다.
        try {
            folder?.close(true)
        } catch (e: Exception) {
            log.error("Error occurred while stop the email receiver", e)
        }
        try {
            store?.close()
        } catch (e: Exception) {
            log.error("Error occurred while stop the email receiver", e)
        }
        polling?.shutdownNow()
        idleThread?.join(3000)
    }

    /**
     * 수신 상태를 점검한다 (v1.6 `MailboxService.start`가 등록하는 진단). 시작하지 않았으면 점검하지 않는다. 폴링으로 받는 경우에도
     * 수신 대기 스레드가 없어 "초기화되지 않았다"고 알린다(v1.6과 같다, R-61).
     */
    fun diagnose(): List<String> {
        if (!running) return emptyList()
        val thread = idleThread
        return when {
            thread == null -> listOf("The Email Receiver is not initialized")
            !thread.isAlive -> listOf("The Email Receiver is not running")
            else -> emptyList()
        }
    }
}
