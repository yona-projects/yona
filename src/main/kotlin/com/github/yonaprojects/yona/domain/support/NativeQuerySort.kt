package com.github.yonaprojects.yona.domain.support

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort

// JPQL @Query는 Pageable의 Sort 속성명(엔티티 필드명, 예: "updatedDate")을 Hibernate가 자동으로
// 실제 컬럼명(snake_case, "updated_date")으로 바꿔주지만, 네이티브 @Query(nativeQuery = true)는
// 이 변환을 해주지 않고 속성명을 그대로 ORDER BY에 꽂아 "Unknown column 'updatedDate'"로 실패한다
// (예: 클라이언트가 컨트롤러에 `?sort=updatedDate,desc`를 보내는 모든 경로). 네이티브 쿼리로 전환한
// 검색 레포지토리 메서드는 전부 호출 직전에 이 변환을 거쳐야 한다.
fun Pageable.toSnakeCaseSort(): Pageable {
    if (sort.isUnsorted) {
        return this
    }
    val converted = Sort.by(
        sort.map { order -> Sort.Order(order.direction, order.property.camelToSnakeCase()) }.toList()
    )
    return PageRequest.of(pageNumber, pageSize, converted)
}

private fun String.camelToSnakeCase(): String =
    replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").lowercase()
