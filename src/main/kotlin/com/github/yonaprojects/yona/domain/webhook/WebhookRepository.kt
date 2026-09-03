package com.github.yonaprojects.yona.domain.webhook

import org.springframework.data.jpa.repository.JpaRepository

interface WebhookRepository : JpaRepository<Webhook, Long> {
    fun findByProjectId(projectId: Long): List<Webhook>
    fun existsByHash(hash: String): Boolean {
        // Ebean Etag existsByHash 호환용 스키마에 따라 필요시 재정의 가능하지만,
        // 웹훅에 직접 Hash를 쓸 일은 드물며 Mocking 명세를 위해 보존
        return false
    }
}
