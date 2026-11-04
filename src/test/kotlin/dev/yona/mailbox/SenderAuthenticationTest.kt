package dev.yona.mailbox

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** 받은 메일의 보낸 사람이 인증되었는지 가리는 규칙을 검증한다(R-61-10). v1.6은 `From` 헤더를 그대로 믿어 위조된 보낸 사람으로 글을 올릴 수 있었다. */
class SenderAuthenticationTest {

    private fun mail(vararg results: String) =
        MailFixtures.mail("<a@x>", "alice@corp.test", listOf("yona@corp.test"), "제목", headers = results.withIndex().associate { (i, v) -> (if (i == 0) "Authentication-Results" else "Authentication-Results-$i") to v })

    @Test
    fun `헤더가 없으면 인증되지 않은 메일이다`() {
        assertEquals(false, SenderAuthentication.verified(MailFixtures.mail("<b@x>", "alice@corp.test", listOf("yona@corp.test"), "제목"), "alice@corp.test"))
    }

    @Test
    fun `보낸 사람의 도메인에 맞는 dkim이나 spf나 dmarc 통과가 있어야 한다`() {
        assertEquals(true, SenderAuthentication.verified(mail("mx.corp.test; dkim=pass header.d=corp.test"), "alice@corp.test"))
        assertEquals(true, SenderAuthentication.verified(mail("mx.corp.test; dkim=pass header.d=mail.corp.test"), "alice@corp.test"))
        assertEquals(true, SenderAuthentication.verified(mail("mx.corp.test; spf=pass smtp.mailfrom=alice@corp.test"), "alice@corp.test"))
        assertEquals(true, SenderAuthentication.verified(mail("mx.corp.test; dmarc=pass header.from=corp.test"), "alice@corp.test"))
    }

    @Test
    fun `다른 도메인이나 실패한 인증은 거절한다`() {
        assertEquals(false, SenderAuthentication.verified(mail("mx.corp.test; dkim=pass header.d=evil.test"), "alice@corp.test"))
        assertEquals(false, SenderAuthentication.verified(mail("mx.corp.test; dkim=fail header.d=corp.test; spf=softfail smtp.mailfrom=alice@corp.test"), "alice@corp.test"))
        assertEquals(false, SenderAuthentication.verified(mail("mx.corp.test; spf=pass smtp.mailfrom=bob@evil.test"), "alice@corp.test"))
    }
}
