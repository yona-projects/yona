package dev.yona.repository.hg

import dev.yona.domain.gpgkey.GpgVerificationService
import dev.yona.domain.project.Project
import dev.yona.domain.vcs.RefUpdate
import org.springframework.stereotype.Component

/**
 * 서명 커밋을 요구하는 규칙(`Ruleset.requireSignedCommits`)을 위한 검사이다(X-7 9c, `decisions.md` D-88 3번). 이름표 이동으로 새로 들어오는 체인지셋마다 이름표가 가리킬
 * 새 머리에서 보이는 `.hgsigs`에 Verified 서명이 있어야 한다. 서명은 서명한 커밋의 후손에 담기므로 푸시에 서명 파일을 더하는 커밋을 함께 올려야 하고, 서명 파일만
 * 바꾸는 커밋은 자기 자신을 서명할 수 없어 검사하지 않는다. 서명 검증(등록 키, 작성자 이메일 일치)은 Git과 같은 [GpgVerificationService]가 한다.
 *
 * 새 커밋은 새 머리의 조상에서 옛 머리의 조상과 다른 이름표가 이미 가진 체인지셋을 뺀 것이다. 새 머리를 읽지 못하면(같은 푸시로 막 들어온 체인지셋이 아직 보이지
 * 않는 경우) 확인할 수 없으므로 **거절 쪽**(`unverifiable`)으로 둔다.
 */
@Component
class HgSignedCommits(private val repositories: HgRepositories, private val verification: GpgVerificationService) {

    /** 이번 이동이 더하는 체인지셋 가운데 Verified 서명이 아닌 것의 (짧은 번호, 이유 코드) 목록이다. 비어 있으면 모두 통과이다. */
    fun unverified(project: Project, update: RefUpdate): List<Pair<String, String>> = repositories.open(requireNotNull(project.owner), requireNotNull(project.name)).use { hg ->
        val changelog = HgRevisions.changelog(hg)
        val newRev = HgChangesets.revOf(changelog, update.new)
        if (newRev < 0) return listOf(update.new.take(SHORT_ID) to UNVERIFIABLE)
        val oldRev = if (update.old == RefUpdate.NULL_ID) -1 else HgChangesets.revOf(changelog, update.old)
        val known = mutableSetOf<Int>()
        if (oldRev >= 0) known += HgChangesets.ancestors(changelog, oldRev)
        // 다른 이름표가 이미 가진 체인지셋은 이번 푸시가 더하는 것이 아니다
        val self = update.ref.removePrefix(HgRepositories.HEADS)
        HgRevisions.bookmarks(hg).filterKeys { it != self }.values.forEach { node ->
            val rev = HgChangesets.revOf(changelog, node)
            if (rev >= 0) known += HgChangesets.ancestors(changelog, rev)
        }
        val signatures = HgSignatures.visibleAt(hg, update.new)
        (HgChangesets.ancestors(changelog, newRev) - known).sortedDescending().mapNotNull { HgChangesets.read(changelog, it) }
            .filterNot { HgSignatures.onlySigns(it.files) }
            .mapNotNull { changeset ->
                val email = HgAuthors.parse(changeset.author).second
                val result = verification.verifyCommit(signatures[changeset.node], email)
                if (result.verified) null else changeset.node.take(SHORT_ID) to result.reason.code
            }
    }

    private companion object {
        const val SHORT_ID = 12

        /** 새 머리를 읽지 못해 서명을 확인할 수 없다는 이유 코드이다. */
        const val UNVERIFIABLE = "unverifiable"
    }
}
