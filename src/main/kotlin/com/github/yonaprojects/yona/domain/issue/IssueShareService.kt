package com.github.yonaprojects.yona.domain.issue

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.User

interface IssueShareService {
    fun findAssignableUsersOfProject(project: Project, query: String, currentUser: User): List<Map<String, Any>>
    fun findAssignableUsers(issue: Issue, query: String, currentUser: User): List<Map<String, Any>>
    fun findSharerByloginIds(issue: Issue, commaSeperatedIds: String): List<Map<String, Any>>
    fun findSharableUsers(query: String, type: String?): List<Map<String, Any>>
    fun changeSharer(issue: Issue, targetLoginIdOrProjectId: String, type: String, action: String, currentUser: User): Map<String, Any>
}
