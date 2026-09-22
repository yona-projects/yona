package com.github.yonaprojects.yona.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType

// @RestController가 도메인 엔티티를 raw로 반환하면 Jackson이 entity->project->
// projectUsers->user 순환을 타고 User.password까지 직렬화할 수 있다. 매번 사람이 감사해서
// 잡는 대신, 같은 실수가 생기면 빌드 자체가 실패하도록 강제한다. User.password/
// passwordSalt/token 필드의 @JsonIgnore(최종 방어선, UserJsonSerializationSpec)와는 서로
// 다른 구멍을 막는 상호보완 장치 — 이 테스트는 Map<String, Any>처럼 타입을 지워서 반환하는
// 경우는 못 잡는다(정적 타입 검사의 한계).
private const val DOMAIN_PACKAGE_PREFIX = "com.github.yonaprojects.yona.domain."
private const val FIXTURE_PACKAGE = "com.github.yonaprojects.yona.architecture.fixtures"

private val MAPPING_ANNOTATIONS = listOf(
    RequestMapping::class.java,
    GetMapping::class.java,
    PostMapping::class.java,
    PutMapping::class.java,
    PatchMapping::class.java,
    DeleteMapping::class.java
)

private fun findLeakedDomainEntity(type: Type, seen: MutableSet<Type> = mutableSetOf()): String? {
    if (!seen.add(type)) return null
    return when (type) {
        is Class<*> -> if (!type.isEnum && type.name.startsWith(DOMAIN_PACKAGE_PREFIX)) type.name else null
        is ParameterizedType ->
            findLeakedDomainEntity(type.rawType, seen)
                ?: type.actualTypeArguments.firstNotNullOfOrNull { findLeakedDomainEntity(it, seen) }
        is WildcardType ->
            (type.upperBounds.asList() + type.lowerBounds.asList())
                .firstNotNullOfOrNull { findLeakedDomainEntity(it, seen) }
        else -> null // TypeVariable/GenericArrayType은 이 코드베이스의 컨트롤러 시그니처에 등장하지 않는다.
    }
}

private fun scanViolations(basePackage: String): List<String> {
    val importedClasses = ClassFileImporter().importPackages(basePackage)
    return importedClasses
        .filter { it.isAnnotatedWith(RestController::class.java) }
        .flatMap { controllerClass ->
            controllerClass.methods
                .filter { method -> MAPPING_ANNOTATIONS.any { method.isAnnotatedWith(it) } }
                .mapNotNull { method ->
                    val leaked = findLeakedDomainEntity(method.reflect().genericReturnType)
                    leaked?.let { "${controllerClass.name}#${method.name}() -> $it" }
                }
        }
}

class RestControllerReturnTypeArchTest : DescribeSpec({
    describe("@RestController 엔드포인트 반환 타입") {
        it("도메인 엔티티(com.github.yonaprojects.yona.domain..*)를 직접/제네릭 인자로 반환해서는 안 된다") {
            val violations = scanViolations("com.github.yonaprojects.yona")
                .filterNot { it.startsWith(FIXTURE_PACKAGE) }

            violations.shouldBeEmpty()
        }

        // 이 검사 로직 자체가 실제로 위반을 잡아내는지(silently vacuous하지 않은지) 증명하는
        // 자체 검증. LeakyDemoController(fixtures 패키지)는 어떤 @ComponentScan에도 잡히지
        // 않는 테스트 전용 픽스처라 실제 서버에는 절대 등록되지 않는다.
        it("자체 검증: 실제로 도메인 엔티티를 반환하는 컨트롤러가 있으면 위반으로 잡아낸다") {
            val violations = scanViolations(FIXTURE_PACKAGE)

            violations.shouldNotBeEmpty()
        }
    }
})
