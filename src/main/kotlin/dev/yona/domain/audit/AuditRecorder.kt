package dev.yona.domain.audit

import dev.yona.security.YonaAuthentication
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.util.UUID

/**
 * 감사 기록을 남기는 곳이다(추가 개발 항목 X-9, `decisions.md` D-62). 호출하는 쪽은 조치와 대상만 주고, 행위자(사용자, 토큰, 시스템, 익명)와 요청 정보(IP, 사용자 에이전트, 요청 번호)는
 * 요청 문맥에서 채운다. 비밀 값은 상세에서 가린다([AuditDetails]).
 *
 * 두 가지 방식이 있다.
 * - [record]: 호출한 트랜잭션에 참여한다. 상태 변경과 함께 커밋되거나 롤백되고, 기록하지 못하면 예외로 변경도 실패한다(fail-closed).
 * - [recordIndependent]: 별도 트랜잭션이다. 로그인 실패나 접근 거부처럼 바깥 요청이 실패해도 남아야 하는 경계 이벤트에 쓴다. 기록하지 못해도 예외를 던지지 않고 오류만 남긴다.
 *
 * 기록하는 동안 [AuditChainHead]를 비관적 잠금으로 잡아 해시 사슬을 한 줄로 만든다. 잠금은 커밋까지 이어지므로 사슬 순서가 커밋 순서이다.
 */
@Service
class AuditRecorder(
    private val logs: AuditLogRepository,
    private val heads: AuditChainHeadRepository,
    private val entityManager: EntityManager,
    private val sink: AuditFileSink,
    private val transactionManager: PlatformTransactionManager
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 기록 시각의 시계이다(에폭 밀리초). 보존 시험이 옛 시각의 기록을 만들려고 바꾼다. 운영에서는 시스템 시계이다. */
    internal var clock: () -> Long = System::currentTimeMillis

    /** 호출한 트랜잭션에서 [event]를 기록한다. 트랜잭션이 없으면 새로 만든다. */
    @Transactional
    fun record(event: AuditEvent): AuditLog {
        val head = lockedHead()
        val request = requestInfo()
        val actor = event.actor ?: currentActor(request != null)
        val entry = AuditLog(
            id = head.lastId + 1, createdMillis = clock(), actorType = actor.type, actorId = actor.id, actorLogin = actor.login?.take(255),
            action = event.action, outcome = event.outcome, targetType = event.targetType, targetId = event.targetId?.take(255), targetLabel = event.targetLabel?.take(255),
            organizationId = event.organizationId, projectId = event.projectId, ip = request?.ip, userAgent = request?.userAgent, requestId = request?.requestId,
            tokenId = actor.tokenId, tokenScopes = actor.tokenScopes, details = AuditDetails.toJson(event.details), prevHash = head.lastHash
        )
        entry.contentHash = AuditHash.contentOf(entry)
        entry.hash = AuditHash.of(entry)
        entityManager.persist(entry)
        head.lastId = entry.id
        head.lastHash = entry.hash
        afterCommit { sink.append(entry) }
        return entry
    }

    /**
     * 별도 트랜잭션에서 [event]를 기록한다. 기록하지 못해도 예외를 던지지 않는다(경계 이벤트는 요청을 막지 않는다).
     *
     * 바깥 트랜잭션이 이미 같은 요청에서 [record]로 사슬의 끝을 잠갔다면 별도 트랜잭션이 그 잠금을 기다려 서로 멈춘다. 그때는 바깥 트랜잭션이 끝난 뒤(커밋이든 롤백이든)에
     * 새 트랜잭션으로 기록한다. 어느 쪽이든 바깥이 롤백해도 이 이벤트는 남는다.
     */
    fun recordIndependent(event: AuditEvent) {
        if (TransactionSynchronizationManager.isSynchronizationActive() && TransactionSynchronizationManager.hasResource(HELD_KEY)) {
            // 요청 문맥(행위자, IP)은 바깥이 끝나는 시점에도 같은 스레드에 있으므로 그대로 읽힌다
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCompletion(status: Int) = writeIndependent(event)
            })
            return
        }
        writeIndependent(event)
    }

    private fun writeIndependent(event: AuditEvent) {
        try {
            TransactionTemplate(transactionManager).apply { propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW }.executeWithoutResult { record(event) }
        } catch (e: Exception) {
            log.error("감사 로그를 기록하지 못했습니다: action={}", event.action, e)
        }
    }

    /** 사슬의 끝 행을 잠가 읽는다. 없으면 별도 트랜잭션에서 만든 뒤 다시 잠근다(동시에 만들려는 쪽이 있어도 하나만 남는다). */
    private fun lockedHead(): AuditChainHead {
        markHeld()
        heads.lockHead()?.let { return it }
        try {
            TransactionTemplate(transactionManager).apply { propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW }.executeWithoutResult {
                if (heads.lockHead() == null) entityManager.persist(AuditChainHead())
            }
        } catch (e: Exception) {
            log.debug("감사 사슬의 끝은 다른 쪽이 먼저 만들었다", e)
        }
        return heads.lockHead() ?: error("감사 사슬의 끝을 만들지 못했습니다")
    }

    /** 이 트랜잭션이 사슬의 끝을 잠갔음을 표시한다. 트랜잭션이 끝나면 표시도 지운다. */
    private fun markHeld() {
        if (!TransactionSynchronizationManager.isSynchronizationActive() || TransactionSynchronizationManager.hasResource(HELD_KEY)) return
        TransactionSynchronizationManager.bindResource(HELD_KEY, true)
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            // 자원 해제는 `afterCompletion`보다 먼저 일어나므로 `beforeCompletion`에서 풀어 두 번째 이벤트의 지연 기록이 새 트랜잭션을 쓰게 한다
            override fun beforeCompletion() {
                if (TransactionSynchronizationManager.hasResource(HELD_KEY)) TransactionSynchronizationManager.unbindResource(HELD_KEY)
            }
        })
    }

    private fun afterCommit(action: () -> Unit) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() = action()
            })
        } else {
            action()
        }
    }

    /** 요청 문맥에서 읽은 접속 정보이다. 요청 밖(스케줄러 등)이면 `null`이다. */
    private class RequestInfo(val ip: String?, val userAgent: String?, val requestId: String)

    private fun requestInfo(): RequestInfo? {
        val attributes = RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes ?: return null
        val request = attributes.request
        val requestId = (attributes.getAttribute(REQUEST_ID_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST) as? String) ?: run {
            val supplied = request.getHeader("X-Request-Id")?.trim()?.takeIf { it.length <= 64 && it.all { c -> c.isLetterOrDigit() || c in "._-" } }
            (supplied ?: UUID.randomUUID().toString()).also { attributes.setAttribute(REQUEST_ID_ATTRIBUTE, it, RequestAttributes.SCOPE_REQUEST) }
        }
        return RequestInfo(request.remoteAddr?.take(64), request.getHeader("User-Agent")?.take(500), requestId)
    }

    /** 인증 객체로 행위자를 정한다. 인증이 없으면 요청 안에서는 익명, 밖에서는 시스템이다. */
    private fun currentActor(inRequest: Boolean): AuditActor {
        val authentication = SecurityContextHolder.getContext().authentication as? YonaAuthentication
            ?: return AuditActor(if (inRequest) AuditActorType.ANONYMOUS else AuditActorType.SYSTEM, null, null)
        val principal = authentication.principal
        return if (authentication.viaToken) {
            AuditActor(AuditActorType.TOKEN, principal.userId, principal.loginId, authentication.tokenId, authentication.scopes?.sorted()?.joinToString(","))
        } else {
            AuditActor(AuditActorType.USER, principal.userId, principal.loginId)
        }
    }

    private companion object {
        const val REQUEST_ID_ATTRIBUTE = "dev.yona.domain.audit.RequestId"

        /** 이 트랜잭션이 사슬의 끝을 잠갔다는 표시의 자원 키이다. */
        val HELD_KEY = Any()
    }
}
