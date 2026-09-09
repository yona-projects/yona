package com.github.yonaprojects.yona.domain.vcs

import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode
import com.github.yonaprojects.yona.domain.gpgkey.GpgVerificationStatus
import com.github.yonaprojects.yona.domain.user.User
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.errors.LargeObjectException
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ConfigConstants
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevTag
import org.eclipse.jgit.revwalk.RevTree
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.treewalk.filter.PathFilter
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.ObjectReader
import org.eclipse.jgit.lib.ObjectLoader
import org.eclipse.jgit.lib.ObjectDatabase
import org.eclipse.jgit.lib.RefDatabase
import org.eclipse.jgit.lib.StoredConfig
import org.eclipse.jgit.lib.BaseRepositoryBuilder
import org.eclipse.jgit.lib.AnyObjectId
import org.eclipse.jgit.lib.AbbreviatedObjectId
import org.eclipse.jgit.lib.FileMode
import org.eclipse.jgit.attributes.AttributesNodeProvider
import org.eclipse.jgit.attributes.AttributesNode
import org.eclipse.jgit.attributes.AttributesRule
import org.eclipse.jgit.lib.ReflogReader
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.RawText
import org.eclipse.jgit.diff.RawTextComparator
import org.eclipse.jgit.diff.DiffAlgorithm
import org.eclipse.jgit.diff.Edit
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.EmptyTreeIterator
import org.eclipse.jgit.util.io.NullOutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import com.github.yonaprojects.yona.domain.support.FileUtil

class GitRepository(
    private val ownerName: String,
    private val projectName: String,
    private val baseDir: String,
    private val userResolver: (String?, String?) -> User?,
    // 새 저장소의 초기 브랜치명. JGit의 Git.init() 기본값은 이 서버가 돌아가는 머신의 전역 git 설정
    // (init.defaultBranch)을 따라 환경마다 달라질 수 있어(GitRepositorySpec.kt의 defaultBranchRef
    // 프로브 참고), 애플리케이션 차원에서 결정론적으로 강제한다(RepositoryService가
    // yona.git.default-branch 설정값을 넘겨줌). userResolver 뒤에 둬서(파라미터 순서 유지) 기존
    // `GitRepository(a, b, c, userResolver)` 형태의 수십 개 테스트 호출부가 그대로 동작하게 한다.
    private val defaultBranch: String = "main",
    // 커밋 목록/상세 화면의 GPG Verified 배지 계산. 기본값(no-op, 항상 UNSIGNED)을 둬서
    // defaultBranch와 마찬가지로 기존 호출부가 그대로 동작하게 한다 — RepositoryService가 실제
    // 구현을 주입한다.
    private val gpgVerifier: (RevCommit) -> GpgVerificationStatus =
        { GpgVerificationStatus.UNSIGNED }
) : PlayRepository {

    private val objectMapper = ObjectMapper()

    private fun <T> useRepository(block: (Repository) -> T): T {
        val gitDir = File(File(baseDir), "$ownerName/$projectName.git")
        val repo = FileRepositoryBuilder().setGitDir(gitDir).build()
        return repo.use(block)
    }

    override fun create() {
        val gitDir = File(File(baseDir), "$ownerName/$projectName.git")
        if (!gitDir.exists()) {
            gitDir.mkdirs()
        }
        Git.init().setDirectory(gitDir).setBare(true).setInitialBranch(defaultBranch).call().close()
    }

    override fun isIntermediateFolder(path: String): Boolean {
        return false
    }

    override fun getMetaDataFromPath(path: String): ObjectNode? {
        return getMetaDataFromPath("HEAD", path)
    }

    override fun getMetaDataFromPath(branch: String, path: String): ObjectNode? {
        return useRepository { repo ->
            val branchName = if (branch.isEmpty()) "HEAD" else branch
            val objectId = repo.resolve(branchName) ?: return@useRepository null
            val revWalk = RevWalk(repo)
            val commit = revWalk.parseCommit(objectId)
            val revTree = commit.tree
            val treeWalk = TreeWalk(repo)
            treeWalk.addTree(revTree)
            treeWalk.isRecursive = false

            if (path.isEmpty()) {
                return@useRepository treeAsJson(path, treeWalk, commit, repo)
            }

            val pathFilter = PathFilter.create(path)
            treeWalk.filter = pathFilter
            var found = false
            while (treeWalk.next()) {
                if (pathFilter.isDone(treeWalk)) {
                    found = true
                    break
                } else if (treeWalk.isSubtree) {
                    treeWalk.enterSubtree()
                }
            }

            if (!found) {
                return@useRepository null
            }

            if (treeWalk.isSubtree) {
                treeWalk.enterSubtree()
                treeAsJson(path, treeWalk, commit, repo)
            } else {
                fileAsJson(path, treeWalk, commit, repo)
            }
        }
    }

    private fun treeAsJson(path: String, treeWalk: TreeWalk, commit: RevCommit, repo: Repository): ObjectNode {
        val result = objectMapper.createObjectNode()
        val listData = objectMapper.createObjectNode()
        result.put("type", "folder")

        val git = Git(repo)
        while (treeWalk.next()) {
            val childName = treeWalk.nameString
            val childPath = if (path.isEmpty()) childName else "$path/$childName"

            val logs = git.log().add(commit).addPath(childPath).setMaxCount(1).call()
            val latestCommit = logs.firstOrNull()
            val data = objectMapper.createObjectNode()

            if (latestCommit != null) {
                val gitCommit = GitCommit(latestCommit, userResolver, gpgVerifier)
                val commitTime = gitCommit.getAuthorDate()?.time ?: 0L
                val user = gitCommit.getAuthor()

                data.put("type", if (treeWalk.isSubtree) "folder" else "file")
                data.put("msg", gitCommit.getShortMessage())
                data.put("author", gitCommit.getAuthorName() ?: "")
                data.put("avatar", "/images/default-avatar-34.png")
                data.put("userName", user?.name ?: "")
                data.put("userLoginId", user?.loginId ?: "")
                data.put("createdDate", commitTime)
                data.put("commitMessage", gitCommit.getShortMessage())
                data.put("commiter", gitCommit.getCommitterName() ?: "")
                data.put("commitDate", commitTime)
                data.put("commitId", latestCommit.name)
                data.put("commitUrl", "/$ownerName/$projectName/commit/${latestCommit.name}")
            }

            val objectLoader = repo.open(treeWalk.getObjectId(0))
            data.put("size", objectLoader.size)
            listData.set(childName, data)
        }
        result.set("data", listData)
        return result
    }

    private fun fileAsJson(path: String, treeWalk: TreeWalk, commit: RevCommit, repo: Repository): ObjectNode {
        val result = objectMapper.createObjectNode()
        val objectLoader = repo.open(treeWalk.getObjectId(0))
        val size = objectLoader.size
        val bytes = objectLoader.bytes
        var isBinary = false
        var mimeType = "application/octet-stream"
        var data: String? = null

        if (size > PlayRepository.MAX_FILE_SIZE_CAN_BE_VIEWED) {
            isBinary = true
        } else {
            isBinary = bytes.contains(0)
            if (!isBinary) {
                data = String(bytes, StandardCharsets.UTF_8)
            }
            mimeType = try {
                val fileTmp = Files.createTempFile("yona-git-mime", null)
                Files.write(fileTmp, bytes)
                val detected = Files.probeContentType(fileTmp)
                Files.delete(fileTmp)
                detected ?: "application/octet-stream"
            } catch (e: Exception) {
                "application/octet-stream"
            }
        }

        val git = Git(repo)
        val logs = git.log().add(commit).addPath(path).setMaxCount(1).call()
        val latestCommit = logs.firstOrNull()

        if (latestCommit != null) {
            val gitCommit = GitCommit(latestCommit, userResolver, gpgVerifier)
            val commitTime = gitCommit.getAuthorDate()?.time ?: 0L
            val user = gitCommit.getAuthor()

            result.put("type", "file")
            result.put("revisionNo", latestCommit.name)
            result.put("author", gitCommit.getAuthorName() ?: "")
            result.put("avatar", "/images/default-avatar-34.png")
            result.put("userName", user?.name ?: "")
            result.put("userLoginId", user?.loginId ?: "")
            result.put("createdDate", commitTime)
            result.put("commitMessage", gitCommit.getShortMessage())
            result.put("commiter", gitCommit.getCommitterName() ?: "")
            result.put("size", size)
            result.put("isBinary", isBinary)
            result.put("mimeType", mimeType)
            result.put("data", data ?: "")
        }

        return result
    }

    override fun getRawFile(revision: String, path: String): ByteArray {
        return useRepository { repo ->
            val objectId = repo.resolve(revision) ?: throw FileNotFoundException()
            val revWalk = RevWalk(repo)
            val commit = revWalk.parseCommit(objectId)
            val treeWalk = TreeWalk.forPath(repo, path, commit.tree) ?: throw FileNotFoundException()
            val objectLoader = repo.open(treeWalk.getObjectId(0))
            objectLoader.bytes
        }
    }

    override fun delete() {
        val gitDir = File(File(baseDir), "$ownerName/$projectName.git")
        if (gitDir.exists()) {
            gitDir.deleteRecursively()
        }
    }

    override fun getPatch(commitId: String): String {
        return useRepository { repo ->
            val objectId = repo.resolve(commitId) ?: return@useRepository ""
            val revWalk = RevWalk(repo)
            val commit = revWalk.parseCommit(objectId)
            val parent = if (commit.parentCount > 0) revWalk.parseCommit(commit.getParent(0)) else null
            getPatch(repo, parent, commit)
        }
    }

    override fun getPatch(revA: String, revB: String): String {
        return useRepository { repo ->
            val idA = repo.resolve(revA) ?: return@useRepository ""
            val idB = repo.resolve(revB) ?: return@useRepository ""
            val revWalk = RevWalk(repo)
            val commitA = revWalk.parseCommit(idA)
            val commitB = revWalk.parseCommit(idB)
            getPatch(repo, commitA, commitB)
        }
    }

    // 위키 페이지별 diff(P3-42) 대응. 커밋 하나가 여러 파일을 건드렸어도 그 중 path 하나만
    // 잘라낸 unified diff를 만든다 — 커밋 vs 부모(getPatch(commitId, path)) 또는 두 임의
    // 리비전 사이(getPatch(revA, revB, path))의 그 파일 변경만 정확히 반영한다(PathFilter로
    // treeWalk를 좁혀서 그 파일 외 변경은 애초에 diff 스캔 대상에 들어오지 않는다).
    fun getPatchForPath(commitId: String, path: String): String {
        return useRepository { repo ->
            val objectId = repo.resolve(commitId) ?: return@useRepository ""
            val revWalk = RevWalk(repo)
            val commit = revWalk.parseCommit(objectId)
            val parent = if (commit.parentCount > 0) revWalk.parseCommit(commit.getParent(0)) else null
            getPatch(repo, parent, commit, path)
        }
    }

    fun getPatchForPath(revA: String, revB: String, path: String): String {
        return useRepository { repo ->
            val idA = repo.resolve(revA) ?: return@useRepository ""
            val idB = repo.resolve(revB) ?: return@useRepository ""
            val revWalk = RevWalk(repo)
            val commitA = revWalk.parseCommit(idA)
            val commitB = revWalk.parseCommit(idB)
            getPatch(repo, commitA, commitB, path)
        }
    }

    private fun getPatch(repo: Repository, commitA: RevCommit?, commitB: RevCommit?, path: String? = null): String {
        val treeWalk = TreeWalk(repo)
        if (commitA == null) {
            treeWalk.addTree(EmptyTreeIterator())
        } else {
            treeWalk.addTree(commitA.tree)
        }
        if (commitB == null) {
            treeWalk.addTree(EmptyTreeIterator())
        } else {
            treeWalk.addTree(commitB.tree)
        }
        treeWalk.isRecursive = true
        if (path != null) {
            treeWalk.filter = PathFilter.create(path)
        }

        val out = ByteArrayOutputStream()
        val diffFormatter = DiffFormatter(out)
        diffFormatter.setRepository(repo)
        diffFormatter.format(DiffEntry.scan(treeWalk))
        return out.toString("UTF-8")
    }

    override fun getDiff(commitId: String): List<Any> {
        return useRepository { repo ->
            val id = repo.resolve(commitId) ?: return@useRepository emptyList()
            val revWalk = RevWalk(repo)
            val commit = revWalk.parseCommit(id)
            val idA = if (commit.parentCount > 0) commit.getParent(0).id else null
            getFileDiffs(repo, repo, idA, id)
        }
    }

    override fun getDiff(revA: String, revB: String): List<Any> {
        return useRepository { repo ->
            val idA = repo.resolve(revA)
            val idB = repo.resolve(revB)
            getFileDiffs(repo, repo, idA, idB)
        }
    }

    fun getDiff(revA: String, otherRepo: GitRepository, revB: String): List<FileDiff> {
        return useRepository { repoA ->
            otherRepo.useRepository { repoB ->
                val idA = repoA.resolve(revA)
                val idB = repoB.resolve(revB)
                getFileDiffs(repoA, repoB, idA, idB)
            }
        }
    }

    private fun getFileDiffs(
        repoA: Repository,
        repoB: Repository,
        commitA: ObjectId?,
        commitB: ObjectId?
    ): List<FileDiff> {
        class MultipleRepositoryObjectReader(
            private val readers: MutableCollection<ObjectReader> = HashSet()
        ) : ObjectReader() {
            override fun newReader(): ObjectReader {
                return MultipleRepositoryObjectReader(readers)
            }

            fun addObjectReader(reader: ObjectReader) {
                readers.add(reader)
            }

            override fun resolve(id: AbbreviatedObjectId): Collection<ObjectId> {
                val result = HashSet<ObjectId>()
                for (reader in readers) {
                    result.addAll(reader.resolve(id))
                }
                return result
            }

            override fun open(objectId: AnyObjectId, typeHint: Int): ObjectLoader? {
                for (reader in readers) {
                    if (reader.has(objectId, typeHint)) {
                        return reader.open(objectId, typeHint)
                    }
                }
                return null
            }

            override fun getShallowCommits(): Set<ObjectId> {
                val union = HashSet<ObjectId>()
                for (reader in readers) {
                    union.addAll(reader.shallowCommits)
                }
                return union
            }

            override fun close() {
                // Do nothing
            }
        }

        val reader = MultipleRepositoryObjectReader()
        reader.addObjectReader(repoA.newObjectReader())
        reader.addObjectReader(repoB.newObjectReader())

        val fakeRepo = object : Repository(BaseRepositoryBuilder<Nothing, Nothing>()) {
            override fun create(bare: Boolean) {
                throw UnsupportedOperationException()
            }
            override fun getObjectDatabase(): ObjectDatabase {
                throw UnsupportedOperationException()
            }
            override fun getRefDatabase(): RefDatabase {
                throw UnsupportedOperationException()
            }
            override fun getConfig(): StoredConfig {
                return repoA.config
            }
            override fun createAttributesNodeProvider(): AttributesNodeProvider {
                return object : AttributesNodeProvider {
                    val emptyAttributesNode = object : AttributesNode(emptyList()) {
                        override fun parse(input: InputStream) {}
                    }
                    override fun getInfoAttributesNode(): AttributesNode {
                        return emptyAttributesNode
                    }
                    override fun getGlobalAttributesNode(): AttributesNode {
                        return emptyAttributesNode
                    }
                }
            }
            override fun scanForRepoChanges() {
                throw UnsupportedOperationException()
            }
            override fun notifyIndexChanged(changed: Boolean) {}
            override fun getReflogReader(refName: String): ReflogReader {
                throw UnsupportedOperationException()
            }
            override fun newObjectReader(): ObjectReader {
                return reader
            }
            override fun getIdentifier(): String {
                return "fake"
            }
        }

        val formatter = DiffFormatter(NullOutputStream.INSTANCE)
        formatter.setRepository(fakeRepo)
        formatter.isDetectRenames = true

        var treeA: RevTree? = null
        var treeB: RevTree? = null

        val treeParserA = if (commitA != null) {
            treeA = RevWalk(repoA).parseTree(commitA)
            val p = CanonicalTreeParser()
            p.reset(reader, treeA)
            p
        } else {
            EmptyTreeIterator()
        }

        val treeParserB = if (commitB != null) {
            treeB = RevWalk(repoB).parseTree(commitB)
            val p = CanonicalTreeParser()
            p.reset(reader, treeB)
            p
        } else {
            EmptyTreeIterator()
        }

        val result = ArrayList<FileDiff>()
        var totalSize = 0
        var totalLines = 0

        val diffs = formatter.scan(treeParserA, treeParserB)
        for (diff in diffs) {
            val fileDiff = FileDiff()
            fileDiff.commitA = commitA?.name
            fileDiff.commitB = commitB?.name
            fileDiff.changeType = diff.changeType
            fileDiff.oldMode = diff.oldMode
            fileDiff.newMode = diff.newMode

            val pathA = diff.getPath(DiffEntry.Side.OLD)
            val pathB = diff.getPath(DiffEntry.Side.NEW)

            var rawA: ByteArray? = null
            if (treeA != null && listOf(
                    DiffEntry.ChangeType.DELETE,
                    DiffEntry.ChangeType.MODIFY,
                    DiffEntry.ChangeType.RENAME,
                    DiffEntry.ChangeType.COPY
                ).contains(diff.changeType)) {
                val t1 = TreeWalk.forPath(repoA, pathA, treeA)
                if (t1 != null) {
                    val blobA = t1.getObjectId(0)
                    fileDiff.pathA = pathA
                    try {
                        rawA = repoA.open(blobA).bytes
                        fileDiff.isBinaryA = RawText.isBinary(rawA)
                        if (fileDiff.isBinaryA) {
                            fileDiff.a = null
                        } else {
                            val charsetStr = FileUtil.detectCharset(rawA)
                            val str = String(rawA, Charset.forName(charsetStr))
                            fileDiff.a = RawText(str.toByteArray(StandardCharsets.UTF_8))
                        }
                    } catch (e: LargeObjectException) {
                        fileDiff.addError(FileDiff.Error.A_SIZE_EXCEEDED)
                    }
                }
            }

            var rawB: ByteArray? = null
            if (treeB != null && listOf(
                    DiffEntry.ChangeType.ADD,
                    DiffEntry.ChangeType.MODIFY,
                    DiffEntry.ChangeType.RENAME,
                    DiffEntry.ChangeType.COPY
                ).contains(diff.changeType)) {
                val t2 = TreeWalk.forPath(repoB, pathB, treeB)
                if (t2 != null) {
                    val blobB = t2.getObjectId(0)
                    fileDiff.pathB = pathB
                    try {
                        rawB = repoB.open(blobB).bytes
                        fileDiff.isBinaryB = RawText.isBinary(rawB)
                        if (fileDiff.isBinaryB) {
                            fileDiff.b = null
                        } else {
                            val charsetStr = FileUtil.detectCharset(rawB)
                            val str = String(rawB, Charset.forName(charsetStr))
                            fileDiff.b = RawText(str.toByteArray(StandardCharsets.UTF_8))
                        }
                    } catch (e: LargeObjectException) {
                        fileDiff.addError(FileDiff.Error.B_SIZE_EXCEEDED)
                    }
                }
            }

            val diffFileLimit = 1000
            val diffSizeLimit = 1000000
            val diffLineLimit = 20000

            if (totalSize > diffSizeLimit || totalLines > diffLineLimit) {
                fileDiff.addError(FileDiff.Error.OTHERS_SIZE_EXCEEDED)
                result.add(fileDiff)
                continue
            }

            val isModifyOrRename = listOf(
                DiffEntry.ChangeType.MODIFY,
                DiffEntry.ChangeType.RENAME
            ).contains(diff.changeType)

            if (fileDiff.a != null && fileDiff.b != null && !(fileDiff.isBinaryA || fileDiff.isBinaryB) && isModifyOrRename) {
                val diffAlgorithm = DiffAlgorithm.getAlgorithm(
                    repoB.config.getEnum(
                        ConfigConstants.CONFIG_DIFF_SECTION,
                        null,
                        ConfigConstants.CONFIG_KEY_ALGORITHM,
                        DiffAlgorithm.SupportedAlgorithm.HISTOGRAM
                    )
                )
                fileDiff.editList = diffAlgorithm.diff(RawTextComparator.DEFAULT, fileDiff.a, fileDiff.b)
                val hunks = fileDiff.getHunks()
                if (hunks != null) {
                    totalSize += hunks.totalSize
                    totalLines += hunks.lines
                }
            }

            if (fileDiff.b != null && !fileDiff.isBinaryB && diff.changeType == DiffEntry.ChangeType.ADD) {
                totalLines += fileDiff.b!!.size()
                if (rawB != null) {
                    totalSize += rawB.size
                }
            }

            if (fileDiff.a != null && !fileDiff.isBinaryA && diff.changeType == DiffEntry.ChangeType.DELETE) {
                totalLines += fileDiff.a!!.size()
                if (rawA != null) {
                    totalSize += rawA.size
                }
            }

            if (result.size > diffFileLimit) {
                break
            }

            result.add(fileDiff)
        }

        return result
    }

    override fun getHistory(pageNum: Int, pageSize: Int, untilRev: String?, path: String?): List<Commit> {
        return useRepository { repo ->
            val git = Git(repo)
            val logCommand = git.log()
            val rev = untilRev ?: "HEAD"
            val objectId = repo.resolve(rev)
            if (objectId != null) {
                // annotated 태그는 ref가 커밋이 아니라 태그 오브젝트를 직접 가리켜, 그 ObjectId를
                // LogCommand.add()에 그대로 넘기면 IncorrectObjectTypeException("not a commit")이
                // 난다 — 이 파일의 다른 모든 resolve() 호출부는 RevWalk.parseCommit()/parseTree()로
                // peel(태그 오브젝트를 실제 커밋까지 따라가는 것)한 뒤 쓰는데 여기만 빠져있었다
                // (lightweight 태그/브랜치는 ref가 커밋을 곧바로 가리켜 우연히 통과했다).
                logCommand.add(RevWalk(repo).parseCommit(objectId))
            }
            if (!path.isNullOrEmpty()) {
                logCommand.addPath(path)
            }
            val commits = logCommand.setSkip(pageNum * pageSize).setMaxCount(pageSize).call()
            commits.map { GitCommit(it, userResolver, gpgVerifier) }
        }
    }

    override fun getCommit(rev: String): Commit? {
        return useRepository { repo ->
            val objectId = repo.resolve(rev) ?: return@useRepository null
            val revWalk = RevWalk(repo)
            val commit = revWalk.parseCommit(objectId)
            GitCommit(commit, userResolver, gpgVerifier)
        }
    }

    override fun getRefNames(): List<String> {
        return useRepository { repo ->
            repo.refDatabase.getRefsByPrefix(Constants.R_HEADS).map { it.name }
        }
    }

    override fun isFile(path: String): Boolean {
        return isFile(path, "HEAD")
    }

    override fun isFile(path: String, revStr: String): Boolean {
        return useRepository { repo ->
            val objectId = repo.resolve(revStr) ?: return@useRepository false
            val revWalk = RevWalk(repo)
            val commit = revWalk.parseCommit(objectId)
            val treeWalk = TreeWalk.forPath(repo, path, commit.tree)
            treeWalk != null && !treeWalk.isSubtree
        }
    }

    override fun renameTo(projectName: String): Boolean {
        return move(ownerName, this.projectName, ownerName, projectName)
    }

    override fun getDefaultBranch(): String {
        return useRepository { repo ->
            repo.fullBranch ?: "refs/heads/master"
        }
    }

    override fun setDefaultBranch(target: String) {
        useRepository { repo ->
            val refUpdate = repo.updateRef(Constants.HEAD)
            val targetRef = if (target.startsWith("refs/")) target else "refs/heads/$target"
            refUpdate.link(targetRef)
        }
    }

    override fun getBranches(): List<GitBranch> {
        return useRepository { repo ->
            val git = Git(repo)
            val refs = git.branchList().call()
            val revWalk = RevWalk(repo)
            refs.map { ref ->
                val commit = revWalk.parseCommit(ref.objectId)
                val gitCommit = GitCommit(commit, userResolver, gpgVerifier)
                val user = userResolver(null, gitCommit.getCommitterEmail())
                GitBranch(ref.name, gitCommit, user)
            }
        }
    }

    override fun getHeadBranch(): GitBranch? {
        return useRepository { repo ->
            val headRef = repo.exactRef(Constants.HEAD) ?: return@useRepository null
            val targetRef = if (headRef.isSymbolic) headRef.target else headRef
            val objectId = targetRef.objectId ?: return@useRepository null
            val revWalk = RevWalk(repo)
            val commit = revWalk.parseCommit(objectId)
            val gitCommit = GitCommit(commit, userResolver, gpgVerifier)
            val user = userResolver(null, gitCommit.getCommitterEmail())
            GitBranch(targetRef.name, gitCommit, user)
        }
    }

    override fun deleteBranch(branchName: String) {
        useRepository { repo ->
            Git(repo).use { git ->
                git.branchDelete()
                    .setBranchNames(branchName)
                    .setForce(true)
                    .call()
            }
        }
    }

    override fun createBranch(branchName: String, startPoint: String) {
        useRepository { repo ->
            Git(repo).use { git ->
                git.branchCreate()
                    .setName(branchName.removePrefix("refs/heads/"))
                    .setStartPoint(startPoint)
                    .call()
            }
        }
    }

    override fun getTagNames(): List<String> {
        return useRepository { repo ->
            repo.refDatabase.getRefsByPrefix(Constants.R_TAGS).map { it.name }
        }
    }

    override fun getTags(): List<GitTag> {
        return useRepository { repo ->
            val git = Git(repo)
            val refs = git.tagList().call()
            val revWalk = RevWalk(repo)
            refs.map { ref ->
                // annotated 태그는 ref가 태그 오브젝트(RevTag)를 가리키고, lightweight 태그는 ref가
                // 커밋을 곧바로 가리킨다 — parseAny()로 실제 오브젝트 타입을 확인해 구분한다
                // (parseCommit()은 어느 쪽이든 자동으로 peel해버려 이 구분 자체가 불가능해진다).
                val any = revWalk.parseAny(ref.objectId)
                if (any is RevTag) {
                    val commit = revWalk.parseCommit(any.`object`)
                    val gitCommit = GitCommit(commit, userResolver, gpgVerifier)
                    val taggerIdent = any.taggerIdent
                    val user = taggerIdent?.let { userResolver(null, it.emailAddress) }
                    val message = any.fullMessage?.trim()?.takeIf { it.isNotEmpty() }
                    GitTag(ref.name, gitCommit, user, message, annotated = true)
                } else {
                    val commit = revWalk.parseCommit(ref.objectId)
                    val gitCommit = GitCommit(commit, userResolver, gpgVerifier)
                    val user = userResolver(null, gitCommit.getCommitterEmail())
                    GitTag(ref.name, gitCommit, user, message = null, annotated = false)
                }
            }
        }
    }

    override fun deleteTag(tagName: String) {
        useRepository { repo ->
            Git(repo).use { git ->
                git.tagDelete()
                    .setTags(tagName.removePrefix(Constants.R_TAGS))
                    .call()
            }
        }
    }

    override fun createTag(tagName: String, startPoint: String, message: String?, taggerName: String?, taggerEmail: String?) {
        useRepository { repo ->
            Git(repo).use { git ->
                val revWalk = RevWalk(repo)
                val objectId = repo.resolve(startPoint)
                    ?: throw IllegalArgumentException("존재하지 않는 시작점입니다: $startPoint")
                val commit = revWalk.parseCommit(objectId)

                val tagCommand = git.tag()
                    .setName(tagName.removePrefix(Constants.R_TAGS))
                    .setObjectId(commit)
                    .setForceUpdate(false)

                if (message != null) {
                    tagCommand.setAnnotated(true)
                    tagCommand.setMessage(message)
                    tagCommand.setTagger(
                        PersonIdent(taggerName ?: "yona", taggerEmail ?: "yona@yona.io")
                    )
                } else {
                    tagCommand.setAnnotated(false)
                }

                tagCommand.call()
            }
        }
    }

    override fun getParentCommitOf(commitId: String): Commit? {
        return useRepository { repo ->
            val objectId = repo.resolve(commitId) ?: return@useRepository null
            val revWalk = RevWalk(repo)
            val commit = revWalk.parseCommit(objectId)
            if (commit.parentCount > 0) {
                GitCommit(commit.getParent(0), userResolver, gpgVerifier)
            } else {
                null
            }
        }
    }

    override fun isEmpty(): Boolean {
        return useRepository { repo ->
            repo.resolve("HEAD") == null
        }
    }

    override fun move(srcProjectOwner: String, srcProjectName: String, destProjectOwner: String, destProjectName: String): Boolean {
        val rootDir = File(baseDir)
        val src = File(rootDir, "$srcProjectOwner/$srcProjectName.git")
        val dest = File(rootDir, "$destProjectOwner/$destProjectName.git")
        src.setWritable(true)

        return try {
            if (src.exists()) {
                if (!dest.parentFile.exists()) {
                    dest.parentFile.mkdirs()
                }
                Files.move(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            true
        } catch (e: IOException) {
            false
        }
    }

    override fun getDirectory(): File {
        return File(File(baseDir), "$ownerName/$projectName.git")
    }

    override fun getArchive(os: OutputStream, branchName: String) {
        useRepository { repo ->
            val objectId = repo.resolve(branchName) ?: return@useRepository
            val revWalk = RevWalk(repo)
            val commit = revWalk.parseCommit(objectId)
            val tree = commit.tree

            val treeWalk = TreeWalk(repo)
            treeWalk.addTree(tree)
            treeWalk.isRecursive = true

            ZipOutputStream(os).use { zos ->
                while (treeWalk.next()) {
                    val path = treeWalk.pathString
                    val fileMode = treeWalk.getFileMode(0)

                    if (fileMode.equals(FileMode.REGULAR_FILE) ||
                        fileMode.equals(FileMode.EXECUTABLE_FILE)) {

                        val fileObjectId = treeWalk.getObjectId(0)
                        val loader = repo.open(fileObjectId)

                        val entry = ZipEntry(path)
                        zos.putNextEntry(entry)
                        loader.copyTo(zos)
                        zos.closeEntry()
                    }
                }
            }
        }
    }

    override fun getBlobId(revision: String, path: String): String? {
        if (revision.isEmpty()) return null
        return useRepository { repo ->
            val objectId = repo.resolve(revision) ?: return@useRepository null
            val revWalk = RevWalk(repo)
            val tree = revWalk.parseTree(objectId)
            val treeWalk = TreeWalk.forPath(repo, path, tree) ?: return@useRepository null
            treeWalk.getObjectId(0).name
        }
    }
}
