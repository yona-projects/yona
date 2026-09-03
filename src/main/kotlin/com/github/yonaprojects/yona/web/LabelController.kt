package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.project.LabelRepository
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class LabelController(
    private val labelRepository: LabelRepository
) {
    private val maxFetchLabels = 1000

    @GetMapping("/labels")
    fun labels(
        @RequestParam(required = false, defaultValue = "") query: String,
        @RequestParam(required = false, defaultValue = "") category: String,
        @RequestParam(required = false) limit: Int?
    ): ResponseEntity<Any> {
        if (limit == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("No limit")
        }

        val total = labelRepository.countByCategoryContainingIgnoreCaseAndNameContainingIgnoreCase(category, query)
        val limitVal = if (limit > maxFetchLabels) maxFetchLabels else limit

        val labels = labelRepository.findByCategoryContainingIgnoreCaseAndNameContainingIgnoreCase(
            category,
            query,
            PageRequest.of(0, limitVal)
        )

        val headers = HttpHeaders()
        if (total > limitVal) {
            headers.set("Content-Range", "items $limitVal/$total")
        }

        val resultList = labels.map { it.name }
        return ResponseEntity.ok().headers(headers).body(resultList)
    }

    @GetMapping("/categories")
    fun categories(
        @RequestParam(required = false) query: String?,
        @RequestParam(required = false) limit: Int?
    ): ResponseEntity<Any> {
        if (limit == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("No limit")
        }

        val limitVal = if (limit > maxFetchLabels) maxFetchLabels else limit
        val hasQuery = !query.isNullOrBlank()

        val total = if (hasQuery) {
            labelRepository.countDistinctCategoriesContaining(query!!)
        } else {
            labelRepository.countDistinctCategories()
        }

        val categories = if (hasQuery) {
            labelRepository.findDistinctCategoriesContaining(query!!, PageRequest.of(0, limitVal))
        } else {
            labelRepository.findDistinctCategories(PageRequest.of(0, limitVal))
        }

        val headers = HttpHeaders()
        if (total > limitVal) {
            headers.set("Content-Range", "items $limitVal/$total")
        }

        return ResponseEntity.ok().headers(headers).body(categories)
    }
}
