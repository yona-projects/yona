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

    // yona 자체 신규 기능 — legacy `-_-api/v1` Open API 네임스페이스에는 통계 API가 존재한 적이
    // 없다(legacy는 `/:user/:project/statistics` HTML 화면뿐). 이전에는 이 경로가 legacy Open API
    // 목록에 있는 것처럼 잘못 놓여 있었으므로 yona 자체 컨벤션(`/api/...`)으로 옮긴다.
    @GetMapping("/api/users/{loginId}/statistics")
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

