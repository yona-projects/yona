package com.github.yonaprojects.yona.domain.support

import com.github.yonaprojects.yona.domain.enumeration.SearchType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.milestone.Milestone
import com.github.yonaprojects.yona.domain.issue.IssueComment
import com.github.yonaprojects.yona.domain.board.PostingComment
import com.github.yonaprojects.yona.domain.pullrequest.PullRequest
import com.github.yonaprojects.yona.domain.pullrequest.ReviewComment
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.util.*

class SearchResult(
    var keyword: String = "",
    var searchType: SearchType = SearchType.AUTO,
    var usersCount: Int = 0,
    var projectsCount: Int = 0,
    var issuesCount: Int = 0,
    var postsCount: Int = 0,
    var milestonesCount: Int = 0,
    var issueCommentsCount: Int = 0,
    var postCommentsCount: Int = 0,
    var reviewsCount: Int = 0,
    // `yona search prs` 대응.
    var pullRequestsCount: Int = 0,

    var users: Page<User> = Page.empty(),
    var projects: Page<Project> = Page.empty(),
    var issues: Page<Issue> = Page.empty(),
    var posts: Page<Posting> = Page.empty(),
    var milestones: Page<Milestone> = Page.empty(),
    var issueComments: Page<IssueComment> = Page.empty(),
    var postComments: Page<PostingComment> = Page.empty(),
    var reviews: Page<ReviewComment> = Page.empty(),
    var pullRequests: Page<PullRequest> = Page.empty()
) {

    fun makeSnippets(contents: String, threshold: Int): List<String> {
        val lowerCaseContents = contents.lowercase(Locale.getDefault())
        val lowerCaseKeyword = keyword.lowercase(Locale.getDefault())

        val beginAndEnds = LinkedList<BeginAndEnd>()
        val indexes = findIndexes(lowerCaseContents, lowerCaseKeyword)

        for (i in indexes.indices) {
            val currentIndex = indexes[i]
            val beginIndex = beginIndex(currentIndex, threshold)
            val endIndex = endIndex(currentIndex + lowerCaseKeyword.length, lowerCaseContents.length, threshold)
            val thisOne = BeginAndEnd(beginIndex, endIndex)
            if (i == 0) {
                beginAndEnds.push(thisOne)
            } else {
                val latestOne = beginAndEnds.peek()
                if (latestOne.endIndex >= thisOne.beginIndex) {
                    val mergedOne = BeginAndEnd(latestOne.beginIndex, thisOne.endIndex)
                    beginAndEnds.pop()
                    beginAndEnds.push(mergedOne)
                } else {
                    beginAndEnds.push(thisOne)
                }
            }
        }

        beginAndEnds.reverse()

        val snippets = ArrayList<String>()
        for (bae in beginAndEnds) {
            snippets.add(contents.substring(bae.beginIndex, bae.endIndex))
        }

        return snippets
    }

    private fun findIndexes(contents: String, keyword: String): List<Int> {
        val indexes = ArrayList<Int>()
        var index = contents.indexOf(keyword)
        while (index != -1) {
            indexes.add(index)
            index = contents.indexOf(keyword, index + keyword.length)
        }
        return indexes
    }

    private fun beginIndex(index: Int, threshold: Int): Int {
        return if (index < threshold) 0 else index - threshold
    }

    private fun endIndex(keywordEndIndex: Int, contentLength: Int, threshold: Int): Int {
        val endIndex = keywordEndIndex + threshold
        return if (endIndex < contentLength) endIndex else contentLength
    }

    fun updateSearchType() {
        if (this.searchType != SearchType.AUTO) {
            return
        }

        if (issuesCount > 0) {
            searchType = SearchType.ISSUE
            return
        }
        if (usersCount > 0) {
            searchType = SearchType.USER
            return
        }
        if (projectsCount > 0) {
            searchType = SearchType.PROJECT
            return
        }
        if (postsCount > 0) {
            searchType = SearchType.POST
            return
        }
        if (milestonesCount > 0) {
            searchType = SearchType.MILESTONE
            return
        }
        if (issueCommentsCount > 0) {
            searchType = SearchType.ISSUE_COMMENT
            return
        }
        if (postCommentsCount > 0) {
            searchType = SearchType.POST_COMMENT
            return
        }
        if (reviewsCount > 0) {
            searchType = SearchType.REVIEW
            return
        }
        // PULL_REQUEST 우선순위는 기존 8개 타입 뒤, 기본값(ISSUE) 폴백 앞에 추가한다(기존
        // 우선순위 순서를 바꾸지 않음).
        if (pullRequestsCount > 0) {
            searchType = SearchType.PULL_REQUEST
            return
        }

        searchType = SearchType.ISSUE
    }

    private class BeginAndEnd(val beginIndex: Int, val endIndex: Int) {
        override fun toString(): String {
            return "BeginAndEnd(beginIndex=$beginIndex, endIndex=$endIndex)"
        }
    }
}
