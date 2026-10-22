package dev.yona.markdown

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** 마크다운 렌더러가 쓰는 빈을 등록한다. */
@Configuration
class MarkdownConfig {

    /** 렌더링 결과 캐시이다. 프로세스에서 하나만 둔다. */
    @Bean
    fun renderedMarkdownCache(): RenderedMarkdownCache = RenderedMarkdownCache()
}
