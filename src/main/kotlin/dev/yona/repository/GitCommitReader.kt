package dev.yona.repository

import dev.yona.domain.project.Project
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.errors.MissingObjectException
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.springframework.stereotype.Component
import java.util.Date

/**
 * Git 저장소의 커밋을 JGit으로 읽는 [CommitReader] 구현이다. v1.6 `GitRepository.getHistory`, `getCommit`
 * (`app/playRepository/GitRepository.java:790-830`)에 해당한다.
 */
@Component
class GitCommitReader(private val git: GitRepositories) : CommitReader {

    override val type: dev.yona.domain.vcs.VcsType get() = dev.yona.domain.vcs.VcsType.GIT

    private fun open(project: Project) = git.open(requireNotNull(project.owner), requireNotNull(project.name))

    override fun history(project: Project, page: Int, size: Int, revision: String?, path: String?): List<CommitInfo>? = open(project).use { repository ->
        val startId = (if (revision == null) repository.resolve(Constants.HEAD) else repository.resolve(revision)) ?: return null
        // 트리나 블롭을 가리키는 태그이면 v1.6은 예외로 500이었다(R-128). 커밋이 아니면 이력이 없는 것으로 본다
        val start = try {
            RevWalk(repository).use { it.parseCommit(startId) }
        } catch (e: org.eclipse.jgit.errors.IncorrectObjectTypeException) {
            return null
        }
        val log = Git(repository).log().add(start)
        if (!path.isNullOrEmpty()) log.addPath(path)
        // v1.6는 `쪽 * 크기 + 크기`개를 읽어 마지막 `크기`개를 남겨, 전체가 두 쪽보다 적으면 마지막 쪽이 앞 쪽과 겹쳤다(R-40). 앞 쪽만큼 건너뛰고 한 쪽만 읽는다
        log.setSkip(page * size).setMaxCount(size).call().map { info(it) }
    }

    override fun commit(project: Project, revision: String): CommitInfo? = open(project).use { repository ->
        val id = repository.resolve(revision) ?: return null
        try { RevWalk(repository).use { info(it.parseCommit(id)) } } catch (e: MissingObjectException) { null }
    }

    override fun commits(project: Project, ids: Collection<String>): Map<String, CommitInfo> {
        if (ids.isEmpty()) return emptyMap()
        return open(project).use { repository ->
            RevWalk(repository).use { walk ->
                ids.distinct().mapNotNull { id ->
                    val resolved = repository.resolve(id) ?: return@mapNotNull null
                    try { id to info(walk.parseCommit(resolved)) } catch (e: Exception) { null }
                }.toMap()
            }
        }
    }

    override fun parentOf(project: Project, revision: String): CommitInfo? = open(project).use { repository ->
        val id = repository.resolve(revision) ?: return null
        RevWalk(repository).use { walk ->
            val commit = walk.parseCommit(id)
            if (commit.parentCount > 0) info(walk.parseCommit(commit.getParent(0).id)) else null
        }
    }

    private fun info(commit: RevCommit): CommitInfo = CommitInfo(
        id = commit.name, shortId = commit.abbreviate(7).name(), shortMessage = commit.shortMessage, message = commit.fullMessage,
        authorName = commit.authorIdent.name, authorEmail = commit.authorIdent.emailAddress, authorDate = commit.authorIdent.`when`,
        committerName = commit.committerIdent.name, committerEmail = commit.committerIdent.emailAddress, commitTime = Date(commit.commitTime * 1000L), parentCount = commit.parentCount,
        signature = if (commit.rawGpgSignature != null) dev.yona.domain.gpgkey.GitSignatures.ofCommit(commit.rawBuffer) else null
    )
}
