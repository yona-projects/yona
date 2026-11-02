package dev.yona.oauth2server.cimd

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * CIMD 문서를 가져올 때 연결해도 되는 주소를 가른다(추가 개발 항목 X-1, `decisions.md` D-71, IETF 초안 8.6절). 특수 용도 주소(RFC 6890: 루프백, 사설, 링크 로컬, 공유 주소 공간, 문서용, 예약,
 * 멀티캐스트 등)로 풀리는 이름은 가져오지 않는다. 이것이 서버가 내부 자원을 대신 읽게 하는 공격(SSRF)을 막는 핵심이다. IPv4 매핑 IPv6 주소는 안쪽 IPv4 주소로 판정한다.
 */
object AddressPolicy {

    /** [address]가 인터넷에서 라우팅되는 공인 주소이면 `true`이다. */
    fun isPublic(address: InetAddress): Boolean = when (address) {
        is Inet4Address -> publicV4(address.address)
        is Inet6Address -> publicV6(address.address)
        else -> false
    }

    private fun u(b: Byte) = b.toInt() and 0xFF

    private fun publicV4(a: ByteArray): Boolean {
        val b0 = u(a[0]); val b1 = u(a[1]); val b2 = u(a[2])
        return !(
            b0 == 0 || b0 == 10 || b0 == 127 || b0 >= 224 ||
                (b0 == 100 && b1 in 64..127) || (b0 == 169 && b1 == 254) || (b0 == 172 && b1 in 16..31) || (b0 == 192 && b1 == 168) ||
                (b0 == 192 && b1 == 0 && (b2 == 0 || b2 == 2)) || (b0 == 192 && b1 == 88 && b2 == 99) || (b0 == 198 && b1 in 18..19) ||
                (b0 == 198 && b1 == 51 && b2 == 100) || (b0 == 203 && b1 == 0 && b2 == 113)
            )
    }

    private fun publicV6(a: ByteArray): Boolean {
        if (a.all { it.toInt() == 0 }) return false
        // IPv4 매핑(::ffff:a.b.c.d)은 안쪽 IPv4 주소로 판정한다
        if (a.take(10).all { it.toInt() == 0 } && u(a[10]) == 0xFF && u(a[11]) == 0xFF) return publicV4(a.copyOfRange(12, 16))
        // NAT64(64:ff9b::/96)도 안쪽 IPv4 주소로 판정한다
        if (u(a[0]) == 0x00 && u(a[1]) == 0x64 && u(a[2]) == 0xFF && u(a[3]) == 0x9B && a.slice(4..11).all { it.toInt() == 0 }) return publicV4(a.copyOfRange(12, 16))
        val b0 = u(a[0]); val b1 = u(a[1])
        return !(
            (a.take(15).all { it.toInt() == 0 } && u(a[15]) == 1) || // ::1
                (b0 and 0xFE) == 0xFC || // fc00::/7 고유 로컬
                (b0 == 0xFE && (b1 and 0xC0) == 0x80) || // fe80::/10 링크 로컬
                (b0 == 0xFE && (b1 and 0xC0) == 0xC0) || // fec0::/10 옛 사이트 로컬
                b0 == 0xFF || // ff00::/8 멀티캐스트
                (b0 == 0x20 && b1 == 0x01 && u(a[2]) == 0x0D && u(a[3]) == 0xB8) // 2001:db8::/32 문서용
            )
    }
}
