package com.github.yonaprojects.yona.domain.vcs

import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode
import com.github.yonaprojects.yona.domain.user.User
import org.tmatesoft.svn.core.*
import org.tmatesoft.svn.core.io.SVNRepository
import org.tmatesoft.svn.core.io.SVNRepositoryFactory
import org.tmatesoft.svn.core.wc.SVNClientManager
import org.tmatesoft.svn.core.wc.SVNRevision
import java.io.*
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.*

class SvnRepository(
    private val ownerName: String,
    private val projectName: String,
    private val baseDir: String,
    private val userResolver: (String) -> User?
) : PlayRepository {

    private val objectMapper = ObjectMapper()

    private fun getSVNRepository(): SVNRepository {
        val svnURL = SVNURL.fromFile(getDirectory())
        return SVNRepositoryFactory.create(svnURL)
    }

    override fun create() {
        val dir = getDirectory()
        if (!dir.exists()) {
            dir.mkdirs()
        }
        SVNRepositoryFactory.createLocalRepository(dir, true, false)
    }

    override fun isIntermediateFolder(path: String): Boolean {
        return false
    }

    override fun getMetaDataFromPath(path: String): ObjectNode? {
        return getMetaDataFromPath(-1, path)
    }

    override fun getMetaDataFromPath(branch: String, path: String): ObjectNode? {
        val revisionNumber = try {
            branch.toInt()
        } catch (e: NumberFormatException) {
            -1
        }
        return getMetaDataFromPath(revisionNumber, path)
    }

    private fun getMetaDataFromPath(revision: Int, path: String): ObjectNode? {
        val repository = getSVNRepository()
        val nodeKind = repository.checkPath(path, revision.toLong())

        if (nodeKind == SVNNodeKind.DIR) {
            val result = objectMapper.createObjectNode()
            val listData = objectMapper.createObjectNode()
            val prop = SVNProperties()
            val entries = ArrayList<SVNDirEntry>()
            repository.getDir(path, revision.toLong(), prop, SVNDirEntry.DIRENT_ALL, entries)

            result.put("type", "folder")

            for (entry in entries) {
                val data = objectMapper.createObjectNode()
                val author = entry.author ?: ""
                val user = userResolver(author)
                val commitTime = entry.date.time

                data.put("type", if (entry.kind == SVNNodeKind.DIR) "folder" else "file")
                data.put("msg", entry.commitMessage ?: "")
                data.put("author", author)
                data.put("avatar", getAvatar(user))
                // 테스트 커버리지 도달 불가(COVERAGE_BACKLOG.md [i] 참고): User.name/loginId가 non-null
                // String 타입이라 user가 non-null일 때 이 elvis의 null 분기 자체가 성립할 수 없다.
                data.put("userName", user?.name ?: "")
                data.put("userLoginId", user?.loginId ?: "")
                data.put("createdDate", commitTime)
                data.put("commitMessage", entry.commitMessage ?: "")
                data.put("commiter", author)
                data.put("commitDate", commitTime)
                data.put("commitId", entry.revision)
                data.put("commitUrl", "/$ownerName/$projectName/commit/${entry.revision}")
                data.put("size", entry.size)

                listData.set(entry.name, data)
            }
            result.set("data", listData)
            return result
        } else if (nodeKind == SVNNodeKind.FILE) {
            return fileAsJson(path, repository, revision.toLong())
        }
        return null
    }

    private fun fileAsJson(path: String, repository: SVNRepository, revision: Long): ObjectNode {
        val baos = ByteArrayOutputStream()
        val prop = SVNProperties()
        repository.getFile(path, revision, prop, baos)
        val entry = repository.info(path, revision)
        val size = entry.size
        var isBinary = false
        var mimeType = "application/octet-stream"
        var data: String? = null

        val bytes = baos.toByteArray()
        if (size > PlayRepository.MAX_FILE_SIZE_CAN_BE_VIEWED) {
            isBinary = true
        } else {
            isBinary = bytes.contains(0)
            if (!isBinary) {
                data = String(bytes, StandardCharsets.UTF_8)
            }
            // 테스트 커버리지 도달 불가(COVERAGE_BACKLOG.md [i] 참고): Files.probeContentType()의 결과가
            // null/non-null 어느 쪽이든 OS의 파일타입 감지 구현체(콘텐츠 스니핑)에 좌우되는 플랫폼
            // 종속적 동작이라 이식성 있게 강제할 수 없고, catch 블록도 임시파일 I/O가 정상 환경에서
            // 실패할 이유가 없어 도달 불가.
            mimeType = try {
                val fileTmp = Files.createTempFile("yona-mime", null)
                Files.write(fileTmp, bytes)
                val detected = Files.probeContentType(fileTmp)
                Files.delete(fileTmp)
                detected ?: "application/octet-stream"
            } catch (e: Exception) {
                "application/octet-stream"
            }
        }

        val author = prop.getStringValue(SVNProperty.LAST_AUTHOR) ?: ""
        val user = userResolver(author)
        val commitDateStr = prop.getStringValue(SVNProperty.COMMITTED_DATE)
        // 테스트 커버리지 도달 불가(COVERAGE_BACKLOG.md [i] 참고): SVN 서버는 커밋마다 svn:date revprop을
        // 항상 유효한 ISO8601 형식으로 기록하므로 commitDateStr==null(else 분기)이나 Instant.parse 실패
        // (catch 분기)는 정상 SVNKit 클라이언트-서버 흐름에서 재현 불가.
        val commitTime = try {
            if (commitDateStr != null) {
                Instant.parse(commitDateStr).toEpochMilli()
            } else {
                entry.date.time
            }
        } catch (e: Exception) {
            entry.date.time
        }

        val result = objectMapper.createObjectNode()
        result.put("type", "file")
        result.put("revisionNo", prop.getStringValue(SVNProperty.COMMITTED_REVISION))
        result.put("author", author)
        result.put("avatar", getAvatar(user))
        // 테스트 커버리지 도달 불가(위 getMetaDataFromPath의 동일 패턴 참고): User.name/loginId가 non-null.
        result.put("userName", user?.name ?: "")
        result.put("userLoginId", user?.loginId ?: "")
        result.put("createdDate", commitTime)
        result.put("commitMessage", entry.commitMessage ?: "")
        result.put("commiter", author)
        result.put("size", size)
        result.put("isBinary", isBinary)
        result.put("mimeType", mimeType)
        result.put("data", data ?: "")

        return result
    }

    private fun getAvatar(user: User?): String {
        return "/images/default-avatar-34.png"
    }

    override fun getRawFile(revision: String, path: String): ByteArray {
        val revId = if (revision == "HEAD") -1L else revision.toLong()
        val repository = getSVNRepository()

        if (repository.checkPath(path, revId) != SVNNodeKind.FILE) {
            throw FileNotFoundException()
        }

        val baos = ByteArrayOutputStream()
        repository.getFile(path, revId, null, baos)
        return baos.toByteArray()
    }

    override fun delete() {
        val dir = getDirectory()
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }

    override fun getPatch(commitId: String): String {
        val rev = commitId.toLong()
        return getPatch(rev - 1, rev)
    }

    override fun getPatch(revA: String, revB: String): String {
        return getPatch(revA.toLong(), revB.toLong())
    }

    private fun getPatch(revA: Long, revB: Long): String {
        val svnURL = SVNURL.fromFile(getDirectory())
        val clientManager = SVNClientManager.newInstance()
        val diffClient = clientManager.diffClient

        val byteArrayOutputStream = ByteArrayOutputStream()
        diffClient.doDiff(
            svnURL, null, SVNRevision.create(revA), SVNRevision.create(revB),
            SVNDepth.INFINITY, true, byteArrayOutputStream
        )
        return byteArrayOutputStream.toString(StandardCharsets.UTF_8.name())
    }

    override fun getDiff(commitId: String): List<Any> {
        throw UnsupportedOperationException()
    }

    override fun getDiff(revA: String, revB: String): List<Any> {
        throw UnsupportedOperationException()
    }

    override fun getHistory(pageNum: Int, pageSize: Int, untilRev: String?, path: String?): List<Commit> {
        val svnURL = SVNURL.fromFile(getDirectory())
        val repository = SVNRepositoryFactory.create(svnURL)

        val paths = arrayOf(path ?: "/")
        val latestRevision = repository.latestRevision
        val startRevision = latestRevision - (pageNum * pageSize)
        var endRevision = startRevision - pageSize
        if (endRevision < 1) {
            endRevision = 1
        }

        if (startRevision < endRevision) {
            return emptyList()
        }

        val result = ArrayList<Commit>()
        val logEntries = ArrayList<SVNLogEntry>()
        repository.log(paths, logEntries, startRevision, endRevision, false, false)
        for (entry in logEntries) {
            result.add(SvnCommit(entry, userResolver))
        }
        return result
    }

    override fun getCommit(rev: String): Commit? {
        val revNum = rev.toLong()
        val paths = arrayOf("/")
        val svnURL = SVNURL.fromFile(getDirectory())
        val repository = SVNRepositoryFactory.create(svnURL)

        val logEntries = ArrayList<SVNLogEntry>()
        repository.log(paths, logEntries, revNum, revNum, false, false)
        // 테스트 커버리지 도달 불가(COVERAGE_BACKLOG.md [i] 참고): 유효한 단일 리비전 요청은 항상
        // 로그 엔트리를 1개 이상 반환하고(리비전 0 포함), 범위 밖 요청은 이 반복문에 도달하기 전에
        // SVNException을 던진다(직접 확인) — "0회 반복 후 null 반환"은 정상 SVNKit 흐름에서 불가능.
        for (entry in logEntries) {
            return SvnCommit(entry, userResolver)
        }
        return null
    }

    override fun getRefNames(): List<String> {
        return listOf(SVNRevision.HEAD.name)
    }

    override fun isFile(path: String): Boolean {
        val repository = getSVNRepository()
        return isFile(path, repository.latestRevision)
    }

    override fun isFile(path: String, revStr: String): Boolean {
        return isFile(path, revStr.toLong())
    }

    fun isFile(path: String, rev: Long): Boolean {
        return getSVNRepository().checkPath(path, rev) == SVNNodeKind.FILE
    }

    override fun renameTo(projectName: String): Boolean {
        return move(ownerName, this.projectName, ownerName, projectName)
    }

    override fun getDefaultBranch(): String {
        return "HEAD"
    }

    override fun setDefaultBranch(target: String) {}

    override fun getBranches(): List<GitBranch> = emptyList()

    override fun getHeadBranch(): GitBranch? = null

    override fun deleteBranch(branchName: String) {}

    override fun createBranch(branchName: String, startPoint: String) {}

    // PR 코드리뷰(P1-20 isOutdated)는 Git 전용 기능이라 SVN에서는 지원하지 않는다.
    override fun getBlobId(revision: String, path: String): String? = null

    override fun getParentCommitOf(commitId: String): Commit? {
        val rev = commitId.toLong() - 1
        return try {
            getCommit(rev.toString())
        } catch (e: Exception) {
            null
        }
    }

    override fun isEmpty(): Boolean {
        val dir = getDirectory()
        if (!dir.exists()) {
            return true
        }
        var repository: SVNRepository? = null
        try {
            val svnURL = SVNURL.fromFile(dir)
            repository = SVNRepositoryFactory.create(svnURL)
            return repository.latestRevision == 0L
        } catch (e: SVNException) {
            // 테스트 커버리지 도달 불가(COVERAGE_BACKLOG.md [i] 참고, javap로 바이트코드 확인): 이 catch의
            // finally 인라인 복사본에서 repository가 non-null인 채로 여기 도달하려면 저장소 열기(create())는
            // 성공하고 이후 getLatestRevision() 호출만 실패하는 손상 상태가 필요한데, 정상 SVNKit API로는
            // 재현할 수 없다.
            return true
        } finally {
            repository?.closeSession()
        }
    }

    override fun move(srcProjectOwner: String, srcProjectName: String, destProjectOwner: String, destProjectName: String): Boolean {
        val rootDir = File(baseDir)
        val src = File(rootDir, "$srcProjectOwner/$srcProjectName")
        val dest = File(rootDir, "$destProjectOwner/$destProjectName")
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
        return File(File(baseDir), "$ownerName/$projectName")
    }

    override fun getArchive(os: OutputStream, branchName: String) {
        // Not implemented (same as legacy Yona)
    }
}
