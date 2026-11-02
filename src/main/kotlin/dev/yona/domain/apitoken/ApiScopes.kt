package dev.yona.domain.apitoken

/**
 * 개인용 API 토큰(Classic)의 범위 모델이다(로드맵 X-5, `decisions.md` D-36, `architecture/api-token.md` 1-1절).
 * 이름과 묶음 규칙은 GitHub 공식 문서의 Classic 범위를 따른다. 범위는 권한을 더하지 못하고 줄이기만 한다. 토큰이 할 수 있는 일은 범위가 허용하는 일과
 * 토큰 주인의 실제 권한의 교집합이다.
 */
object ApiScopes {

    const val REPO = "repo"
    const val PUBLIC_REPO = "public_repo"
    const val SITE_ADMIN = "site_admin"

    /** 사이트 관리자가 아니어도 고를 수 있는 범위이다. 화면에 보이는 순서이다. */
    private val GENERAL: List<String> = listOf(
        "repo", "public_repo", "admin:repo_hook", "write:repo_hook", "read:repo_hook", "admin:org", "write:org", "read:org", "user", "read:user", "user:email",
        "admin:gpg_key", "write:gpg_key", "read:gpg_key", "admin:ssh_key", "write:ssh_key", "read:ssh_key", "admin:deploy_key", "write:deploy_key", "read:deploy_key"
    )

    /**
     * 각 범위가 포함하는 범위이다(자기 자신은 항상 포함한다). `repo`는 프로젝트 웹훅 범위와 `public_repo`를, `admin:`은 `write:`와 `read:`를,
     * `user`는 `read:user`와 `user:email`을, `admin:gpg_key`는 `write:gpg_key`와 `read:gpg_key`를 포함한다(X-4, D-65). SSH 키(`ssh_key`)와 배포 키(`deploy_key`)도 같다(X-3, D-66). 그 밖의 범위는 서로 포함하지 않는다.
     */
    private val INCLUDES: Map<String, Set<String>> = mapOf(
        "repo" to setOf("public_repo", "admin:repo_hook", "write:repo_hook", "read:repo_hook"),
        "admin:repo_hook" to setOf("write:repo_hook", "read:repo_hook"),
        "write:repo_hook" to setOf("read:repo_hook"),
        "admin:org" to setOf("write:org", "read:org"),
        "write:org" to setOf("read:org"),
        "user" to setOf("read:user", "user:email"),
        "admin:gpg_key" to setOf("write:gpg_key", "read:gpg_key"),
        "write:gpg_key" to setOf("read:gpg_key"),
        "admin:ssh_key" to setOf("write:ssh_key", "read:ssh_key"),
        "write:ssh_key" to setOf("read:ssh_key"),
        "admin:deploy_key" to setOf("write:deploy_key", "read:deploy_key"),
        "write:deploy_key" to setOf("read:deploy_key")
    )

    /** 사용자가 고를 수 있는 범위이다. `site_admin`은 사이트 관리자만 고를 수 있다. */
    fun selectable(siteManager: Boolean): List<String> = if (siteManager) GENERAL + SITE_ADMIN else GENERAL

    /**
     * 쉼표로 이은 범위 문자열을 집합으로 읽는다. 공백과 중복은 정리한다. 모르는 범위, 예약 범위(`workflow` 등), 전체(`*`)는 받지 않는다.
     * @throws IllegalArgumentException 모르는 범위가 있으면
     */
    fun parse(raw: String?): Set<String> {
        val known = GENERAL.toSet() + SITE_ADMIN
        val result = LinkedHashSet<String>()
        raw.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach {
            require(it in known) { "알 수 없는 범위: $it" }
            result += it
        }
        return result
    }

    /** 쉼표로 이은 범위 문자열을 읽되 모르는 범위는 건너뛴다(저장된 값을 읽을 때 쓴다). */
    fun parseOrEmpty(raw: String?): Set<String> {
        val known = GENERAL.toSet() + SITE_ADMIN
        return raw.orEmpty().split(',').map { it.trim() }.filter { it in known }.toCollection(LinkedHashSet())
    }

    /** 범위 집합을 저장용 쉼표 문자열로 만든다. */
    fun format(scopes: Collection<String>): String = scopes.joinToString(",")

    /** [granted]가 [required] 범위를 허용하면 `true`이다(포함 관계를 따른다). */
    fun implies(granted: Set<String>, required: String): Boolean = granted.any { it == required || required in INCLUDES[it].orEmpty() }
}
