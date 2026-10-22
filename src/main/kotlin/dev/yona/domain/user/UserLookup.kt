package dev.yona.domain.user

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 이메일 주소로 사용자를 찾는다. v1.6 `User.findByEmail`(`app/models/User.java:337-355`)에 해당한다.
 *
 * 먼저 대표 이메일이 정확히 같은 사용자를, 없으면 인증이 끝난 보조 이메일의 사용자를, 그래도 없으면 대소문자를 무시하고 대표 이메일이
 * 같은 사용자를 찾는다. 커밋 작성자를 사용자로 연결할 때 쓴다. 찾지 못하면 `null`이며 v1.6의 "이메일만 가진 익명 사용자"에 해당한다.
 */
@Component
class UserLookup(private val users: UserRepository, private val emails: EmailRepository) {

    @Transactional(readOnly = true)
    fun findByEmail(email: String?): User? {
        if (email.isNullOrEmpty()) return null
        users.findByEmail(email)?.let { return it }
        emails.findByEmailIgnoreCaseAndValid(email, true).firstOrNull { it.email == email }?.user?.let { return it }
        return users.findFirstByEmailIgnoreCaseOrderById(email)
    }
}
