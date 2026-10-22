package dev.yona.access

import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.project.Project

/**
 * 프로젝트 자체를 권한 검사의 대상으로 바꾼다 (v1.6 `Project.asResource()`).
 * 프로젝트는 다른 프로젝트에 속하지 않으므로 종류가 `PROJECT`인 [GlobalResource]이다.
 */
fun Project.asResource(): GlobalResource = GlobalResource(requireNotNull(id).toString(), ResourceType.PROJECT)

/**
 * 프로젝트의 라벨을 권한 검사의 대상으로 바꾼다 (v1.6 `Project.labelsAsResource()`).
 * 라벨을 붙이고 떼는 권한이 프로젝트 구성원에게 있는지 확인할 때 쓴다.
 */
fun Project.labelsAsResource(): ProjectResource = ProjectResource(requireNotNull(id).toString(), ResourceType.PROJECT_LABELS, this)
