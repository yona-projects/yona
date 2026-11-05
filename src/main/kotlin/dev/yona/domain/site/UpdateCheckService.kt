package dev.yona.domain.site

import dev.yona.config.YonaProperties
import dev.yona.domain.enumeration.PropertyName
import dev.yona.domain.misc.PropertyService
import org.eclipse.jgit.api.Git
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.info.BuildProperties
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service

/** 원격 Git 저장소의 태그 이름 목록을 읽는다. 테스트에서는 바꿔 끼운다. */
fun interface RemoteTags {
    /** @return `refs/tags/v1.2.3` 같은 참조 이름들 */
    fun list(repositoryUrl: String): List<String>
}

/** JGit의 `ls-remote`로 태그를 읽는다 (v1.6 `YobiUpdate.fetchVersionToUpdate`). 브랜치는 읽지 않는다. */
@Component
class JGitRemoteTags(private val timeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS) : RemoteTags {
    // 저장소가 응답하지 않아도 화면이 오래 기다리지 않도록 제한 시간을 둔다. v1.6은 시간 제한이 없었다(R-55-3)
    override fun list(repositoryUrl: String): List<String> =
        Git.lsRemoteRepository().setRemote(repositoryUrl).setHeads(false).setTags(true).setTimeout(timeoutSeconds).call().map { it.name }

    private companion object {
        const val DEFAULT_TIMEOUT_SECONDS = 10
    }
}

/**
 * 새 버전이 나왔는지 확인한다. v1.6 `models.YobiUpdate`(`app/models/YobiUpdate.java:38-127`)에 해당한다.
 *
 * 저장소의 태그 중 `v`로 시작하는 것을 버전으로 읽어 현재 버전보다 높은 가장 큰 버전을 찾는다. 읽을 수 없는 태그는 경고만 남기고 건너뛴다.
 * 찾은 값([versionToUpdate])과 알림 여부([shouldNotify])는 서버 전체가 하나를 공유한다(사용자별이 아니다). 알림을 끄면(`unwatch`) 속성 `UPDATE_NOTICE_OFF`에
 * 기록해 서버를 다시 시작해도 꺼져 있다. v1.6은 메모리에만 두어 다시 시작하면 켜졌다(R-55-2).
 */
@Service
class UpdateCheckService(
    private val properties: YonaProperties,
    private val remoteTags: RemoteTags,
    rawCurrentVersion: String,
    private val persisted: PropertyService? = null
) {

    @Autowired
    constructor(properties: YonaProperties, remoteTags: RemoteTags, build: ObjectProvider<BuildProperties>, persisted: PropertyService) :
        this(properties, remoteTags, build.ifAvailable?.version ?: UNKNOWN_VERSION, persisted)

    private val log = LoggerFactory.getLogger(javaClass)

    /** 현재 실행 중인 버전이다 (v1.6 `Config.getCurrentVersion`). */
    val currentVersion: String = rawCurrentVersion

    @Volatile
    private var found: String? = null

    /** 마지막으로 찾은 새 버전이다. 없으면 `null`이다. */
    val versionToUpdate: String? get() = found

    /** 알림을 켜 두었는지이다. 처음 쓸 때 속성에서 읽고 이후에는 메모리 값을 쓴다(화면마다 DB를 읽지 않는다). */
    @Volatile
    private var watched: Boolean? = null

    private fun isWatched(): Boolean = watched ?: (persisted?.get(PropertyName.UPDATE_NOTICE_OFF) != "true").also { watched = it }

    /**
     * 저장소를 읽어 새 버전을 찾고 [versionToUpdate]를 갱신한다. 호출한 스레드에서 바로 실행한다.
     *
     * @return 새 버전. 없으면 `null`
     * @throws Exception 저장소를 읽지 못했을 때. 이전 값은 그대로 남는다
     */
    fun refresh(): String? {
        var latest = Semver.parse(Semver.semverize(currentVersion))
        var updateRequired = false
        for (name in remoteTags.list(properties.update.repositoryUrl)) {
            val tag = name.removePrefix("refs/tags/")
            if (!tag.startsWith("v")) continue
            val version = try {
                Semver.parse(Semver.semverize(tag))
            } catch (e: IllegalArgumentException) {
                log.warn("Failed to parse a version: {}", tag)
                continue
            }
            if (version > latest) {
                latest = version
                updateRequired = true
            }
        }
        found = if (updateRequired) latest.toString() else null
        return found
    }

    /** 새 버전이 있고 알림을 끄지 않았으면 `true`이다. 알림은 사이트 관리자에게만 보인다. */
    fun shouldNotify(): Boolean = isWatched() && versionToUpdate != null

    /** 알림을 끈다. 서버 전체에 적용되고 서버를 다시 시작해도 꺼져 있다(v1.6 `isWatched = false`는 다시 시작하면 켜졌다, R-55-2). */
    fun unwatch() {
        watched = false
        persisted?.set(PropertyName.UPDATE_NOTICE_OFF, "true")
    }

    /** 릴리스 페이지 주소이다 (v1.6 `YobiUpdate.getReleaseUrl`). */
    fun releaseUrl(version: String): String = String.format(properties.update.releaseUrlFormat, version)

    /** 확인한 값과 알림 설정을 처음 상태로 되돌린다. 테스트에서 쓴다. */
    fun reset() {
        found = null
        watched = true
        persisted?.set(PropertyName.UPDATE_NOTICE_OFF, "false")
    }

    private companion object {
        const val UNKNOWN_VERSION = "0.0.0"
    }
}
