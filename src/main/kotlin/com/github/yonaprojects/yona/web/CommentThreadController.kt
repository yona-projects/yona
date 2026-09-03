package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.enumeration.Operation
import com.github.yonaprojects.yona.domain.pullrequest.CommentThread.ThreadState
import com.github.yonaprojects.yona.domain.pullrequest.CodeReviewService
import com.github.yonaprojects.yona.domain.pullrequest.CommentThreadRepository
import com.github.yonaprojects.yona.domain.user.UserRepository
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseBody

@Controller
class CommentThreadController(
    private val codeReviewService: CodeReviewService,
    private val userRepository: UserRepository,
    private val commentThreadRepository: CommentThreadRepository,
    private val accessControl: AccessControl
) {

    @PostMapping("/threads/{id}/open")
    @ResponseBody
    fun open(
        @PathVariable id: Long,
        authentication: Authentication?
    ): ResponseEntity<Unit> = updateState(id, ThreadState.OPEN, authentication)

    @PostMapping("/threads/{id}/close")
    @ResponseBody
    fun close(
        @PathVariable id: Long,
        authentication: Authentication?
    ): ResponseEntity<Unit> = updateState(id, ThreadState.CLOSED, authentication)

    private fun updateState(
        id: Long,
        state: ThreadState,
        authentication: Authentication?
    ): ResponseEntity<Unit> {
        val user = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return ResponseEntity.status(401).build()

        val thread = commentThreadRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val project = thread.project
            ?: return ResponseEntity.notFound().build()

        val operation = when (state) {
            ThreadState.OPEN -> Operation.REOPEN
            ThreadState.CLOSED -> Operation.CLOSE
        }

        if (!accessControl.isAllowed(user, project, thread, operation)) {
            return ResponseEntity.status(403).build()
        }

        codeReviewService.updateThreadState(id, state, user)
        return ResponseEntity.ok().build()
    }
}

