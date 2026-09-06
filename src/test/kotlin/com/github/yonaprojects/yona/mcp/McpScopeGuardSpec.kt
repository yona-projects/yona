package com.github.yonaprojects.yona.mcp

import com.github.yonaprojects.yona.config.ApiTokenAuthenticationFilter
import com.github.yonaprojects.yona.domain.apitoken.ApiToken
import com.github.yonaprojects.yona.domain.apitoken.ApiTokenPermission
import com.github.yonaprojects.yona.domain.apitoken.ApiTokenScope
import com.github.yonaprojects.yona.domain.apitoken.ApiTokenScopeGroup
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.user.User
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.time.Instant
import java.time.temporal.ChronoUnit

// yona-wiki P3-07(MCP 서버) Step3 — McpScopeGuard는 "도구 호출 전에 스코프를 검증하는 유일한
// 지점"이라는 계획 문서 보안 요구사항의 핵심이라, JWT/PAT 두 인증 방식 각각에 대해 허용/거부
// 양쪽 경로를 전부 단위 테스트로 고정한다(IssueMcpTools/PullRequestMcpTools 쪽 테스트는 이
// 클래스가 실제로 각 도구의 첫 줄에서 호출되는지만 검증하면 된다 — 판정 로직 자체의 정확성은
// 여기서 전부 검증됨).
class McpScopeGuardSpec : DescribeSpec({
    val guard = McpScopeGuard()

    fun jwtAuth(scope: String): JwtAuthenticationToken {
        val jwt = Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .claim("sub", "tester")
            .claim("scope", scope)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()
        // JwtAuthenticationToken(jwt)(authorities 없는 단일 인자 생성자)는 authenticated=false로
        // 만들어져(AbstractOAuth2TokenAuthenticationToken이 authorities가 비어있으면 인증되지 않은
        //것으로 취급) McpScopeGuard.require()의 첫 번째 인증 여부 체크에서 항상 걸린다 — 실제
        // 리소스 서버(ResourceServerConfig.jwtDecoder + Spring의 기본 JwtAuthenticationConverter)가
        // 만드는 토큰은 "SCOPE_xxx" 권한을 채워 authenticated=true로 만들므로, 이 테스트도 그와
        // 동등하게 비어있지 않은 권한 목록을 넘긴다.
        return JwtAuthenticationToken(jwt, listOf(SimpleGrantedAuthority("SCOPE_placeholder")))
    }

    afterTest { RequestContextHolder.resetRequestAttributes() }

    describe("OAuth JWT 인증") {
        it("필요한 write 스코프가 있으면 통과해야 한다") {
            shouldNotThrowAny {
                guard.require(jwtAuth("issues:read issues:write"), ApiTokenScopeGroup.ISSUES, ApiTokenPermission.WRITE)
            }
        }

        it("write 스코프만 있어도 read 요구사항은 통과해야 한다(GitHub Fine-grained PAT과 동일한 상위호환 원칙)") {
            shouldNotThrowAny {
                guard.require(jwtAuth("issues:write"), ApiTokenScopeGroup.ISSUES, ApiTokenPermission.READ)
            }
        }

        it("read 스코프만 있는데 write를 요구하면 거부해야 한다") {
            shouldThrow<AccessDeniedException> {
                guard.require(jwtAuth("issues:read"), ApiTokenScopeGroup.ISSUES, ApiTokenPermission.WRITE)
            }
        }

        it("다른 그룹 스코프만 있으면 거부해야 한다(PULL_REQUESTS 스코프로 ISSUES 도구를 호출)") {
            shouldThrow<AccessDeniedException> {
                guard.require(jwtAuth("pull_requests:write"), ApiTokenScopeGroup.ISSUES, ApiTokenPermission.WRITE)
            }
        }

        it("merge_pull_request에 해당하는 PULL_REQUESTS:write 스코프가 없으면 거부해야 한다") {
            shouldThrow<AccessDeniedException> {
                guard.require(jwtAuth("pull_requests:read"), ApiTokenScopeGroup.PULL_REQUESTS, ApiTokenPermission.WRITE)
            }
        }

        it("scope 클레임이 아예 없으면 거부해야 한다") {
            val jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", "tester")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build()
            shouldThrow<AccessDeniedException> {
                guard.require(
                    JwtAuthenticationToken(jwt, listOf(SimpleGrantedAuthority("SCOPE_placeholder"))),
                    ApiTokenScopeGroup.ISSUES, ApiTokenPermission.READ
                )
            }
        }
    }

    describe("인증되지 않은 요청") {
        it("authentication이 null이면 거부해야 한다") {
            shouldThrow<AccessDeniedException> {
                guard.require(null, ApiTokenScopeGroup.ISSUES, ApiTokenPermission.READ)
            }
        }
    }

    describe("Fine-grained PAT 인증(request attribute로 전달된 ApiToken)") {
        val owner = User(id = 1L, loginId = "pat-owner", name = "PAT", email = "pat@example.com")
        val project = Project(id = 10L, owner = "pat-owner", name = "repo", projectScope = ProjectScope.PUBLIC)

        fun withApiToken(apiToken: ApiToken?): UsernamePasswordAuthenticationToken {
            val request = MockHttpServletRequest()
            if (apiToken != null) {
                request.setAttribute(ApiTokenAuthenticationFilter.SCOPED_API_TOKEN_ATTRIBUTE, apiToken)
            }
            RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
            return UsernamePasswordAuthenticationToken("pat-owner", null, emptyList())
        }

        it("ISSUES:write 스코프를 가진 전체 저장소 토큰이면 통과해야 한다") {
            val token = ApiToken(owner = owner, allRepositories = true, expiresAt = Instant.now().plus(1, ChronoUnit.DAYS))
            token.scopes.add(ApiTokenScope(apiToken = token, scopeGroup = ApiTokenScopeGroup.ISSUES, permission = ApiTokenPermission.WRITE))
            val auth = withApiToken(token)

            shouldNotThrowAny { guard.require(auth, ApiTokenScopeGroup.ISSUES, ApiTokenPermission.WRITE, project) }
        }

        it("스코프 그룹에 권한이 없으면 거부해야 한다") {
            val token = ApiToken(owner = owner, allRepositories = true, expiresAt = Instant.now().plus(1, ChronoUnit.DAYS))
            val auth = withApiToken(token)

            shouldThrow<AccessDeniedException> { guard.require(auth, ApiTokenScopeGroup.ISSUES, ApiTokenPermission.READ, project) }
        }

        it("특정 저장소로 좁혀진 토큰이 스코프 밖 저장소를 대상으로 하면 거부해야 한다") {
            val otherProject = Project(id = 99L, owner = "pat-owner", name = "other-repo", projectScope = ProjectScope.PUBLIC)
            val token = ApiToken(owner = owner, allRepositories = false, expiresAt = Instant.now().plus(1, ChronoUnit.DAYS))
            token.scopes.add(ApiTokenScope(apiToken = token, scopeGroup = ApiTokenScopeGroup.ISSUES, permission = ApiTokenPermission.WRITE))
            token.scopedProjects.add(otherProject)
            val auth = withApiToken(token)

            shouldThrow<AccessDeniedException> { guard.require(auth, ApiTokenScopeGroup.ISSUES, ApiTokenPermission.WRITE, project) }
        }

        it("만료된 토큰이면 거부해야 한다") {
            val token = ApiToken(owner = owner, allRepositories = true, expiresAt = Instant.now().minus(1, ChronoUnit.DAYS))
            token.scopes.add(ApiTokenScope(apiToken = token, scopeGroup = ApiTokenScopeGroup.ISSUES, permission = ApiTokenPermission.WRITE))
            val auth = withApiToken(token)

            shouldThrow<AccessDeniedException> { guard.require(auth, ApiTokenScopeGroup.ISSUES, ApiTokenPermission.WRITE, project) }
        }

        it("request attribute에 ApiToken이 없으면(원인모를 인증 방식) 거부해야 한다") {
            val auth = withApiToken(null)

            shouldThrow<AccessDeniedException> { guard.require(auth, ApiTokenScopeGroup.ISSUES, ApiTokenPermission.READ, project) }
        }
    }
})
