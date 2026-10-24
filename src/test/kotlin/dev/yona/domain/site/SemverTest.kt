package dev.yona.domain.site

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 업데이트 확인이 쓰는 버전 정규화와 비교(`Semver`)를 검증한다. 원본: `app/utils/Config.java:228-240`(`semverize`), `YobiUpdate.java:91-127`. */
class SemverTest {

    @Test
    fun `태그를 의미 있는 버전 문자열로 바꾼다`() {
        assertEquals("1.2.3", Semver.semverize("v1.2.3"))
        assertEquals("0.5.0", Semver.semverize("0.5"))
        assertEquals("0.4.0-pre", Semver.semverize("0.4.0.pre"))
        assertEquals("1.0.0-rc.1", Semver.semverize("v1.0.0-rc.1"))
        assertEquals("1.16.0", Semver.semverize("v1.16.0"))
    }

    @Test
    fun `버전을 크기 순으로 비교한다`() {
        assertTrue(Semver.parse("1.16.1") > Semver.parse("1.16.0"))
        assertTrue(Semver.parse("1.17.0") > Semver.parse("1.9.9"))
        assertTrue(Semver.parse("2.0.0") > Semver.parse("1.99.99"))
    }

    @Test
    fun `프리릴리스는 같은 번호의 정식 버전보다 낮다`() {
        assertTrue(Semver.parse("1.0.0-rc.1") < Semver.parse("1.0.0"))
        assertTrue(Semver.parse("1.0.0-alpha") < Semver.parse("1.0.0-beta"))
        assertTrue(Semver.parse("1.0.0-rc.2") > Semver.parse("1.0.0-rc.1"))
        assertTrue(Semver.parse("1.0.0-rc.10") > Semver.parse("1.0.0-rc.2"))
        assertTrue(Semver.parse("1.0.0-1") < Semver.parse("1.0.0-alpha"))
    }

    @Test
    fun `빌드 메타데이터는 비교에 쓰지 않는다`() {
        assertEquals(0, Semver.parse("1.0.0+build.1").compareTo(Semver.parse("1.0.0+build.2")))
    }

    @Test
    fun `올바르지 않은 버전은 오류이다`() {
        assertFailsWith<IllegalArgumentException> { Semver.parse("not-a-version") }
        assertFailsWith<IllegalArgumentException> { Semver.parse("1.2") }
    }
}
