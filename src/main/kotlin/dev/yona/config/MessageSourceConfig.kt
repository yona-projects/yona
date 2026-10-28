package dev.yona.config

import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.MessageSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.support.ResourceBundleMessageSource
import org.springframework.core.env.Environment
import org.springframework.util.StringUtils

/**
 * 메시지 소스를 [RestrictedMessageSource]로 만든다. 스프링 부트가 만들어 주는 메시지 소스는 이 빈이 있으면 만들어지지 않으므로, 같은 설정 키
 * (`spring.messages.*`)를 읽어 같은 모양의 번들 메시지 소스를 직접 만든 뒤 지원 언어 목록으로 한정한다.
 */
@Configuration(proxyBeanMethods = false)
class MessageSourceConfig {

    @Bean(name = ["messageSource"])
    fun messageSource(environment: Environment, properties: ObjectProvider<YonaProperties>): MessageSource {
        val bundle = ResourceBundleMessageSource().apply {
            setBasenames(*StringUtils.commaDelimitedListToStringArray(StringUtils.trimAllWhitespace(environment.getProperty("spring.messages.basename", "messages"))))
            setDefaultEncoding(environment.getProperty("spring.messages.encoding", "UTF-8"))
            setFallbackToSystemLocale(environment.getProperty("spring.messages.fallback-to-system-locale", Boolean::class.java, true))
            setAlwaysUseMessageFormat(environment.getProperty("spring.messages.always-use-message-format", Boolean::class.java, false))
            setUseCodeAsDefaultMessage(environment.getProperty("spring.messages.use-code-as-default-message", Boolean::class.java, false))
        }
        return RestrictedMessageSource(bundle) { SupportedLocales.of(properties.getObject().langs) }
    }
}
