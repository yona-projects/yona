package com.github.yonaprojects.yona.domain.support

import com.github.yonaprojects.yona.domain.enumeration.SearchType
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.springframework.data.domain.Page

class SearchResultSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("모든 필드의 getter/setter가 정상 동작해야 한다") {
            val result = SearchResult()

            result.keyword = "kw"
            result.searchType = SearchType.PROJECT
            result.usersCount = 1
            result.projectsCount = 2
            result.issuesCount = 3
            result.postsCount = 4
            result.milestonesCount = 5
            result.issueCommentsCount = 6
            result.postCommentsCount = 7
            result.reviewsCount = 8
            result.users = Page.empty()
            result.projects = Page.empty()
            result.issues = Page.empty()
            result.posts = Page.empty()
            result.milestones = Page.empty()
            result.issueComments = Page.empty()
            result.postComments = Page.empty()
            result.reviews = Page.empty()

            result.keyword shouldBe "kw"
            result.searchType shouldBe SearchType.PROJECT
            result.usersCount shouldBe 1
            result.projectsCount shouldBe 2
            result.issuesCount shouldBe 3
            result.postsCount shouldBe 4
            result.milestonesCount shouldBe 5
            result.issueCommentsCount shouldBe 6
            result.postCommentsCount shouldBe 7
            result.reviewsCount shouldBe 8
            result.users shouldBe Page.empty<Any>()
            result.projects shouldBe Page.empty<Any>()
            result.issues shouldBe Page.empty<Any>()
            result.posts shouldBe Page.empty<Any>()
            result.milestones shouldBe Page.empty<Any>()
            result.issueComments shouldBe Page.empty<Any>()
            result.postComments shouldBe Page.empty<Any>()
            result.reviews shouldBe Page.empty<Any>()
        }
    }
    describe("makeSnippets()") {
        it("검색어가 본문에 없으면 빈 리스트를 반환해야 한다") {
            val result = SearchResult(keyword = "없는단어")
            result.makeSnippets("본문 내용입니다", 5) shouldBe emptyList()
        }

        it("검색어 위치가 threshold보다 앞이면 시작 인덱스를 0으로 클램프해야 한다") {
            val result = SearchResult(keyword = "abc")
            val snippets = result.makeSnippets("abcdefghij", 10)
            snippets[0] shouldBe "abcdefghij".substring(0, minOf(3 + 3 + 10, 10))
        }

        it("검색어 위치가 threshold보다 뒤면 index-threshold로 시작해야 한다") {
            val result = SearchResult(keyword = "xyz")
            val contents = "0123456789xyz9876543210"
            val snippets = result.makeSnippets(contents, 2)
            // index=10, threshold=2 -> beginIndex=8
            snippets[0].startsWith(contents.substring(8, 10)) shouldBe true
        }

        it("끝 인덱스가 본문 길이를 넘으면 본문 길이로 클램프해야 한다") {
            val result = SearchResult(keyword = "end")
            val contents = "0123end"
            val snippets = result.makeSnippets(contents, 100)
            snippets[0] shouldBe contents
        }

        it("대소문자를 구분하지 않고 매칭해야 한다") {
            val result = SearchResult(keyword = "ABC")
            val snippets = result.makeSnippets("xxabcxx", 2)
            snippets shouldBe listOf("xxabcxx")
        }

        it("겹치지 않는 여러 매치는 각각 별도 스니펫으로 만들고 원래 순서를 유지해야 한다") {
            val result = SearchResult(keyword = "hit")
            val contents = "hit" + "0".repeat(18) + "hit"
            val snippets = result.makeSnippets(contents, 1)
            snippets.size shouldBe 2
            snippets[0] shouldBe contents.substring(0, 4)
            snippets[1] shouldBe contents.substring(20, contents.length)
        }

        it("겹치는 매치는 하나의 스니펫으로 병합해야 한다") {
            val result = SearchResult(keyword = "aa")
            val contents = "xaaaax"
            // "aa"가 index 1, 2, 3에서 매치(overlap) -> threshold 큰 값으로 전부 겹치게
            val snippets = result.makeSnippets(contents, 10)
            snippets.size shouldBe 1
            snippets[0] shouldBe contents
        }
    }

    describe("updateSearchType()") {
        it("searchType이 이미 AUTO가 아니면 값을 바꾸지 않아야 한다") {
            val result = SearchResult(searchType = SearchType.USER, issuesCount = 5)
            result.updateSearchType()
            result.searchType shouldBe SearchType.USER
        }

        it("issuesCount > 0이면 ISSUE로 설정해야 한다") {
            val result = SearchResult(issuesCount = 1)
            result.updateSearchType()
            result.searchType shouldBe SearchType.ISSUE
        }

        it("usersCount > 0이면 USER로 설정해야 한다") {
            val result = SearchResult(usersCount = 1)
            result.updateSearchType()
            result.searchType shouldBe SearchType.USER
        }

        it("projectsCount > 0이면 PROJECT로 설정해야 한다") {
            val result = SearchResult(projectsCount = 1)
            result.updateSearchType()
            result.searchType shouldBe SearchType.PROJECT
        }

        it("postsCount > 0이면 POST로 설정해야 한다") {
            val result = SearchResult(postsCount = 1)
            result.updateSearchType()
            result.searchType shouldBe SearchType.POST
        }

        it("milestonesCount > 0이면 MILESTONE으로 설정해야 한다") {
            val result = SearchResult(milestonesCount = 1)
            result.updateSearchType()
            result.searchType shouldBe SearchType.MILESTONE
        }

        it("issueCommentsCount > 0이면 ISSUE_COMMENT로 설정해야 한다") {
            val result = SearchResult(issueCommentsCount = 1)
            result.updateSearchType()
            result.searchType shouldBe SearchType.ISSUE_COMMENT
        }

        it("postCommentsCount > 0이면 POST_COMMENT로 설정해야 한다") {
            val result = SearchResult(postCommentsCount = 1)
            result.updateSearchType()
            result.searchType shouldBe SearchType.POST_COMMENT
        }

        it("reviewsCount > 0이면 REVIEW로 설정해야 한다") {
            val result = SearchResult(reviewsCount = 1)
            result.updateSearchType()
            result.searchType shouldBe SearchType.REVIEW
        }

        // yona-wiki P3-02 Step8.6 항목3(2026-09-01, 우선순위 3위) — `yona search prs` 대응.
        it("pullRequestsCount > 0이면 PULL_REQUEST로 설정해야 한다") {
            val result = SearchResult(pullRequestsCount = 1)
            result.updateSearchType()
            result.searchType shouldBe SearchType.PULL_REQUEST
        }

        it("모든 카운트가 0이면 기본값 ISSUE로 설정해야 한다") {
            val result = SearchResult()
            result.updateSearchType()
            result.searchType shouldBe SearchType.ISSUE
        }
    }
})
