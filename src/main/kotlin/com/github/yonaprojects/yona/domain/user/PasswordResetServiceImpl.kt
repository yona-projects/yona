package com.github.yonaprojects.yona.domain.user

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
@Transactional
class PasswordResetServiceImpl(
    private val userRepository: UserRepository,
    private val passwordEncodingService: PasswordEncodingService
) : PasswordResetService {

    // loginId -> hashString
    private val resetHashMap = ConcurrentHashMap<String, String>()
    // hashString -> timestamp (milliseconds)
    private val resetHashTimetable = ConcurrentHashMap<String, Long>()

    companion object {
        private const val HASH_EXPIRE_TIME_MILLISEC = 3600 * 1000 // 1 hour
    }

    override fun generateResetHash(loginId: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
        val randomSalt = UUID.randomUUID().toString()
        digest.update(loginId.toByteArray(Charsets.UTF_8))
        digest.update(randomSalt.toByteArray(Charsets.UTF_8))
        val hashedBytes = digest.digest()
        
        // Hex String으로 변환
        return hashedBytes.joinToString("") { String.format("%02x", it) }
    }

    override fun addHashToResetTable(loginId: String, hashString: String) {
        resetHashMap[loginId] = hashString
        resetHashTimetable[hashString] = System.currentTimeMillis()
    }

    override fun isValidResetHash(hashString: String): Boolean {
        if (!resetHashMap.containsValue(hashString)) {
            return false
        }
        if (isExpired(hashString)) {
            removeResetHash(hashString)
            return false
        }
        return true
    }

    override fun resetPassword(hashString: String, newPassword: String): Boolean {
        if (!isValidResetHash(hashString)) {
            return false
        }

        val loginId = getKeyByValue(resetHashMap, hashString) ?: return false
        val user = userRepository.findByLoginId(loginId).orElse(null) ?: return false

        user.passwordSalt = null
        user.password = passwordEncodingService.encode(newPassword)
        userRepository.save(user)

        removeResetHash(hashString)
        return true
    }

    private fun isExpired(hashString: String): Boolean {
        val timestamp = resetHashTimetable[hashString] ?: return true
        return (timestamp + HASH_EXPIRE_TIME_MILLISEC) < System.currentTimeMillis()
    }

    private fun removeResetHash(hashString: String) {
        val key = getKeyByValue(resetHashMap, hashString)
        if (key != null) {
            resetHashMap.remove(key)
        }
        resetHashTimetable.remove(hashString)
    }

    private fun <K, V> getKeyByValue(map: Map<K, V>, value: V): K? {
        for ((key, v) in map.entries) {
            if (value == v) {
                return key
            }
        }
        return null
    }
}
