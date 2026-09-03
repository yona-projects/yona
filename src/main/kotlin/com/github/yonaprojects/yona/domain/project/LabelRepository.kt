package com.github.yonaprojects.yona.domain.project

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface LabelRepository : JpaRepository<Label, Long> {
    fun findByCategoryAndName(category: String, name: String): Optional<Label>

    fun findByCategoryContainingIgnoreCaseAndNameContainingIgnoreCase(
        category: String,
        name: String,
        pageable: Pageable
    ): List<Label>

    fun countByCategoryContainingIgnoreCaseAndNameContainingIgnoreCase(
        category: String,
        name: String
    ): Long

    @Query("SELECT DISTINCT l.category FROM Label l WHERE LOWER(l.category) LIKE LOWER(CONCAT('%', :query, '%'))")
    fun findDistinctCategoriesContaining(
        @Param("query") query: String,
        pageable: Pageable
    ): List<String>

    @Query("SELECT COUNT(DISTINCT l.category) FROM Label l WHERE LOWER(l.category) LIKE LOWER(CONCAT('%', :query, '%'))")
    fun countDistinctCategoriesContaining(
        @Param("query") query: String
    ): Long

    @Query("SELECT DISTINCT l.category FROM Label l")
    fun findDistinctCategories(
        pageable: Pageable
    ): List<String>

    @Query("SELECT COUNT(DISTINCT l.category) FROM Label l")
    fun countDistinctCategories(): Long
}
