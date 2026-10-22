package dev.yona.repository

import dev.yona.config.YonaProperties
import org.springframework.stereotype.Component
import java.io.File

/**
 * 저장소와 첨부 파일을 두는 데이터 디렉터리를 알려 준다 (D-16).
 *
 * 우선순위는 설정 `yona.data-dir`, JVM 시스템 속성 `yona.data`(v1.6 `utils.Config.getYonaDataDir`), 작업 디렉터리 순이다.
 * 기존 v1.6 설치의 `-Dyona.data`와 그 아래 디렉터리 구조(`repo/git/...`)를 그대로 쓸 수 있다.
 */
@Component
class DataDirectory(private val properties: YonaProperties) {

    /** 데이터 디렉터리의 루트이다. 아무것도 지정하지 않으면 작업 디렉터리를 가리키는 빈 경로이다. */
    fun root(): File {
        val configured = properties.dataDir.ifEmpty { System.getProperty("yona.data").orEmpty() }
        return File(configured)
    }
}
