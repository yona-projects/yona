package dev.yona.oauth2server.cimd

import org.junit.jupiter.api.Test
import java.net.InetAddress
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** CIMD 문서를 가져올 때 막는 주소(IETF 초안 8.6절의 특수 용도 주소, RFC 6890)를 검증한다(X-1 5단계, `decisions.md` D-71). */
class AddressPolicyTest {

    private fun ip(text: String) = InetAddress.getByName(text)

    @Test
    fun `공인 주소는 통과한다`() {
        for (ok in listOf("8.8.8.8", "1.1.1.1", "93.184.216.34", "172.32.0.1", "2606:4700:4700::1111", "2001:4860:4860::8888")) assertTrue(AddressPolicy.isPublic(ip(ok)), ok)
    }

    @Test
    fun `루프백과 사설과 링크 로컬과 특수 용도 IPv4 주소는 막는다`() {
        for (bad in listOf(
            "127.0.0.1", "127.1.2.3", "10.0.0.1", "10.255.255.255", "172.16.0.1", "172.31.255.255", "192.168.1.1", "169.254.169.254", "0.0.0.0", "100.64.0.1", "100.127.255.255",
            "192.0.0.1", "192.0.2.1", "198.18.0.1", "198.19.255.255", "198.51.100.1", "203.0.113.1", "224.0.0.1", "239.255.255.255", "240.0.0.1", "255.255.255.255"
        )) assertFalse(AddressPolicy.isPublic(ip(bad)), bad)
    }

    @Test
    fun `IPv6의 루프백과 고유 로컬과 링크 로컬과 문서용과 IPv4 매핑 사설 주소는 막는다`() {
        for (bad in listOf("::1", "::", "fc00::1", "fd12:3456::1", "fe80::1", "ff02::1", "2001:db8::1", "::ffff:127.0.0.1", "::ffff:10.0.0.1", "::ffff:169.254.169.254", "64:ff9b::7f00:1")) assertFalse(AddressPolicy.isPublic(ip(bad)), bad)
        assertTrue(AddressPolicy.isPublic(ip("::ffff:8.8.8.8")))
    }
}
