package dev.yona.web

import dev.yona.util.UrlPaths
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.thymeleaf.context.IExpressionContext
import org.thymeleaf.dialect.AbstractDialect
import org.thymeleaf.dialect.IExpressionObjectDialect
import org.thymeleaf.expression.IExpressionObjectFactory

/**
 * 템플릿에서 프로젝트와 조직의 주소 앞부분을 만드는 표현식 객체이다(`${#paths.project(owner, name)}`).
 *
 * v1.6의 역방향 라우트(`routes.IssueApp.issues(owner, name)` 등)는 경로 변수를 퍼센트 인코딩하므로 한글이나 공백이 든 이름은
 * `/tester/%ED%95%9C...`처럼 나온다. 템플릿에서 이름을 문자열로 이어 붙이면 날것의 한글이 속성에 남아 공식 릴리스와 달라지므로,
 * 이어 붙이는 대신 이 객체를 쓴다. 빈 참조(`@빈`)는 `th:with` 밖의 속성에서 Thymeleaf가 막으므로 표현식 객체로 둔다.
 */
class TemplatePaths {

    /** `/소유자/이름`을 인코딩해서 돌려준다. */
    fun project(owner: String?, name: String?): String = UrlPaths.projectPath(owner, name)

    /** `/organizations/조직이름`을 인코딩해서 돌려준다. */
    fun organization(name: String?): String = UrlPaths.organizationPath(name)

    /** 한 조각만 인코딩한다. */
    fun segment(value: String?): String = UrlPaths.encode(value.orEmpty())
}

/** [TemplatePaths]를 `#paths`라는 이름의 표현식 객체로 알리는 Thymeleaf 방언이다. */
class TemplatePathsDialect : AbstractDialect("paths"), IExpressionObjectDialect {

    private val paths = TemplatePaths()

    private val factory = object : IExpressionObjectFactory {
        override fun getAllExpressionObjectNames(): Set<String> = setOf(NAME)
        override fun buildObject(context: IExpressionContext, expressionObjectName: String): Any? = if (expressionObjectName == NAME) paths else null
        override fun isCacheable(expressionObjectName: String): Boolean = true
    }

    override fun getExpressionObjectFactory(): IExpressionObjectFactory = factory

    private companion object {
        const val NAME = "paths"
    }
}

/** 방언을 템플릿 엔진에 등록한다(Spring Boot가 [org.thymeleaf.dialect.IDialect] 빈을 모두 엔진에 더한다). */
@Configuration
class TemplatePathsConfig {
    @Bean
    fun templatePathsDialect(): TemplatePathsDialect = TemplatePathsDialect()
}
