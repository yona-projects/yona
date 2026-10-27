package dev.yona.util

import org.junit.jupiter.api.Test
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** v1.6 `Config.getDefaultAddress`: 네트워크 인터페이스에서 처음 만나는 비 와일드카드 IPv4 주소이다. */
class ServerAddressTest {

    @Test
    fun `인터페이스를 돌며 처음 만나는 IPv4 주소를 쓴다`() {
        val expected = NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }.firstOrNull { !it.isAnyLocalAddress && it is Inet4Address }
        val actual = ServerAddress.default()
        if (expected != null) assertEquals(expected, actual) else assertTrue(actual.hostAddress.isNotEmpty())
    }
}
