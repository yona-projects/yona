package com.github.yonaprojects.yona.config.oauth2server

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.File
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import tools.jackson.databind.ObjectMapper

// yona-wiki P3-07(MCP 서버) Step2 — OAuth2 액세스 토큰(JWT) 서명용 RSA 키 쌍을 최초 1회 생성해
// yona.data 디렉터리(업로드/H2 파일과 동일한 기존 관례, application.yml의 yona.upload.base-dir 참고)
// 아래에 저장하고, 이후 재시작마다 그대로 재사용한다. 매번 새로 생성하면(가장 흔한 예제 코드의
// 방식) yona 프로세스를 재시작할 때마다 그 전에 발급된 모든 OAuth2 액세스/리프레시 토큰이 즉시
// 무효화돼(서명 검증 실패) 사용자가 매번 다시 로그인해야 하는 실사용상 문제가 있어, 최소한의
// 영속화를 직접 구현했다(전용 키 관리 인프라나 키 로테이션까지는 이번 범위에 포함하지 않음 —
// 과도한 설계 금지).
@Component
class JwkKeyPairProvider(
    @Value("\${yona.oauth2.signing-key-path:\${yona.data:data}/oauth2/jwt-signing-key.json}")
    private val keyPath: String
) {
    val keyPair: KeyPair by lazy { loadOrGenerate() }

    private data class StoredKeyPair(val privateKeyBase64: String, val publicKeyBase64: String)

    private fun loadOrGenerate(): KeyPair {
        val file = File(keyPath)
        if (file.exists()) {
            val stored = mapper.readValue(file, StoredKeyPair::class.java)
            val keyFactory = KeyFactory.getInstance("RSA")
            val privateKey = keyFactory.generatePrivate(
                PKCS8EncodedKeySpec(Base64.getDecoder().decode(stored.privateKeyBase64))
            )
            val publicKey = keyFactory.generatePublic(
                X509EncodedKeySpec(Base64.getDecoder().decode(stored.publicKeyBase64))
            )
            return KeyPair(publicKey, privateKey)
        }

        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        val generated = generator.generateKeyPair()

        file.parentFile?.mkdirs()
        mapper.writeValue(
            file,
            StoredKeyPair(
                privateKeyBase64 = Base64.getEncoder().encodeToString(generated.private.encoded),
                publicKeyBase64 = Base64.getEncoder().encodeToString(generated.public.encoded)
            )
        )
        // 코디네이터 push 전 리뷰(2026-09-07, P3-03 SshInternalSecretProvider 리뷰 중 동일 패턴을
        // 재사용하는 이 파일에서도 함께 발견) — 이 파일에 RSA 개인키가 평문으로 들어있어, 같은
        // 호스트의 다른 로컬 사용자가 읽으면 임의로 OAuth2 액세스 토큰을 위조 서명할 수 있다.
        // 소유자 전용으로 제한한다(Windows에서는 File API가 조용히 무시함 — 예외 없음).
        file.setReadable(false, false)
        file.setReadable(true, true)
        file.setWritable(false, false)
        file.setWritable(true, true)
        return generated
    }

    companion object {
        private val mapper = ObjectMapper()
    }
}
