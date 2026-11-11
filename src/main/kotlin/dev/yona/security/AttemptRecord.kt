package dev.yona.security

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 연속 실패 횟수와 잠금 만료 시각을 저장하는 행이다(v2 추가, usersec-F12). [key]는 `2fa:<사용자 번호>`, `device:<사용자 번호>`,
 * `login:<소문자 로그인 ID>`처럼 어디서 센 횟수인지를 나타낸다. 서버를 다시 시작해도 잠금이 남게 DB에 둔다.
 *
 * @property key 기본 키
 * @property failures 잠금 전까지 연달아 틀린 횟수
 * @property lockedUntil 잠금이 풀리는 시각(에폭 밀리초). 잠겨 있지 않으면 0
 */
@Entity
@Table(name = "attempt_record")
class AttemptRecord(
    @Id @Column(name = "attempt_key", length = 255)
    var key: String = "",
    var failures: Int = 0,
    @Column(name = "locked_until")
    var lockedUntil: Long = 0
)

/** [AttemptRecord] 저장소이다. */
interface AttemptRecordRepository : JpaRepository<AttemptRecord, String>

/**
 * [AttemptRecord]를 읽고 쓰는 저장소 래퍼이다. 모든 메서드가 호출한 요청과 별개의 트랜잭션에서 실행된다. 그래야 요청이 중간에 되돌아가도 실패 기록이
 * 남고, 같은 요청 안에서 기록한 뒤 다시 읽을 때 낡은 값을 보지 않는다.
 */
@Component
class AttemptStore(private val records: AttemptRecordRepository) {

    /** [key]의 잠금 만료 시각(에폭 밀리초)이다. 기록이 없으면 0이다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    fun lockedUntil(key: String): Long = records.findById(key).orElse(null)?.lockedUntil ?: 0

    /** 실패를 하나 더하고, [limit]에 닿으면 [lockMillis] 동안 잠근 뒤 `true`를 돌려준다. 이미 풀린 잠금은 먼저 지우고 센다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun fail(key: String, now: Long, limit: Int, lockMillis: Long): Boolean {
        val entry = records.findById(key).orElseGet { AttemptRecord(key) }
        if (entry.lockedUntil in 1..now) { entry.failures = 0; entry.lockedUntil = 0 }
        entry.failures++
        var locked = false
        if (entry.failures >= limit) { entry.lockedUntil = now + lockMillis; entry.failures = 0; locked = true }
        records.saveAndFlush(entry)
        return locked
    }

    /** [key]의 기록을 지운다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun clear(key: String) { if (records.existsById(key)) records.deleteById(key) }
}
