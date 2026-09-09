package com.github.yonaprojects.yona.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.support.ContextPropagatingTaskDecorator
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

@Configuration
@EnableAsync
class AsyncConfig {

    @Bean(name = ["taskExecutor"])
    fun taskExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 5
        executor.maxPoolSize = 10
        executor.queueCapacity = 100
        executor.setThreadNamePrefix("yona-async-")
        // 트레이싱 계측 대응 — 기본 ThreadPoolTaskExecutor는
        // ThreadLocal 기반 트레이스 컨텍스트(현재 Span 등)를 워커 스레드로 넘기지 않는다.
        // Micrometer Context Propagation(ContextRegistry)에 등록된 ThreadLocalAccessor를 통해
        // @Async("taskExecutor") 경계를 넘어서도 trace-id가 끊기지 않게 한다.
        executor.setTaskDecorator(ContextPropagatingTaskDecorator())
        executor.initialize()
        return executor
    }
}
