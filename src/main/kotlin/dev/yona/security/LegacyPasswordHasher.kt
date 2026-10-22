package dev.yona.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Yona v1.6과 호환되는 패스워드 해시이다.
 *
 * v1.6은 Apache Shiro의 `Sha256Hash(평문, 솔트, 1024).toBase64()`를 사용했다(`UserApp.hashedPassword`,
 * `app/controllers/UserApp.java:1027-1032`). 기존 계정의 해시를 그대로 검증할 수 있어야 하므로
 * 같은 알고리즘을 그대로 구현한다.
 *
 * - 첫 번째 반복은 `SHA-256(솔트의 UTF-8 바이트 + 평문의 UTF-8 바이트)`이다.
 * - 이후 반복은 직전 해시값에 `SHA-256`을 다시 적용한다. 총 반복 횟수는 1024회이다.
 * - 결과는 Base64(패딩 포함)로 인코딩한다.
 *
 * 솔트는 바이트 배열이 아니라 **문자열의 UTF-8 바이트**로 취급한다. v1.6의 초기 데이터에 들어 있는
 * `[B@1032a4` 같은 값도 문자열 그대로 솔트가 된다.
 */
object LegacyPasswordHasher {

    /** v1.6의 `HASH_ITERATIONS`이다. */
    private const val ITERATIONS = 1024

    /** v1.6이 새 솔트를 만들 때 쓰던 Shiro `SecureRandomNumberGenerator`의 기본 길이(바이트)이다. */
    private const val SALT_BYTES = 16

    private val random = SecureRandom()

    /**
     * 평문 패스워드와 솔트로 저장용 해시 문자열을 만든다.
     *
     * @param plainText 사용자가 입력한 평문 패스워드
     * @param salt `n4user.password_salt`에 저장된 솔트 문자열
     * @return Base64로 인코딩한 해시
     */
    fun hash(plainText: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt.toByteArray(Charsets.UTF_8))
        var hashed = digest.digest(plainText.toByteArray(Charsets.UTF_8))
        repeat(ITERATIONS - 1) {
            digest.reset()
            hashed = digest.digest(hashed)
        }
        return Base64.getEncoder().encodeToString(hashed)
    }

    /**
     * 저장된 해시가 입력한 평문 패스워드와 일치하는지 검사한다.
     *
     * 해시 비교에 걸리는 시간으로 값을 추측하지 못하도록 상수 시간 비교를 사용한다.
     */
    fun matches(plainText: String, salt: String, storedHash: String): Boolean =
        MessageDigest.isEqual(
            hash(plainText, salt).toByteArray(Charsets.UTF_8),
            storedHash.toByteArray(Charsets.UTF_8)
        )

    /** 새 계정을 위한 솔트를 만든다. 16바이트 난수를 Base64(24자)로 인코딩한다. */
    fun generateSalt(): String {
        val bytes = ByteArray(SALT_BYTES)
        random.nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }
}
