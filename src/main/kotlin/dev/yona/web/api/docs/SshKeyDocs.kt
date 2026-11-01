package dev.yona.web.api.docs

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "등록한 SSH 공개키 한 개(내 키)")
class SshKeyNode(
    val id: Long,
    @get:Schema(description = "등록할 때 붙인 이름") val name: String?,
    @get:Schema(description = "키 종류", example = "ssh-ed25519") val keyType: String,
    @get:Schema(description = "지문(`SHA256:...`, `ssh-keygen -lf`의 출력과 같다)") val fingerprint: String,
    @get:Schema(description = "공개키 한 줄(`종류 본문`)") val publicKey: String,
    @get:Schema(description = "등록한 시각") val createdAt: String?
)

@Schema(description = "다른 사용자의 SSH 공개키 한 개. 등록할 때 붙인 이름은 보이지 않는다")
class PublicSshKeyNode(
    val id: Long,
    @get:Schema(description = "키 종류", example = "ssh-ed25519") val keyType: String,
    @get:Schema(description = "지문(`SHA256:...`)") val fingerprint: String,
    @get:Schema(description = "공개키 한 줄(`종류 본문`)") val publicKey: String,
    @get:Schema(description = "등록한 시각") val createdAt: String?
)

@Schema(description = "내 SSH 키 목록 응답")
class SshKeyList(@get:Schema(description = "최근에 등록한 순서") val keys: List<SshKeyNode>)

@Schema(description = "다른 사용자의 SSH 공개키 목록 응답")
class PublicSshKeyList(@get:Schema(description = "최근에 등록한 순서") val keys: List<PublicSshKeyNode>)

@Schema(description = "SSH 키 한 개 응답")
class SshKeyResult(val result: SshKeyNode)

@Schema(description = "SSH 키 등록 요청")
class SshKeyCreateRequest(
    @get:Schema(description = "이름(100자까지). 비우면 키 끝의 주석으로 정한다") val name: String? = null,
    @get:Schema(description = "공개키 한 줄(`~/.ssh/id_ed25519.pub`의 내용)", requiredMode = Schema.RequiredMode.REQUIRED) val publicKey: String
)

@Schema(description = "프로젝트의 배포 키 한 개")
class DeployKeyNode(
    val id: Long,
    @get:Schema(description = "등록할 때 붙인 이름") val name: String?,
    @get:Schema(description = "키 종류", example = "ssh-ed25519") val keyType: String,
    @get:Schema(description = "지문(`SHA256:...`)") val fingerprint: String,
    @get:Schema(description = "공개키 한 줄(`종류 본문`)") val publicKey: String,
    @get:Schema(description = "읽기 전용이면 참. 거짓이면 푸시도 할 수 있다") val readOnly: Boolean,
    @get:Schema(description = "등록한 시각") val createdAt: String?
)

@Schema(description = "배포 키 목록 응답")
class DeployKeyList(@get:Schema(description = "최근에 등록한 순서") val keys: List<DeployKeyNode>)

@Schema(description = "배포 키 한 개 응답")
class DeployKeyResult(val result: DeployKeyNode)

@Schema(description = "배포 키 등록 요청")
class DeployKeyCreateRequest(
    @get:Schema(description = "이름(100자까지). 비우면 키 끝의 주석으로 정한다") val name: String? = null,
    @get:Schema(description = "공개키 한 줄", requiredMode = Schema.RequiredMode.REQUIRED) val publicKey: String,
    @get:Schema(description = "읽기 전용 여부. 생략하면 참이다(GitHub와 같다)", defaultValue = "true") val readOnly: Boolean? = null
)
