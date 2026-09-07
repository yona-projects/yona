package com.github.yonaprojects.yona.domain.vcs

import com.github.yonaprojects.yona.domain.gpgkey.GpgVerificationStatus
import com.github.yonaprojects.yona.domain.user.User
import io.github.search5.hg4j.api.HgCommit as NativeHgCommit
import java.util.Date
import java.util.TimeZone

// yona-wiki P3-12 1라운드 — GitCommit/SvnCommit과 동일한 패턴의 Mercurial 커밋 래퍼.
// hg4j의 io.github.search5.hg4j.api.HgCommit(레코드성 값 객체)을 이 앱의 Commit 추상클래스로 감싼다.
// 이름이 hg4j 쪽과 같아(HgCommit) 파일 안에서는 별칭(NativeHgCommit)으로 구분한다.
class HgCommit(
    private val native: NativeHgCommit,
    private val userResolver: (String?, String?) -> User?
) : Commit() {

    // 레거시 Mercurial 관례("Full Name <email@example.com>") 파싱 — JGit의 PersonIdent와 달리
    // hg4j는 author를 이름/이메일로 분리하지 않고 원본 문자열 그대로 노출한다(실제 hg 저장소의
    // 저장 형식 자체가 분리되어 있지 않음).
    private val authorNameAndEmail: Pair<String, String?> by lazy {
        val raw = native.author
        val match = AUTHOR_PATTERN.matchEntire(raw)
        if (match != null) {
            val (name, email) = match.destructured
            (name.trim().ifEmpty { raw } to email)
        } else {
            raw to null
        }
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
        TimeZone.getTimeZone(java.time.ZoneOffset.ofTotalSeconds(native.timezoneOffset))

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
        // yona-wiki P3-12 1라운드 범위 밖 — Mercurial의 GPG 서명 검증은 커밋 확장(gpg) 별도 조사가
        // 필요해 이번 라운드에서는 다루지 않는다(Git의 GpgSignatureVerifier와 동일한 수준으로 만들
        // 근거가 아직 없음).
        return GpgVerificationStatus.UNSIGNED
    }

    companion object {
        private val AUTHOR_PATTERN = Regex("""^(.*?)\s*<(.+)>$""")
    }
}
