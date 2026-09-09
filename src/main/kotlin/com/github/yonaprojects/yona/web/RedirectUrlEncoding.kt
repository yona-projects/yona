package com.github.yonaprojects.yona.web

import org.springframework.web.util.UriUtils
import java.nio.charset.StandardCharsets

// Spring의 "redirect:" 뷰 이름은 {var} 형태의 URI 템플릿 변수만 자동으로
// percent-encode한다(RedirectView.expandUriTemplateVariables). 컨트롤러들이 지금까지 Kotlin
// 문자열 보간으로 owner/projectName을 미리 문자열에 끼워넣어 리다이렉트 경로를 만들어왔는데, 그러면
// 인코딩될 기회 자체가 없어서 한글처럼 ASCII가 아닌 문자가 섞인 Location 헤더를 Tomcat이 조용히
// 버려버린다 — 응답 자체는 302로 오지만 Location 헤더가 아예 없어 브라우저가 그냥 빈 화면만 보여준다
// (예: 프로젝트 생성 폼에 한글 이름을 넣으면 재현됨). owner/프로젝트명이 리다이렉트 경로에
// 들어가는 모든 곳에서 이 함수로 명시적으로 인코딩해야 한다.
fun String.encodePathSegment(): String = UriUtils.encodePathSegment(this, StandardCharsets.UTF_8)
