package com.github.yonaprojects.yona.domain.vcs

import com.github.yonaprojects.yona.domain.gpgkey.GpgVerificationStatus
import com.github.yonaprojects.yona.domain.user.User
import io.github.search5.hg4j.api.HgCommit as NativeHgCommit
import java.time.ZoneOffset
import java.util.Date
import java.util.TimeZone

// GitCommit/SvnCommit과 동일한 패턴의 Mercurial 커밋 래퍼. hg4j의
// io.github.search5.hg4j.api.HgCommit(레코드성 값 객체)을 이 앱의 Commit 추상클래스로 감싼다.
// 이름이 hg4j 쪽과 같아(HgCommit) 파일 안에서는 별칭(NativeHgCommit)으로 구분한다.
class HgCommit(
    private val native: NativeHgCommit,
    private val userResolver: (String?, String?) -> User?,
    // 기본값은 항상 UNSIGNED로 판정하는 no-op(기존 호출부/테스트가 그대로 동작하게 하기 위함) —
    // RepositoryService/HgRepository가 실제 GpgSignatureVerifier.verify(NativeHgCommit)를 넘겨준다.
    private val gpgVerifier: (NativeHgCommit) -> GpgVerificationStatus = { GpgVerificationStatus.UNSIGNED }
) : Commit() {

    // 레거시 Mercurial 관례("Full Name <email@example.com>") 파싱 — JGit의 PersonIdent와 달리
    // hg4j는 author를 이름/이메일로 분리하지 않고 원본 문자열 그대로 노출한다(실제 hg 저장소의
    // 저장 형식 자체가 분리되어 있지 않음).
    private val authorNameAndEmail: Pair<String, String?> by lazy {
        val raw = native.author
        val email = parseAuthorEmail(raw)
        val match = AUTHOR_PATTERN.matchEntire(raw)
        val name = if (match != null) match.destructured.component1().trim().ifEmpty { raw } else raw
        name to email
    }

    override fun getShortId(): String = native.nodeId.toString()

    override fun getId(): String = native.nodeId.toHex()

    override fun getShortMessage(): String {
        val msg = getMessage()
        return if (!msg.isNullOrEmpty()) msg.trim().lineSequence().first() else ""
    }

    override fun getMessage(): String? = native.message

    override fun getAuthor(): User? = userResolver(authorNameAndEmail.first, authorNameAndEmail.second)

    override fun getAuthorName(): String? = authorNameAndEmail.first

    override fun getAuthorEmail(): String? = authorNameAndEmail.second

    // Mercurial은 커밋에 저자/커미터 구분이 없다(항상 하나의 author).
    override fun getAuthorDate(): Date = Date(native.timestamp * 1000)

    override fun getAuthorTimezone(): TimeZone =
        TimeZone.getTimeZone(ZoneOffset.ofTotalSeconds(native.timezoneOffset))

    override fun getCommitterName(): String? = authorNameAndEmail.first

    override fun getCommitterEmail(): String? = authorNameAndEmail.second

    override fun getCommitterDate(): Date = getAuthorDate()

    override fun getCommitterTimezone(): TimeZone = getAuthorTimezone()

    // 부모 개수는 이 값 객체가 직접 들고 있지 않아(revision/nodeId만 노출) 저장소 조회가 필요하다 —
    // HgRepository.getParentCommitOf()가 이미 revision-1 기준으로 부모를 구하므로, 여기서는 root
    // 커밋(revision 0)만 0으로 판정하고 나머지는 병합 여부까지는 구분하지 않고 1로 근사한다.
    // (Git처럼 병합 커밋의 부모가 2개인 경우까지 정확히 구분하는 것은 2라운드 과제.)
    override fun getParentCount(): Int = if (native.revision == 0) 0 else 1

    override fun getGpgVerificationStatus(): GpgVerificationStatus {
        return gpgVerifier(native)
    }

    companion object {
        private val AUTHOR_PATTERN = Regex("""^(.*?)\s*<(.+)>$""")

        // HgCommit 인스턴스 없이도 author 문자열에서 이메일만 뽑아야 하는 곳
        // (GpgSignatureVerifier.verify(NativeHgCommit))이 있어 공개 유틸로 뺀다.
        fun parseAuthorEmail(raw: String): String? {
            val match = AUTHOR_PATTERN.matchEntire(raw) ?: return null
            return match.destructured.component2()
        }
    }
}
