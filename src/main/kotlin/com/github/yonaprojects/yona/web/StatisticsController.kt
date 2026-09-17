package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.support.StatisticsService
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.ResponseBody

@Controller
class StatisticsController(
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val statisticsService: StatisticsService
) {

    @GetMapping("/projects/{owner}/{projectName}/statistics")
    fun statistics(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        model.addAttribute("project", project)
        return "project/statistics"
    }

    // legacy `UserApi.java`가 issue/posting/assignedIssue/issueComment/postingComment/
    // issueVoter/issueCommentVoter 집계를 그대로 반환하던 `-_-api/v1/users/:user/statistics`와
    // 동일한 경로를 쓴다.
    @GetMapping("/-_-api/v1/users/{loginId}/statistics")
    @ResponseBody
    fun userStatistics(
        @PathVariable loginId: String
    ): ResponseEntity<UserStatisticsResponse> {
        val user = userRepository.findByLoginId(loginId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val response = statisticsService.getUserStatistics(user.id!!)
        return ResponseEntity.ok(response)
    }
}

