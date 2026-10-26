package dev.yona.security

import dev.yona.config.YonaProperties
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.misc.AttachmentRepository
import dev.yona.domain.user.User
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.net.InetAddress
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * 사용자 아바타 주소를 정한다. v1.6 `User.avatarUrl`, `GravatarUtil.getAvatar`와 같다.
 *
 * 사용자가 올린 아바타 첨부(`USER_AVATAR`)가 있으면 그 파일 주소를, 없으면 Gravatar 주소를 돌려준다.
 * Gravatar 서버에 접속할 수 없으면 기본 아바타 이미지를 쓴다. 접속 가능 여부는 기동할 때 한 번만
 * 확인한다 (`Config.isConnectableToGravatar`).
 */
@Service
class AvatarService(
    private val attachments: AttachmentRepository,
    properties: YonaProperties
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** Gravatar 서버에 접속할 수 있는지 여부이다. 검사를 끄면 `false`로 본다. */
    internal var gravatarReachable: Boolean = properties.gravatar.check && checkGravatar()

    /**
     * @param user 아바타를 보여 줄 사용자
     * @param size Gravatar 이미지 한 변의 길이(픽셀). 업로드한 아바타에는 적용되지 않는다
     */
    fun avatarUrl(user: User, size: Int = DEFAULT_SIZE): String {
        val avatar = attachments
            .findByContainerTypeAndContainerIdOrderById(ResourceType.USER_AVATAR, requireNotNull(user.id))
            .lastOrNull()
        return if (avatar == null) gravatarUrl(user.email, size) else "/files/${avatar.id}"
    }

    /** 이메일 주소의 Gravatar 주소이다. Gravatar에 접속할 수 없으면 기본 아바타이다 (v1.6 `GravatarUtil.getAvatar`). */
    fun gravatarUrl(email: String?, size: Int, defaultImage: String = DEFAULT_GRAVATAR_IMAGE): String {
        if (!gravatarReachable) return DEFAULT_AVATAR_URL
        val hash = md5Hex(email ?: "")
        return "https://www.gravatar.com/avatar/$hash?s=$size&d=" + URLEncoder.encode(defaultImage, Charsets.UTF_8)
    }

    /** v1.6 `MD5Util.md5Hex`: 문자열을 CP1252 바이트로 바꿔 MD5를 구하고 소문자 16진수로 표현한다. */
    private fun md5Hex(message: String): String =
        MessageDigest.getInstance("MD5").digest(message.toByteArray(charset("windows-1252")))
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    private fun checkGravatar(): Boolean = try {
        InetAddress.getByName("ko.gravatar.com").isReachable(REACHABLE_TIMEOUT_MILLIS) &&
            InetAddress.getByName("www.gravatar.com").isReachable(REACHABLE_TIMEOUT_MILLIS)
    } catch (e: java.io.IOException) {
        log.warn("Gravatar server is unreachable. Gravatar service will not work.")
        false
    }

    companion object {
        /** Gravatar 이미지의 기본 크기이다 (v1.6 `GravatarUtil.DEFAULT_SIZE`). */
        const val DEFAULT_SIZE = 64

        /** Gravatar에 접속할 수 없을 때 쓰는 기본 아바타이다 (v1.6 `UserApp.DEFAULT_AVATAR_URL`). */
        const val DEFAULT_AVATAR_URL = "/assets/images/default-avatar-128.png"

        private const val DEFAULT_GRAVATAR_IMAGE =
            "https://ko.gravatar.com/userimage/53495145/0eaeeb47c620542ad089f17377298af6.png"

        /**
         * Subversion 코드 화면의 등록하지 않은 작성자에게 쓰는 기본 이미지이다. v1.6 `SVNRepository.java:143`에 문자열로 박혀 있고 `http`이다
         * (SSL 혼합 콘텐츠 수정 커밋이 `GravatarUtil`의 상수만 `https`로 바꾸고 이 문자열을 놓쳤다). 그대로 따른다.
         */
        const val SVN_DEFAULT_GRAVATAR_IMAGE = "http://ko.gravatar.com/userimage/53495145/0eaeeb47c620542ad089f17377298af6.png"
        private const val REACHABLE_TIMEOUT_MILLIS = 100
    }
}
