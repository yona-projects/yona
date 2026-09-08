package com.github.yonaprojects.yona.domain.vcs

import com.github.yonaprojects.yona.domain.support.FileUtil
import com.github.yonaprojects.yona.domain.user.User
import io.github.search5.hg4j.api.CatCommand
import io.github.search5.hg4j.api.DiffCommand
import io.github.search5.hg4j.api.Hg
import io.github.search5.hg4j.api.LogCommand
import io.github.search5.hg4j.lib.NodeId
import org.eclipse.jgit.diff.DiffAlgorithm
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.RawText
import org.eclipse.jgit.diff.RawTextComparator
import org.eclipse.jgit.lib.FileMode
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.Charset
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
    private val userResolver: (String?, String?) -> User?,
    // yona-wiki P3-19 — 커밋 목록/상세 화면의 GPG Verified 배지 계산(GpgSignatureVerifier.
    // verify(NativeHgCommit)). GitRepository.kt와 동일한 패턴: 기본값(no-op, 항상 UNSIGNED)을 둬서
    // 기존 호출부가 그대로 동작하게 한다 — RepositoryService가 실제 구현을 주입한다.
    private val gpgVerifier: (io.github.search5.hg4j.api.HgCommit) -> com.github.yonaprojects.yona.domain.gpgkey.GpgVerificationStatus =
        { com.github.yonaprojects.yona.domain.gpgkey.GpgVerificationStatus.UNSIGNED }
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
                    val commit = HgCommit(lastCommit, userResolver, gpgVerifier)
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
        val commit = lastCommit?.let { HgCommit(it, userResolver, gpgVerifier) }
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

    // yona-wiki P3-12 2라운드 마무리(회고 2026-09-08/09) — GitRepository.getPatch()/getDiff()와
    // 동일한 아키텍처로 구현한다: hg4j의 DiffCommand(io.github.search5.hg4j.api.DiffCommand)는 두
    // 리비전의 매니페스트(git의 tree에 대응)를 비교해 변경된 경로+ChangeType(ADD/MODIFY/DELETE)
    // 목록을 이미 정확히 계산해준다(TreeWalk+ManifestTreeIterator, hg의 "hard part") — 이 목록만
    // 재사용하고, 각 파일의 old/new 콘텐츠 바이트는 CatCommand로 직접 가져와(HgRepository의 기존
    // fileAsJson()과 동일한 hg.cat().setFile(path).setRevision(rev) 패턴) JGit의 DiffAlgorithm/
    // RawText/EditList로 직접 라인 단위 diff를 계산한다 — DiffCommand 자신의 사전 렌더링된 유니파이드
    // diff 텍스트(getDiffContent())는 파싱해 되돌리지 않는다(왕복 변환은 우회로일 뿐). FileDiff가
    // JGit의 EditList/RawText 위에 지어져 있어(FileDiff.kt 상단 참고, hunk 분할/interestLine/사이즈
    // 제한 로직이 전부 진짜 EditList를 전제) 이 방식이 git 쪽과 완전히 동일한 소비자 계약
    // (getDiff()가 실제 List<FileDiff>를 반환 — PullRequestServiceImpl의 unchecked cast가 성립해야
    // 함)을 만족시키는 유일한 선택이다.
    //
    // "부모" 리비전 해석은 이 파일의 getParentCommitOf()/HgCommit.getParentCount()가 이미 채택한
    // 근사(리비전 번호 - 1)를 그대로 따른다 — hg4j의 공개 porcelain API에는 특정 changeset의 실제
    // parent1 리비전 번호를 노출하는 명령이 없다(ParentsCommand는 워킹 디렉터리의 부모만 노출).
    // revNum == 0(최초 커밋)일 때 revNum - 1 == -1이 되는데, 이는 hg4j의 ManifestTreeIterator/
    // DiffCommand 자체가 "매니페스트 없음(빈 트리)"으로 해석하는 정확히 그 sentinel 값과 일치한다
    // (ManifestTreeIterator.loadEntries()가 revision == "-1"에서 즉시 빈 목록을 반환) — git 쪽의
    // EmptyTreeIterator(첫 커밋의 부모)에 대응.
    override fun getPatch(commitId: String): String {
        return useHg { hg ->
            val revNum = resolveRevisionNumber(hg, commitId) ?: return@useHg ""
            val oldRevNum = if (revNum > 0) revNum - 1 else -1
            buildPatchText(hg, oldRevNum, revNum)
        }
    }

    override fun getPatch(revA: String, revB: String): String {
        return useHg { hg ->
            val oldRevNum = resolveRevisionNumber(hg, revA) ?: return@useHg ""
            val newRevNum = resolveRevisionNumber(hg, revB) ?: return@useHg ""
            buildPatchText(hg, oldRevNum, newRevNum)
        }
    }

    // revA/revB 중 하나가 존재하지 않는 리비전이면 GitRepository.getPatch()와 동일하게 빈 문자열을
    // 반환한다(getDiff()와 달리 "존재하지 않음 == 빈 트리"로 근사하지 않는다 — Git 쪽 선례 그대로).
    private fun buildPatchText(hg: Hg, oldRevNum: Int, newRevNum: Int): String {
        val entries = computeChangedEntries(hg, oldRevNum, newRevNum)
        if (entries.isEmpty()) return ""

        // 실제 `hg diff`의 파일별 구분 헤더("diff -r <짧은노드ID> -r <짧은노드ID> <path>")를 그대로
        // 재현한다 — DiffCommand.DiffEntry.getDiffContent()는 "--- .../+++ .../@@ ..." 본문만 주고
        // 이 구분 헤더는 만들지 않는다(여러 파일의 diffContent를 그냥 이어붙이면 파일 경계가 애매해짐).
        val oldHex = if (oldRevNum >= 0) nativeCommitAt(hg, oldRevNum)?.nodeId?.toHex()?.take(12) else null
        val newHex = if (newRevNum >= 0) nativeCommitAt(hg, newRevNum)?.nodeId?.toHex()?.take(12) else null
        val nullParentHex = "0".repeat(12)

        val sb = StringBuilder()
        for (entry in entries) {
            sb.append("diff -r ").append(oldHex ?: nullParentHex)
                .append(" -r ").append(newHex ?: nullParentHex)
                .append(" ").append(entry.path).append("\n")
            sb.append(entry.diffContent)
        }
        return sb.toString()
    }

    override fun getDiff(commitId: String): List<Any> {
        return useHg { hg ->
            val revNum = resolveRevisionNumber(hg, commitId) ?: return@useHg emptyList()
            val oldRevNum = if (revNum > 0) revNum - 1 else -1
            getFileDiffs(hg, oldRevNum, revNum)
        }
    }

    // GitRepository.getDiff(revA, revB)와 동일하게, revA/revB 중 하나가 존재하지 않는 리비전이면
    // (resolveRevisionNumber가 null을 반환하면) "빈 트리"로 근사한다(revNum -1) — revA가 없으면
    // 전부 ADD, revB가 없으면 전부 DELETE로 나타난다. getDiff(commitId) 단일 인자 오버로드와 달리
    // 여기서는 존재하지 않아도 빈 리스트로 조기 반환하지 않는다(Git 쪽 GitRepositorySpec의 대응
    // 테스트 관례를 그대로 따름).
    override fun getDiff(revA: String, revB: String): List<Any> {
        return useHg { hg ->
            val oldRevNum = resolveRevisionNumber(hg, revA) ?: -1
            val newRevNum = resolveRevisionNumber(hg, revB) ?: -1
            getFileDiffs(hg, oldRevNum, newRevNum)
        }
    }

    // GitRepository.getFileDiffs()의 hg 대응. oldRevNum/newRevNum은 -1이면 "리비전 없음(빈 매니페스트)"
    // — DiffCommand/ManifestTreeIterator 양쪽이 공유하는 동일한 sentinel이라 별도 널 처리 없이 그대로
    // 넘긴다. 파일 크기/전체 diff 크기 제한(diffFileLimit/diffSizeLimit/diffLineLimit)도 Git 쪽과
    // 동일한 상수로 재현해 병적으로 큰 diff가 커밋 상세/PR 페이지를 그대로 무너뜨리지 않게 한다.
    private fun getFileDiffs(hg: Hg, oldRevNum: Int, newRevNum: Int): List<FileDiff> {
        val entries = computeChangedEntries(hg, oldRevNum, newRevNum)

        val commitAHex = if (oldRevNum >= 0) nativeCommitAt(hg, oldRevNum)?.nodeId?.toHex() else null
        val commitBHex = if (newRevNum >= 0) nativeCommitAt(hg, newRevNum)?.nodeId?.toHex() else null

        // hg4j의 TreeCommand.TreeEntry.mode는 git 스타일 전체 비트마스크가 아니라 0644/0755(실행
        // 파일)/0120000(심볼릭 링크)만 담는 단순화된 값이다(TreeCommand.call() 참고) — FileMode로
        // 정규화해 PR diff 파셜(partial_filediff.html)의 isFileModeChanged()가 최소한 "파일 실행
        // 권한 변경/심볼릭 링크 여부" 정도는 git과 동일한 방식으로 판정할 수 있게 한다.
        val oldModes = if (oldRevNum >= 0) hg.tree().setRevision(oldRevNum).call().associate { it.path to it.mode } else emptyMap()
        val newModes = if (newRevNum >= 0) hg.tree().setRevision(newRevNum).call().associate { it.path to it.mode } else emptyMap()

        val diffFileLimit = 1000
        val diffSizeLimit = 1000000
        val diffLineLimit = 20000
        var totalSize = 0
        var totalLines = 0

        val result = ArrayList<FileDiff>()
        for (entry in entries) {
            val fileDiff = FileDiff()
            fileDiff.commitA = commitAHex
            fileDiff.commitB = commitBHex
            fileDiff.changeType = when (entry.changeType) {
                DiffCommand.ChangeType.ADD -> DiffEntry.ChangeType.ADD
                DiffCommand.ChangeType.DELETE -> DiffEntry.ChangeType.DELETE
                DiffCommand.ChangeType.MODIFY -> DiffEntry.ChangeType.MODIFY
                null -> DiffEntry.ChangeType.MODIFY
            }

            val path = entry.path

            if (totalSize > diffSizeLimit || totalLines > diffLineLimit) {
                if (fileDiff.changeType != DiffEntry.ChangeType.ADD) fileDiff.pathA = path
                if (fileDiff.changeType != DiffEntry.ChangeType.DELETE) fileDiff.pathB = path
                fileDiff.addError(FileDiff.Error.OTHERS_SIZE_EXCEEDED)
                result.add(fileDiff)
                if (result.size > diffFileLimit) break
                continue
            }

            var rawA: ByteArray? = null
            if (fileDiff.changeType != DiffEntry.ChangeType.ADD) {
                fileDiff.pathA = path
                fileDiff.oldMode = toFileMode(oldModes[path])
                try {
                    rawA = hg.cat().setFile(path).setRevision(oldRevNum.toString()).call()
                    fileDiff.isBinaryA = RawText.isBinary(rawA)
                    fileDiff.a = if (fileDiff.isBinaryA) {
                        null
                    } else {
                        val charsetStr = FileUtil.detectCharset(rawA)
                        val str = String(rawA, Charset.forName(charsetStr))
                        RawText(str.toByteArray(StandardCharsets.UTF_8))
                    }
                } catch (e: Exception) {
                    fileDiff.addError(FileDiff.Error.A_SIZE_EXCEEDED)
                }
            }

            var rawB: ByteArray? = null
            if (fileDiff.changeType != DiffEntry.ChangeType.DELETE) {
                fileDiff.pathB = path
                fileDiff.newMode = toFileMode(newModes[path])
                try {
                    rawB = hg.cat().setFile(path).setRevision(newRevNum.toString()).call()
                    fileDiff.isBinaryB = RawText.isBinary(rawB)
                    fileDiff.b = if (fileDiff.isBinaryB) {
                        null
                    } else {
                        val charsetStr = FileUtil.detectCharset(rawB)
                        val str = String(rawB, Charset.forName(charsetStr))
                        RawText(str.toByteArray(StandardCharsets.UTF_8))
                    }
                } catch (e: Exception) {
                    fileDiff.addError(FileDiff.Error.B_SIZE_EXCEEDED)
                }
            }

            if (fileDiff.a != null && fileDiff.b != null && !(fileDiff.isBinaryA || fileDiff.isBinaryB) &&
                fileDiff.changeType == DiffEntry.ChangeType.MODIFY
            ) {
                val diffAlgorithm = DiffAlgorithm.getAlgorithm(DiffAlgorithm.SupportedAlgorithm.HISTOGRAM)
                fileDiff.editList = diffAlgorithm.diff(RawTextComparator.DEFAULT, fileDiff.a, fileDiff.b)
                val hunks = fileDiff.getHunks()
                if (hunks != null) {
                    totalSize += hunks.totalSize
                    totalLines += hunks.lines
                }
            }

            if (fileDiff.b != null && !fileDiff.isBinaryB && fileDiff.changeType == DiffEntry.ChangeType.ADD) {
                totalLines += fileDiff.b!!.size()
                rawB?.let { totalSize += it.size }
            }

            if (fileDiff.a != null && !fileDiff.isBinaryA && fileDiff.changeType == DiffEntry.ChangeType.DELETE) {
                totalLines += fileDiff.a!!.size()
                rawA?.let { totalSize += it.size }
            }

            result.add(fileDiff)
            if (result.size > diffFileLimit) break
        }

        return result
    }

    // 주의: 코틀린에는 자바의 8진수 리터럴 표기(0755 등)가 없다 — 여기서 비교하는 값은 hg4j
    // TreeCommand.call()이 실제로 만드는 자바 8진수 리터럴(0644/0755/0120000)의 10진수 값
    // (420/493/40960)이다. 리터럴로 8진수를 그대로 옮겨 적으면 10진수로 잘못 해석되어(120000,
    // 755) 절대 매치되지 않는 조용한 버그가 되므로 10진수로 명시하고 주석에 원래 8진수 값을 남긴다.
    // hg4j의 DiffCommand는 newRevision에 리터럴 -1을 넘기면(revB가 존재하지 않는 커밋이라
    // getDiff(revA, revB)가 -1로 근사하는 경우) oldRevision과 동일하게 "빈 매니페스트"로 정확히
    // 처리한다(hg4j 2026-09-09 수정 — 예전에는 newRevision의 "값 미지정" sentinel도 우연히 -1이라
    // "빈 매니페스트"가 아니라 "tip으로 대체"돼 버리는 실제 버그가 있었다. DiffCommand.java에서
    // "값 미지정" 전용 sentinel(Integer.MIN_VALUE)을 -1과 분리해 근본 수정했다 — 상세는
    // hg4j 저장소 커밋/DiffCommand.NOT_SET 주석 참고). 그 결과 old/new 둘 다(또는 한쪽만)
    // -1이어도 별도 우회 없이 DiffCommand를 그대로 호출하면 된다 — 둘 다 -1이면 빈 매니페스트끼리
    // 비교해 자연스럽게 빈 결과가 나온다.
    private fun computeChangedEntries(hg: Hg, oldRevNum: Int, newRevNum: Int): List<DiffCommand.DiffEntry> {
        return hg.diff().setOldRevision(oldRevNum).setNewRevision(newRevNum).call()
    }

    private fun toFileMode(rawMode: Int?): FileMode {
        return when (rawMode) {
            null -> FileMode.MISSING
            40960 -> FileMode.SYMLINK // 0120000
            493 -> FileMode.EXECUTABLE_FILE // 0755
            else -> FileMode.REGULAR_FILE // 0644 (기본값)
        }
    }

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
                .map { HgCommit(it, userResolver, gpgVerifier) }
        }
    }

    override fun getCommit(rev: String): Commit? {
        return useHg { hg ->
            val revNum = resolveRevisionNumber(hg, rev) ?: return@useHg null
            hg.log().setStartRev(revNum.toString()).call()
                .find { it.revision == revNum }
                ?.let { HgCommit(it, userResolver, gpgVerifier) }
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

    // yona-wiki P3-12 2라운드 — bookmark를 git의 브랜치 개념에 매핑하기로 확정했으므로(아래
    // getBranches() 주석 참고) 여기서도 bookmark 이름들을 노출한다. tip은 항상 함께 노출해
    // "브랜치가 하나도 없는" 저장소에서도 최소한 하나의 참조점은 있도록 한다.
    override fun getRefNames(): List<String> = useHg { hg -> hg.bookmark().call().keys.toList() } + "tip"

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

    // yona-wiki P3-12 2라운드 설계 결정(사용자 확정, 재론의 안 함) — yona의 git 모양 "브랜치"
    // 개념(getBranches/createBranch/deleteBranch/getHeadBranch)은 Mercurial의 **bookmark**에
    // 매핑한다. Mercurial의 진짜 "named branch"(`hg branch`)는 커밋에 영구히 새겨지는(삭제가
    // 아니라 "close"만 가능한) 별개의 개념이라 git 브랜치와 근본적으로 다르지만, bookmark는
    // git 브랜치처럼 자유롭게 이동/삭제 가능한 포인터라 이 코드베이스의 표준 관례(모호하면
    // GitHub/주류 forge 관례를 따름 — Bitbucket이 과거 Mercurial 저장소를 git 모양 UI로 노출할 때
    // 쓰던 것과 동일한 매핑)에 부합한다. named branch 지원은 이번 범위 밖(out of scope, 계획
    // 문서 참고).
    //
    // GitBranch/GitTag의 name 필드는 GitRepository와 동일하게 "refs/heads/"/"refs/tags/" 접두어를
    // 붙인 형태로 채운다 — shortName이 그 접두어를 제거하는 것을 전제로 설계된 값 객체라(각 파일
    // 상단 주석 참고) Hg 쪽도 같은 계약을 지켜야 소비자(BranchApiController 등, 향후 Hg를 붙일
    // 경우)가 두 백엔드를 구분 없이 다룰 수 있다.
    private fun nativeCommitAt(hg: Hg, revNum: Int) =
        hg.log().setStartRev(revNum.toString()).call().find { it.revision == revNum }

    override fun getBranches(): List<GitBranch> {
        return useHg { hg ->
            val bookmarks = hg.bookmark().call()
            bookmarks.mapNotNull { (name, hex) ->
                val revNum = resolveRevisionNumber(hg, hex) ?: return@mapNotNull null
                val native = nativeCommitAt(hg, revNum) ?: return@mapNotNull null
                val commit = HgCommit(native, userResolver, gpgVerifier)
                val user = userResolver(commit.getAuthorName(), commit.getAuthorEmail())
                GitBranch("refs/heads/$name", commit, user)
            }
        }
    }

    override fun getHeadBranch(): GitBranch? {
        return useHg { hg ->
            val activeName = hg.bookmark().getActiveBookmark() ?: return@useHg null
            val hex = hg.bookmark().call()[activeName] ?: return@useHg null
            val revNum = resolveRevisionNumber(hg, hex) ?: return@useHg null
            val native = nativeCommitAt(hg, revNum) ?: return@useHg null
            val commit = HgCommit(native, userResolver, gpgVerifier)
            val user = userResolver(commit.getAuthorName(), commit.getAuthorEmail())
            GitBranch("refs/heads/$activeName", commit, user)
        }
    }

    override fun deleteBranch(branchName: String) {
        useHg { hg ->
            val name = branchName.removePrefix("refs/heads/")
            val existing = hg.bookmark().call()
            if (!existing.containsKey(name)) {
                throw IllegalArgumentException("존재하지 않는 브랜치입니다: $name")
            }
            hg.bookmark().setBookmarkName(name).setDelete(true).call()
        }
    }

    override fun createBranch(branchName: String, startPoint: String) {
        useHg { hg ->
            val name = branchName.removePrefix("refs/heads/")
            // 코디네이터 리뷰(2026-09-08) — hg4j의 BookmarkCommand 자체는 "tip"을 막지 않지만, 실제
            // hg CLI는 `hg bookmark tip`을 "the name 'tip' is reserved"로 거부한다(태그와 동일한
            // 예약어 검사, mercurial의 scmutil.checknewlabel()이 bookmark/tag 양쪽에 공유됨) —
            // createTag()의 동일한 가드와 대칭으로 여기도 지어내지 않고 실제 hg 동작을 재현한다.
            if (name == "tip") {
                throw IllegalArgumentException("'tip'은 예약된 이름입니다")
            }
            val existing = hg.bookmark().call()
            if (existing.containsKey(name)) {
                throw IllegalArgumentException("이미 존재하는 브랜치입니다: $name")
            }
            val revNum = resolveRevisionNumber(hg, startPoint)
                ?: throw IllegalArgumentException("존재하지 않는 시작점입니다: $startPoint")
            val native = nativeCommitAt(hg, revNum)
                ?: throw IllegalArgumentException("존재하지 않는 시작점입니다: $startPoint")
            hg.bookmark().setBookmarkName(name).setRevision(native.nodeId.toHex()).call()
        }
    }

    // hg4j의 TagsCommand는 실제 hg CLI와 동일하게 항상 pseudo-tag "tip"을 목록 맨 앞에 끼워
    // 넣는다(리포지토리 최신 리비전을 가리킬 뿐 실제로 생성/삭제 가능한 태그가 아니다) — yona의
    // git 모양 태그 API에는 대응 개념이 없으므로 두 메서드 모두에서 걸러낸다.
    override fun getTagNames(): List<String> {
        return useHg { hg ->
            hg.tags().call().filter { it.name != "tip" }.map { "refs/tags/${it.name}" }
        }
    }

    override fun getTags(): List<GitTag> {
        return useHg { hg ->
            val commits = hg.log().call()
            hg.tags().call()
                .filter { it.name != "tip" }
                .mapNotNull { tag ->
                    val native = commits.find { it.revision == tag.rev } ?: return@mapNotNull null
                    val commit = HgCommit(native, userResolver, gpgVerifier)
                    val user = userResolver(commit.getAuthorName(), commit.getAuthorEmail())
                    // Mercurial 태그는 git의 annotated 태그처럼 별도의 태거 신원/GPG 서명을 갖는
                    // 오브젝트가 아니다(태그 자체는 그냥 `.hgtags`에 커밋된 텍스트 한 줄이고,
                    // 그 커밋 자체의 작성자가 있을 뿐 "태거"라는 별도 개념이 없다) — 지어내지
                    // 않고 GitTag의 lightweight 태그와 동일하게 tagger/message는 항상 null,
                    // annotated=false로 둔다(GitTag.kt 상단 주석의 invariant와 일치).
                    GitTag("refs/tags/${tag.name}", commit, user, message = null, annotated = false)
                }
        }
    }

    override fun deleteTag(tagName: String) {
        useHg { hg ->
            val name = tagName.removePrefix("refs/tags/")
            val existing = hg.tags().call().any { it.name == name && it.name != "tip" }
            if (!existing) {
                throw IllegalArgumentException("존재하지 않는 태그입니다: $name")
            }
            hg.tag().setTagName(name).setRemove(true).call()
        }
    }

    // message/taggerName/taggerEmail은 GitRepository와의 시그니처 대칭을 위해 받지만 사용하지
    // 않는다 — hg4j의 TagCommand는 태그 커밋의 저자/메시지를 자체적으로 고정하고("hg4j
    // <hg4j@google.com>", "Added tag X for changeset Y") 커스터마이즈할 방법을 제공하지 않는다
    // (실제 hg CLI도 태그 커밋 메시지에 한해서만 커밋 에디터를 열지, 이 라이브러리는 그 계층까지
    // 만들지 않았다). getTags()가 이 필드들을 애초에 노출하지 않는 것과 일관된 선택 — 없는
    // 기능을 지어내지 않는다.
    override fun createTag(tagName: String, startPoint: String, message: String?, taggerName: String?, taggerEmail: String?) {
        useHg { hg ->
            val name = tagName.removePrefix("refs/tags/")
            if (name == "tip") {
                throw IllegalArgumentException("'tip'은 예약된 이름입니다")
            }
            val existing = hg.tags().call().any { it.name == name && it.name != "tip" }
            if (existing) {
                throw IllegalArgumentException("이미 존재하는 태그입니다: $name")
            }
            val revNum = resolveRevisionNumber(hg, startPoint)
                ?: throw IllegalArgumentException("존재하지 않는 시작점입니다: $startPoint")
            val native = nativeCommitAt(hg, revNum)
                ?: throw IllegalArgumentException("존재하지 않는 시작점입니다: $startPoint")
            hg.tag().setTagName(name).setNodeId(native.nodeId.getBytes()).call()
        }
    }

    override fun getBlobId(revision: String, path: String): String? = null

    override fun getParentCommitOf(commitId: String): Commit? {
        return useHg { hg ->
            val revNum = resolveRevisionNumber(hg, commitId) ?: return@useHg null
            if (revNum <= 0) return@useHg null
            hg.log().call().find { it.revision == revNum - 1 }?.let { HgCommit(it, userResolver, gpgVerifier) }
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
