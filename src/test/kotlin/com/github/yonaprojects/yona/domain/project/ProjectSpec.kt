package com.github.yonaprojects.yona.domain.project

import com.github.yonaprojects.yona.domain.user.User
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

// Project 엔티티 자신의 계산 프로퍼티(isPrivate/isPublic/isProtected/isForkedFromOrigin/hasForks/
// associationProjects)만 다룬다 — 서비스/컨트롤러 로직은 각 서비스별 스펙에서 다룬다.
class ProjectSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("forkingProjects/enrolledUsers/labels setter가 정상 동작해야 한다") {
            val project = Project(name = "p")
            val fork = Project(name = "fork")

            project.forkingProjects = mutableListOf(fork)
            project.enrolledUsers = mutableListOf(User())
            project.labels = mutableSetOf()

            project.forkingProjects shouldBe mutableListOf(fork)
            project.enrolledUsers.size shouldBe 1
            project.labels shouldBe mutableSetOf()
        }
    }

    describe("isPrivate/isPublic/isProtected") {
        it("projectScope이 PRIVATE이면 isPrivate만 true여야 한다") {
            val project = Project(name = "p", projectScope = ProjectScope.PRIVATE)
            project.isPrivate shouldBe true
            project.isPublic shouldBe false
            project.isProtected shouldBe false
        }

        it("projectScope이 PUBLIC이면 isPublic만 true여야 한다") {
            val project = Project(name = "p", projectScope = ProjectScope.PUBLIC)
            project.isPrivate shouldBe false
            project.isPublic shouldBe true
            project.isProtected shouldBe false
        }

        it("projectScope이 PROTECTED이면 isProtected만 true여야 한다") {
            val project = Project(name = "p", projectScope = ProjectScope.PROTECTED)
            project.isPrivate shouldBe false
            project.isPublic shouldBe false
            project.isProtected shouldBe true
        }
    }

    describe("isForkedFromOrigin") {
        it("originalProject가 없으면 false여야 한다") {
            Project(name = "p", originalProject = null).isForkedFromOrigin shouldBe false
        }

        it("originalProject가 있으면 true여야 한다") {
            val origin = Project(id = 1L, name = "origin")
            Project(name = "fork", originalProject = origin).isForkedFromOrigin shouldBe true
        }
    }

    describe("hasForks") {
        it("forkingProjects가 비어있으면 false여야 한다") {
            Project(name = "p", forkingProjects = mutableListOf()).hasForks shouldBe false
        }

        it("forkingProjects가 있으면 true여야 한다") {
            val fork = Project(id = 2L, name = "fork")
            Project(name = "p", forkingProjects = mutableListOf(fork)).hasForks shouldBe true
        }
    }

    describe("associationProjects") {
        it("포크가 아니면 자기 자신과 자신의 fork들만 포함해야 한다") {
            val fork = Project(id = 3L, name = "fork")
            val project = Project(id = 4L, name = "p", originalProject = null, forkingProjects = mutableListOf(fork))

            project.associationProjects shouldBe listOf(project, fork)
        }

        it("원본이 isCodeEnabled=false이면 원본을 포함하지 않아야 한다") {
            val origin = Project(id = 5L, name = "origin", isCodeEnabled = false, isPullRequestEnabled = true)
            val project = Project(id = 6L, name = "fork", originalProject = origin)

            project.associationProjects shouldBe listOf(project)
        }

        it("원본이 isCodeEnabled=true이지만 isPullRequestEnabled=false이면 원본을 포함하지 않아야 한다") {
            val origin = Project(id = 7L, name = "origin", isCodeEnabled = true, isPullRequestEnabled = false)
            val project = Project(id = 8L, name = "fork", originalProject = origin)

            project.associationProjects shouldBe listOf(project)
        }

        it("원본이 isCodeEnabled/isPullRequestEnabled 모두 true이면 원본까지 포함해야 한다") {
            val origin = Project(id = 9L, name = "origin", isCodeEnabled = true, isPullRequestEnabled = true)
            val project = Project(id = 10L, name = "fork", originalProject = origin)

            project.associationProjects shouldBe listOf(project, origin)
        }
    }
})
