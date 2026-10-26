package dev.yona.security

import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

/**
 * Gravatar 주소를 검증한다. 원본: `GravatarUtil.getAvatar`와 Subversion 코드 화면의 `SVNRepository.getAvatar`(기본 이미지가 `http`인 문자열 리터럴).
 * 테스트 환경은 Gravatar에 접속하지 못하는 경우가 많아, 접속 가능한 상태로 바꿔 놓고 확인한다.
 */
class AvatarServiceTest : AbstractIntegrationTest() {

    @Autowired private lateinit var avatars: AvatarService
    private var before = false

    @BeforeEach
    fun reachable() { before = avatars.gravatarReachable; avatars.gravatarReachable = true }

    @AfterEach
    fun restore() { avatars.gravatarReachable = before }

    private val image = "https%3A%2F%2Fko.gravatar.com%2Fuserimage%2F53495145%2F0eaeeb47c620542ad089f17377298af6.png"

    @Test
    fun `이메일의 MD5와 크기와 기본 이미지로 주소를 만든다`() {
        assertEquals("https://www.gravatar.com/avatar/be9d18f611892a738e54f2a3a171e2f9?s=34&d=$image", avatars.gravatarUrl("dev@example.com", 34))
        assertEquals("https://www.gravatar.com/avatar/d41d8cd98f00b204e9800998ecf8427e?s=34&d=$image", avatars.gravatarUrl("", 34))
    }

    @Test
    fun `Subversion 코드 화면의 기본 이미지는 http이다 (v1_6 문자열 리터럴)`() {
        val svn = "http%3A%2F%2Fko.gravatar.com%2Fuserimage%2F53495145%2F0eaeeb47c620542ad089f17377298af6.png"
        assertEquals("https://www.gravatar.com/avatar/d41d8cd98f00b204e9800998ecf8427e?s=34&d=$svn", avatars.gravatarUrl("", 34, AvatarService.SVN_DEFAULT_GRAVATAR_IMAGE))
    }

    @Test
    fun `접속할 수 없으면 기본 아바타이다`() {
        avatars.gravatarReachable = false
        assertEquals(AvatarService.DEFAULT_AVATAR_URL, avatars.gravatarUrl("dev@example.com", 34))
    }
}
