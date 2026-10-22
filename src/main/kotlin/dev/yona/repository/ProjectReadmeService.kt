package dev.yona.repository

import dev.yona.domain.project.Project
import dev.yona.io.CharsetDetection
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.treewalk.filter.PathFilter
import org.springframework.stereotype.Service

/**
 * 프로젝트 저장소의 README를 찾아 읽는다. v1.6 `Project.readme()`, `Project.getReadmeFileName()`에 해당한다.
 *
 * 저장소 `HEAD`의 최상위에서 `README.md`를 먼저, 없으면 `readme.md`를 찾는다. 파일 인코딩은 내용을 보고
 * 감지하며 감지하지 못하면 UTF-8로 읽는다(v1.6 `FileUtil.detectCharset`). 저장소가 없거나 커밋이 없으면 README도 없다.
 */
@Service
class ProjectReadmeService(private val repositories: RepositoryService) {

    /** README 파일 이름이다. 없으면 `null`이다. */
    fun fileName(project: Project): String? = withRepository(project) { repository ->
        CANDIDATES.firstOrNull { isFile(repository, it) }
    }

    /** README 내용이다. 파일이 없거나 읽을 수 없으면 `null`이다. */
    fun read(project: Project): String? = withRepository(project) { repository ->
        val name = CANDIDATES.firstOrNull { isFile(repository, it) } ?: return@withRepository null
        val bytes = rawFile(repository, name) ?: return@withRepository null
        String(bytes, java.nio.charset.Charset.forName(CharsetDetection.detect(bytes)))
    }

    private fun <T> withRepository(project: Project, block: (Repository) -> T?): T? = try {
        repositories.openRepository(project).use(block)
    } catch (e: Exception) {
        // v1.6은 저장소를 열지 못하거나 파일을 읽지 못하면 모두 README가 없는 것으로 본다.
        null
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
    }
}
