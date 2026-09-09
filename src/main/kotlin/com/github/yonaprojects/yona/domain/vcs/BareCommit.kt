package com.github.yonaprojects.yona.domain.vcs

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.User
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException
import org.eclipse.jgit.api.errors.JGitInternalException
import org.eclipse.jgit.dircache.DirCache
import org.eclipse.jgit.dircache.DirCacheEntry
import org.eclipse.jgit.internal.JGitText
import org.eclipse.jgit.lib.*
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.TreeWalk
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.text.MessageFormat
import java.time.Instant

class BareCommit(project: Project, user: User, gitBaseDir: String, defaultBranch: String = "main") {
    private val repository: Repository
    private val personIdent: PersonIdent
    private var commitMessage: String? = null
    private var file: File? = null
    // 새 프로젝트의 실제 초기 브랜치(GitRepository.create()가 만드는 것)와 일치해야 한다 — 안
    // 그러면 setRefName() 없이 이 기본값으로 커밋하는 경로(README/ISSUE_TEMPLATE 등)가 실제 HEAD가
    // 가리키는 빈 브랜치가 아니라 별도의 고아 브랜치를 만들어버린다.
    private var refName: String = Constants.R_HEADS + defaultBranch
    private var headObjectId: ObjectId? = null

    init {
        val gitDir = File(File(gitBaseDir), "${project.owner}/${project.name}.git")
        this.repository = FileRepositoryBuilder().setGitDir(gitDir).build()
        this.personIdent = PersonIdent(user.name ?: user.loginId, user.email ?: "")
    }

    fun setRefName(refName: String) {
        this.refName = refName
    }

    @Throws(IOException::class)
    fun commitTextFile(fileNameWithPath: String, contents: String, message: String): ObjectId? {
        this.file = File(fileNameWithPath)
        this.commitMessage = message
        
        var commitId: ObjectId? = null
        val inserter = repository.newObjectInserter()
        try {
            val ref = repository.findRef(refName)
            this.headObjectId = ref?.objectId ?: ObjectId.zeroId()
            
            val blobId = inserter.insert(Constants.OBJ_BLOB, contents.toByteArray(Charsets.UTF_8))
            val treeId = createTreeWith(inserter, file!!.name, blobId)
            
            val commitBuilder = CommitBuilder().apply {
                setTreeId(treeId)
                if (headObjectId != ObjectId.zeroId()) {
                    setParentId(headObjectId)
                }
                author = personIdent
                committer = personIdent
                setMessage(commitMessage)
            }
            
            commitId = inserter.insert(commitBuilder)
            inserter.flush()
            
            val ru = repository.updateRef(refName)
            ru.isForceUpdate = false
            ru.setRefLogIdent(personIdent)
            ru.setNewObjectId(commitId)
            if (ref?.objectId != null) {
                ru.setExpectedOldObjectId(headObjectId)
            }
            ru.setRefLogMessage(commitMessage, true)
            ru.update()
        } finally {
            inserter.close()
            repository.close()
        }
        return commitId
    }

    // yona BareCommit.java의 bare commit(https://gist.github.com/porcelli/3882505 인용) 대응.
    // 위 3-인자 commitTextFile()과 달리 1) branchName으로 지정한 refs/heads/<branchName>에만
    // 커밋을 반영하고, 2) DirCache+재귀 TreeWalk로 기존 트리의 모든 하위 경로(nested path) 파일을 보존한 채
    // path(중첩 경로 가능) 위치에 새 파일을 반영한다. GitUtil.commitTextFile()가 setRefName(branchName)을
    // 먼저 호출한 뒤 이 메서드를 호출하는 것을 전제로 한다(yona와 동일한 사용 계약).
    @Throws(IOException::class)
    fun commitTextFile(branchName: String, path: String, text: String, message: String): ObjectId? {
        this.file = File(repository.directory, path)
        this.file!!.parentFile?.mkdirs()
        this.file!!.writeText(text, Charsets.UTF_8)

        var commitId: ObjectId? = null
        val git = Git(repository)
        try {
            git.repository.newObjectInserter().use { inserter ->
                this.headObjectId = git.repository.resolve("$refName^{commit}")
                val index = createTemporaryIndex(git, headObjectId, path, file!!)
                val indexTreeId = index.writeTree(inserter)

                val commit = getCommitBuilder(message, indexTreeId)

                commitId = inserter.insert(commit)
                inserter.flush()

                val ru = getRefUpdate(branchName, commitId!!, git)
                when (val rc = ru.forceUpdate()) {
                    RefUpdate.Result.NEW, RefUpdate.Result.FORCED, RefUpdate.Result.FAST_FORWARD -> {}
                    RefUpdate.Result.REJECTED, RefUpdate.Result.LOCK_FAILURE ->
                        throw ConcurrentRefUpdateException(JGitText.get().couldNotLockHEAD, ru.ref, rc)
                    else ->
                        throw JGitInternalException(
                            MessageFormat.format(JGitText.get().updatingRefFailed, Constants.HEAD, commitId.toString(), rc)
                        )
                }
            }
        } catch (t: Throwable) {
            throw RuntimeException(t)
        }

        return commitId
    }

    private fun getRefUpdate(branchName: String, commitId: ObjectId, git: Git): RefUpdate {
        val revWalk = RevWalk(git.repository)
        val revCommit = revWalk.parseCommit(commitId)
        val ru = git.repository.updateRef(Constants.R_HEADS + branchName)
        if (this.headObjectId == null) {
            ru.setExpectedOldObjectId(ObjectId.zeroId())
        } else {
            ru.setExpectedOldObjectId(this.headObjectId)
        }
        ru.setNewObjectId(commitId)
        ru.setRefLogMessage("commit: " + revCommit.shortMessage, false)
        revWalk.close()
        return ru
    }

    private fun getCommitBuilder(message: String, indexTreeId: ObjectId): CommitBuilder {
        val commit = CommitBuilder()
        commit.author = this.personIdent
        commit.committer = this.personIdent
        commit.encoding = Charsets.UTF_8
        commit.message = message
        // headObjectId는 저장소에 커밋이 아직 없는 경우 null일 수 있다
        if (this.headObjectId != null) {
            commit.setParentId(this.headObjectId)
        }
        commit.setTreeId(indexTreeId)
        return commit
    }

    private fun createTemporaryIndex(git: Git, headId: ObjectId?, path: String, file: File): DirCache {
        val inCoreIndex = DirCache.newInCore()
        val dcBuilder = inCoreIndex.builder()
        val inserter = git.repository.newObjectInserter()

        try {
            val dcEntry = DirCacheEntry(path)
            dcEntry.setLength(file.length())
            dcEntry.setLastModified(Instant.ofEpochMilli(file.lastModified()))
            dcEntry.fileMode = FileMode.REGULAR_FILE

            FileInputStream(file).use { inputStream ->
                dcEntry.setObjectId(inserter.insert(Constants.OBJ_BLOB, file.length(), inputStream))
            }

            dcBuilder.add(dcEntry)

            if (headId != null) {
                val treeWalk = TreeWalk(git.repository)
                val hIdx = treeWalk.addTree(RevWalk(git.repository).parseTree(headId))
                treeWalk.isRecursive = true

                while (treeWalk.next()) {
                    val walkPath = treeWalk.pathString
                    val hTree = treeWalk.getTree(hIdx, CanonicalTreeParser::class.java)

                    if (walkPath != path) {
                        // HEAD의 다른 모든 경로를 임시 in-core 인덱스에 그대로 옮겨온다
                        val dcEntry2 = DirCacheEntry(walkPath)
                        dcEntry2.setObjectId(hTree.entryObjectId)
                        dcEntry2.fileMode = hTree.entryFileMode
                        dcBuilder.add(dcEntry2)
                    }
                }
                treeWalk.close()
            }

            dcBuilder.finish()
        } finally {
            inserter.close()
        }

        return inCoreIndex
    }

    private fun createTreeWith(inserter: ObjectInserter, fileName: String, fileObjectId: ObjectId): ObjectId {
        return if (headObjectId == ObjectId.zeroId() || headObjectId == null) {
            val formatter = TreeFormatter().apply {
                append(fileName, FileMode.REGULAR_FILE, fileObjectId)
            }
            inserter.insert(formatter)
        } else {
            val formatter = TreeFormatter()
            val revWalk = RevWalk(repository)
            val commit = revWalk.parseCommit(headObjectId)
            val treeParser = CanonicalTreeParser(byteArrayOf(), revWalk.objectReader, commit.tree.id)
            
            var isInserted = false
            while (!treeParser.eof()) {
                val entryName = String(treeParser.entryPathBuffer, 0, treeParser.entryPathLength, Charsets.UTF_8)
                var nameForComparison = entryName
                if (treeParser.entryFileMode == FileMode.TREE) {
                    nameForComparison = "$entryName/"
                }
                
                if (nameForComparison.compareTo(fileName) == 0 && !isInserted) {
                    formatter.append(fileName, FileMode.REGULAR_FILE, fileObjectId)
                    isInserted = true
                } else if (nameForComparison.compareTo(fileName) > 0 && !isInserted) {
                    formatter.append(fileName, FileMode.REGULAR_FILE, fileObjectId)
                    formatter.append(entryName.toByteArray(Charsets.UTF_8), treeParser.entryFileMode, treeParser.entryObjectId)
                    isInserted = true
                } else {
                    formatter.append(entryName.toByteArray(Charsets.UTF_8), treeParser.entryFileMode, treeParser.entryObjectId)
                }
                treeParser.next()
            }
            if (!isInserted) {
                formatter.append(fileName, FileMode.REGULAR_FILE, fileObjectId)
            }
            inserter.insert(formatter)
        }
    }
}
