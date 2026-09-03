package com.github.yonaprojects.yona.domain.support

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.pullrequest.CommentThread
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface ReviewThreadService {
    fun getReviewThreads(project: Project, condition: ReviewSearchCondition, pageable: Pageable): Page<CommentThread>
    fun getReviewThreads(project: Project, condition: ReviewSearchCondition): List<CommentThread>
    fun countReviewThreads(project: Project, condition: ReviewSearchCondition): Long
}
