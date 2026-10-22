package dev.yona.access

import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.project.Project

/**
 * 프로젝트 자체를 권한 검사의 대상으로 바꾼다 (v1.6 `Project.asResource()`).
 * 프로젝트는 다른 프로젝트에 속하지 않으므로 종류가 `PROJECT`인 [GlobalResource]이다.
 */
fun Project.asResource(): GlobalResource = GlobalResource(requireNotNull(id).toString(), ResourceType.PROJECT)
