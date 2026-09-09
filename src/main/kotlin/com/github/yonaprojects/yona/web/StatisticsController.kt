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

    // yona UserApi.statistics() 대응 — legacy 원본 경로는 `-_-api/v1/users/:user/statistics`다.
    // 이 클래스 신설 당시 "legacy Open API 네임스페이스에 통계 API가 존재한 적이 없다"고 잘못
    // 판단해 `/api/...`로만 이식했는데, 실제로는 legacy `UserApi.java`에 issue/posting/
    // assignedIssue/issueComment/postingComment/issueVoter/issueCommentVoter 집계를 그대로
    // 반환하는 동일한 기능이 있었다(원본 소스로 재확인). 기존 경로는 유지한 채 원본 경로를 별칭으로
    // 되돌린다.
    @GetMapping(value = ["/api/users/{loginId}/statistics", "/-_-api/v1/users/{loginId}/statistics"])
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

