package com.github.yonaprojects.yona.web

import io.mockk.every
import io.mockk.mockk

/** 2단 보기 선택이 없는 요청을 흉내 내는 리졸버 목. relaxed 목은 String?에 빈 문자열을 돌려줘 뷰 이름으로 오인되므로 null을 명시한다. */
fun noSelectionResolver(): CrossProjectDetailResolver = mockk {
    every { handleSelection(any(), any(), any(), any(), any(), any()) } returns null
}
