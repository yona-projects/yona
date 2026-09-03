package com.github.yonaprojects.yona.service

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.issue.IssueLabelRepository
import com.github.yonaprojects.yona.domain.issue.IssueCommentRepository
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.board.PostingCommentRepository
import com.github.yonaprojects.yona.domain.milestone.Milestone
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import com.github.yonaprojects.yona.domain.attachment.AttachmentRepository
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.enumeration.State
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestTemplate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Service
class MigrationService(
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val organizationUserRepository: OrganizationUserRepository,
    private val issueRepository: IssueRepository,
    private val issueLabelRepository: IssueLabelRepository,
    private val issueCommentRepository: IssueCommentRepository,
    private val postingRepository: PostingRepository,
    private val postingCommentRepository: PostingCommentRepository,
    private val milestoneRepository: MilestoneRepository,
    private val attachmentRepository: AttachmentRepository,
    @Value("\${github.client.id:}") private val clientId: String = "",
    @Value("\${github.client.secret:}") private val clientSecret: String = "",
    @Value("\${github.allow.migration:false}") private val allowMigration: Boolean = false
) {

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneId.systemDefault())
    private val yonaServer = "/"

    fun isAllowMigration(): Boolean {
        return allowMigration
    }

    fun getOAuthToken(code: String): String {
        val restTemplate = RestTemplate()
        val url = "https://github.com/login/oauth/access_token"
        val headers = HttpHeaders()
        headers.accept = listOf(MediaType.APPLICATION_JSON)
        headers.contentType = MediaType.APPLICATION_FORM_URLENCODED

        val map = LinkedMultiValueMap<String, String>()
        map.add("client_id", clientId)
        map.add("client_secret", clientSecret)
        map.add("code", code)

        val entity = HttpEntity(map, headers)
        return try {
            val response = restTemplate.postForEntity(url, entity, Map::class.java)
            val body = response.body
            (body?.get("access_token") as? String) ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    fun getMigrationProjects(user: User): List<Map<String, Any>> {
        val sourceProjects = mutableSetOf<Project>()

        // 사용자가 MANAGER인 프로젝트들
        val userProjects = projectUserRepository.findByUserId(user.id!!)
            .filter { it.role.id == RoleType.MANAGER.roleType }
            .map { it.project }
        sourceProjects.addAll(userProjects)

        // 사용자가 ORG_ADMIN인 단체의 프로젝트들
        val orgUsers = organizationUserRepository.findByUserIdAndRoleId(user.id!!, RoleType.ORG_ADMIN.roleType)
        for (ou in orgUsers) {
            sourceProjects.addAll(ou.organization.projects)
        }

        val sortedList = sourceProjects.sortedWith(compareBy({ it.owner }, { it.name }))
        return sortedList.map { p ->
            mapOf(
                "owner" to (p.owner ?: ""),
                "projectName" to p.name,
                "private" to (p.projectScope != ProjectScope.PUBLIC),
                "members" to p.projectUsers.size,
                "full_name" to "${p.owner}/${p.name}"
            )
        }
    }

    fun getMigrationProjectDetail(owner: String, projectName: String): Map<String, Any>? {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null) ?: return null

        val assignees = project.projectUsers.map { pu ->
            mapOf(
                "name" to (pu.user.name ?: ""),
                "login" to pu.user.loginId,
                "email" to (pu.user.email ?: "")
            )
        }

        val issueCount = issueRepository.countByProject(project)
        val postCount = postingRepository.countByProject(project)
        val milestoneCount = milestoneRepository.countByProject(project)

        return mapOf(
            "owner" to (project.owner ?: ""),
            "projectName" to project.name,
            "full_name" to "${project.owner}/${project.name}",
            "assignees" to assignees,
            "memberCount" to project.projectUsers.size,
            "issueCount" to issueCount,
            "postCount" to postCount,
            "milestoneCount" to milestoneCount
        )
    }

    fun exportLabels(owner: String, projectName: String): Map<String, Any>? {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null) ?: return null

        val labelList = issueLabelRepository.findByProject(project)
        val labelsMap = labelList.associate { label ->
            label.id.toString() to mapOf(
                "id" to label.id,
                "name" to label.name,
                "categoryId" to label.category.id,
                "categoryName" to label.category.name
            )
        }

        return mapOf("labels" to labelsMap)
    }

    fun exportIssueLabelPairs(owner: String, projectName: String): Map<String, Any>? {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null) ?: return null

        val issues = issueRepository.findByProject(project)
        val pairs = mutableListOf<Map<String, Any>>()
        for (issue in issues) {
            for (label in issue.labels) {
                pairs.add(
                    mapOf(
                        "issue_id" to (issue.id ?: 0L),
                        "issue_label_id" to (label.id ?: 0L)
                    )
                )
            }
        }

        return mapOf("issueLabelPairs" to pairs)
    }

    fun exportMilestones(owner: String, projectName: String): List<Map<String, Any>>? {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null) ?: return null

        val milestones = milestoneRepository.findByProject(project)
        return milestones.map { m ->
            val node = mutableMapOf<String, Any?>()
            node["id"] = m.id
            node["title"] = m.title
            node["state"] = m.state.name.lowercase()
            node["description"] = m.contents

            if (m.dueDate != null) {
                node["due_on"] = formatter.format(m.dueDate)
            }

            mapOf("milestone" to node)
        }
    }

    fun exportIssues(owner: String, projectName: String, withWikiCommit: Boolean): List<Map<String, Any>>? {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null) ?: return null

        val issues = issueRepository.findByProject(project)
        return issues.map { issue ->
            val node = mutableMapOf<String, Any?>()
            node["id"] = issue.id
            node["title"] = issue.title

            val originalIssueLink = "$yonaServer${project.owner}/${project.name}/issue/${issue.number}"
            val body = if (withWikiCommit) {
                addOriginalAuthorName(
                    relativeLinksToWikiCommitPath(issue.body ?: ""),
                    issue.authorLoginId ?: "",
                    issue.authorName ?: "",
                    "이슈",
                    originalIssueLink
                )
            } else {
                addOriginalAuthorName(
                    relativeLinksToAbsolutePath(issue.body ?: ""),
                    issue.authorLoginId ?: "",
                    issue.authorName ?: "",
                    "이슈",
                    originalIssueLink
                )
            }

            val sb = StringBuilder(body)
            if (withWikiCommit) {
                addAttachmentsStringUsingWikiCommit(sb, ResourceType.ISSUE_POST, issue.id.toString())
            } else {
                addAttachmentsString(sb, ResourceType.ISSUE_POST, issue.id.toString())
            }
            node["body"] = sb.toString()

            if (issue.createdDate != null) {
                node["created_at"] = formatter.format(issue.createdDate)
            }
            node["assignee"] = issue.assignee?.user?.loginId
            node["milestone"] = issue.milestone?.title
            node["milestoneId"] = issue.milestone?.id
            node["closed"] = (issue.state == State.CLOSED)

            val rawComments = issueCommentRepository.findByIssueIdOrderByCreatedDateAsc(issue.id!!)
            val comments = rawComments.map { comment ->
                val commentNode = mutableMapOf<String, Any?>()
                if (comment.createdDate != null) {
                    commentNode["created_at"] = formatter.format(comment.createdDate)
                }

                val commentBody = if (withWikiCommit) {
                    addOriginalAuthorName(
                        relativeLinksToWikiCommitPath(comment.contents),
                        comment.authorLoginId ?: "",
                        comment.authorName ?: "",
                        "코멘트",
                        "$originalIssueLink#comment-${comment.id}"
                    )
                } else {
                    addOriginalAuthorName(
                        relativeLinksToAbsolutePath(comment.contents),
                        comment.authorLoginId ?: "",
                        comment.authorName ?: "",
                        "코멘트",
                        "$originalIssueLink#comment-${comment.id}"
                    )
                }

                val csb = StringBuilder(commentBody)
                if (withWikiCommit) {
                    addAttachmentsStringUsingWikiCommit(csb, ResourceType.ISSUE_COMMENT, comment.id.toString())
                } else {
                    addAttachmentsString(csb, ResourceType.ISSUE_COMMENT, comment.id.toString())
                }
                commentNode["body"] = csb.toString()
                commentNode
            }

            mapOf(
                "issue" to node,
                "comments" to comments
            )
        }
    }

    fun exportPosts(owner: String, projectName: String, withWikiCommit: Boolean): List<Map<String, Any>>? {
        val project = projectRepository.findByOwnerAndName(owner, projectName).orElse(null) ?: return null

        val postings = postingRepository.findByProject(project)
        return postings.map { post ->
            val node = mutableMapOf<String, Any?>()
            node["title"] = post.title

            val originalPostingLink = "$yonaServer${project.owner}/${project.name}/post/${post.number}"
            val body = if (withWikiCommit) {
                addOriginalAuthorName(
                    relativeLinksToWikiCommitPath(post.body ?: ""),
                    post.authorLoginId ?: "",
                    post.authorName ?: "",
                    "게시글",
                    originalPostingLink
                )
            } else {
                addOriginalAuthorName(
                    relativeLinksToAbsolutePath(post.body ?: ""),
                    post.authorLoginId ?: "",
                    post.authorName ?: "",
                    "게시글",
                    originalPostingLink
                )
            }

            val sb = StringBuilder(body)
            if (withWikiCommit) {
                addAttachmentsStringUsingWikiCommit(sb, ResourceType.BOARD_POST, post.id.toString())
            } else {
                addAttachmentsString(sb, ResourceType.BOARD_POST, post.id.toString())
            }
            node["body"] = sb.toString()

            if (post.createdDate != null) {
                node["created_at"] = formatter.format(post.createdDate)
            }

            val rawComments = postingCommentRepository.findByPostingIdOrderByCreatedDateAsc(post.id!!)
            val comments = rawComments.map { comment ->
                val commentNode = mutableMapOf<String, Any?>()
                if (comment.createdDate != null) {
                    commentNode["created_at"] = formatter.format(comment.createdDate)
                }

                val commentBody = if (withWikiCommit) {
                    addOriginalAuthorName(
                        relativeLinksToWikiCommitPath(comment.contents),
                        comment.authorLoginId ?: "",
                        comment.authorName ?: "",
                        "코멘트",
                        "$originalPostingLink#comment-${comment.id}"
                    )
                } else {
                    addOriginalAuthorName(
                        relativeLinksToAbsolutePath(comment.contents),
                        comment.authorLoginId ?: "",
                        comment.authorName ?: "",
                        "코멘트",
                        "$originalPostingLink#comment-${comment.id}"
                    )
                }

                val csb = StringBuilder(commentBody)
                if (withWikiCommit) {
                    addAttachmentsStringUsingWikiCommit(csb, ResourceType.NONISSUE_COMMENT, comment.id.toString())
                } else {
                    addAttachmentsString(csb, ResourceType.NONISSUE_COMMENT, comment.id.toString())
                }
                commentNode["body"] = csb.toString()
                commentNode
            }

            mapOf(
                "issue" to node,
                "comments" to comments
            )
        }
    }

    private fun addOriginalAuthorName(
        bodyText: String,
        authorLoginId: String,
        authorName: String,
        type: String,
        link: String
    ): String {
        return "@$authorLoginId ($authorName) 님이 작성한 [$type]($link)입니다. \n\\---\n\n$bodyText"
    }

    private fun relativeLinksToAbsolutePath(text: String): String {
        return text.replace(Regex("(<img src=['\"])/([^'\"<>]+)(['\"]>)")) { matchResult ->
            "${matchResult.groupValues[1]}$yonaServer${matchResult.groupValues[2]}${matchResult.groupValues[3]}"
        }.replace(Regex("\\[([^\\]]*)\\]\\(/([^\\)]*)\\)")) { matchResult ->
            "[${matchResult.groupValues[1]}]($yonaServer${matchResult.groupValues[2]})"
        }
    }

    private fun relativeLinksToWikiCommitPath(text: String): String {
        return text.replace(Regex("(<img src=['\"])/([^'\"<>]+)(['\"]>)")) { matchResult ->
            "${matchResult.groupValues[1]}$yonaServer${matchResult.groupValues[2]}${matchResult.groupValues[3]}"
        }.replace(Regex("\\[([^\\]]*)\\]\\(/([^\\)]*)\\)")) { matchResult ->
            "[${matchResult.groupValues[1]}](../wiki/${matchResult.groupValues[2]}/$text)"
        }
    }

    private fun addAttachmentsString(sb: StringBuilder, type: ResourceType, id: String) {
        val attachments = attachmentRepository.findByContainerTypeAndContainerId(type, id)
        if (attachments.isNotEmpty()) {
            sb.append("\n\n--- attachments ---")
            for (attachment in attachments) {
                sb.append("\n[${attachment.name}]($yonaServer" + "files/${attachment.id})")
            }
        }
    }

    private fun addAttachmentsStringUsingWikiCommit(sb: StringBuilder, type: ResourceType, id: String) {
        val attachments = attachmentRepository.findByContainerTypeAndContainerId(type, id)
        if (attachments.isNotEmpty()) {
            sb.append("\n\n--- attachments ---")
            for (attachment in attachments) {
                val cleanName = attachment.name.replace("#", "%23")
                sb.append("\n[${attachment.name}](../wiki/files/${attachment.id}/$cleanName)")
            }
        }
    }
}
