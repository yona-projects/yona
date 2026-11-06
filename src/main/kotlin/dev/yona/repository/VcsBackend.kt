package dev.yona.repository

import dev.yona.domain.project.Project
import dev.yona.domain.vcs.VcsType
import org.springframework.stereotype.Component

/** 저장소 종류별 구현이 어느 종류를 맡는지 밝힌다. */
interface VcsPart {
    /** 이 구현이 맡는 저장소 종류. */
    val type: VcsType
}

/**
 * 한 종류의 저장소를 만들고 지우고 옮기고 참조를 알려 주는 계약이다. [RepositoryService]가 프로젝트의 `vcs` 값으로 구현을 골라 쓴다
 * (Mercurial 도입을 위한 공통화 2a, D-86). 종류마다 `if (vcs == ...)`로 가르던 자리를 구현 추가로 바꾼다.
 *
 * 소유자와 이름은 URL 디코딩이 끝난 값이다. 지원하지 않는 일([cloneLocal], [cloneRemote])은 기본으로 [UnsupportedOperationException]이다.
 */
interface RepositoryLifecycle : VcsPart {

    /** 저장소를 새로 만든다. */
    fun create(owner: String, name: String)

    /** 저장소 디렉터리를 지운다. 없으면 아무 일도 하지 않는다. */
    fun delete(owner: String, name: String)

    /** 저장소를 다른 소유자나 이름의 위치로 옮긴다. 원래 위치에 없으면 성공이고, 옮기지 못하면 `false`이다. */
    fun move(owner: String, name: String, newOwner: String, newName: String): Boolean

    /** 저장소의 브랜치, 태그 등 참조 이름이다. */
    fun refNames(owner: String, name: String): List<String>

    /** 저장소의 기본 브랜치 이름이다. */
    fun defaultBranch(owner: String, name: String): String

    /** 저장소의 기본 브랜치를 [target]으로 바꾼다. 바꿀 수 없는 종류는 아무 일도 하지 않는다. */
    fun setDefaultBranch(owner: String, name: String, target: String)

    /** 원본 저장소를 포크 저장소로 복제한다. */
    fun cloneLocal(owner: String, name: String, forkOwner: String, forkName: String): Unit =
        throw UnsupportedOperationException("지원하지 않는 VCS입니다: ${type.columnValue}")

    /** 다른 저장소를 주소로 가져와 복제한다. */
    fun cloneRemote(owner: String, name: String, url: String, authId: String?, authPw: String?): Unit =
        throw UnsupportedOperationException("지원하지 않는 VCS입니다: ${type.columnValue}")
}

/**
 * 커밋 이력과 커밋 하나를 읽는 계약이다. 결과는 종류와 무관한 [CommitInfo]이다. [dev.yona.repository.CommitHistoryService]가 지표와 함께 이것을 부른다.
 */
interface CommitReader : VcsPart {

    /**
     * [revision](없으면 `HEAD`)부터 거슬러 오르는 이력의 [page]번째 쪽(0부터)이다. [path]를 주면 그 경로를 바꾼 커밋만 모은다.
     * 리비전을 풀 수 없으면 `null`이다.
     */
    fun history(project: Project, page: Int, size: Int, revision: String?, path: String?): List<CommitInfo>?

    /** [revision]의 커밋이다. 없으면 `null`이다(풀 수 없거나 커밋이 아닌 객체 포함). */
    fun commit(project: Project, revision: String): CommitInfo?

    /** 커밋 번호 [ids]에 해당하는 커밋을 한 번에 읽는다. 풀 수 없거나 커밋이 아닌 번호는 결과에서 빠진다. */
    fun commits(project: Project, ids: Collection<String>): Map<String, CommitInfo>

    /** 커밋의 첫 부모이다. 부모가 없으면 `null`이다. */
    fun parentOf(project: Project, revision: String): CommitInfo?

    /**
     * 푸시로 새로 생기거나 바뀐 ref([oldId]에서 [newId]로)가 가져온 커밋이다(최근 것이 앞). [newId]까지의 이력에서 [oldId]까지를 뺀 것이고,
     * [oldId]가 [dev.yona.domain.vcs.RefUpdate.NULL_ID]이면 [newId] 하나뿐이다. 푸시가 없는 종류(Subversion)는 빈 목록이다.
     */
    fun pushedCommits(project: Project, oldId: String, newId: String): List<CommitInfo> = emptyList()
}

/**
 * 코드 보기(폴더, 파일, 원본 내용, 내려받기)를 위해 저장소를 읽는 계약이다. 결과는 화면과 JSON 응답이 함께 쓰는 맵이다.
 * [dev.yona.repository.CodeBrowserService]가 지표와 함께 이것을 부른다.
 */
interface CodeReader : VcsPart {

    /** 저장소에 읽을 내용이 하나도 없으면 `true`이다. */
    fun isEmpty(project: Project): Boolean

    /**
     * [revision](브랜치, 태그, 커밋 번호, 없으면 기본 위치)에서 [path]의 폴더나 파일 정보이다. 폴더는 `{"type": "folder", "data": {이름: {…}}}`,
     * 파일은 `{"type": "file", …}`이다. 저장소가 비었거나 위치와 경로가 없으면 `null`이다.
     */
    fun metaData(project: Project, revision: String?, path: String): Map<String, Any?>?

    /** [path]가 폴더 하나만 담은 중간 폴더이면 `true`이다. 중간 폴더를 건너뛰지 않는 종류는 항상 `false`이다. */
    fun isIntermediateFolder(project: Project, path: String): Boolean = false

    /** [revision]의 [path] 파일 내용이다. 폴더이거나 없으면 `null`이다. */
    fun rawFile(project: Project, revision: String, path: String): ByteArray?

    /** 메모리에 올리지 않고 흘려보내야 하는 큰 파일이면 그 정보를, 아니면 `null`이다. 지원하지 않는 종류는 항상 `null`이다. */
    fun largeRawFile(project: Project, revision: String, path: String): LargeRawFile? = null

    /** [branch]의 내용을 zip으로 [out]에 쓴다. */
    fun archive(project: Project, branch: String, out: java.io.OutputStream)
}

/**
 * 저장소의 파일을 텍스트로 읽는 계약이다(README, 설정 파일 등). 읽다가 생긴 오류는 호출하는 쪽([dev.yona.repository.RepositoryFileReader],
 * [dev.yona.repository.ProjectReadmeService])이 "없는 것"으로 본다(v1.6과 같다).
 */
interface TextReader : VcsPart {

    /** 기본 위치(`HEAD`, 최신)의 [path] 파일 내용이다. 파일이 없거나 폴더이면 `null`이다. */
    fun readText(project: Project, path: String): String?

    /** README 파일 이름이다. 없으면 `null`이다. */
    fun readmeFileName(project: Project): String?

    /** README 내용이다. 파일이 없으면 `null`이다. */
    fun readme(project: Project): String?

    /**
     * 게시글 화면이 README 글의 본문으로 보여 주는 README이다(v1.6 `BareRepository.readREADME`). 지원하지 않는 종류는 `null`이다.
     */
    fun readmeForPosting(project: Project): String? = null
}

/**
 * 브랜치 한 줄의 저장소 쪽 값이다. 풀 리퀘스트 같은 도메인 정보는 [dev.yona.repository.BranchService]가 더한다.
 *
 * @property name 참조 전체 이름(`refs/heads/master`)
 * @property headCommitId 머리 커밋 전체 번호
 * @property committerDate 머리 커밋의 커밋 시각
 */
data class RefHead(val name: String, val headCommitId: String, val headCommitShortId: String, val committerDate: java.util.Date)

/** 태그를 단 사람이다. 주석 태그의 태그 단 사람으로 쓴다. */
data class TagAuthor(val name: String, val email: String)

/**
 * 브랜치와 태그를 읽고 바꾸는 계약이다. 브랜치와 태그가 없는 종류(Subversion)는 구현이 없다([VcsBackends.refs]가 지원하지 않는 VCS로 알린다).
 */
interface RefReader : VcsPart {

    /** `refs/heads/` 아래 브랜치만 머리 커밋이 최근인 순서로 모은다. 커밋을 가리키지 않는 참조는 건너뛴다. */
    fun branches(project: Project): List<RefHead>

    /** 기본 브랜치(`HEAD`가 가리키는 브랜치)이다. 저장소가 비어 `HEAD`를 풀 수 없으면 `null`이다. */
    fun headBranch(project: Project): RefHead?

    /** 브랜치를 지운다(병합 여부와 관계없이 강제로). */
    fun deleteBranch(project: Project, branchName: String)

    /** 태그를 태그 시각이 최근인 순서로 모은다. 같으면 이름순이고, 시각을 알 수 없는 태그는 맨 뒤이다. */
    fun tags(project: Project): List<TagInfo>

    /** 이름으로 태그 하나를 찾는다. 없으면 `null`이다. */
    fun findTag(project: Project, shortName: String): TagInfo?

    /** 태그를 만든다. [message]가 있으면 주석 태그(태그 단 사람은 [tagger])이고 없으면 경량 태그이다. */
    fun createTag(project: Project, name: String, target: String?, message: String?, tagger: TagAuthor): TagCreation

    /** 태그를 지운다. 그런 태그가 없으면 `false`이다. */
    fun deleteTag(project: Project, shortName: String): Boolean
}

/**
 * 메모리에 올리지 않고 흘려보내는 큰 파일이다. 크기와 종류 감지용 앞부분, 스트림 열기를 담고, 다 쓰면 [close]로 저장소를 닫는다.
 */
class LargeRawFile(val size: Long, val prefix: ByteArray, private val owner: AutoCloseable, private val opener: () -> java.io.InputStream) : java.io.Closeable {
    fun open(): java.io.InputStream = opener()
    override fun close() = owner.close()
}

/**
 * 저장소 종류별 구현을 `project.vcs` 열 값으로 찾는 등록부이다. 새 종류는 역할별 구현([RepositoryLifecycle], [CommitReader], [CodeReader], [TextReader], [RefReader])을
 * 빈으로 등록하기만 하면 된다.
 */
@Component
class VcsBackends(lifecycles: List<RepositoryLifecycle>, commits: List<CommitReader>, code: List<CodeReader>, text: List<TextReader>, refs: List<RefReader>) {

    private val lifecycles: Map<VcsType, RepositoryLifecycle> = lifecycles.associateBy { it.type }
    private val commits: Map<VcsType, CommitReader> = commits.associateBy { it.type }
    private val code: Map<VcsType, CodeReader> = code.associateBy { it.type }
    private val text: Map<VcsType, TextReader> = text.associateBy { it.type }
    private val refs: Map<VcsType, RefReader> = refs.associateBy { it.type }

    private fun <T> pick(parts: Map<VcsType, T>, vcs: String?): T =
        VcsType.of(vcs)?.let { parts[it] } ?: throw UnsupportedOperationException("지원하지 않는 VCS입니다: $vcs")

    /**
     * [vcs] 열 값의 수명 구현이다.
     *
     * @throws UnsupportedOperationException 모르는 값이거나 구현이 없을 때(아래 읽기 구현도 같다)
     */
    fun lifecycle(vcs: String?): RepositoryLifecycle = pick(lifecycles, vcs)

    /** [vcs]의 커밋 읽기 구현이다. */
    fun commits(vcs: String?): CommitReader = pick(commits, vcs)

    /** [vcs]의 코드 읽기 구현이다. */
    fun code(vcs: String?): CodeReader = pick(code, vcs)

    /** [vcs]의 파일 텍스트 읽기 구현이다. */
    fun text(vcs: String?): TextReader = pick(text, vcs)

    /** [vcs]의 브랜치와 태그 구현이다. 브랜치와 태그가 없는 종류는 지원하지 않는 VCS이다. */
    fun refs(vcs: String?): RefReader = pick(refs, vcs)

    companion object {
        /** 단위 시험용으로 구현을 직접 묶는다. 필요 없는 역할은 비워 둔다. */
        fun of(
            lifecycles: List<RepositoryLifecycle> = emptyList(), commits: List<CommitReader> = emptyList(), code: List<CodeReader> = emptyList(),
            text: List<TextReader> = emptyList(), refs: List<RefReader> = emptyList()
        ) = VcsBackends(lifecycles, commits, code, text, refs)
    }
}
