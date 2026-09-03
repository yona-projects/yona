package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.notification.NotificationEventRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
class NotificationController(
    private val notificationEventRepository: NotificationEventRepository,
    private val userRepository: UserRepository
) {

    data class NotificationResponse(
        val id: Long,
        val title: String,
        val senderId: Long?,
        val created: Instant?,
        val resourceType: String,
        val resourceId: String,
        val eventType: String
    )

    private fun getLoginUser(authentication: Authentication?): User {
        if (authentication == null) throw IllegalArgumentException("Unauthorized")
        return userRepository.findByLoginId(authentication.name)
            .orElseThrow { IllegalArgumentException("User not found") }
    }

    @GetMapping("/api/notifications")
    fun getNotifications(
        @RequestParam(defaultValue = "0") from: Int,
        @RequestParam(defaultValue = "10") size: Int,
        authentication: Authentication?
    ): ResponseEntity<List<NotificationResponse>> {
        val user = try {
            getLoginUser(authentication)
        } catch (e: Exception) {
            return ResponseEntity.status(401).build()
        }

        // PageRequest의 page index 계산 (from 인덱스를 size로 나누어 계산)
        val pageIndex = if (size > 0) from / size else 0
        val pageSize = if (size > 0) size else 10
        val pageable = PageRequest.of(pageIndex, pageSize)
        
        val notificationPage = notificationEventRepository.findByReceiver(user, pageable)
        val responseList = notificationPage.content.map { event ->
            NotificationResponse(
                id = event.id ?: 0L,
                title = event.title,
                senderId = event.senderId,
                created = event.created,
                resourceType = event.resourceType.name,
                resourceId = event.resourceId,
                eventType = event.eventType.name
            )
        }

        return ResponseEntity.ok(responseList)
    }
}
