package com.github.yonaprojects.yona.domain.vcs

import com.github.yonaprojects.yona.domain.user.User
import io.github.search5.hg4j.api.CatCommand
import io.github.search5.hg4j.api.Hg
import io.github.search5.hg4j.api.LogCommand
import io.github.search5.hg4j.lib.NodeId
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption

// yona-wiki P3-12(Mercurial 지원) 1라운드 — search5/hg4j(형제 디렉터리, includeBuild로 연결)로 감싸는
// PlayRepository 구현. GitRepository/SvnRepository와 동일한 (ownerName, projectName, baseDir,
// userResolver) 생성자 형태를 따른다.
//
// **범위(1라운드)**: create/isEmpty/getDefaultBranch/getMetaDataFromPath/getRawFile/getHistory/
// getCommit/getParentCommitOf/move/renameTo — "저장소를 만들고 커밋을 탐색"하는 핵심 경로만 실제
// 구현한다. 브랜치/태그 CRUD, diff/patch 생성, archive는 SvnRepository의 선례(대응 개념이 없거나
// 아직 다루지 않은 기능은 빈 값/no-op/UnsupportedOperationException으로 명시)를 그대로 따라 2라운드
// 이후로 미룬다 — Mercurial의 "branch"는 git과 달리 커밋에 영구히 새겨지는 개념이라(삭제가 아니라
// "close"만 가능) 잘못된 매핑을 서둘러 확정하지 않기 위함(계획 문서 참고).
//
// **bare 저장소 개념 없음**: GitRepository는 서버 호스팅을 위해 bare(작업 디렉터리 없는) 저장소를
// 쓰지만, Mercurial은 애초에 그런 구분이 없다 — `hg serve`도 일반(작업 디렉터리가 있는) 저장소를
// 그대로 서빙한다. hg4j의 add()/commit() 포셀린 API도 디스크의 실제 파일을 스캔하는 구조라, 여기서도
// 그냥 일반 저장소로 init한다.
class HgRepository(
    private val ownerName: String,
    private val projectName: String,
    private val baseDir: String,
    private val userResolver: (String?, String?) -> User?
) : PlayRepository {

    private val objectMapper = ObjectMapper()

    private fun <T> useHg(block: (Hg) -> T): T {
        return Hg.open(getDirectory()).use(block)
    }

    override fun create() {
        val dir = getDirectory()
        if (!dir.exists()) {
            dir.mkdirs()
        }
        Hg.init().setDirectory(dir).call()
    }

    override fun isIntermediateFolder(path: String): Boolean = false

    override fun getMetaDataFromPath(path: String): ObjectNode? = getMetaDataFromPath("tip", path)

    override fun getMetaDataFromPath(branch: String, path: String): ObjectNode? {
        return useHg { hg ->
            val revision = resolveRevisionNumber(hg, branch) ?: return@useHg null
            val entries = hg.tree().setRevision(revision).call()
            val normalizedPath = path.trim('/')

            val exactFile = entries.find { it.path == normalizedPath }
            if (exactFile != null) {
                return@useHg fileAsJson(hg, revision, normalizedPath)
            }

            // 정확히 일치하는 파일이 없으면 폴더로 취급 — 그 경로를 prefix로 갖는 항목들에서
            // "바로 아래 자식" 레벨만 추려 디렉터리 목록을 구성한다(hg4j의 tree()는 SVN의
            // getDir()/Git의 TreeWalk 서브트리 탐색과 달리 revision 전체의 평평한 매니페스트만
            // 주므로, 클라이언트 쪽에서 경로 접두어 기준으로 그룹핑한다).
            val prefix = if (normalizedPath.isEmpty()) "" else "$normalizedPath/"
            val childNames = LinkedHashSet<String>()
            for (entry in entries) {
                if (!entry.path.startsWith(prefix)) continue
                val rest = entry.path.removePrefix(prefix)
                if (rest.isEmpty()) continue
                childNames.add(rest.substringBefore('/'))
            }
            if (childNames.isEmpty() && normalizedPath.isNotEmpty()) {
                // prefix에 해당하는 항목이 하나도 없으면 존재하지 않는 경로.
                return@useHg null
            }

            val result = objectMapper.createObjectNode()
            val listData = objectMapper.createObjectNode()
            result.put("type", "folder")

            for (childName in childNames) {
                val childPath = if (normalizedPath.isEmpty()) childName else "$normalizedPath/$childName"
                val isFileChild = entries.any { it.path == childPath }
                val lastCommit = hg.log().setStartRev(revision.toString()).setFollowPath(childPath).call().firstOrNull()

                val data = objectMapper.createObjectNode()
                data.put("type", if (isFileChild) "file" else "folder")
                if (lastCommit != null) {
                    val commit = HgCommit(lastCommit, userResolver)
                    val user = commit.getAuthor()
                    val commitTime = commit.getAuthorDate()?.time ?: 0L
                    data.put("msg", commit.getShortMessage())
                    data.put("author", commit.getAuthorName() ?: "")
                    data.put("avatar", "/images/default-avatar-34.png")
                    data.put("userName", user?.name ?: "")
                    data.put("userLoginId", user?.loginId ?: "")
                    data.put("createdDate", commitTime)
                    data.put("commitMessage", commit.getShortMessage())
                    data.put("commiter", commit.getCommitterName() ?: "")
                    data.put("commitDate", commitTime)
                    data.put("commitId", commit.getId())
                    data.put("commitUrl", "/$ownerName/$projectName/commit/${commit.getId()}")
                }
                if (isFileChild) {
                    data.put("size", hg.cat().setFile(childPath).setRevision(revision.toString()).call().size)
                }
                listData.set(childName, data)
            }
            result.set("data", listData)
            result
        }
    }

    private fun fileAsJson(hg: Hg, revision: Int, path: String): ObjectNode {
        val bytes = hg.cat().setFile(path).setRevision(revision.toString()).call()
        val lastCommit = hg.log().setStartRev(revision.toString()).setFollowPath(path).call().firstOrNull()
        val commit = lastCommit?.let { HgCommit(it, userResolver) }
        val user = commit?.getAuthor()

        val isBinary = bytes.contains(0)
        val data = if (!isBinary) String(bytes, StandardCharsets.UTF_8) else null

        val result = objectMapper.createObjectNode()
        result.put("type", "file")
        result.put("revisionNo", commit?.getId() ?: revision.toString())
        result.put("author", commit?.getAuthorName() ?: "")
        result.put("avatar", "/images/default-avatar-34.png")
        result.put("userName", user?.name ?: "")
        result.put("userLoginId", user?.loginId ?: "")
        result.put("createdDate", commit?.getAuthorDate()?.time ?: 0L)
        result.put("commitMessage", commit?.getShortMessage() ?: "")
        result.put("commiter", commit?.getCommitterName() ?: "")
        result.put("size", bytes.size)
        result.put("isBinary", isBinary)
        result.put("mimeType", if (isBinary) "application/octet-stream" else "text/plain")
        result.put("data", data ?: "")
        return result
    }

    override fun getRawFile(revision: String, path: String): ByteArray {
        return useHg { hg ->
            val revNum = resolveRevisionNumber(hg, revision) ?: throw FileNotFoundException(path)
            try {
                hg.cat().setFile(path).setRevision(revNum.toString()).call()
            } catch (e: IOException) {
                throw FileNotFoundException(path)
            }
        }
    }

    override fun delete() {
        val dir = getDirectory()
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }

    // yona-wiki P3-12 2라운드 과제 — hg4j의 DiffCommand로 통합 diff 생성 가능하나, Git/SVN이 만드는
    // patch 포맷(unified diff 텍스트)과의 정합을 이번 라운드에서 확정하지 않는다.
    override fun getPatch(commitId: String): String = throw UnsupportedOperationException()

    override fun getPatch(revA: String, revB: String): String = throw UnsupportedOperationException()

    override fun getDiff(commitId: String): List<Any> = throw UnsupportedOperationException()

    override fun getDiff(revA: String, revB: String): List<Any> = throw UnsupportedOperationException()

    override fun getHistory(pageNum: Int, pageSize: Int, untilRev: String?, path: String?): List<Commit> {
        return useHg { hg ->
            val logCommand: LogCommand = hg.log()
            if (untilRev != null) logCommand.setStartRev(untilRev)
            if (!path.isNullOrEmpty()) logCommand.setFollowPath(path)
            // hg4j의 log()는 페이지네이션 파라미터가 없어(전체를 한 번에 반환) 여기서 잘라낸다 —
            // 대형 저장소에서는 비효율적이라 2라운드에서 개선 검토 필요.
            logCommand.call()
                .drop(pageNum * pageSize)
                .take(pageSize)
                .map { HgCommit(it, userResolver) }
        }
    }

    override fun getCommit(rev: String): Commit? {
        return useHg { hg ->
            val revNum = resolveRevisionNumber(hg, rev) ?: return@useHg null
            hg.log().setStartRev(revNum.toString()).call()
                .find { it.revision == revNum }
                ?.let { HgCommit(it, userResolver) }
        }
    }

    // "tip"/정수 리비전 번호/40자 hex 노드ID 문자열을 실제 로컬 리비전 번호로 해석한다.
    private fun resolveRevisionNumber(hg: Hg, rev: String): Int? {
        val allCommits = hg.log().call()
        if (allCommits.isEmpty()) return null
        return when {
            rev.isEmpty() || rev == "tip" || rev == "HEAD" || rev == "default" -> allCommits.first().revision
            rev.toIntOrNull() != null -> rev.toInt().takeIf { it in allCommits.indices }
            rev.length == 40 -> allCommits.find { it.nodeId == NodeId.fromHex(rev) }?.revision
            else -> allCommits.find { it.nodeId.toString() == rev }?.revision
        }
    }

    // yona-wiki P3-12 2라운드 과제 — Mercurial의 ref는 git과 개념이 달라(브랜치=커밋에 영구히
    // 새겨짐, 북마크=git 브랜치에 더 가까운 이동 가능한 포인터) getRefNames()가 브랜치/북마크 중
    // 무엇을, 어떤 이름 규칙으로 노출할지는 별도 설계가 필요하다. 1라운드는 tip 하나만 노출.
    override fun getRefNames(): List<String> = listOf("tip")

    override fun isFile(path: String): Boolean {
        return useHg { hg ->
            val revNum = resolveRevisionNumber(hg, "tip") ?: return@useHg false
            hg.tree().setRevision(revNum).call().any { it.path == path.trim('/') }
        }
    }

    override fun isFile(path: String, revStr: String): Boolean {
        return useHg { hg ->
            val revNum = resolveRevisionNumber(hg, revStr) ?: return@useHg false
            hg.tree().setRevision(revNum).call().any { it.path == path.trim('/') }
        }
    }

    override fun renameTo(projectName: String): Boolean {
        return move(ownerName, this.projectName, ownerName, projectName)
    }

    // Mercurial 저장소의 "기본 브랜치"는 항상 이름이 "default"로 고정된 첫 브랜치다(git처럼 임의
    // 이름으로 바꿀 수 있는 개념이 아니다) — SvnRepository가 "HEAD" 고정을 반환하는 것과 동일한
    // 이유로 setDefaultBranch는 no-op.
    override fun getDefaultBranch(): String = "default"

    override fun setDefaultBranch(target: String) {}

    // yona-wiki P3-12 2라운드 과제(계획 문서 참고) — 아래 전부 SvnRepository의 선례(대응 개념
    // 없음/아직 미착수는 빈 값 또는 no-op)를 따른다.
    override fun getBranches(): List<GitBranch> = emptyList()

    override fun getHeadBranch(): GitBranch? = null

    override fun deleteBranch(branchName: String) {}

    override fun createBranch(branchName: String, startPoint: String) {}

    override fun getTagNames(): List<String> = emptyList()

    override fun getTags(): List<GitTag> = emptyList()

    override fun deleteTag(tagName: String) {}

    override fun createTag(tagName: String, startPoint: String, message: String?, taggerName: String?, taggerEmail: String?) {}

    override fun getBlobId(revision: String, path: String): String? = null

    override fun getParentCommitOf(commitId: String): Commit? {
        return useHg { hg ->
            val revNum = resolveRevisionNumber(hg, commitId) ?: return@useHg null
            if (revNum <= 0) return@useHg null
            hg.log().call().find { it.revision == revNum - 1 }?.let { HgCommit(it, userResolver) }
        }
    }

    override fun isEmpty(): Boolean {
        val dir = getDirectory()
        if (!dir.exists()) return true
        return try {
            useHg { hg -> hg.log().call().isEmpty() }
        } catch (e: Exception) {
            true
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

    // yona-wiki P3-12 2라운드 과제 — hg4j의 ArchiveCommand는 파일시스템 목적지(File)를 받는 구조라
    // PlayRepository의 스트림(OutputStream) 시그니처와 바로 맞지 않는다(임시 디렉터리 경유 변환이
    // 필요). SvnRepository도 동일하게 미구현("Not implemented (same as legacy Yona)")이라 그 선례를
    //따른다.
    override fun getArchive(os: OutputStream, branchName: String) {}
}
