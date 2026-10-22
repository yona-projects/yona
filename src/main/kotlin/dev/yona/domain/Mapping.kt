package dev.yona.domain

import org.hibernate.Length

/**
 * v1.6의 `longtext` 컬럼에 해당하는 길이이다. Hibernate가 DB마다 가장 큰 문자열 타입을 고른다.
 */
const val LONG_TEXT: Int = Length.LONG32
