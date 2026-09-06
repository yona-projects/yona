package com.github.yonaprojects.yona.domain.vcs

import tools.jackson.databind.node.ObjectNode
import java.io.File
import java.io.IOException
import java.io.OutputStream

interface PlayRepository {
    companion object {
        const val MAX_FILE_SIZE_CAN_BE_VIEWED: Long = 1024 * 1024 // 1MB
    }

    @Throws(IOException::class, Exception::class)
    fun create()

    fun isIntermediateFolder(path: String): Boolean

    @Throws(IOException::class, Exception::class)
    fun getMetaDataFromPath(path: String): ObjectNode?

    @Throws(IOException::class, Exception::class)
    fun getMetaDataFromPath(branch: String, path: String): ObjectNode?

    @Throws(IOException::class, Exception::class)
    fun getRawFile(revision: String, path: String): ByteArray

    @Throws(Exception::class)
    fun delete()

    @Throws(IOException::class, Exception::class)
    fun getPatch(commitId: String): String

    @Throws(IOException::class, Exception::class)
    fun getPatch(revA: String, revB: String): String

    @Throws(IOException::class)
    fun getDiff(commitId: String): List<Any>

    @Throws(IOException::class)
    fun getDiff(revA: String, revB: String): List<Any>

    @Throws(IOException::class, Exception::class)
    fun getHistory(pageNum: Int, pageSize: Int, untilRev: String?, path: String?): List<Commit>

    @Throws(IOException::class, Exception::class)
    fun getCommit(rev: String): Commit?

    fun getRefNames(): List<String>

    fun isFile(path: String): Boolean

    fun isFile(path: String, revStr: String): Boolean

    fun renameTo(projectName: String): Boolean

    fun getDefaultBranch(): String

    fun setDefaultBranch(target: String)

    fun getBranches(): List<GitBranch>

    fun getHeadBranch(): GitBranch?

    fun deleteBranch(branchName: String)

    fun createBranch(branchName: String, startPoint: String)

    // yona-wiki P3-10 — 브랜치의 getRefNames()/getBranches()/deleteBranch()/createBranch()와
    // 정확히 같은 패턴의 git 태그 지원. getTagNames()는 코드브라우저의 통합 ref 셀렉터가
    // getRefNames()(브랜치)와 나란히 쓰는 원시 ref 이름 목록("refs/tags/v1" 형태), getTags()는
    // 태그 목록 화면이 쓰는 상세 뷰 모델(GitTag) 목록이다.
    fun getTagNames(): List<String>

    fun getTags(): List<GitTag>

    fun deleteTag(tagName: String)

    // message가 null이면 lightweight 태그, 아니면 annotated 태그(taggerName/taggerEmail로 태그
    // 오브젝트의 태거 identity를 채운다 — annotated 태그는 git 자체가 태거 identity를 항상 요구함).
    fun createTag(tagName: String, startPoint: String, message: String?, taggerName: String?, taggerEmail: String?)

    fun getParentCommitOf(commitId: String): Commit?

    fun isEmpty(): Boolean

    fun move(srcProjectOwner: String, srcProjectName: String, destProjectOwner: String, destProjectName: String): Boolean

    fun getDirectory(): File

    fun getArchive(os: OutputStream, branchName: String)

    // yona PullRequest.getBlobId() 대응 (P1-20, CodeCommentThread.isOutdated()에서 사용).
    // 리비전에 해당 경로가 없으면(파일이 그 시점에 존재하지 않으면) null.
    fun getBlobId(revision: String, path: String): String?
}
