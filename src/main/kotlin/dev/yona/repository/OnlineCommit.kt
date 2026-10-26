package dev.yona.repository

import dev.yona.domain.project.Project
import dev.yona.domain.user.User
import dev.yona.io.CharsetDetection
import dev.yona.io.LineEnding
import org.eclipse.jgit.dircache.DirCache
import org.eclipse.jgit.dircache.DirCacheBuilder
import org.eclipse.jgit.dircache.DirCacheEntry
import org.eclipse.jgit.lib.CommitBuilder
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.FileMode
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.lib.RefUpdate
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.TreeFormatter
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.TreeWalk
import org.springframework.stereotype.Component

/**
 * 작업 디렉터리 없이 bare 저장소에 텍스트 파일을 직접 커밋한다("온라인 커밋"). v1.6 `playRepository.BareCommit`과
 * `utils.GitUtil`(`app/playRepository/BareCommit.java`, `app/utils/GitUtil.java`)에 해당한다.
 *
 * 두 가지 방식이 있다.
 * - [commitRootFile]: 저장소 `HEAD`의 최상위 파일 하나를 바꾼다(README.md, ISSUE_TEMPLATE.md).
 * - [commitTextFile]: 지정한 브랜치의 임의 경로 파일을 바꾼다(코드 화면의 온라인 편집).
 *
 * 구현 차이: v1.6의 [commitTextFile]은 내용을 저장소 디렉터리에 임시 파일로 써 둔 뒤 읽어 들이는데, 여기서는 메모리에서 바로
 * 블롭으로 만든다. 저장소에 남는 객체는 같고 저장소 디렉터리에 찌꺼기 파일이 남지 않는다.
 */
@Component
class OnlineCommit(private val repositories: RepositoryService) {

    /**
     * 저장소 `HEAD`의 최상위 [fileName] 파일을 [contents]로 바꾸는 커밋을 만들고 커밋 번호를 돌려준다.
     * 내용은 기존 파일의 줄바꿈에 맞추고 끝에 줄바꿈을 붙인다. 다른 파일은 그대로 두고, 파일이 없으면 이름 순서에 맞게 끼워 넣는다.
     */
    fun commitRootFile(project: Project, actor: User, fileName: String, contents: String, message: String): ObjectId? {
        val identity = PersonIdent(actor.name.orEmpty(), actor.email.orEmpty())
        return repositories.openRepository(project).use { repository ->
            repository.newObjectInserter().use { inserter ->
                val text = LineEnding.addEol(LineEnding.change(contents, fileLineEnding(repository, fileName)))!!
                val head = headObject(repository, Constants.HEAD)
                val blob = inserter.insert(Constants.OBJ_BLOB, text.toByteArray(Charsets.UTF_8))
                val tree = inserter.insert(treeWith(repository, head, fileName, blob))
                val commit = CommitBuilder().apply {
                    setTreeId(tree)
                    if (head != ObjectId.zeroId()) setParentId(head)
                    author = identity
                    committer = identity
                    this.message = message
                }
                val commitId = inserter.insert(commit)
                inserter.flush()
                repository.updateRef(Constants.HEAD).apply {
                    isForceUpdate = false
                    setRefLogIdent(identity)
                    setNewObjectId(commitId)
                    if (head != ObjectId.zeroId()) setExpectedOldObjectId(head)
                    setRefLogMessage(message, true)
                }.update()
                commitId
            }
        }
    }

    /**
     * [branch]의 [path] 파일을 [text]로 바꾸는 커밋을 만들고 그 브랜치를 새 커밋으로 옮긴다. 내용은 가공하지 않는다.
     * 브랜치가 없으면 부모 없는 첫 커밋으로 만든다. 갱신에 실패하면 [IllegalStateException]이다.
     */
    fun commitTextFile(project: Project, actor: User, branch: String, path: String, text: String, message: String): ObjectId {
        val identity = PersonIdent(actor.name.orEmpty(), actor.email.orEmpty())
        return repositories.openRepository(project).use { repository ->
            repository.newObjectInserter().use { inserter ->
                val head = repository.resolve("$branch^{commit}")
                val index = DirCache.newInCore()
                val builder: DirCacheBuilder = index.builder()
                val entry = DirCacheEntry(path).apply {
                    val bytes = text.toByteArray(Charsets.UTF_8)
                    setLength(bytes.size.toLong())
                    setLastModified(java.time.Instant.now())
                    fileMode = FileMode.REGULAR_FILE
                    setObjectId(inserter.insert(Constants.OBJ_BLOB, bytes))
                }
                builder.add(entry)
                if (head != null) {
                    RevWalk(repository).use { walk ->
                        TreeWalk(repository).use { treeWalk ->
                            val index0 = treeWalk.addTree(walk.parseTree(head))
                            treeWalk.isRecursive = true
                            while (treeWalk.next()) {
                                val walkPath = treeWalk.pathString
                                if (walkPath == path) continue
                                val tree = treeWalk.getTree(index0, CanonicalTreeParser::class.java)
                                builder.add(DirCacheEntry(walkPath).apply { setObjectId(tree.entryObjectId); fileMode = tree.entryFileMode })
                            }
                        }
                    }
                }
                builder.finish()
                val commit = CommitBuilder().apply {
                    author = identity
                    committer = identity
                    setEncoding(Charsets.UTF_8)
                    this.message = message
                    if (head != null) setParentId(head)
                    setTreeId(index.writeTree(inserter))
                }
                val commitId = inserter.insert(commit)
                inserter.flush()
                val update = repository.updateRef("refs/heads/$branch").apply {
                    setExpectedOldObjectId(head ?: ObjectId.zeroId())
                    setNewObjectId(commitId)
                    setRefLogMessage("commit: ${message.lineSequence().firstOrNull().orEmpty()}", false)
                }
                when (val result = update.forceUpdate()) {
                    RefUpdate.Result.NEW, RefUpdate.Result.FORCED, RefUpdate.Result.FAST_FORWARD -> commitId
                    else -> throw IllegalStateException("브랜치 $branch 를 갱신하지 못했습니다: $result")
                }
            }
        }
    }

    /**
     * [branch]의 [path] 파일을 문자열로 읽는다. 브랜치나 파일이 없거나 읽을 수 없으면 `null`이다
     * (v1.6 `GitUtil.getReadTextFile`은 모든 오류를 `null`로 돌려준다).
     */
    fun readTextFile(project: Project, branch: String, path: String): String? = try {
        repositories.openRepository(project).use { repository ->
            val head = repository.resolve("$branch^{commit}") ?: return@use null
            RevWalk(repository).use { walk ->
                val treeWalk = TreeWalk.forPath(repository, path, walk.parseTree(head)) ?: return@use null
                treeWalk.use {
                    if (it.isSubtree) return@use null
                    val bytes = repository.open(it.getObjectId(0)).bytes
                    String(bytes, java.nio.charset.Charset.forName(CharsetDetection.detect(bytes)))
                }
            }
        }
    } catch (e: Exception) {
        null
    }

    /** [ref]가 가리키는 커밋이다. 아직 커밋이 없으면 영 번호이다. */
    private fun headObject(repository: Repository, ref: String): ObjectId {
        val found: Ref? = repository.findRef(ref)
        return found?.objectId ?: ObjectId.zeroId()
    }

    /**
     * 기존 파일의 줄바꿈 종류이다 (v1.6 `BareRepository.findFileLineEnding`).
     *
     * v1.6은 `TreeWalk`에 경로 필터만 걸고 `next()`를 부르지 않은 채 `getObjectId(0)`을 읽는다. 그래서 요청한 파일이 아니라 `HEAD` 최상위 트리의
     * **첫 엔트리**(깃 정렬 순서의 첫째)를 기준으로 삼는다. 예를 들어 `ISSUE_TEMPLATE.md`가 CRLF이고 `README.md`가 LF이면 README를 고칠 때도
     * CRLF로 판정한다. 이 동작을 그대로 따른다([refactoring-backlog.md] R-91). 첫 엔트리가 디렉터리이면 트리의 원본 바이트를 문자열로 해석하고,
     * 서브모듈(gitlink)이면 `MissingObjectException`이 나서 커밋을 만들지 않는다(호출한 쪽이 로그만 남긴다). 커밋이 없으면 UNDEFINED이다.
     * 트리가 비어 있으면 v1.6은 `ArrayIndexOutOfBoundsException`으로 `500`이 나지만 v2는 같은 예외를 호출한 쪽이 잡아 커밋 없이 계속한다.
     */
    private fun fileLineEnding(repository: Repository, @Suppress("UNUSED_PARAMETER") fileName: String): LineEnding.EndingType {
        val head = headObject(repository, Constants.HEAD)
        if (head == ObjectId.zeroId()) return LineEnding.EndingType.UNDEFINED
        val firstEntry = RevWalk(repository).use { walk ->
            TreeWalk(repository).use { treeWalk ->
                treeWalk.addTree(walk.parseCommit(head).tree)
                treeWalk.isRecursive = false
                treeWalk.getObjectId(0)
            }
        }
        if (firstEntry == ObjectId.zeroId()) return LineEnding.EndingType.UNDEFINED
        return LineEnding.find(String(repository.open(firstEntry).bytes, Charsets.UTF_8))
    }

    /** 기존 최상위 트리의 [fileName] 항목을 [blob]으로 바꾼(없으면 이름 순서에 맞게 끼워 넣은) 트리이다 (v1.6 `rebuildExistingTreeWith`). */
    private fun treeWith(repository: Repository, head: ObjectId, fileName: String, blob: ObjectId): TreeFormatter {
        val formatter = TreeFormatter()
        if (head == ObjectId.zeroId()) {
            formatter.append(fileName, FileMode.REGULAR_FILE, blob)
            return formatter
        }
        val walk = RevWalk(repository)
        val parser = CanonicalTreeParser(byteArrayOf(), walk.objectReader, walk.parseCommit(head).tree.id)
        var inserted = false
        while (!parser.eof()) {
            val entryName = String(parser.entryPathBuffer, 0, parser.entryPathLength, Charsets.UTF_8)
            val comparison = if (parser.entryFileMode == FileMode.TREE) "$entryName/" else entryName
            when {
                comparison == fileName && !inserted -> { formatter.append(fileName, FileMode.REGULAR_FILE, blob); inserted = true }
                comparison > fileName && !inserted -> {
                    formatter.append(fileName, FileMode.REGULAR_FILE, blob)
                    formatter.append(entryName.toByteArray(Charsets.UTF_8), parser.entryFileMode, parser.entryObjectId)
                    inserted = true
                }
                else -> formatter.append(entryName.toByteArray(Charsets.UTF_8), parser.entryFileMode, parser.entryObjectId)
            }
            parser.next()
        }
        if (!inserted) formatter.append(fileName, FileMode.REGULAR_FILE, blob)
        walk.close()
        return formatter
    }
}
