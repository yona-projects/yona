package dev.yona.web.api.docs

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "서명 판정(GitHub의 `verification`). 서명이 없으면 `verified`는 거짓이고 `reason`은 `unsigned`이다")
class VerificationNode(
    @get:Schema(description = "Verified이면 참") val verified: Boolean,
    @get:Schema(description = "판정 이유: `valid`, `unsigned`, `unknown_key`, `expired_key`, `not_signing_key`, `malformed_signature`, `invalid`, `no_user`, `bad_email`, `unverified_email`", example = "valid") val reason: String,
    @get:Schema(description = "서명한 키 주인의 로그인 ID. 주인을 확정하지 못했으면 없다") val signer: String? = null,
    @get:Schema(description = "서명이 밝힌 발행 키 ID(16자리 16진수). SSH 서명은 키 지문(`SHA256:...`)이다. 서명이 없거나 읽을 수 없으면 없다") val keyId: String? = null,
    @get:Schema(description = "서명 형식: `gpg`(OpenPGP) 또는 `ssh`(`gpg.format=ssh`, 등록한 사용자 SSH 키로 검증). 서명이 없으면 `gpg`이다", example = "gpg") val format: String = "gpg"
)

@Schema(description = "GPG 하위 키")
class GpgSubkeyNode(
    @get:Schema(description = "키 ID(16자리 대문자 16진수)") val keyId: String,
    @get:Schema(description = "지문(대문자 16진수)") val fingerprint: String,
    @get:Schema(description = "서명 용도로 쓸 수 있으면 참") val canSign: Boolean,
    @get:Schema(description = "만료 시각. 만료가 없으면 없다") val expiresAt: String? = null
)

@Schema(description = "등록한 GPG 공개키 한 개(내 키)")
class GpgKeyNode(
    val id: Long,
    @get:Schema(description = "등록할 때 붙인 이름") val name: String?,
    @get:Schema(description = "주 키의 키 ID(16자리 대문자 16진수)") val keyId: String,
    @get:Schema(description = "주 키의 지문(대문자 16진수)") val fingerprint: String,
    @get:Schema(description = "키의 사용자 ID에 든 이메일(소문자)") val emails: List<String>,
    @get:Schema(description = "주 키가 서명 용도로 쓸 수 있으면 참") val canSign: Boolean,
    @get:Schema(description = "등록한 시각") val createdAt: String?,
    @get:Schema(description = "만료 시각. 만료가 없으면 없다") val expiresAt: String?,
    @get:Schema(description = "하위 키") val subkeys: List<GpgSubkeyNode>
)

@Schema(description = "다른 사용자의 GPG 공개키 한 개. 등록할 때 붙인 이름은 보이지 않는다")
class PublicGpgKeyNode(
    val id: Long,
    @get:Schema(description = "주 키의 키 ID(16자리 대문자 16진수)") val keyId: String,
    @get:Schema(description = "주 키의 지문(대문자 16진수)") val fingerprint: String,
    @get:Schema(description = "키의 사용자 ID에 든 이메일(소문자)") val emails: List<String>,
    @get:Schema(description = "주 키가 서명 용도로 쓸 수 있으면 참") val canSign: Boolean,
    @get:Schema(description = "등록한 시각") val createdAt: String?,
    @get:Schema(description = "만료 시각. 만료가 없으면 없다") val expiresAt: String?,
    @get:Schema(description = "하위 키") val subkeys: List<GpgSubkeyNode>
)

@Schema(description = "내 GPG 키 목록 응답")
class GpgKeyList(@get:Schema(description = "최근에 등록한 순서") val keys: List<GpgKeyNode>)

@Schema(description = "다른 사용자의 GPG 공개키 목록 응답")
class PublicGpgKeyList(@get:Schema(description = "최근에 등록한 순서") val keys: List<PublicGpgKeyNode>)

@Schema(description = "GPG 키 한 개 응답")
class GpgKeyResult(val result: GpgKeyNode)

@Schema(description = "GPG 키 등록 요청")
class GpgKeyCreateRequest(
    @get:Schema(description = "이름(100자까지). 비우면 키의 첫 이메일로 정한다") val name: String? = null,
    @get:Schema(description = "ASCII armor 공개키(`gpg --armor --export`의 출력)", requiredMode = Schema.RequiredMode.REQUIRED) val publicKey: String
)
