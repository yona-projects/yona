package dev.yona.markdown

import dev.yona.config.YonaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** 마크다운 렌더러가 쓰는 빈을 등록한다. */
@Configuration
class MarkdownConfig {

    /** 렌더링 결과 캐시이다. 프로세스에서 하나만 둔다. */
    @Bean
    fun renderedMarkdownCache(): RenderedMarkdownCache = RenderedMarkdownCache()

    /** 렌더링한 HTML의 링크 후처리기이다. 설정과 호스트 이름은 기동할 때 정한다. */
    @Bean
    fun linkPostProcessor(properties: YonaProperties, hostname: SiteHostname): LinkPostProcessor =
        LinkPostProcessor(properties.noreferrer, hostname.name())
}
