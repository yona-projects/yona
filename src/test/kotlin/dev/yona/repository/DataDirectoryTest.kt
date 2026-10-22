package dev.yona.repository

import dev.yona.config.YonaProperties
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals

/**
 * 데이터 디렉터리 결정 규칙을 검증한다 (D-16).
 *
 * 우선순위: 설정 `yona.data-dir` > JVM 시스템 속성 `yona.data`(v1.6 `utils.Config.getYonaDataDir`) > 작업 디렉터리.
 */
class DataDirectoryTest {

    @AfterEach
    fun clearSystemProperty() {
        System.clearProperty("yona.data")
    }

    @Test
    fun `설정 값을 쓴다`() {
        assertEquals(File("/var/yona"), DataDirectory(YonaProperties(dataDir = "/var/yona")).root())
    }

    @Test
    fun `설정이 없으면 v1_6의 시스템 속성을 쓴다`() {
        System.setProperty("yona.data", "/legacy/data")
        assertEquals(File("/legacy/data"), DataDirectory(YonaProperties()).root())
    }

    @Test
    fun `설정이 시스템 속성보다 우선한다`() {
        System.setProperty("yona.data", "/legacy/data")
        assertEquals(File("/var/yona"), DataDirectory(YonaProperties(dataDir = "/var/yona")).root())
    }

    @Test
    fun `둘 다 없으면 작업 디렉터리를 쓴다`() {
        assertEquals(File(""), DataDirectory(YonaProperties()).root())
    }
}
