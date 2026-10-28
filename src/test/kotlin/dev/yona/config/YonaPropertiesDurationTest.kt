package dev.yona.config

import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import kotlin.test.assertEquals

/**
 * 기간 설정이 v1.6의 `getMilliseconds` 형식(`30s`, `5m`, `6h`, `1d`)과 단위 없는 숫자(밀리초)를 모두 받는지 검증한다.
 *
 * 원본: `conf/application.conf.default`가 `application.notification.bymail.interval = 60s`, `delay = 180s`,
 * `draft-time = 30s`, `application.update.notification.interval = 6h`처럼 기간 문자열을 쓴다.
 */
class YonaPropertiesDurationTest {

    private fun bind(vararg pairs: Pair<String, String>): YonaProperties =
        Binder(MapConfigurationPropertySource(mapOf(*pairs))).bind("yona", YonaProperties::class.java).orElseGet { YonaProperties() }

    @Test
    fun `기본 설정 파일의 기간 문자열을 읽는다`() {
        val p = bind(
            "yona.notification-mail-interval" to "60s", "yona.notification-mail-delay" to "180s", "yona.notification-draft-time" to "30s",
            "yona.update.interval" to "6h", "yona.notification-mail-init-delay" to "10s", "yona.issue-event-draft-time" to "45s",
            "yona.mailbox-polling-interval" to "5m", "yona.temporary-files-keep-up-time" to "1d", "yona.update.init-delay" to "2s"
        )
        assertEquals(60_000L, p.notificationMailInterval.toMillis())
        assertEquals(180_000L, p.notificationMailDelay.toMillis())
        assertEquals(30_000L, p.notificationDraftTime.toMillis())
        assertEquals(6 * 3_600_000L, p.update.interval.toMillis())
        assertEquals(10_000L, p.notificationMailInitDelay.toMillis())
        assertEquals(45_000L, p.issueEventDraftTime.toMillis())
        assertEquals(300_000L, p.mailboxPollingInterval.toMillis())
        assertEquals(86_400_000L, p.temporaryFilesKeepUpTime.toMillis())
        assertEquals(2_000L, p.update.initDelay.toMillis())
    }

    @Test
    fun `단위 없는 숫자는 밀리초로 읽는다`() {
        val p = bind("yona.notification-mail-delay" to "500", "yona.update.interval" to "0")
        assertEquals(500L, p.notificationMailDelay.toMillis())
        assertEquals(0L, p.update.interval.toMillis())
    }

    @Test
    fun `설정이 없을 때의 기본값은 기본 설정 파일의 값이다`() {
        val p = YonaProperties()
        assertEquals(60_000L, p.notificationMailInterval.toMillis())
        assertEquals(180_000L, p.notificationMailDelay.toMillis())
        assertEquals(30_000L, p.notificationDraftTime.toMillis())
        assertEquals(6 * 3_600_000L, p.update.interval.toMillis(), "업데이트 확인 주기의 기본은 6시간이다")
        assertEquals(100, p.notificationMailRecipientLimit, "알림 메일 수신자 제한의 기본은 100명이다")
        assertEquals(true, p.noreferrer, "noreferrer의 기본은 true이다")
        assertEquals("https://github.com/yona-projects/yona", p.update.repositoryUrl, "업데이트 확인 저장소의 기본은 기본 설정 파일의 값이다")
        assertEquals(false, p.imap.use, "메일 수신(imap.use)의 기본은 꺼 두는 것이다")
        assertEquals(true, p.imap.ssl)
        assertEquals("Inbox", p.imap.folder)
        assertEquals("ldap.forumsys.com", p.ldap.host)
        assertEquals("ou=scientists,dc=example,dc=com", p.ldap.baseDn)
        assertEquals("OU=user,DC=abc,DC=com", p.ldap.distinguishedNamePostfix)
        assertEquals("givenName", p.ldap.englishNameAttributeName)
    }
}
