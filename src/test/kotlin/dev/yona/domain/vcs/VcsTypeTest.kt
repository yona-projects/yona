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

    @Test
    fun `Git은 화면과 API가 묻는 나머지 기능도 지원하고 리비전 번호는 쓰지 않는다`() {
        for (capability in GIT_ONLY_IN_V16 + OTHER_GIT_FEATURES) assertThat(VcsType.GIT.supports(capability)).describedAs("Git의 $capability").isTrue()
        assertThat(VcsType.GIT.supports(VcsCapability.NUMERIC_REVISIONS)).isFalse()
        assertThat(VcsType.GIT.supports(VcsCapability.AUTHOR_LOGIN_IDS)).isFalse()
    }

    @Test
    fun `Subversion은 리비전 번호를 쓰고 Git이 지원하는 나머지 기능은 지원하지 않는다`() {
        for (capability in OTHER_GIT_FEATURES) assertThat(VcsType.SUBVERSION.supports(capability)).describedAs("Subversion의 $capability").isFalse()
        assertThat(VcsType.SUBVERSION.supports(VcsCapability.NUMERIC_REVISIONS)).isTrue()
        assertThat(VcsType.SUBVERSION.supports(VcsCapability.AUTHOR_LOGIN_IDS)).isTrue()
    }

    @Test
    fun `템플릿이 쓰는 지원 목록은 능력 선언과 같다`() {
        val git = VcsSupport("GIT")
        assertThat(git.pullRequests).isTrue()
        assertThat(git.fork).isTrue()
        assertThat(git.protectedRefs).isTrue()
        assertThat(git.deployKeys).isTrue()
        assertThat(git.lfs).isTrue()
        assertThat(git.onlineEditing).isTrue()
        assertThat(git.numericRevisions).isFalse()
        val svn = VcsSupport("Subversion")
        assertThat(svn.pullRequests).isFalse()
        assertThat(svn.onlineEditing).isFalse()
        assertThat(svn.numericRevisions).isTrue()
        // 모르는 값은 아무것도 지원하지 않는다(v1.6의 `"GIT".equals(vcs)`가 거짓인 것과 같다)
        assertThat(VcsSupport(null).pullRequests).isFalse()
        assertThat(VcsSupport(null).numericRevisions).isFalse()
        assertThat(VcsSupport(null).authorLoginIds).isFalse()
    }

    private companion object {
        /** v1.6에서 `project.isGit` 등으로 Git에서만 열던 나머지 기능이다. */
        val OTHER_GIT_FEATURES = listOf(
            VcsCapability.DEFAULT_BRANCH, VcsCapability.README_POSTING, VcsCapability.ARCHIVE_DOWNLOAD, VcsCapability.ONLINE_EDITING,
            VcsCapability.LINE_COMMENT_THREADS, VcsCapability.COMMIT_EMAILS
        )


        /** v1.6에서 `@IsOnlyGitAvailable`이 붙었던 기능이다. */
        val GIT_ONLY_IN_V16 = listOf(
            VcsCapability.BRANCHES, VcsCapability.TAGS, VcsCapability.FORK, VcsCapability.PULL_REQUESTS,
            VcsCapability.DEPLOY_KEYS, VcsCapability.LFS, VcsCapability.PROTECTED_REFS
        )
    }
}
