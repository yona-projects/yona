package dev.yona.domain.site

import dev.yona.config.YonaProperties
import dev.yona.repository.DataDirectory
import me.desair.tus.server.TusFileUploadService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.io.File
import java.io.InputStream

/**
 * 사이트 관리의 데이터 가져오기에서 쓰는 이어 올리기(TUS 규약) 저장소이다 (X-53, `decisions.md` D-144).
 *
 * 올린 조각은 애플리케이션 데이터 디렉터리 아래 `tus-import`에 둔다. 끝나지 않은 올리기는 [YonaProperties.importUploadExpireHours]가 지나면
 * [cleanup]이 지운다. 한 파일은 [YonaProperties.importUploadMaxBytes]까지만 받는다.
 */
@Component
class TusImportStore(private val properties: YonaProperties, private val dataDirectory: DataDirectory) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** TUS 요청을 처리하는 서비스이다. 처음 쓸 때 만든다. */
    val service: TusFileUploadService by lazy {
        TusFileUploadService()
            .withStoragePath(File(dataDirectory.root(), "tus-import").absolutePath)
            .withUploadUri(UPLOAD_URI)
            .withMaxUploadSize(properties.importUploadMaxBytes)
            .withUploadExpirationPeriod(properties.importUploadExpireHours * 60 * 60 * 1000)
            .withDownloadFeature()
    }

    /** 올리기가 끝난 파일의 내용을 연다. 끝나지 않았거나 없으면 `null`이다. */
    fun open(uploadUri: String): InputStream? {
        val info = runCatching { service.getUploadInfo(uploadUri) }.getOrNull() ?: return null
        return if (info.isUploadInProgress) null else service.getUploadedBytes(uploadUri)
    }

    /** 올린 조각을 지운다. */
    fun delete(uploadUri: String) {
        runCatching { service.deleteUpload(uploadUri) }.onFailure { log.warn("이어 올리기 조각을 지우지 못했습니다: {}", uploadUri, it) }
    }

    /** 만료된 이어 올리기 조각을 한 시간마다 지운다(D-144 정기 정리). */
    @Scheduled(fixedDelay = 60 * 60 * 1000, initialDelay = 5 * 60 * 1000)
    fun cleanup() {
        runCatching { service.cleanup() }.onFailure { log.warn("이어 올리기 조각 정리에 실패했습니다", it) }
    }

    companion object {
        /** 이어 올리기 요청을 받는 주소이다. */
        const val UPLOAD_URI = "/sites/tus"
    }
}
