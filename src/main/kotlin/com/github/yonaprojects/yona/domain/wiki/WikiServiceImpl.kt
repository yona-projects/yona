package com.github.yonaprojects.yona.domain.wiki

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.vcs.BareCommit
import com.github.yonaprojects.yona.domain.vcs.Commit
import com.github.yonaprojects.yona.domain.vcs.GitRepository
import org.eclipse.jgit.lib.Constants
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.FileNotFoundException
import java.time.Instant

@Service
class WikiServiceImpl(
    private val userRepository: UserRepository,
    @Value("\${yona.git.base-dir:/tmp/yona/git}")
    private val gitBaseDir: String,
    @Value("\${yona.git.default-branch:main}")
    private val gitDefaultBranch: String
) : WikiService {

    companion object {
        // GitHub/Forgejo 위키가 "_history", "_new" 등을 예약 경로로 쓰는 것과 동일한 이유 —
        // 웹 UI 라우팅(WikiViewController)이 "/wiki/_edit/**", "/wiki/_new" 등을 페이지 제목보다
        // 먼저 매치해야 해서, 페이지 제목의 첫 세그먼트로 이 값들을 허용하지 않는다.
        private val RESERVED_FIRST_SEGMENTS = setOf("_new", "_edit", "_delete", "_history", "_search", "_diff")
    }

    // 위키 저장소는 프로젝트의 실제 코드 저장소와 완전히 분리된 별도 bare git 저장소다 —
    // 이름 규칙만 code 저장소(`<owner>/<project>.git`)와 나란히 두어 "<owner>/<project>.wiki.git"로
    // 짓는다(GitServletConfig가 이미 이 명명 규칙으로 clone/push를 서빙하도록 손봤다).
    private fun wikiRepoName(project: Project): String = "${project.name}.wiki"

    private fun wikiGitRepository(project: Project): GitRepository {
        return GitRepository(
            ownerName = project.owner ?: "",
            projectName = wikiRepoName(project),
            baseDir = gitBaseDir,
            userResolver = { _, email ->
                if (email != null) userRepository.findByEmail(email).orElse(null) else null
            },
            defaultBranch = gitDefaultBranch
        )
    }

    private fun bareCommit(project: Project, user: User): BareCommit {
        val bare = BareCommit(project, user, gitBaseDir, gitDefaultBranch, repoNameOverride = wikiRepoName(project))
        bare.setRefName(Constants.R_HEADS + gitDefaultBranch)
        return bare
    }

    override fun isInitialized(project: Project): Boolean {
        val repo = wikiGitRepository(project)
        return repo.getDirectory().exists() && !repo.isEmpty()
    }

    override fun ensureRepository(project: Project) {
        val repo = wikiGitRepository(project)
        if (!repo.getDirectory().exists()) {
            repo.create()
        }
    }

    override fun listPages(project: Project): List<WikiPageSummary> {
        val repo = wikiGitRepository(project)
        if (!repo.getDirectory().exists() || repo.isEmpty()) return emptyList()
        val results = mutableListOf<WikiPageSummary>()
        collectPages(repo, "", results)
        return results.sortedBy { it.title.lowercase() }
    }

    // GitRepository.getMetaDataFromPath()(디렉터리 한 단계 조회 + 자식별 최신 커밋 메타데이터)를
    // 재귀적으로 호출해 트리 전체를 훑는다 — 새 저수준 JGit 순회를 만들지 않고 기존 메서드를
    // 그대로 재사용한다.
    private fun collectPages(repo: GitRepository, path: String, acc: MutableList<WikiPageSummary>) {
        val node = repo.getMetaDataFromPath("HEAD", path) ?: return
        val data = node.get("data") ?: return
        for ((name, entry) in data.properties()) {
            val childPath = if (path.isEmpty()) name else "$path/$name"
            val type = entry.get("type")?.asText()
            if (type == "folder") {
                collectPages(repo, childPath, acc)
            } else if (childPath.endsWith(".md")) {
                val createdDate = entry.get("createdDate")?.asLong(0L) ?: 0L
                acc.add(
                    WikiPageSummary(
                        path = childPath,
                        title = pathToTitle(childPath),
                        updatedAt = if (createdDate > 0) Instant.ofEpochMilli(createdDate) else null,
                        lastCommitMessage = entry.get("commitMessage")?.asText(),
                        lastAuthorName = entry.get("author")?.asText()
                    )
                )
            }
        }
    }

    override fun getPage(project: Project, title: String, rev: String): WikiPageContent? {
        val path = titleToPath(title)
        val repo = wikiGitRepository(project)
        if (!repo.getDirectory().exists()) return null
        val bytes = try {
            repo.getRawFile(rev, path)
        } catch (e: FileNotFoundException) {
            return null
        } catch (e: Exception) {
            return null
        }
        val headCommit = repo.getHistory(0, 1, rev, path).firstOrNull()
        return WikiPageContent(
            path = path,
            title = title,
            content = String(bytes, Charsets.UTF_8),
            revision = headCommit?.getId()
        )
    }

    override fun savePage(
        project: Project,
        user: User,
        oldTitle: String?,
        newTitle: String,
        content: String,
        message: String?
    ): String {
        val newPath = titleToPath(newTitle)
        val oldPath = oldTitle?.let { if (it == newTitle) null else titleToPath(it) }
        ensureRepository(project)

        val commitMessage = message?.trim()?.takeIf { it.isNotEmpty() } ?: defaultMessage(oldTitle, newTitle)

        val bare = bareCommit(project, user)
        val commitId = bare.commitPage(gitDefaultBranch, oldPath, newPath, content, commitMessage)
            ?: throw IllegalStateException("위키 페이지 저장에 실패했습니다: $newTitle")
        return commitId.name
    }

    private fun defaultMessage(oldTitle: String?, newTitle: String): String {
        return when {
            oldTitle == null -> "Create $newTitle"
            oldTitle != newTitle -> "Rename $oldTitle to $newTitle"
            else -> "Update $newTitle"
        }
    }

    override fun deletePage(project: Project, user: User, title: String, message: String?): String {
        val path = titleToPath(title)
        val commitMessage = message?.trim()?.takeIf { it.isNotEmpty() } ?: "Delete $title"
        val bare = bareCommit(project, user)
        val commitId = bare.deletePage(gitDefaultBranch, path, commitMessage)
            ?: throw IllegalStateException("위키 페이지 삭제에 실패했습니다: $title")
        return commitId.name
    }

    override fun history(project: Project, title: String, pageNum: Int, pageSize: Int): List<Commit> {
        val path = titleToPath(title)
        val repo = wikiGitRepository(project)
        if (!repo.getDirectory().exists() || repo.isEmpty()) return emptyList()
        return repo.getHistory(pageNum, pageSize, "HEAD", path)
    }

    override fun diff(project: Project, title: String, commitId: String): String {
        val path = titleToPath(title)
        val repo = wikiGitRepository(project)
        return repo.getPatchForPath(commitId, path)
    }

    override fun compare(project: Project, title: String, revA: String, revB: String): String {
        val path = titleToPath(title)
        val repo = wikiGitRepository(project)
        return repo.getPatchForPath(revA, revB, path)
    }

    override fun search(project: Project, query: String): List<WikiPageSummary> {
        if (query.isBlank()) return listPages(project)
        val lowered = query.trim().lowercase()
        return listPages(project).filter { it.title.lowercase().contains(lowered) }
    }

    override fun titleToPath(title: String): String {
        val trimmed = title.trim().trim('/')
        require(trimmed.isNotEmpty()) { "위키 페이지 제목은 비어 있을 수 없습니다." }
        val segments = trimmed.removeSuffix(".md").split("/")
        require(segments.all { it.isNotBlank() && it != "." && it != ".." }) {
            "잘못된 위키 페이지 제목입니다: $title"
        }
        require(segments.first() !in RESERVED_FIRST_SEGMENTS) {
            "예약된 페이지 제목입니다: ${segments.first()}"
        }
        return segments.joinToString("/") + ".md"
    }

    override fun pathToTitle(path: String): String = path.removeSuffix(".md")
}
