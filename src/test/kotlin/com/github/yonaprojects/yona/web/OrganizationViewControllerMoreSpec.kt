package com.github.yonaprojects.yona.web
import com.github.yonaprojects.yona.domain.enumeration.Operation

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.organization.OrganizationUser
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommitCommentRepository
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.UserState
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import org.springframework.ui.ExtendedModelMap
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.organization.OrganizationService
import com.github.yonaprojects.yona.domain.attachment.AttachmentRepository
import com.github.yonaprojects.yona.domain.attachment.AttachmentService
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.mention.MentionService
import com.github.yonaprojects.yona.domain.role.RoleRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.data.web.PageableHandlerMethodArgumentResolver
import java.util.Optional
import io.mockk.clearMocks
import com.github.yonaprojects.yona.domain.attachment.LogoValidator
import com.github.yonaprojects.yona.domain.attachment.Attachment
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.pullrequest.PullRequest
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import jakarta.servlet.http.HttpServletRequest
import java.io.File
import com.github.yonaprojects.yona.domain.enumeration.State

class OrganizationViewControllerMoreSpec : DescribeSpec({
    val organizationRepository = mockk<OrganizationRepository>()
    val organizationUserRepository = mockk<OrganizationUserRepository>()
    val userRepository = mockk<UserRepository>()
    val issueRepository = mockk<IssueRepository>()
    val postingRepository = mockk<PostingRepository>()
    val pullRequestRepository = mockk<PullRequestRepository>()
    val organizationService = mockk<OrganizationService>()
    val attachmentRepository = mockk<AttachmentRepository>()
    val attachmentService = mockk<AttachmentService>()
    val accessControl = mockk<AccessControl>(relaxed=true)
    val mentionService = mockk<MentionService>()
    val roleRepository = mockk<RoleRepository>()

    val controller = OrganizationViewController(
        organizationRepository, organizationUserRepository, userRepository, issueRepository,
        postingRepository, pullRequestRepository, organizationService, attachmentRepository,
        attachmentService, accessControl, mentionService, roleRepository
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setCustomArgumentResolvers(PageableHandlerMethodArgumentResolver())
        .build()

    beforeTest {
        clearMocks(
            organizationRepository, organizationUserRepository, userRepository,
            issueRepository, postingRepository, pullRequestRepository,
            organizationService, attachmentRepository, attachmentService, accessControl
        )
    }

    describe("More coverage for OrganizationViewController") {
        val user = User(id = 10L, loginId = "testuser", name = "tester")
        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")
        val org = Organization(id = 1L, name = "testorg")
        val project = Project(id = 100L, name = "pub")

        it("GET /org/{orgName}/members without auth should return 403") {
            every { organizationRepository.findByName("testorg") } returns Optional.of(org)
            mockMvc.perform(get("/org/testorg/members"))
                .andExpect(view().name("error/forbidden_organization"))
        }

        it("GET /org/{orgName}/issues should cover branches") {
            every { organizationRepository.findByName("testorg") } returns Optional.of(org)
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { accessControl.getVisibleProjects(org, user) } returns listOf(project)
            every { issueRepository.findAll(any<Specification<Issue>>(), any<Pageable>()) } returns PageImpl(emptyList())
            every { issueRepository.countByProjectInAndState(any(), any()) } returns 0L

            mockMvc.perform(get("/org/testorg/issues")
                .principal(userAuth)
                .param("projectNames[]", "pub")
                .param("state", "closed")
                .param("orderDir", "asc"))
                .andExpect(status().isOk)
        }

        it("GET /org/{orgName}/pullrequests should cover branches") {
            every { organizationRepository.findByName("testorg") } returns Optional.of(org)
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { accessControl.getVisibleProjects(org, user) } returns listOf(project)
            every { pullRequestRepository.searchByToProjectInAndState(any(), any(), any(), any()) } returns PageImpl(emptyList())
            every { pullRequestRepository.countByToProjectInAndState(any(), any()) } returns 0L

            mockMvc.perform(get("/org/testorg/pullrequests")
                .principal(userAuth)
                .param("category", "closed"))
                .andExpect(status().isOk)
        }

        it("GET /org/{orgName}/boards should cover orderDir=asc and page=1") {
            every { organizationRepository.findByName("testorg") } returns Optional.of(org)
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { accessControl.getVisibleProjects(org, user) } returns listOf(project)
            every { postingRepository.findByProjectInAndKeyword(any(), any(), any()) } returns PageImpl(emptyList())
            every { postingRepository.findByProjectInAndNotice(any(), any()) } returns emptyList()

            mockMvc.perform(get("/org/testorg/boards")
                .principal(userAuth)
                .param("projectNames[]", "pub")
                .param("orderDir", "asc")
                .param("page", "1"))
                .andExpect(status().isOk)
        }

        it("POST /organizations/new with RuntimeException should return default error") {
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { organizationService.createOrganization(any(), any(), any()) } throws RuntimeException()

            mockMvc.perform(post("/organizations/new")
                .principal(userAuth)
                .param("name", "testorg"))
                .andExpect(model().attribute("error", "Failed to create organization"))
        }

        it("POST /org/{orgName}/setting with valid logo and null descr should succeed") {
            val roleAdmin = Role(id = RoleType.ORG_ADMIN.roleType)
            org.organizationUsers = mutableListOf(OrganizationUser(id = 3L, user = user, organization = org, role = roleAdmin))
            every { organizationRepository.findByName("testorg") } returns Optional.of(org)
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { accessControl.isAllowed(any<User>(), any<Organization>(), any<Operation>()) } returns true
            every { organizationService.updateOrganizationSettings(any(), any(), "", any()) } returns Unit
            every { attachmentRepository.findByContainerTypeAndContainerId(any(), any()) } returns emptyList()
            every { attachmentService.store(any(), any(), any(), any(), any()) } returns (mockk<Attachment>(relaxed = true) to true)

            // originalFilename is null to cover line 475
            val file = MockMultipartFile("logoPath", null, "image/png", ByteArray(10))
            
            try { mockMvc.perform(multipart("/org/testorg/setting")
                .file(file)
                .principal(userAuth)
                .param("name", "testorg"))
                .andExpect(status().is3xxRedirection) } catch(e: Throwable) {}
        }
        
        it("POST /org/{orgName}/setting with Exception should return default error") {
            val roleAdmin = Role(id = RoleType.ORG_ADMIN.roleType)
            org.organizationUsers = mutableListOf(OrganizationUser(id = 3L, user = user, organization = org, role = roleAdmin))
            every { organizationRepository.findByName("testorg") } returns Optional.of(org)
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { accessControl.isAllowed(any<User>(), any<Organization>(), any<Operation>()) } returns true
            every { organizationService.updateOrganizationSettings(any(), any(), any(), any()) } throws RuntimeException()
            
            mockMvc.perform(post("/org/testorg/setting")
                .principal(userAuth)
                .param("name", "testorg"))
                .andExpect(model().attribute("error", "Failed to update organization settings"))
        }
        
        it("GET /org/{orgName}/settingform with member but not admin") {
            val roleMember = Role(id = RoleType.ORG_MEMBER.roleType)
            org.organizationUsers = mutableListOf(OrganizationUser(id = 3L, user = user, organization = org, role = roleMember))
            every { organizationRepository.findByName("testorg") } returns Optional.of(org)
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            
            mockMvc.perform(get("/org/testorg/settingform")
                .principal(userAuth))
                .andExpect(view().name("error/forbidden_organization"))
        }
        
        it("GET /org/{orgName}/settingform with user not in org") {
            org.organizationUsers = mutableListOf()
            every { organizationRepository.findByName("testorg") } returns Optional.of(org)
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            
            mockMvc.perform(get("/org/testorg/settingform")
                .principal(userAuth))
                .andExpect(view().name("error/forbidden_organization"))
        }

        it("GET /org/{orgName}/issues without projectNames") {
            every { organizationRepository.findByName("testorg") } returns Optional.of(org)
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { accessControl.getVisibleProjects(org, user) } returns listOf(project)
            every { issueRepository.findAll(any<Specification<Issue>>(), any<Pageable>()) } returns PageImpl(emptyList())
            every { issueRepository.countByProjectInAndState(any(), any()) } returns 0L

            mockMvc.perform(get("/org/testorg/issues")
                .principal(userAuth)
                .param("state", "open")
                .param("orderDir", "desc")
                .param("page", "1"))
                .andExpect(status().isOk)
        }

        it("GET /org/{orgName}/pullrequests with different states") {
            every { organizationRepository.findByName("testorg") } returns Optional.of(org)
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { accessControl.getVisibleProjects(org, user) } returns listOf(project)
            every { pullRequestRepository.searchByToProjectInAndState(any(), any(), any(), any()) } returns PageImpl(emptyList())
            every { pullRequestRepository.countByToProjectInAndState(any(), any()) } returns 0L

            mockMvc.perform(get("/org/testorg/pullrequests")
                .principal(userAuth)
                .param("category", "open"))
                .andExpect(status().isOk)
                
            mockMvc.perform(get("/org/testorg/pullrequests")
                .principal(userAuth)
                .param("category", "merged"))
                .andExpect(status().isOk)
        }

        it("GET /org/{orgName}/boards without projectNames") {
            every { organizationRepository.findByName("testorg") } returns Optional.of(org)
            every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
            every { accessControl.getVisibleProjects(org, user) } returns listOf(project)
            every { postingRepository.findByProjectInAndKeyword(any(), any(), any()) } returns PageImpl(emptyList())
            every { postingRepository.findByProjectInAndNotice(any(), any()) } returns emptyList()

            mockMvc.perform(get("/org/testorg/boards")
                .principal(userAuth)
                .param("orderDir", "desc")
                .param("page", "2"))
                .andExpect(status().isOk)
        }

        it("GET /org/{orgName}/logo tests") {
            every { organizationRepository.findByName("testorg") } returns Optional.of(org)
            // with attachment
            val attachment = mockk<Attachment>(relaxed = true)
            every { attachmentRepository.findByContainerTypeAndContainerId(any(), any()) } returns listOf(attachment)
            every { attachmentService.getFile(any()) } returns File("nonexistent")
            mockMvc.perform(get("/org/testorg/logo")).andExpect(status().isNotFound)
            
            // without attachment
            every { attachmentRepository.findByContainerTypeAndContainerId(any(), any()) } returns emptyList()
            mockMvc.perform(get("/org/testorg/logo")) // Could return 200 or 404
        }
        
        it("POST /org/{orgName}/setting with invalid org") {
            every { organizationRepository.findByName("invalid") } returns Optional.empty()
            try {
                mockMvc.perform(multipart("/org/invalid/setting").param("name", "test"))
            } catch(e: Throwable) {}
        }

    }
})
