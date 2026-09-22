package com.github.yonaprojects.yona.config

import com.github.yonaprojects.yona.domain.enumeration.State
import org.springframework.core.convert.converter.Converter
import org.springframework.stereotype.Component

// State는 자기 자신을 소문자(OPEN("open") 등)로 표현하고 State.getValue()로 그걸 다시 파싱하는
// 헬퍼까지 갖고 있지만, @RequestParam state: State로 직접 바인딩하는 컨트롤러들은 이 헬퍼를 거치지
// 않고 Spring 기본 규칙(Enum.valueOf, 상수명 그대로인 대문자)만 적용받는다 — 그래서 API가 스스로
// 노출하는 표기(state=open)로 호출하면 400이 났다. 이 컨버터를 등록해 모든 State 바인딩이
// State.getValue()와 동일한 규칙(대소문자 무관)을 따르게 한다.
@Component
class StateConverter : Converter<String, State> {
    override fun convert(source: String): State = State.getValue(source.trim().lowercase())
}
