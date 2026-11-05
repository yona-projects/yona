package dev.yona.security

import dev.yona.config.YonaProperties
import dev.yona.domain.user.UserRepository
import dev.yona.repository.DataDirectory
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.math.BigInteger
import java.nio.file.Files
import java.security.SecureRandom

/**
 * 사이트 비밀 키의 상태와 최초 기동 설정이다. v1.6 `Global`의 `isSecretInvalid`, `isRestartRequired`, `hasFailedToUpdateSecretKey`,
 * `getConfigSecretAction`(`app/Global.java:73-79, 158, 238-240, 290-370`)에 해당한다.
 *
 * 비밀 키(`yona.secret`)가 기본값이거나 비어 있으면 서버가 처음 기동한 것으로 본다([invalid]). 이때는 모든 요청이 기본 관리자 계정을 정하는
 * 화면으로 가고([SiteSecretInterceptor]), 정하면 기본 관리자 계정을 바꾸고 새 비밀 키를 설정 파일
 * (`<데이터 디렉터리>/config/secret.properties`, 시작할 때 `spring.config.import`로 읽는다)에 쓴 뒤 [restartRequired]가 참이 되어 재시작 안내만 보인다.
 * 설정 파일에 쓰지 못하면 계정은 바꾸지 않고 [SecretWriteException]을 던진다(R-57-5).
 */
@Component
class SiteSecret(
    private val properties: YonaProperties,
    private val users: UserRepository,
    private val dataDirectory: DataDirectory
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var invalidSecret: Boolean = properties.secret.isBlank() || properties.secret == YonaProperties.DEFAULT_SECRET

    @Volatile
    private var restart: Boolean = false

    /** 비밀 키를 아직 정하지 않았으면 `true`이다. */
    val invalid: Boolean get() = invalidSecret

    /** 계정을 정했으니 재시작해야 하면 `true`이다. 이 상태에서는 모든 요청이 재시작 안내이다. */
    val restartRequired: Boolean get() = restart

    /** 상태를 처음으로 되돌린다. 테스트에서 쓴다. */
    fun reset(invalid: Boolean) {
        invalidSecret = invalid
        restart = false
    }

    /**
     * 새 비밀 키를 설정 파일에 먼저 쓰고, 쓰기에 성공했을 때만 기본 관리자(`admin`) 계정의 이름, 이메일, 비밀번호를 바꾼다. 끝나면 [restartRequired]가 참이다.
     * 쓰지 못하면 계정은 바뀌지 않고 예외를 던진다. v1.6은 계정을 먼저 바꾸고 쓰기 실패를 로그에만 남겨, 계정은 바뀌었는데 비밀 키는 기본값인 채로
     * 관리자가 설정 파일을 직접 고쳐야 했다(R-57-5).
     *
     * @throws SecretWriteException 설정 파일에 쓰지 못했을 때
     */
    @Transactional
    fun configure(name: String?, email: String?, password: String) {
        try {
            writeSecret(generate())
        } catch (e: Exception) {
            log.warn("Failed to update secret key", e)
            throw SecretWriteException(e)
        }
        // v1.6은 admin 사용자가 없으면 NPE였다(C034). 없으면 계정은 건너뛰고 비밀 키만 정한다
        users.findByLoginIdIgnoreCase(DEFAULT_ADMIN_LOGIN_ID)?.let { admin ->
            // 솔트는 일반 가입과 같은 방식(Base64)으로 만든다. v1.6은 16바이트 난수의 Arrays.toString 문자열("[12, -3, ...]")로 만들었다(R-100)
            val salt = LegacyPasswordHasher.generateSalt()
            admin.name = name
            admin.email = email
            admin.passwordSalt = salt
            admin.password = LegacyPasswordHasher.hash(password, salt)
            users.save(admin)
        }
        restart = true
    }

    private fun writeSecret(secret: String) {
        val file = File(dataDirectory.root(), "config/secret.properties")
        Files.createDirectories(file.parentFile.toPath())
        Files.writeString(file.toPath(), "yona.secret=$secret\n")
    }

    /** 새 비밀 키를 설정 파일에 쓰지 못했을 때의 예외이다. */
    class SecretWriteException(cause: Throwable) : RuntimeException("Failed to write the secret key", cause)

    companion object {
        /** 설치 직후 기본 관리자 계정의 로그인 ID이다 (v1.6 `SiteAdmin.SITEADMIN_DEFAULT_LOGINID`). */
        const val DEFAULT_ADMIN_LOGIN_ID = "admin"

        /** 새 비밀 키이다. 130비트 난수를 32진수로 쓴 문자열이다 (v1.6 `BigInteger(130, random).toString(32)`). */
        fun generate(): String = BigInteger(130, SecureRandom()).toString(32)
    }
}
