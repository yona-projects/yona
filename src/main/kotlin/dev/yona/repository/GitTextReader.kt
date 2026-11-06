package dev.yona.repository

import dev.yona.domain.project.Project
import dev.yona.domain.vcs.VcsType
import dev.yona.io.CharsetDetection
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.treewalk.filter.PathFilter
import org.springframework.stereotype.Component

/**
 * Git 저장소의 파일을 `HEAD` 기준으로 텍스트로 읽는 [TextReader] 구현이다(JGit). README는 최상위의 `README.md`, `readme.md` 순이고,
 * 게시글용 README는 트리 순서로 처음 만나는 네 가지 이름 중 하나이다.
 */
@Component
class GitTextReader(private val git: GitRepositories) : TextReader {

    override val type: VcsType get() = VcsType.GIT

    private fun open(project: Project) = git.open(requireNotNull(project.owner), requireNotNull(project.name))

    override fun readText(project: Project, path: String): String? = open(project).use { repository ->
        val head = repository.resolve(Constants.HEAD) ?: return null
        RevWalk(repository).use { walk ->
            val treeWalk = TreeWalk.forPath(repository, path, walk.parseTree(head)) ?: return null
            treeWalk.use {
                if (it.isSubtree) return null
                val bytes = repository.open(it.getObjectId(0)).bytes
                String(bytes, java.nio.charset.Charset.forName(CharsetDetection.detect(bytes)))
            }
        }
    }

    override fun readmeFileName(project: Project): String? = open(project).use { repository -> CANDIDATES.firstOrNull { isFile(repository, it) } }

    override fun readme(project: Project): String? = open(project).use { repository ->
        val name = CANDIDATES.firstOrNull { isFile(repository, it) } ?: return null
        val bytes = rawFile(repository, name) ?: return null
        String(bytes, java.nio.charset.Charset.forName(CharsetDetection.detect(bytes)))
    }

    /**
     * 게시글 화면이 README 글의 본문으로 보여 주는 README이다 (v1.6 `BareRepository.readREADME`). `HEAD` 최상위에서 트리 순서로
     * 처음 만나는 `README.md`, `readme.md`, `README.markdown`, `readme.markdown`을 읽는다. 없으면 `null`이다. 인코딩은 설정한 문자 집합(UTF-8)으로 읽는다.
     */
    override fun readmeForPosting(project: Project): String? = open(project).use { repository ->
        val head = repository.resolve(Constants.HEAD) ?: return null
        RevWalk(repository).use { walk ->
            org.eclipse.jgit.treewalk.TreeWalk(repository).use { treeWalk ->
                treeWalk.addTree(walk.parseTree(head))
                treeWalk.isRecursive = false
                while (treeWalk.next()) {
                    if (treeWalk.pathString in ANY_README) return String(repository.open(treeWalk.getObjectId(0)).bytes, Charsets.UTF_8)
                }
                null
            }
        }
    }

    private fun isFile(repository: Repository, path: String): Boolean {
        val head = repository.resolve(Constants.HEAD) ?: return false
        RevWalk(repository).use { walk ->
            TreeWalk(repository).use { treeWalk ->
                treeWalk.addTree(walk.parseTree(head))
                treeWalk.isRecursive = true
                treeWalk.filter = PathFilter.create(path)
                return treeWalk.next()
            }
        }
    }

    private fun rawFile(repository: Repository, path: String): ByteArray? {
        val head = repository.resolve(Constants.HEAD) ?: return null
        RevWalk(repository).use { walk ->
            val treeWalk = TreeWalk.forPath(repository, path, walk.parseTree(head)) ?: return null
            treeWalk.use {
                if (it.isSubtree) return null
                return repository.open(it.getObjectId(0)).bytes
            }
        }
    }

    private companion object {
        val CANDIDATES = listOf("README.md", "readme.md")
        val ANY_README = setOf("README.md", "readme.md", "README.markdown", "readme.markdown")
    }
}
