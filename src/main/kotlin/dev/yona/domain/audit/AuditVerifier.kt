package dev.yona.domain.audit

import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 사슬 검증의 결과이다.
 *
 * @property ok 사슬이 온전하면 `true`
 * @property checked 검증한 기록 수
 * @property brokenId 처음 어긋난 기록의 번호(온전하면 `null`)
 * @property reason 어긋난 이유(`hash`, `prev_hash`, `head`)
 */
data class AuditVerification(val ok: Boolean, val checked: Long, val brokenId: Long? = null, val reason: String? = null)

/**
 * 감사 해시 사슬을 처음부터 다시 계산해 변조나 누락을 찾는다(`decisions.md` D-62). 보존으로 지운 앞부분은 [AuditChainHead]의 `pruned_hash`에서 이어 가며,
 * 마지막 기록이 사슬의 끝(`last_id`, `last_hash`)과 같은지도 본다. 끝 기록을 지우는 변조는 이 비교로 잡는다.
 */
@Service
class AuditVerifier(private val logs: AuditLogRepository, private val heads: AuditChainHeadRepository) {

    @Transactional(readOnly = true)
    fun verify(): AuditVerification {
        val head = heads.findById(1L).orElse(null)
        var previousHash = head?.prunedHash ?: AuditHash.GENESIS
        var lastId = head?.prunedThroughId ?: 0L
        var checked = 0L
        while (true) {
            val page = logs.findByIdGreaterThanOrderByIdAsc(lastId, PageRequest.of(0, PAGE))
            if (page.isEmpty()) break
            for (entry in page) {
                if (entry.prevHash != previousHash) return AuditVerification(false, checked, entry.id, "prev_hash")
                if (entry.hash != AuditHash.of(entry)) return AuditVerification(false, checked, entry.id, "hash")
                previousHash = entry.hash
                lastId = entry.id
                checked++
            }
        }
        if (head != null && (head.lastId != lastId || head.lastHash != previousHash)) return AuditVerification(false, checked, head.lastId, "head")
        return AuditVerification(true, checked)
    }

    private companion object {
        const val PAGE = 500
    }
}
