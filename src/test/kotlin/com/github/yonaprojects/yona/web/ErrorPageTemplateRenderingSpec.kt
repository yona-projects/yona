package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.organization.OrganizationUser
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleRepository
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import io.kotest.matchers.string.shouldContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.multipart.MaxUploadSizeExceededException

// GlobalExceptionHandler(#53)는 실제로 MaxUploadSizeExceededException을 잡아 error/413을
// 렌더링하는지 검증해야 하는데, MockMvc의 MockMultipartHttpServletRequest는 실제 서블릿 컨테이너의
// MultipartConfigElement 크기 제한을 강제하지 않아(getParts()가 등록된 Part를 그대로 반환) 진짜
// 큰 파일을 업로드해도 예외가 발생하지 않는다(직접 확인함 — 컨트롤러까지 정상 도달해 anonymous
// 403으로 응답했다). 그래서 이 예외가 실제로 던져지는 상황을 테스트 전용 컨트롤러로 재현해
// DispatcherServlet -> 같은 프로덕션 GlobalExceptionHandler -> 실제 ThymeleafViewResolver까지
// 그대로 태운다. 컨트롤러/뷰 리졸버/템플릿은 전부 진짜이고, 예외 발생 지점만 대체한 것이다.
@RestController
private class MaxUploadSizeExceededTestController {
    @GetMapping("/__test/trigger-max-upload-size-exceeded")
    fun trigger(): String {
        throw MaxUploadSizeExceededException(10L)
    }
}

@TestConfiguration
private class MaxUploadSizeExceededTestConfig {
    @Bean
    fun maxUploadSizeExceededTestController() = MaxUploadSizeExceededTestController()
}

// P-템플릿 그룹3 #45/#47/#49/#50/#53 대응 — mockk 단위 테스트는 뷰 이름만 확인하고 실제 Thymeleaf
// 렌더링을 거치지 않으므로, 이 스펙은 실제 ViewResolver(webAppContextSetup)로 요청을 태워 새로 만든
// 컨텍스트 인지형 에러 화면들(error/notfound, error/forbidden, error/forbidden_organization,
// error/badrequest, error/413)이 문법 오류 없이 렌더링되고 기대한 문구/링크가 실제 HTML에 나타나는지
// 확인한다. 이 세션에서 mockk 테스트만 통과하고 실제 렌더링은 한 번도 검증 안 된 파샬에서
// SpelEvaluationException이 실제로 발견된 전례가 있어(원인: 프래그먼트 인자 안의 T(...)/gathering
// 제약) 반드시 이 방식으로 검증한다.
@Transactional
@Import(MaxUploadSizeExceededTestConfig::class)
class ErrorPageTemplateRenderingSpec @Autowired constructor(
    private val webApplicationContext: WebApplicationContext,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val organizationRepository: OrganizationRepository,
    private val organizationUserRepository: OrganizationUserRepository,
    private val roleRepository: RoleRepository,
    private val repositoryService: RepositoryService,
    private val projectUserRepository: ProjectUserRepository
) : AbstractIntegrationTest() {

    private val mockMvc: MockMvc by lazy { MockMvcBuilders.webAppContextSetup(webApplicationContext).build() }

    init {
        describe("에러 페이지 컨텍스트 인지형 렌더링 (P-템플릿 그룹3 #45/#47/#49/#50/#53)") {
            // 이름이 고유한(errpage- 접두) 픽스처만 만들고 클래스에 붙은 @Transactional이 각
            // 테스트 종료 시 롤백하므로, 다른 스펙의 데이터를 건드리는 전역 deleteAll()은 쓰지 않는다.

            it("이슈를 찾지 못하면 error/notfound가 프로젝트 헤더/메뉴와 함께 실제로 렌더링돼야 한다 (#45)") {
                // BootstrapSetupInterceptor는 DB에 유저가 0명이면 무조건 /bootstrap-setup으로
                // 리다이렉트하므로, 인증 없이 GET하는 이 화면도 유저를 최소 1명 만들어둬야 한다.
                userRepository.save(User(loginId = "errpage-bootstrap1", name = "부트스트랩", email = "errpage-bootstrap1@yona.io"))
                val project = projectRepository.save(
                    Project(name = "errpage-proj1", owner = "errpage-owner1", projectScope = ProjectScope.PUBLIC)
                )

                val body = mockMvc.perform(get("/${project.owner}/${project.name}/issue/999"))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                // targetType="issue_post" -> error.notfound.issue_post 메시지 + 이슈 목록으로 가는
                // 링크(TemplateHelper.notFoundReturnUrl) + 프로젝트 헤더(owner/name breadcrumb)가
                // 모두 실제 HTML에 나타나야 한다.
                body shouldContain "존재하지 않는 이슈"
                body shouldContain "/${project.owner}/${project.name}/issues?state=all"
                body shouldContain project.name!!
            }

            it("비공개 프로젝트에 비회원이 접근하면 error/forbidden이 프로젝트 헤더와 함께 실제로 렌더링돼야 한다 (#47)") {
                val project = projectRepository.save(
                    Project(name = "errpage-proj2", owner = "errpage-owner2", projectScope = ProjectScope.PRIVATE)
                )
                val outsider = userRepository.save(
                    User(loginId = "errpage-outsider", name = "외부인", email = "errpage-outsider@yona.io")
                )
                val auth = UsernamePasswordAuthenticationToken(outsider.loginId, "pw")

                val body = mockMvc.perform(get("/${project.owner}/${project.name}/issues").principal(auth))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                body shouldContain "권한이 없거나 존재하지 않는 프로젝트"
                body shouldContain project.name!!
            }

            it("조직 멤버 목록에 비Admin 멤버가 접근하면 error/forbidden_organization이 조직 헤더와 함께 실제로 렌더링돼야 한다 (#49)") {
                val org = organizationRepository.save(Organization(name = "errpage-org1"))
                val member = userRepository.save(
                    User(loginId = "errpage-orgmember", name = "조직멤버", email = "errpage-orgmember@yona.io")
                )
                val memberRole = roleRepository.findById(RoleType.ORG_MEMBER.roleType).orElseGet {
                    roleRepository.save(Role(id = RoleType.ORG_MEMBER.roleType, name = "ORG_MEMBER"))
                }
                organizationUserRepository.save(OrganizationUser(user = member, organization = org, role = memberRole))
                val auth = UsernamePasswordAuthenticationToken(member.loginId, "pw")

                val body = mockMvc.perform(get("/org/${org.name}/members").principal(auth))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                body shouldContain "권한이 없습니다"
                body shouldContain org.name!!
            }

            it("SVN 프로젝트에 브랜치 기본값 지정을 요청하면 error/badrequest가 프로젝트 헤더와 함께 실제로 렌더링돼야 한다 (#50)") {
                val project = projectRepository.save(
                    Project(name = "errpage-proj3", owner = "errpage-owner3", vcs = "SVN", projectScope = ProjectScope.PUBLIC)
                )
                val user = userRepository.save(
                    User(loginId = "errpage-svnuser", name = "SVN유저", email = "errpage-svnuser@yona.io")
                )
                val auth = UsernamePasswordAuthenticationToken(user.loginId, "pw")

                val body = mockMvc.perform(
                    post("/${project.owner}/${project.name}/code/master/setAsDefault").principal(auth)
                )
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                body shouldContain "잘못된 요청"
                body shouldContain project.name!!
            }

            it("MaxUploadSizeExceededException이 발생하면 GlobalExceptionHandler가 error/413을 실제로 렌더링해야 한다 (#53)") {
                userRepository.save(User(loginId = "errpage-bootstrap2", name = "부트스트랩2", email = "errpage-bootstrap2@yona.io"))

                val body = mockMvc.perform(get("/__test/trigger-max-upload-size-exceeded"))
                    .andExpect(status().isPayloadTooLarge)
                    .andReturn().response.contentAsString

                body shouldContain "너무 큰 텍스트 데이터를 보냈습니다"
                // MaxUploadSizeExceededException.maxUploadSize(10바이트)가 그대로 메시지 인자로
                // 전달되는지도 함께 확인한다.
                body shouldContain "10"
            }

            it("게시글을 찾지 못하면 error/notfound가 프로젝트 헤더/메뉴와 함께 실제로 렌더링돼야 한다 (BoardViewController#viewPost, #45)") {
                userRepository.save(User(loginId = "errpage-bootstrap-board", name = "부트스트랩", email = "errpage-bootstrap-board@yona.io"))
                val project = projectRepository.save(
                    Project(name = "errpage-board-proj", owner = "errpage-board-owner", projectScope = ProjectScope.PUBLIC)
                )

                val body = mockMvc.perform(get("/${project.owner}/${project.name}/post/999"))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                // targetType="board_post" -> error.notfound.board_post 메시지 + 게시글 목록으로 가는
                // 링크(TemplateHelper.notFoundReturnUrl) + 프로젝트 헤더(owner/name breadcrumb)가
                // 모두 실제 HTML에 나타나야 한다.
                body shouldContain "존재하지 않는 글입니다"
                body shouldContain "/${project.owner}/${project.name}/posts"
                body shouldContain project.name!!
            }

            it("비공개 프로젝트에 비회원이 게시글 목록에 접근하면 error/forbidden이 프로젝트 헤더와 함께 실제로 렌더링돼야 한다 (BoardViewController#listPosts, #47)") {
                val project = projectRepository.save(
                    Project(name = "errpage-board-proj2", owner = "errpage-board-owner2", projectScope = ProjectScope.PRIVATE)
                )
                val outsider = userRepository.save(
                    User(loginId = "errpage-board-outsider", name = "외부인", email = "errpage-board-outsider@yona.io")
                )
                val auth = UsernamePasswordAuthenticationToken(outsider.loginId, "pw")

                val body = mockMvc.perform(get("/${project.owner}/${project.name}/posts").principal(auth))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                body shouldContain "권한이 없습니다"
                body shouldContain project.name!!
            }

            it("비공개 프로젝트에 비회원이 코드 비교 화면에 접근하면 error/forbidden이 프로젝트 헤더와 함께 실제로 렌더링돼야 한다 (CompareViewController#compare, #47)") {
                val project = projectRepository.save(
                    Project(name = "errpage-compare-proj", owner = "errpage-compare-owner", projectScope = ProjectScope.PRIVATE, vcs = "GIT")
                )
                val outsider = userRepository.save(
                    User(loginId = "errpage-compare-outsider", name = "외부인2", email = "errpage-compare-outsider@yona.io")
                )
                val auth = UsernamePasswordAuthenticationToken(outsider.loginId, "pw")

                val body = mockMvc.perform(
                    get("/${project.owner}/${project.name}/compare/aaaaaaa..bbbbbbb").principal(auth)
                )
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                body shouldContain "권한이 없습니다"
                body shouldContain project.name!!
            }
        }

        describe("에러 페이지 컨텍스트 인지형 렌더링 (TASK-0259, Branch/CodeViewController 담당분)") {
            // 이름이 고유한(errpage2- 접두) 픽스처만 만들고 클래스에 붙은 @Transactional이 각 테스트
            // 종료 시 롤백하므로, 다른 스펙의 데이터를 건드리는 전역 deleteAll()은 쓰지 않는다.

            it("멤버 전용 코드 프로젝트에 비멤버가 접근하면 error/forbidden이 프로젝트 헤더와 함께 실제로 렌더링돼야 한다 (CodeViewController#codeBrowser, yona CodeApp.java:60-62)") {
                // BootstrapSetupInterceptor는 DB에 유저가 0명이면 무조건 /bootstrap-setup으로
                // 리다이렉트하므로, 인증 없이 GET하는 이 화면도 유저를 최소 1명 만들어둬야 한다.
                userRepository.save(User(loginId = "errpage2-bootstrap1", name = "부트스트랩", email = "errpage2-bootstrap1@yona.io"))
                val project = projectRepository.save(
                    Project(
                        name = "errpage2-proj1",
                        owner = "errpage2-owner1",
                        vcs = "GIT",
                        projectScope = ProjectScope.PUBLIC,
                        isCodeAccessibleMemberOnly = true
                    )
                )

                val body = mockMvc.perform(get("/${project.owner}/${project.name}/code"))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                // messageKey 기본값 "error.forbidden" 메시지 + 프로젝트 헤더(owner/name breadcrumb)가
                // 실제 HTML에 함께 나타나야 한다(제네릭 error/403이었다면 프로젝트 헤더가 없다).
                body shouldContain "권한이 없습니다"
                body shouldContain project.name!!
            }

            it("존재하지 않는 브랜치로 코드 브라우저에 접근하면 error/notfound가 브랜치명을 포함한 메시지와 함께 실제로 렌더링돼야 한다 (CodeViewController#codeBrowserWithBranch, yona CodeApp.java:115-117)") {
                userRepository.save(User(loginId = "errpage2-bootstrap2", name = "부트스트랩2", email = "errpage2-bootstrap2@yona.io"))
                val project = projectRepository.save(
                    Project(name = "errpage2-proj2", owner = "errpage2-owner2", vcs = "GIT", projectScope = ProjectScope.PUBLIC)
                )
                // 커밋이 하나도 없는 빈 저장소만 실제로 만들어둔다 — 어떤 브랜치를 요청해도
                // getMetaDataFromAncestorDirectories()가 null을 반환해 notfound 경로를 탄다.
                repositoryService.getRepository(project).create()

                val body = mockMvc.perform(get("/${project.owner}/${project.name}/code/no-such-branch"))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                // targetType="code" -> error.notfound.code="{0} branch does not exist. Check project
                // default branch!" 메시지(title={0}=브랜치명) + 프로젝트 설정 페이지로 가는 복귀 링크
                // (TemplateHelper.notFoundReturnUrl의 "code" 케이스) + 프로젝트 헤더가 모두 실제
                // HTML에 나타나야 한다.
                body shouldContain "no-such-branch 브랜치가 없습니다"
                body shouldContain "/${project.owner}/${project.name}/setting"
                body shouldContain project.name!!
            }
        }

        describe("에러 페이지 컨텍스트 인지형 렌더링 — 리뷰/리뷰스레드 컨트롤러 (TASK-0259, P-템플릿 #45/#47)") {
            // errpage-rev- 접두 픽스처만 만들고 클래스에 붙은 @Transactional이 각 테스트 종료 시
            // 롤백하므로, 다른 스펙의 데이터를 건드리는 전역 deleteAll()은 쓰지 않는다.

            it("PR 리뷰어 등록(review)을 프로젝트 비멤버가 요청하면 error/forbidden이 프로젝트 헤더와 함께 렌더링돼야 한다 (ReviewApiController#review, P-템플릿 #47)") {
                // yona ReviewApp.java:41 @IsAllowed(Operation.ACCEPT, ResourceType.PULL_REQUEST) 대응.
                val project = projectRepository.save(
                    Project(name = "errpage-revproj1", owner = "errpage-revowner1", projectScope = ProjectScope.PUBLIC)
                )
                val outsider = userRepository.save(
                    User(loginId = "errpage-rev-outsider1", name = "리뷰외부인1", email = "errpage-rev-outsider1@yona.io")
                )
                val auth = UsernamePasswordAuthenticationToken(outsider.loginId, "pw")

                val body = mockMvc.perform(
                    post("/api/${project.owner}/${project.name}/pullRequest/999999/review").principal(auth)
                )
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                body shouldContain "권한이 없습니다"
                body shouldContain project.name!!
            }

            it("존재하지 않는 PR에 리뷰어 등록(review)을 요청하면 error/notfound가 프로젝트 헤더와 함께 렌더링돼야 한다 (ReviewApiController#review, P-템플릿 #45)") {
                // yona IsAllowedAction.call()의 resourceObject == null 분기는 notfound.render(
                // "error.notfound", project, resourceType.resource())를 호출하는데 PULL_REQUEST의
                // resource()는 "pull_request"라 notfound.scala.html의 4개 case 중 어느 것과도
                // 매치되지 않고 항상 default(제네릭 문구)로 빠진다 — targetType을 비워 그 실제
                // 도달 분기를 그대로 재현했으므로 제네릭 "페이지를 찾을 수 없습니다" 문구가 나온다.
                val project = projectRepository.save(
                    Project(name = "errpage-revproj2", owner = "errpage-revowner2", projectScope = ProjectScope.PUBLIC)
                )
                val member = userRepository.save(
                    User(loginId = "errpage-rev-member1", name = "리뷰멤버1", email = "errpage-rev-member1@yona.io")
                )
                val memberRole = roleRepository.findById(RoleType.MEMBER.roleType).orElseGet {
                    roleRepository.save(Role(id = RoleType.MEMBER.roleType, name = "MEMBER"))
                }
                projectUserRepository.save(ProjectUser(user = member, project = project, role = memberRole))
                val auth = UsernamePasswordAuthenticationToken(member.loginId, "pw")

                val body = mockMvc.perform(
                    post("/api/${project.owner}/${project.name}/pullRequest/999999/review").principal(auth)
                )
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                body shouldContain "페이지를 찾을 수 없습니다"
                body shouldContain project.name!!
            }

            it("PR 리뷰어 해제(unreview)를 프로젝트 비멤버가 요청하면 error/forbidden이 프로젝트 헤더와 함께 렌더링돼야 한다 (ReviewApiController#unreview, P-템플릿 #47)") {
                // yona ReviewApp.java:55 @IsAllowed(Operation.ACCEPT, ResourceType.PULL_REQUEST) 대응.
                val project = projectRepository.save(
                    Project(name = "errpage-revproj3", owner = "errpage-revowner3", projectScope = ProjectScope.PUBLIC)
                )
                val outsider = userRepository.save(
                    User(loginId = "errpage-rev-outsider2", name = "리뷰외부인2", email = "errpage-rev-outsider2@yona.io")
                )
                val auth = UsernamePasswordAuthenticationToken(outsider.loginId, "pw")

                val body = mockMvc.perform(
                    post("/api/${project.owner}/${project.name}/pullRequest/999999/unreview").principal(auth)
                )
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                body shouldContain "권한이 없습니다"
                body shouldContain project.name!!
            }

            it("비공개 프로젝트의 PR 목록에 프로젝트 멤버가 아닌 사용자가 접근하면 error/forbidden이 프로젝트 헤더와 함께 렌더링돼야 한다 (PullRequestViewController#listPullRequests, P-템플릿 #47)") {
                val project = projectRepository.save(
                    Project(name = "errpage-pr-proj1", owner = "errpage-pr-owner1", projectScope = ProjectScope.PRIVATE, vcs = "GIT")
                )
                val outsider = userRepository.save(
                    User(loginId = "errpage-pr-outsider1", name = "PR외부인1", email = "errpage-pr-outsider1@yona.io")
                )
                val auth = UsernamePasswordAuthenticationToken(outsider.loginId, "pw")

                val body = mockMvc.perform(get("/${project.owner}/${project.name}/pulls").principal(auth))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                body shouldContain "권한이 없습니다"
                body shouldContain project.name!!
            }

            it("존재하지 않는 PR 번호로 상세화면에 접근하면 error/notfound가 프로젝트 헤더와 함께 렌더링돼야 한다 (PullRequestViewController#viewPullRequest, P-템플릿 #45)") {
                userRepository.save(User(loginId = "errpage-pr-bootstrap1", name = "부트스트랩", email = "errpage-pr-bootstrap1@yona.io"))
                val project = projectRepository.save(
                    Project(name = "errpage-pr-proj2", owner = "errpage-pr-owner2", projectScope = ProjectScope.PUBLIC, vcs = "GIT")
                )

                val body = mockMvc.perform(get("/${project.owner}/${project.name}/pull/999"))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                // targetType이 비어있는(레거시 IsAllowedAction의 resourceType.resource()=="pull_request"가
                // notfound.html의 4개 case 중 어느 것과도 안 맞아 항상 제네릭 문구로 빠지는) 경로이므로
                // 제네릭 error.notfound 메시지가 그대로 나오되, 프로젝트 헤더는 유지돼야 한다.
                body shouldContain "페이지를 찾을 수 없습니다"
                body shouldContain project.name!!
            }

            it("비공개 프로젝트의 리뷰 스레드 목록(reviewThreads)을 코드 접근 권한 없는 사용자가 요청하면 error/forbidden이 프로젝트 헤더와 함께 렌더링돼야 한다 (ReviewThreadController#reviewThreads, P-템플릿 #47)") {
                // yona ReviewThreadApp.java:41 @IsAllowed(Operation.READ) 대응.
                val project = projectRepository.save(
                    Project(name = "errpage-revproj4", owner = "errpage-revowner4", projectScope = ProjectScope.PRIVATE)
                )
                val outsider = userRepository.save(
                    User(loginId = "errpage-rev-outsider3", name = "리뷰외부인3", email = "errpage-rev-outsider3@yona.io")
                )
                val auth = UsernamePasswordAuthenticationToken(outsider.loginId, "pw")

                val body = mockMvc.perform(
                    get("/${project.owner}/${project.name}/reviews").principal(auth)
                )
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                body shouldContain "권한이 없습니다"
                body shouldContain project.name!!
            }
        }
    }
}
