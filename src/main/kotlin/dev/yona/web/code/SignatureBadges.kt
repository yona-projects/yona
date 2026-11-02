package dev.yona.web.code

import dev.yona.domain.gpgkey.GpgVerificationService
import dev.yona.repository.CommitInfo
import dev.yona.repository.TagInfo
import org.springframework.stereotype.Component

/**
 * 화면에 보일 서명 표시이다(X-4). 서명이 없는 커밋과 태그에는 만들지 않는다.
 *
 * @property verified Verified이면 `true`이다
 * @property reason 판정 이유 코드(`valid`, `unknown_key`, `bad_email` 등). 번역된 설명의 메시지 키 `gpgKey.reason.<코드>`에 쓴다
 * @property signer 서명한 키 주인의 로그인 ID. 주인을 확정하지 못했으면 `null`
 * @property keyId 서명이 밝힌 발행 키 ID(SSH 서명은 키 지문). 읽을 수 없으면 `null`
 * @property format 서명 형식. `gpg` 또는 `ssh`(D-68). 설명 문구를 고른다(`gpgKey.reason.*`, `sshKey.reason.*`)
 */
data class SignatureBadge(val verified: Boolean, val reason: String, val signer: String?, val keyId: String?, val format: String = "gpg")

/**
 * 커밋과 태그의 서명을 검증해 화면 표시([SignatureBadge])로 바꾼다(추가 개발 항목 X-4, `decisions.md` D-65).
 * 커밋터(태그는 태그를 단 사람) 이메일로 키 주인을 확인하며, 서명이 없으면 `null`을 돌려 아무것도 보이지 않게 한다.
 */
@Component
class SignatureBadges(private val verification: GpgVerificationService) {

    fun of(commit: CommitInfo): SignatureBadge? = commit.signature?.let { badge(verification.verifyCommit(it, commit.committerEmail)) }

    fun of(tag: TagInfo): SignatureBadge? = tag.signature?.let { badge(verification.verifyTag(it, tag.taggerEmail)) }

    private fun badge(result: dev.yona.domain.gpgkey.SignatureVerification) = SignatureBadge(result.verified, result.reason.code, result.signerLogin, result.keyId, result.format)
}
