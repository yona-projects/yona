package com.github.yonaprojects.yona.domain.support

import com.github.yonaprojects.yona.domain.enumeration.SearchType
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.user.User
import org.springframework.data.domain.Pageable

interface SearchService {
    fun searchInAll(keyword: String, searchType: SearchType, user: User?, pageable: Pageable): SearchResult
    fun searchInAProject(keyword: String, searchType: SearchType, user: User?, project: Project, pageable: Pageable): SearchResult
    fun searchInAGroup(keyword: String, searchType: SearchType, user: User?, organization: Organization, pageable: Pageable): SearchResult
}
