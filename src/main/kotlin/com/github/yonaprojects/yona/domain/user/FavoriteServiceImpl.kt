package com.github.yonaprojects.yona.domain.user

import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class FavoriteServiceImpl(
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val organizationRepository: OrganizationRepository,
    private val issueRepository: IssueRepository,
    private val favoriteProjectRepository: FavoriteProjectRepository,
    private val favoriteOrganizationRepository: FavoriteOrganizationRepository,
    private val favoriteIssueRepository: FavoriteIssueRepository
) : FavoriteService {

    override fun toggleFavoriteProject(userId: Long, projectId: Long): Boolean {
        val user = userRepository.findById(userId).orElseThrow { IllegalArgumentException("사용자를 찾을 수 없습니다.") }
        val project = projectRepository.findById(projectId).orElseThrow { IllegalArgumentException("프로젝트를 찾을 수 없습니다.") }

        val favoriteOpt = favoriteProjectRepository.findByUserIdAndProjectId(userId, projectId)
        return if (favoriteOpt.isPresent) {
            favoriteProjectRepository.delete(favoriteOpt.get())
            false
        } else {
            val favorite = FavoriteProject(user, project)
            favoriteProjectRepository.save(favorite)
            true
        }
    }

    override fun toggleFavoriteOrganization(userId: Long, organizationId: Long): Boolean {
        val user = userRepository.findById(userId).orElseThrow { IllegalArgumentException("사용자를 찾을 수 없습니다.") }
        val organization = organizationRepository.findById(organizationId).orElseThrow { IllegalArgumentException("조직을 찾을 수 없습니다.") }

        val favoriteOpt = favoriteOrganizationRepository.findByUserIdAndOrganizationId(userId, organizationId)
        return if (favoriteOpt.isPresent) {
            favoriteOrganizationRepository.delete(favoriteOpt.get())
            false
        } else {
            val favorite = FavoriteOrganization(user, organization)
            favoriteOrganizationRepository.save(favorite)
            true
        }
    }

    override fun toggleFavoriteIssue(userId: Long, issueId: Long): Boolean {
        val user = userRepository.findById(userId).orElseThrow { IllegalArgumentException("사용자를 찾을 수 없습니다.") }
        val issue = issueRepository.findById(issueId).orElseThrow { IllegalArgumentException("이슈를 찾을 수 없습니다.") }

        val favoriteOpt = favoriteIssueRepository.findByUserIdAndIssueId(userId, issueId)
        return if (favoriteOpt.isPresent) {
            favoriteIssueRepository.delete(favoriteOpt.get())
            false
        } else {
            val favorite = FavoriteIssue(user = user, issue = issue)
            favoriteIssueRepository.save(favorite)
            true
        }
    }

    @Transactional(readOnly = true)
    override fun getFavoriteProjects(userId: Long): List<FavoriteProject> {
        return favoriteProjectRepository.findByUserId(userId)
    }

    @Transactional(readOnly = true)
    override fun getFavoriteOrganizations(userId: Long): List<FavoriteOrganization> {
        return favoriteOrganizationRepository.findByUserId(userId)
    }

    @Transactional(readOnly = true)
    override fun getFavoriteIssues(userId: Long): List<FavoriteIssue> {
        return favoriteIssueRepository.findByUserId(userId)
    }
}
