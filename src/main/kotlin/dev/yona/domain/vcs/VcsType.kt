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
            VcsCapability.DEPLOY_KEYS, VcsCapability.LFS, VcsCapability.PROTECTED_REFS
        )
    ),

    /** Subversion. v1.6에서 Git 전용이던 기능을 지원하지 않는다. */
    SUBVERSION("Subversion", emptySet());

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
    PROTECTED_REFS
}

/** `project.vcs` 열 값으로 바로 기능을 묻는 도우미이다. */
object VcsCapabilities {

    /** [vcs] 열 값의 종류가 [capability]를 지원하면 `true`이다. 모르는 값이면 `false`이다. */
    fun supports(vcs: String?, capability: VcsCapability): Boolean = VcsType.of(vcs)?.supports(capability) ?: false
}
