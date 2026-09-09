package com.github.yonaprojects.yona.domain.twofactor

import dev.samstevens.totp.code.CodeVerifier
import dev.samstevens.totp.code.DefaultCodeGenerator
import dev.samstevens.totp.code.DefaultCodeVerifier
import dev.samstevens.totp.code.HashingAlgorithm
import dev.samstevens.totp.qr.QrData
import dev.samstevens.totp.qr.QrGenerator
import dev.samstevens.totp.qr.ZxingPngQrGenerator
import dev.samstevens.totp.secret.DefaultSecretGenerator
import dev.samstevens.totp.time.SystemTimeProvider
import dev.samstevens.totp.util.Utils
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

data class TotpEnrollment(
    val rawSecret: String,
    val qrCodeDataUri: String
)

// RFC 6238 TOTP 발급/검증 + QR코드 생성. Spring Security엔 TOTP 내장 기능이 없어
// dev.samstevens.totp를 채택했다(QR PNG 생성까지 한 라이브러리로 해결).
@Component
class TotpCodeVerifier(
    @Value("\${yona.site-name:Yona}") private val issuer: String
) {
    private val secretGenerator = DefaultSecretGenerator()
    private val qrGenerator: QrGenerator = ZxingPngQrGenerator()
    private val verifier: CodeVerifier = DefaultCodeVerifier(DefaultCodeGenerator(), SystemTimeProvider())

    fun generateEnrollment(accountLabel: String): TotpEnrollment {
        val secret = secretGenerator.generate()
        return TotpEnrollment(rawSecret = secret, qrCodeDataUri = qrCodeDataUri(accountLabel, secret))
    }

    // 코드 검증 실패로 등록 화면을 다시 보여줘야 할 때, 이미 발급된 secret으로 QR을 다시
    // 그리기 위한 용도 — 재시도할 때마다 새 secret을 발급하면 사용자가 앱에 다시 등록해야 한다.
    fun qrCodeDataUri(accountLabel: String, secret: String): String {
        val data = QrData.Builder()
            .label(accountLabel)
            .secret(secret)
            .issuer(issuer)
            .algorithm(HashingAlgorithm.SHA1)
            .digits(6)
            .period(30)
            .build()
        val qrPng = qrGenerator.generate(data)
        return Utils.getDataUriForImage(qrPng, qrGenerator.imageMimeType)
    }

    fun isValid(secret: String, code: String): Boolean = verifier.isValidCode(secret, code)
}
