package dev.yona.domain.vcs

/**
 * 프로젝트 저장소의 종류이다. `project.vcs` 열 값은 v1.6과 같은 문자열을 쓴다(`GIT`, `Subversion`).
 *
 * 종류마다 지원하는 기능이 다르다. 화면, 컨트롤러, API가 `project.vcs == "GIT"` 같은 비교로 종류를 가르는 대신 [supports]로 기능을
 * 묻는다(Mercurial 도입을 위한 공통화, D-86). 종류가 늘어도 호출한 쪽은 바뀌지 않고 이 선언만 바뀐다.
 *
 * @property columnValue `project.vcs` 열에 저장하는 값
 * @property capabilities 이 종류가 지원하는 기능
 */
enum class VcsType(val columnValue: String, val capabilities: Set<VcsCapability>) {

    /** Git. v1.6에서 `@IsOnlyGitAvailable`이 붙었던 기능을 모두 지원한다. */
    GIT(
        "GIT",
        setOf(
            VcsCapability.BRANCHES, VcsCapability.TAGS, VcsCapability.FORK, VcsCapability.PULL_REQUESTS,
            VcsCapability.DEPLOY_KEYS, VcsCapability.LFS, VcsCapability.PROTECTED_REFS,
            VcsCapability.DEFAULT_BRANCH, VcsCapability.README_POSTING, VcsCapability.ARCHIVE_DOWNLOAD, VcsCapability.ONLINE_EDITING,
            VcsCapability.LINE_COMMENT_THREADS, VcsCapability.COMMIT_EMAILS
        )
    ),

    /** Subversion. v1.6에서 Git 전용이던 기능을 지원하지 않고, 커밋을 리비전 번호로 가리킨다. */
    SUBVERSION("Subversion", setOf(VcsCapability.NUMERIC_REVISIONS, VcsCapability.AUTHOR_LOGIN_IDS)),

    /**
     * Mercurial(hg4j, X-7, D-86). 이름표(bookmark)를 브랜치로 보인다. 기능은 구현이 끝나는 단계마다 더한다: 지금은 읽기(브랜치와 태그 목록, 내려받기,
     * 커밋 작성자의 이메일, README 글)이고, 서빙(6단계)과 포크(7단계), 풀 리퀘스트(8단계), LFS와 서명(9단계)은 아직 아니다.
     */
    MERCURIAL("Mercurial", setOf(VcsCapability.BRANCHES, VcsCapability.TAGS, VcsCapability.ARCHIVE_DOWNLOAD, VcsCapability.COMMIT_EMAILS, VcsCapability.README_POSTING));

    /** [capability]를 지원하면 `true`이다. */
    fun supports(capability: VcsCapability): Boolean = capability in capabilities

    companion object {
        /**
         * `project.vcs` 열 값에 해당하는 종류이다. 모르는 값이나 비어 있는 값이면 `null`이다. v1.6처럼 대소문자를 구분한다
         * (`"GIT".equals(vcs)`).
         */
        fun of(columnValue: String?): VcsType? = entries.firstOrNull { it.columnValue == columnValue }
    }
}

/**
 * 저장소 종류가 지원하거나 지원하지 않을 수 있는 기능이다. v1.6에서 Git에서만 동작하던 기능이 대부분이다.
 */
enum class VcsCapability {
    /** 브랜치 목록과 관리 화면. */
    BRANCHES,

    /** 태그 목록, 생성, 삭제. */
    TAGS,

    /** 프로젝트 포크와 복제. */
    FORK,

    /** 풀 리퀘스트(목록, 생성, 병합, 리뷰). */
    PULL_REQUESTS,

    /** 프로젝트별 배포 키(SSH). */
    DEPLOY_KEYS,

    /** Git LFS. */
    LFS,

    /** 브랜치 보호 규칙. */
    PROTECTED_REFS,

    /** 기본 브랜치를 바꾸는 설정과 API. */
    DEFAULT_BRANCH,

    /** 저장소의 README를 게시글로 쓰는 기능(게시글 본문을 저장소 파일로 읽는다). */
    README_POSTING,

    /** 코드 보기의 내려받기(zip) 링크. */
    ARCHIVE_DOWNLOAD,

    /** 화면에서 저장소에 파일을 바로 커밋하는 기능(새 파일, README 만들기, 이슈 템플릿). */
    ONLINE_EDITING,

    /** 코드 보기 파일 단위의 줄 코멘트 스레드와 그 개수. */
    LINE_COMMENT_THREADS,

    /** 커밋 작성자가 이메일로 식별된다. 없으면 작성자 로그인 ID로 사용자를 찾는다. */
    COMMIT_EMAILS,

    /** 커밋을 리비전 번호로 가리킨다(Subversion). 없으면 커밋 ID(해시)와 브랜치 이름으로 가리킨다. */
    NUMERIC_REVISIONS,

    /** 커밋 작성자가 사용자 로그인 ID로 기록되고 이메일이 없다(Subversion). 없으면 이메일로 사용자를 찾는다. */
    AUTHOR_LOGIN_IDS
}

/** `project.vcs` 열 값으로 바로 기능을 묻는 도우미이다. */
object VcsCapabilities {

    /** [vcs] 열 값의 종류가 [capability]를 지원하면 `true`이다. 모르는 값이면 `false`이다. */
    fun supports(vcs: String?, capability: VcsCapability): Boolean = VcsType.of(vcs)?.supports(capability) ?: false
}

/**
 * 화면 템플릿이 `${projectView.vcs.pullRequests}`처럼 쓰는 기능 지원 목록이다. [VcsType]의 능력 선언을 이름 있는 속성으로 보여 준다.
 * [columnValue]가 모르는 값이면 아무것도 지원하지 않는다.
 */
class VcsSupport(columnValue: String?) {
    private val type = VcsType.of(columnValue)

    private fun has(capability: VcsCapability) = type?.supports(capability) ?: false

    val branches: Boolean get() = has(VcsCapability.BRANCHES)
    val tags: Boolean get() = has(VcsCapability.TAGS)
    val fork: Boolean get() = has(VcsCapability.FORK)
    val pullRequests: Boolean get() = has(VcsCapability.PULL_REQUESTS)
    val deployKeys: Boolean get() = has(VcsCapability.DEPLOY_KEYS)
    val lfs: Boolean get() = has(VcsCapability.LFS)
    val protectedRefs: Boolean get() = has(VcsCapability.PROTECTED_REFS)
    val defaultBranch: Boolean get() = has(VcsCapability.DEFAULT_BRANCH)
    val readmePosting: Boolean get() = has(VcsCapability.README_POSTING)
    val archiveDownload: Boolean get() = has(VcsCapability.ARCHIVE_DOWNLOAD)
    val onlineEditing: Boolean get() = has(VcsCapability.ONLINE_EDITING)
    val lineCommentThreads: Boolean get() = has(VcsCapability.LINE_COMMENT_THREADS)
    val commitEmails: Boolean get() = has(VcsCapability.COMMIT_EMAILS)
    val numericRevisions: Boolean get() = has(VcsCapability.NUMERIC_REVISIONS)
    val authorLoginIds: Boolean get() = has(VcsCapability.AUTHOR_LOGIN_IDS)
}
