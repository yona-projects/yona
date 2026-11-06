package dev.yona.domain.vcs

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** 저장소 종류와 종류별 능력 선언을 확인한다. `project.vcs` 열 값은 v1.6과 같은 문자열이다. */
class VcsTypeTest {

    @Test
    fun `열 값을 종류로 읽는다`() {
        assertThat(VcsType.of("GIT")).isEqualTo(VcsType.GIT)
        assertThat(VcsType.of("Subversion")).isEqualTo(VcsType.SUBVERSION)
    }

    @Test
    fun `모르는 값과 비어 있는 값은 종류가 없다`() {
        assertThat(VcsType.of(null)).isNull()
        assertThat(VcsType.of("")).isNull()
        // v1.6은 대소문자를 구분해 비교했다(`"GIT".equals(vcs)`)
        assertThat(VcsType.of("git")).isNull()
        assertThat(VcsType.of("SUBVERSION")).isNull()
    }

    @Test
    fun `열 값은 v1_6과 같다`() {
        assertThat(VcsType.GIT.columnValue).isEqualTo("GIT")
        assertThat(VcsType.SUBVERSION.columnValue).isEqualTo("Subversion")
    }

    @Test
    fun `Git은 v1_6에서 Git 전용이던 기능을 모두 지원한다`() {
        for (capability in GIT_ONLY_IN_V16) {
            assertThat(VcsType.GIT.supports(capability)).describedAs("Git의 $capability").isTrue()
        }
    }

    @Test
    fun `Subversion은 v1_6에서 Git 전용이던 기능을 지원하지 않는다`() {
        for (capability in GIT_ONLY_IN_V16) {
            assertThat(VcsType.SUBVERSION.supports(capability)).describedAs("Subversion의 $capability").isFalse()
        }
    }

    @Test
    fun `프로젝트의 열 값으로 바로 능력을 묻는다`() {
        assertThat(VcsCapabilities.supports("GIT", VcsCapability.PULL_REQUESTS)).isTrue()
        assertThat(VcsCapabilities.supports("Subversion", VcsCapability.PULL_REQUESTS)).isFalse()
        assertThat(VcsCapabilities.supports(null, VcsCapability.PULL_REQUESTS)).isFalse()
        assertThat(VcsCapabilities.supports("Mercurial", VcsCapability.PULL_REQUESTS)).isFalse()
    }

    private companion object {
        /** v1.6에서 `@IsOnlyGitAvailable`이 붙었던 기능이다. */
        val GIT_ONLY_IN_V16 = listOf(
            VcsCapability.BRANCHES, VcsCapability.TAGS, VcsCapability.FORK, VcsCapability.PULL_REQUESTS,
            VcsCapability.DEPLOY_KEYS, VcsCapability.LFS, VcsCapability.PROTECTED_REFS
        )
    }
}
