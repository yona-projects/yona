package dev.yona.repository

import dev.yona.domain.project.Project
import dev.yona.domain.vcs.VcsType
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevTag
import org.eclipse.jgit.revwalk.RevWalk
import org.springframework.stereotype.Component

/**
 * Git 저장소의 브랜치와 태그를 JGit으로 읽고 바꾸는 [RefReader] 구현이다. v1.6 `GitRepository.getBranches`, `getHeadBranch`, `deleteBranch`에
 * 해당하고(목록은 브랜치만, D-30), 태그는 `decisions.md` D-30, D-52의 전용 기능이다.
 */
@Component
class GitRefReader(private val git: GitRepositories) : RefReader {

    override val type: VcsType get() = VcsType.GIT

    private fun open(project: Project) = git.open(requireNotNull(project.owner), requireNotNull(project.name))

    /**
     * `refs/heads/` 아래의 브랜치만 머리 커밋이 최근인 순서로 모은다.
     *
     * v1.6 `getBranches`는 태그와 원격 참조(`refs/remotes/` 아래)까지 "브랜치"로 모았다. 태그와 원격 참조는 브랜치 화면과 풀 리퀘스트의
     * 출발, 도착 목록에서 의미가 없어(`decisions.md` D-30) 결과에서 뺀다. 커밋을 읽기 전에 거르므로 커밋이 아닌 대상을 가리키는 태그가 있어도 실패하지 않는다.
     */
    override fun branches(project: Project): List<RefHead> = open(project).use { repository ->
        RevWalk(repository).use { walk ->
            repository.refDatabase.refs.filter { ref -> ref.name.startsWith(Constants.R_HEADS) && ref.objectId != null }
                .mapNotNull { ref -> runCatching { head(ref.name, walk.parseCommit(ref.objectId)) }.getOrNull() }
                .sortedByDescending { it.committerDate }
        }
    }

    override fun headBranch(project: Project): RefHead? = open(project).use { repository ->
        val name = repository.findRef(Constants.HEAD)?.target?.name ?: return null
        val id = repository.resolve(name) ?: return null
        RevWalk(repository).use { head(name, it.parseCommit(id)) }
    }

    override fun deleteBranch(project: Project, branchName: String) {
        open(project).use { repository ->
            Git(repository).branchDelete().setBranchNames(branchName).setForce(true).call()
        }
    }

    private fun head(name: String, commit: RevCommit) = RefHead(name, commit.name, commit.abbreviate(7).name(), commit.committerIdent.`when`)

    override fun tags(project: Project): List<TagInfo> = open(project).use { repository ->
        RevWalk(repository).use { walk ->
            repository.refDatabase.refs.filter { it.name.startsWith(Constants.R_TAGS) && it.objectId != null }
                .mapNotNull { ref -> runCatching { info(walk, ref.name, ref.objectId) }.getOrNull() }
                .sortedWith(compareBy<TagInfo>({ it.sortDate == null }, { -(it.sortDate?.time ?: 0L) }, { it.shortName }))
        }
    }

    override fun findTag(project: Project, shortName: String): TagInfo? = open(project).use { repository ->
        val ref = repository.exactRef(Constants.R_TAGS + shortName) ?: return null
        val id = ref.objectId ?: return null
        RevWalk(repository).use { walk -> runCatching { info(walk, ref.name, id) }.getOrNull() }
    }

    override fun createTag(project: Project, name: String, target: String?, message: String?, tagger: TagAuthor): TagCreation = open(project).use { repository ->
        if (!org.eclipse.jgit.lib.Repository.isValidRefName(Constants.R_TAGS + name)) return TagCreation(null, TagCreateError.INVALID_NAME)
        if (repository.exactRef(Constants.R_TAGS + name) != null) return TagCreation(null, TagCreateError.ALREADY_EXISTS)
        val id = runCatching { repository.resolve(target ?: Constants.HEAD) }.getOrNull()
            ?: return TagCreation(null, if (target == null) TagCreateError.EMPTY_REPOSITORY else TagCreateError.NO_TARGET)
        val commit = RevWalk(repository).use { walk -> runCatching { walk.parseCommit(id) }.getOrNull() } ?: return TagCreation(null, TagCreateError.NO_TARGET)
        Git(repository).tag().setName(name).setObjectId(commit).setAnnotated(message != null).apply { if (message != null) { setMessage(message); setTagger(PersonIdent(tagger.name, tagger.email)) } }.call()
        TagCreation(findTag(project, name), null)
    }

    override fun deleteTag(project: Project, shortName: String): Boolean = open(project).use { repository ->
        if (repository.exactRef(Constants.R_TAGS + shortName) == null) return false
        Git(repository).tagDelete().setTags(Constants.R_TAGS + shortName).call()
        true
    }

    private fun info(walk: RevWalk, name: String, id: org.eclipse.jgit.lib.ObjectId): TagInfo {
        var target = walk.parseAny(id)
        var first: RevTag? = null
        while (target is RevTag) {
            if (first == null) first = target
            target = walk.parseAny(target.`object`)
        }
        val commit = target as? RevCommit
        return TagInfo(
            name = name, shortName = name.removePrefix(Constants.R_TAGS), annotated = first != null, message = first?.fullMessage,
            taggerName = first?.taggerIdent?.name, taggerDate = first?.taggerIdent?.`when`, commitId = commit?.name, commitDate = commit?.committerIdent?.`when`,
            taggerEmail = first?.taggerIdent?.emailAddress, signature = first?.takeIf { it.rawGpgSignature != null }?.let { dev.yona.domain.gpgkey.GitSignatures.ofTag(it.rawBuffer) }
        )
    }
}
