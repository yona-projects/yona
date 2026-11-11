package dev.yona.security

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository

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
