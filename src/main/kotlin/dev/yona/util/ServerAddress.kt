package dev.yona.util

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * 서버의 기본 주소이다. v1.6 `Config.getDefaultAddress`: 네트워크 인터페이스를 차례로 돌며 처음 만나는 IPv4 주소(와일드카드 `0.0.0.0` 제외)를 쓰고,
 * 없으면 `InetAddress.getLocalHost()`이다. 설정 호스트 이름이 없고 요청 밖에서(알림 메일 스케줄러 등) 주소를 만들 때 쓴다.
 * 인터페이스 순서는 실행 환경에 따라 다르다.
 */
object ServerAddress {

    fun default(): InetAddress {
        NetworkInterface.getNetworkInterfaces()?.let { interfaces ->
            for (networkInterface in interfaces) {
                for (address in networkInterface.inetAddresses) {
                    if (!address.isAnyLocalAddress && address is Inet4Address) return address
                }
            }
        }
        return InetAddress.getLocalHost()
    }
}
