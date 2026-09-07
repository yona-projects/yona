package com.github.yonaprojects.yona.domain.vcs

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

// yona-wiki P3-12(Mercurial 지원) 1라운드 — GIT<->SUBVERSION 2지선다 토글을 3종 순환으로 확장한
// nextVcsInCycle()의 순수 로직 검증.
class VcsTypeSpec : DescribeSpec({

    describe("nextVcsInCycle") {
        it("GIT 다음은 SUBVERSION") {
            nextVcsInCycle("GIT") shouldBe "SUBVERSION"
        }

        it("SUBVERSION 다음은 MERCURIAL") {
            nextVcsInCycle("SUBVERSION") shouldBe "MERCURIAL"
        }

        it("MERCURIAL 다음은 다시 GIT로 순환") {
            nextVcsInCycle("MERCURIAL") shouldBe "GIT"
        }

        it("null이면 GIT을 현재값으로 간주해 SUBVERSION을 반환") {
            nextVcsInCycle(null) shouldBe "SUBVERSION"
        }

        it("대소문자·별칭(SVN/HG)도 정규화해서 순환한다") {
            nextVcsInCycle("svn") shouldBe "MERCURIAL"
            nextVcsInCycle("hg") shouldBe "GIT"
            nextVcsInCycle("git") shouldBe "SUBVERSION"
        }
    }
})
