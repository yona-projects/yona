package dev.yona.util

import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.util.concurrent.ConcurrentHashMap

/**
 * 요청 하나 안에서만 쓰는 조회 결과 보관소이다(`decisions.md` A-628). 같은 요청에서 같은 프로젝트나 사용자를 여러 번 읽는 질의를 한 번으로 줄인다.
 *
 * 이 앱은 요청 전체를 하나의 영속성 컨텍스트로 묶지 않는다(`spring.jpa.open-in-view: false`). 접근 검사, 프로젝트 확인, 컨트롤러가 각자 세션을 열어 같은 행을 다시 읽는데,
 * PostgreSQL에서는 질의당 0.05ms라 티가 나지 않지만 질의 한 번이 약 1ms인 DB(Cubrid)에서는 이슈 목록 화면 지연의 상당 부분이 된다.
 *
 * 안전을 위해 규칙을 좁게 둔다.
 * - **읽기 요청(GET, HEAD)에서만** 보관한다. 쓰기 요청은 읽은 뒤 바꾸므로 낡은 값이 남을 수 있다.
 * - 요청 밖(스케줄러, 백그라운드 스레드)에서는 보관하지 않는다.
 * - 값이 없으면(`null`) 보관하지 않아 나중에 생기면 읽힌다.
 * - [entity]는 **트랜잭션 밖에서만** 보관한다. 트랜잭션 안에서는 하이버네이트의 1차 캐시가 같은 id의 중복 조회를 이미 막고, 서로 다른 세션의 엔터티를 섞으면
 *   지연 로딩이 닫힌 세션에서 실패할 수 있다. 트랜잭션 밖에서 읽은 엔터티는 어차피 분리 상태이고 모든 호출자가 같은 상태를 보므로 공유해도 같다.
 * - [value]는 문자열 같은 일반 값만 보관한다(엔터티가 아니다). 트랜잭션과 무관하게 보관할 수 있어, 검색 결과 행마다 작성자 이름을 읽는 것처럼 JPQL 질의가
 *   매번 DB로 가는 반복을 줄인다.
 */
object RequestMemo {

    private const val ATTRIBUTE = "dev.yona.util.RequestMemo"

    /** 엔터티를 읽는다. 읽기 요청이고 트랜잭션 밖이면 같은 [key]로 한 번만 [load]한다. */
    fun <T : Any> entity(key: String, load: () -> T?): T? =
        if (TransactionSynchronizationManager.isActualTransactionActive()) load() else memo(key, load)

    /** 일반 값(엔터티가 아닌 것)을 읽는다. 읽기 요청이면 같은 [key]로 한 번만 [load]한다. */
    fun <T : Any> value(key: String, load: () -> T?): T? = memo(key, load)

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> memo(key: String, load: () -> T?): T? {
        val attributes = RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes ?: return load()
        if (attributes.request.method.uppercase() !in READ_METHODS) return load()
        val map = attributes.getAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST) as? ConcurrentHashMap<String, Any>
            ?: ConcurrentHashMap<String, Any>().also { attributes.setAttribute(ATTRIBUTE, it, RequestAttributes.SCOPE_REQUEST) }
        map[key]?.let { return it as T }
        val loaded = load() ?: return null
        map[key] = loaded
        return loaded
    }

    private val READ_METHODS = setOf("GET", "HEAD")
}
