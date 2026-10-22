package dev.yona.support

import dev.yona.domain.project.Project
import dev.yona.repository.RepositoryService
import org.eclipse.jgit.lib.CommitBuilder
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.FileMode
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.TreeFormatter

/** 테스트에서 bare Git 저장소에 파일을 담은 커밋을 직접 만드는 도우미이다. */
object GitTestSupport {

    /**
     * [project] 저장소의 `HEAD` 브랜치에 [files]를 최상위 파일로 담은 커밋 하나를 만든다.
     * 저장소는 미리 만들어져 있어야 하며, 이전 내용은 모두 대체된다.
     */
    fun commitFiles(repositories: RepositoryService, project: Project, files: Map<String, ByteArray>) {
        repositories.openRepository(project).use { repository ->
            val inserter = repository.newObjectInserter()
            val tree = TreeFormatter()
            files.toSortedMap().forEach { (name, bytes) ->
                tree.append(name, FileMode.REGULAR_FILE, inserter.insert(Constants.OBJ_BLOB, bytes))
            }
            val commit = CommitBuilder().apply {
                setTreeId(inserter.insert(tree))
                author = PersonIdent("tester", "tester@example.com")
                committer = author
                message = "init"
            }
            val commitId = inserter.insert(commit)
            inserter.flush()
            repository.updateRef(Constants.HEAD).apply {
                setNewObjectId(commitId)
                setForceUpdate(true)
            }.update()
            check(repository.resolve(Constants.HEAD) == commitId) { "커밋을 HEAD에 반영하지 못했습니다." }
        }
    }
}
