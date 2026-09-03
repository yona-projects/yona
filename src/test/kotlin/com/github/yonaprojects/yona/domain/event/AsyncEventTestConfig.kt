package com.github.yonaprojects.yona.domain.event

import com.github.yonaprojects.yona.domain.mail.MailService
import com.github.yonaprojects.yona.domain.project.GitService
import io.mockk.mockk
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration
class AsyncEventTestConfig {
    @Bean
    @Primary
    fun mockMailService(): MailService = mockk(relaxed = true)

    @Bean
    @Primary
    fun mockGitService(): GitService = mockk(relaxed = true)
}
