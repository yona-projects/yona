package com.github.yonaprojects.yona.domain.support

import com.github.yonaprojects.yona.web.UserStatisticsResponse

interface StatisticsService {
    fun getUserStatistics(userId: Long): UserStatisticsResponse
}
