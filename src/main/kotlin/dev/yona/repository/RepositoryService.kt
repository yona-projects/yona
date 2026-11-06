package dev.yona.repository

import dev.yona.domain.project.Project
import dev.yona.repository.svn.SvnRepositories
import org.eclipse.jgit.lib.Repository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * 프로젝트의 저장소를 만들고 지우고 여는 진입점이다. v1.6 `playRepository.RepositoryService`에 해당한다.
 *
 * 프로젝트의 `vcs` 값(`GIT`, `Subversion`)으로 저장소 종류를 고른다. 종류별 구현은 [VcsBackends]가 맡는다(Git은 JGit, Subversion은
 * SVNKit, D-13). 포크와 가져오기는 구현이 지원할 때만 된다. 저장소 열기([openRepository])는 아직 Git만 지원한다.
 */
@Service
class RepositoryService @Autowired constructor(
    private val backends: VcsBackends,
    private val git: GitRepositories,
    private val wiki: WikiRepositories,
    private val lfs: dev.yona.domain.lfs.LfsStorage
) {

    /** 단위 시험용이다. Git과 Subversion 구현으로 등록부를 만든다. */
    constructor(git: GitRepositories, svn: SvnRepositories, wiki: WikiRepositories, lfs: dev.yona.domain.lfs.LfsStorage) :
        this(
            VcsBackends.of(
                GitBackend(GitLifecycle(git), GitCommitReader(git)),
                dev.yona.repository.svn.SvnBackend(dev.yona.repository.svn.SvnLifecycle(svn), dev.yona.repository.svn.SvnCommitReader(svn))
            ),
            git, wiki, lfs
        )

    /**
     * 프로젝트의 저장소를 새로 만든다. 같은 위치에 이미 있으면 먼저 지운다 (v1.6 `createRepository`).
     *
     * @throws UnsupportedOperationException 지원하지 않는 VCS일 때
     */
    fun createRepository(project: Project) {
        deleteRepository(project)
        backends.lifecycle(project.vcs).create(owner(project), name(project))
    }

    /** 프로젝트의 저장소 디렉터리를 지운다. 없으면 아무 일도 하지 않는다. */
    fun deleteRepository(project: Project) = backends.lifecycle(project.vcs).delete(owner(project), name(project))

    /**
     * 프로젝트 저장소 디렉터리를 다른 소유자나 이름의 위치로 옮긴다. 옮기지 못하면 `false`이다
     * (v1.6 `PlayRepository.move`).
     */
    fun moveRepository(project: Project, newOwner: String, newName: String): Boolean {
        val moved = backends.lifecycle(project.vcs).move(owner(project), name(project), newOwner, newName)
        // 위키 저장소는 프로젝트의 VCS와 무관하게 항상 따라간다 (X-17, D-60). LFS 객체도 따라간다(X-8, D-79).
        return moved && wiki.move(owner(project), name(project), newOwner, newName) && lfs.move(owner(project), name(project), newOwner, newName)
    }

    /**
     * 프로젝트의 LFS 객체를 지운다. 없으면 아무 일도 하지 않는다. 프로젝트를 지울 때만 부른다(위키와 같다). 포크가 빌려 쓰던 객체는 그 전에
     * [dev.yona.domain.lfs.LfsService.adoptForForks]로 포크가 가져가 있어야 한다.
     */
    fun deleteLfs(project: Project) = lfs.delete(project)

    /** 프로젝트의 위키 저장소를 만든다. 이미 있으면 아무 일도 하지 않는다. 프로젝트의 VCS와 무관하게 git이다 (X-17, D-60). */
    fun createWiki(project: Project) = wiki.create(owner(project), name(project))

    /**
     * 프로젝트의 위키 저장소를 지운다. 없으면 아무 일도 하지 않는다. 프로젝트를 지울 때만 부른다. [deleteRepository]는 VCS를
     * 바꾸거나 가져오기에 실패했을 때도 쓰이므로 위키까지 지우지 않는다.
     */
    fun deleteWiki(project: Project) = wiki.delete(owner(project), name(project))

    /** Git 프로젝트의 저장소를 연다. 호출한 쪽이 닫아야 한다. */
    fun openRepository(project: Project): Repository {
        if (project.vcs != VCS_GIT) throw unsupported(project)
        return git.open(owner(project), name(project))
    }

    /** Git 스마트 HTTP용으로 저장소를 연다. 디렉터리가 없어도 예외가 없다 ([GitRepositories.openForHttp]). */
    fun openRepositoryForHttp(project: Project): Repository {
        if (project.vcs != VCS_GIT) throw unsupported(project)
        return git.openForHttp(owner(project), name(project))
    }

    /**
     * [original]의 Git 저장소를 [fork]의 위치로 복제한다 (v1.6 `GitRepository.cloneLocalRepository`).
     *
     * @throws UnsupportedOperationException Git이 아닌 프로젝트일 때
     */
    fun cloneRepository(original: Project, fork: Project) {
        if (original.vcs != VCS_GIT) throw unsupported(original)
        if (fork.vcs != VCS_GIT) throw unsupported(fork)
        backends.lifecycle(fork.vcs).cloneLocal(owner(original), name(original), owner(fork), name(fork))
    }

    /** 다른 Git 저장소를 [project]의 저장소로 복제해 가져온다 (v1.6 `GitRepository.cloneRepository`). */
    fun importRepository(project: Project, url: String, authId: String?, authPw: String?) {
        backends.lifecycle(project.vcs).cloneRemote(owner(project), name(project), url, authId, authPw)
    }

    /** 프로젝트 저장소 디렉터리를 새 이름으로 옮긴다. 옮기지 못하면 `false`이다 (v1.6 `PlayRepository.renameTo`). */
    fun renameRepository(project: Project, newName: String): Boolean = moveRepository(project, owner(project), newName)

    /** 저장소의 브랜치, 태그, 원격 참조 이름이다. Subversion은 `HEAD` 하나이다 (v1.6 `getRefNames`). */
    fun refNames(project: Project): List<String> = backends.lifecycle(project.vcs).refNames(owner(project), name(project))

    /** 저장소의 기본 브랜치를 [target]으로 바꾼다 (v1.6 `setDefaultBranch`). */
    fun setDefaultBranch(project: Project, target: String) = backends.lifecycle(project.vcs).setDefaultBranch(owner(project), name(project), target)

    /**
     * 프로젝트 저장소의 기본 브랜치 이름이다. 예: `refs/heads/master`. 저장소를 열 수 없으면 `HEAD`이다
     * (v1.6 `Project.defaultBranch`).
     */
    fun defaultBranch(project: Project): String = try {
        backends.lifecycle(project.vcs).defaultBranch(owner(project), name(project))
    } catch (e: Exception) {
        "HEAD"
    }

    private fun owner(project: Project) = requireNotNull(project.owner) { "프로젝트의 소유자가 없습니다." }

    private fun name(project: Project) = requireNotNull(project.name) { "프로젝트의 이름이 없습니다." }

    private fun unsupported(project: Project) =
        UnsupportedOperationException("지원하지 않는 VCS입니다: ${project.vcs}")

    companion object {
        /** `project.vcs`의 Git 값이다 (v1.6 `RepositoryService.VCS_GIT`). */
        const val VCS_GIT = "GIT"

        /** `project.vcs`의 Subversion 값이다 (v1.6 `RepositoryService.VCS_SUBVERSION`). */
        const val VCS_SUBVERSION = "Subversion"
    }
}
