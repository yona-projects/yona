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

    // 브랜치의 getRefNames()/getBranches()/deleteBranch()/createBranch()와
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

    // yona PullRequest.getBlobId() 대응(CodeCommentThread.isOutdated()에서 사용).
    // 리비전에 해당 경로가 없으면(파일이 그 시점에 존재하지 않으면) null.
    fun getBlobId(revision: String, path: String): String?

    // Mercurial named branch(`hg branch`) 지원. git/SVN에는 대응 개념이 없어
    // 기본 구현은 빈 목록(SvnRepository.getBranches()가 빈 목록을 반환하는 선례와 동일 패턴)으로
    // 둔다 — HgRepository만 의미 있게 override한다.
    fun getNamedBranchNames(): List<String> = emptyList()

    fun getNamedBranches(): List<GitBranch> = emptyList()

    // 코드브라우저 "새 파일"/"편집"(온라인 커밋) 쓰기 경로 — Git은 BareCommit(JGit)이
    // bare 저장소를 직접 다루는 기존 경로를 그대로 쓰고 이 인터페이스를 거치지 않는다. Mercurial
    // 전용으로 신설: branchBookmark는 커밋을 반영할 bookmark 이름(빈 문자열/"tip"/"default"/"HEAD"면
    // pseudo-ref로 보고 bookmark를 만들거나 옮기지 않는다), namedBranchName은 지정 시 커밋 전
    // `hg branch <name>`으로 작업 디렉터리 상태를 바꿔 그 named branch로 커밋되게 한다(null/빈
    // 문자열이면 현재 상태 유지 — named branch를 만드는 유일한 방법이라 별도 "생성" API가 없다).
    fun commitTextFile(
        branchBookmark: String,
        namedBranchName: String?,
        path: String,
        content: String,
        message: String,
        authorName: String?,
        authorEmail: String?
    ): Unit = throw UnsupportedOperationException("commitTextFile is not supported for this VCS")
}
