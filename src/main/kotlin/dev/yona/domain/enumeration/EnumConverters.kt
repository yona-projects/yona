package dev.yona.domain.enumeration

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/**
 * enum을 이름(문자열)으로 저장하는 변환기의 공통 구현이다.
 *
 * `@Enumerated`를 쓰면 Hibernate가 DB 고유 ENUM 타입이나 CHECK 제약을 만든다. v1.6은 20.sql에서 모든
 * enum CHECK 제약을 제거해 새 enum 값을 DB 변경 없이 저장할 수 있게 했으므로, 변환기로 단순한
 * `VARCHAR` 컬럼에 저장해 같은 성질을 유지한다.
 */
abstract class EnumNameConverter<E : Enum<E>>(private val type: Class<E>) : AttributeConverter<E?, String?> {
    override fun convertToDatabaseColumn(attribute: E?): String? = attribute?.name

    override fun convertToEntityAttribute(dbData: String?): E? =
        dbData?.let { name -> type.enumConstants.first { it.name == name } }
}

/**
 * enum을 선언 순서(서수)로 저장하는 변환기의 공통 구현이다. 서수를 쓰는 컬럼은 v1.6의 `State`, `WebhookType`뿐이다.
 * CHECK 제약을 만들지 않는 이유는 [EnumNameConverter]와 같다. 선언 순서를 바꾸면 기존 데이터가 깨진다.
 */
abstract class EnumOrdinalConverter<E : Enum<E>, N : Number>(
    private val type: Class<E>,
    private val toNumber: (Int) -> N
) : AttributeConverter<E?, N?> {
    override fun convertToDatabaseColumn(attribute: E?): N? = attribute?.let { toNumber(it.ordinal) }

    override fun convertToEntityAttribute(dbData: N?): E? = dbData?.let { type.enumConstants[it.toInt()] }
}

@Converter(autoApply = true) class UserStateConverter : EnumNameConverter<UserState>(UserState::class.java)
@Converter(autoApply = true) class ProjectScopeConverter : EnumNameConverter<ProjectScope>(ProjectScope::class.java)
@Converter(autoApply = true) class EventTypeConverter : EnumNameConverter<EventType>(EventType::class.java)
@Converter(autoApply = true) class ResourceTypeConverter : EnumNameConverter<ResourceType>(ResourceType::class.java)
@Converter(autoApply = true) class SideConverter : EnumNameConverter<Side>(Side::class.java)
@Converter(autoApply = true) class ThreadStateConverter : EnumNameConverter<ThreadState>(ThreadState::class.java)
@Converter(autoApply = true)
class PullRequestCommitStateConverter : EnumNameConverter<PullRequestCommitState>(PullRequestCommitState::class.java)
@Converter(autoApply = true) class PropertyNameConverter : EnumNameConverter<PropertyName>(PropertyName::class.java)

/** `issue.state`, `milestone.state`, `pull_request.state`는 `int(11)`이다. */
@Converter(autoApply = true)
class StateConverter : EnumOrdinalConverter<State, Int>(State::class.java, { it })

/** `webhook.webhook_type`은 `tinyint(1)`이다. */
@Converter(autoApply = true)
class WebhookTypeConverter : EnumOrdinalConverter<WebhookType, Byte>(WebhookType::class.java, { it.toByte() })

