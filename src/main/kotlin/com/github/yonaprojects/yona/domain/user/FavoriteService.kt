package com.github.yonaprojects.yona.domain.user

interface FavoriteService {
    fun toggleFavoriteProject(userId: Long, projectId: Long): Boolean
    fun toggleFavoriteOrganization(userId: Long, organizationId: Long): Boolean
    fun toggleFavoriteIssue(userId: Long, issueId: Long): Boolean
    
    fun getFavoriteProjects(userId: Long): List<FavoriteProject>
    fun getFavoriteOrganizations(userId: Long): List<FavoriteOrganization>
    fun getFavoriteIssues(userId: Long): List<FavoriteIssue>
}
