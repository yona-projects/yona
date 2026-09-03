package com.github.yonaprojects.yona.config.oauth2

import com.github.yonaprojects.yona.domain.user.LinkedAccountRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.UserState
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// yona UserCredential.merge(otherUser) 대응 (P1-56). yona가 Global.askMerge()를 null로 두어
// 확인 절차 없이 자동 병합하는 것과 동일하게, CustomOAuth2UserService.loadUser()가 병합 충돌을
// 감지한 즉시 이 서비스를 호출해 병합을 수행한다.
@Service
class OAuth2AccountMergeService(
    private val userRepository: UserRepository,
    private val linkedAccountRepository: LinkedAccountRepository
) {
    @Transactional
    fun merge(keepUserId: Long, otherUserId: Long): User {
        val keepUser = userRepository.findById(keepUserId)
            .orElseThrow { IllegalArgumentException("User not found: $keepUserId") }

        if (keepUserId == otherUserId) {
            return keepUser
        }

        val otherUser = userRepository.findById(otherUserId)
            .orElseThrow { IllegalArgumentException("User not found: $otherUserId") }

        val accountsToMove = linkedAccountRepository.findByUser(otherUser)
        accountsToMove.forEach { it.user = keepUser }
        linkedAccountRepository.saveAll(accountsToMove)

        // yona UserCredential.merge()의 "otherUser.active = false" 대응 — 병합된(사라질) 계정은
        // 다시는 로그인 수단으로 쓸 수 없도록 잠근다(P0-13의 LOCKED 계정 로그인 차단 로직이 그대로 적용됨).
        otherUser.state = UserState.LOCKED
        userRepository.save(otherUser)

        return keepUser
    }
}
