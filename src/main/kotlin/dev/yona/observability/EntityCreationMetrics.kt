package dev.yona.observability

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import jakarta.annotation.PostConstruct
import jakarta.persistence.EntityManagerFactory
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.event.service.spi.EventListenerRegistry
import org.hibernate.event.spi.EventType
import org.hibernate.event.spi.PostCommitInsertEventListener
import org.hibernate.event.spi.PostInsertEvent
import org.hibernate.persister.entity.EntityPersister
import org.springframework.stereotype.Component

/**
 * 협업 객체의 생성 수 지표이다(로드맵 X-10 C5d, `decisions.md` A-320 6번): `yona.entities.created{entity}`. 서비스마다 흩어진 생성 경로(화면, API, 가져오기)를
 * 따로 세지 않고, **커밋된 삽입**을 하이버네이트 이벤트로 한 곳에서 센다. 롤백된 삽입은 세지 않는다. 허용 목록([ENTITIES])의 엔티티만 세며 이름과 번호는 태그에 넣지 않는다.
 * DB를 세는 gauge가 아니라 카운터이므로 DB 부하가 없다.
 */
@Component
class EntityCreationMetrics(private val entityManagerFactory: EntityManagerFactory, private val registry: MeterRegistry) : PostCommitInsertEventListener {

    @PostConstruct
    fun register() {
        entityManagerFactory.unwrap(SessionFactoryImplementor::class.java).serviceRegistry.getService(EventListenerRegistry::class.java)!!
            .appendListeners(EventType.POST_COMMIT_INSERT, this)
    }

    override fun onPostInsert(event: PostInsertEvent) {
        val name = ENTITIES[event.entity.javaClass.simpleName] ?: return
        Counter.builder("yona.entities.created").tag("entity", name).description("커밋된 협업 객체 생성 수").register(registry).increment()
    }

    override fun onPostInsertCommitFailed(event: PostInsertEvent) {}

    override fun requiresPostCommitHandling(persister: EntityPersister): Boolean = true

    companion object {
        /** 세는 엔티티(클래스 이름)와 태그 값이다. */
        val ENTITIES = mapOf(
            "Project" to "project", "User" to "user", "Issue" to "issue", "IssueComment" to "issue_comment", "Posting" to "post", "PostingComment" to "post_comment",
            "PullRequest" to "pull_request", "ReviewComment" to "review_comment", "Attachment" to "attachment"
        )
    }
}
