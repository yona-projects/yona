package dev.yona.support

import dev.yona.domain.project.Project
import dev.yona.repository.RepositoryService
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.CommitBuilder
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.FileMode
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.TreeFormatter
import org.eclipse.jgit.transport.RefSpec
import java.io.File
import java.nio.file.Files
import java.util.Date
import java.util.TimeZone

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

    /**
     * [project] 저장소에 [refName](`refs/tags/v1`, `refs/remotes/origin/x` 등)을 만든다. 기본은 `HEAD`의 커밋을 가리키고,
     * [toTree]가 참이면 커밋이 아니라 `HEAD`의 트리를 가리킨다(커밋이 아닌 대상을 가리키는 태그를 만들 때 쓴다).
     */
    fun createRef(repositories: RepositoryService, project: Project, refName: String, toTree: Boolean = false) {
        repositories.openRepository(project).use { repository ->
            val target = requireNotNull(repository.resolve(if (toTree) "HEAD^{tree}" else "HEAD")) { "HEAD를 풀 수 없습니다." }
            repository.updateRef(refName).apply { setNewObjectId(target); setForceUpdate(true) }.update()
        }
    }

    /**
     * [project] 저장소의 `HEAD` 커밋에 주석 태그 [name]을 만든다. [message]는 태그 메시지, [taggerName]과 [time]은 태그를 단 사람과 시각이다.
     */
    fun createAnnotatedTag(repositories: RepositoryService, project: Project, name: String, message: String, taggerName: String = "릴리서", time: Long = 1_800_000_000_000L) {
        repositories.openRepository(project).use { repository ->
            val head = repository.resolve("HEAD")
            org.eclipse.jgit.api.Git(repository).tag().setName(name).setAnnotated(true).setMessage(message)
                .setTagger(PersonIdent(taggerName, "tagger@example.com", Date(time), TimeZone.getTimeZone("UTC")))
                .setObjectId(org.eclipse.jgit.revwalk.RevWalk(repository).use { it.parseAny(head) }).setSigned(false).call()
        }
    }

    /**
     * 테스트에서 만들 커밋 하나이다.
     *
     * @property files 이 커밋에서 쓰거나 바꾸는 파일(경로는 `dir/name`처럼 하위 폴더를 쓸 수 있다)
     * @property deleted 이 커밋에서 지우는 파일
     * @property authorEmail 작성자 이메일(사용자 찾기에 쓴다)
     */
    class TestCommit(
        val message: String, val files: Map<String, ByteArray> = emptyMap(), val deleted: List<String> = emptyList(),
        val authorName: String = "tester", val authorEmail: String = "tester@example.com"
    )

    /**
     * 임시 작업 저장소에서 [commits]를 차례로 만들어 [project] 저장소의 [branch] 브랜치로 밀어 넣는다. 커밋 시각은 2023-11-14부터 하루씩 늘어난다.
     * 저장소가 비어 있으면 `HEAD`가 [branch]를 가리키게 한다.
     */
    fun pushCommits(repositories: RepositoryService, project: Project, vararg commits: TestCommit, branch: String = "master", startTime: Long = 1_700_000_000_000L) {
        val bare = repositories.openRepository(project).use { it.directory }
        val work: File = Files.createTempDirectory("yona-it-work").toFile()
        try {
            Git.init().setDirectory(work).setInitialBranch(branch).call().use { git ->
                var time = startTime
                for (commit in commits) {
                    commit.files.forEach { (path, bytes) ->
                        val file = File(work, path)
                        file.parentFile.mkdirs()
                        file.writeBytes(bytes)
                        git.add().addFilepattern(path).call()
                    }
                    commit.deleted.forEach { git.rm().addFilepattern(it).call() }
                    val ident = PersonIdent(commit.authorName, commit.authorEmail, Date(time), TimeZone.getTimeZone("UTC"))
                    git.commit().setMessage(commit.message).setAuthor(ident).setCommitter(ident).setAllowEmpty(true).call()
                    time += 86_400_000L
                }
                git.push().setRemote(bare.toURI().toString()).setRefSpecs(RefSpec("refs/heads/$branch:refs/heads/$branch")).call()
            }
        } finally {
            work.deleteRecursively()
        }
    }

    /**
     * [project] 저장소의 [branch] 위에 [commits]를 이어서 쌓고 밀어 넣는다. 브랜치가 없으면 [baseBranch]에서 갈라져 나온다.
     * 풀 리퀘스트처럼 이어지는 이력이 필요한 테스트에서 쓴다. 커밋 시각은 [startTime]부터 하루씩 늘어난다.
     */
    fun appendCommits(
        repositories: RepositoryService, project: Project, vararg commits: TestCommit, branch: String = "master", baseBranch: String = "master",
        startTime: Long = 1_750_000_000_000L
    ) {
        val bare = repositories.openRepository(project).use { it.directory }
        val work: File = Files.createTempDirectory("yona-it-work").toFile()
        try {
            Git.cloneRepository().setURI(bare.toURI().toString()).setDirectory(work).setBranch("refs/heads/$baseBranch").call().use { git ->
                if (branch != baseBranch) {
                    val exists = git.repository.resolve("refs/remotes/origin/$branch") != null
                    if (exists) git.checkout().setCreateBranch(true).setName(branch).setStartPoint("origin/$branch").call()
                    else git.checkout().setCreateBranch(true).setName(branch).call()
                }
                var time = startTime
                for (commit in commits) {
                    commit.files.forEach { (path, bytes) ->
                        val file = File(work, path)
                        file.parentFile.mkdirs()
                        file.writeBytes(bytes)
                        git.add().addFilepattern(path).call()
                    }
                    commit.deleted.forEach { git.rm().addFilepattern(it).call() }
                    val ident = PersonIdent(commit.authorName, commit.authorEmail, Date(time), TimeZone.getTimeZone("UTC"))
                    git.commit().setMessage(commit.message).setAuthor(ident).setCommitter(ident).setAllowEmpty(true).call()
                    time += 86_400_000L
                }
                git.push().setRemote(bare.toURI().toString()).setRefSpecs(RefSpec("refs/heads/$branch:refs/heads/$branch")).call()
            }
        } finally {
            work.deleteRecursively()
        }
    }
}
