package com.github.yonaprojects.yona.domain.vcs

import com.github.yonaprojects.yona.domain.user.User
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import org.tmatesoft.svn.core.SVNLogEntry
import java.util.Date

class SvnCommitSpec : DescribeSpec({
    fun entry(revision: Long, author: String?, message: String?, date: Date? = Date(0)) =
        SVNLogEntry(emptyMap<String, Any>(), revision, author, date, message)

    describe("getMessage()") {
        it("entry의 message를 그대로 반환해야 한다") {
            val commit = SvnCommit(entry(1L, "yona", "commit message"), { null })
            commit.getMessage() shouldBe "commit message"
        }
    }

    describe("getAuthor()") {
        it("authorName이 null이면 null을 반환하고 userResolver를 호출하지 않아야 한다") {
            var resolverCalled = false
            val commit = SvnCommit(entry(1L, null, "msg"), { resolverCalled = true; null })
            commit.getAuthor().shouldBeNull()
            resolverCalled shouldBe false
        }

        it("authorName이 있으면 userResolver의 결과를 반환해야 한다") {
            val resolvedUser = User(id = 1L, loginId = "yona", name = "요나")
            val commit = SvnCommit(entry(1L, "yona", "msg"), { name -> if (name == "yona") resolvedUser else null })
            commit.getAuthor() shouldBe resolvedUser
        }

        it("authorName은 있지만 userResolver가 못 찾으면 null을 반환해야 한다") {
            val commit = SvnCommit(entry(1L, "ghost", "msg"), { null })
            commit.getAuthor().shouldBeNull()
        }
    }

    describe("getAuthorName()") {
        it("entry의 author를 그대로 반환해야 한다") {
            SvnCommit(entry(1L, "yona", "msg"), { null }).getAuthorName() shouldBe "yona"
        }

        it("entry의 author가 null이면 null을 반환해야 한다") {
            SvnCommit(entry(1L, null, "msg"), { null }).getAuthorName().shouldBeNull()
        }
    }

    describe("getId()") {
        it("revision을 문자열로 변환해 반환해야 한다") {
            SvnCommit(entry(42L, "yona", "msg"), { null }).getId() shouldBe "42"
        }
    }

    describe("getShortId()") {
        it("getId()와 동일한 값을 반환해야 한다") {
            val commit = SvnCommit(entry(7L, "yona", "msg"), { null })
            commit.getShortId() shouldBe commit.getId()
        }
    }

    describe("getShortMessage()") {
        it("message가 null이면 빈 문자열을 반환해야 한다") {
            SvnCommit(entry(1L, "yona", null), { null }).getShortMessage() shouldBe ""
        }

        it("message가 빈 문자열이면 빈 문자열을 반환해야 한다") {
            SvnCommit(entry(1L, "yona", ""), { null }).getShortMessage() shouldBe ""
        }

        it("한 줄 메시지는 그대로 반환해야 한다") {
            SvnCommit(entry(1L, "yona", "single line"), { null }).getShortMessage() shouldBe "single line"
        }

        it("여러 줄 메시지는 첫 줄만 반환해야 한다") {
            SvnCommit(entry(1L, "yona", "first line\nsecond line\nthird line"), { null })
                .getShortMessage() shouldBe "first line"
        }

        // getShortMessage()는 메시지 전체를 trim()한 뒤 "\n"으로 split하므로, 줄바꿈 직전의 공백은
        // trim 대상이 아니다(문자열 맨 앞/뒤 공백만 제거됨). 그래서 앞뒤 공백만 있는 한 줄 메시지로 검증한다.
        it("앞뒤 공백이 있는 메시지는 trim 후 반환해야 한다") {
            SvnCommit(entry(1L, "yona", "  padded line  "), { null })
                .getShortMessage() shouldBe "padded line"
        }

        it("공백만 있는 메시지는 trim 결과가 빈 문자열이 되어 빈 문자열을 반환해야 한다") {
            SvnCommit(entry(1L, "yona", "   "), { null }).getShortMessage() shouldBe ""
        }
    }

    describe("getAuthorEmail()") {
        it("항상 null을 반환해야 한다") {
            SvnCommit(entry(1L, "yona", "msg"), { null }).getAuthorEmail().shouldBeNull()
        }
    }

    describe("getAuthorDate()") {
        it("entry의 date를 그대로 반환해야 한다") {
            val date = Date(123456L)
            SvnCommit(entry(1L, "yona", "msg", date), { null }).getAuthorDate() shouldBe date
        }

        it("entry의 date가 null이면 null을 반환해야 한다") {
            SvnCommit(entry(1L, "yona", "msg", null), { null }).getAuthorDate().shouldBeNull()
        }
    }

    describe("getAuthorTimezone()") {
        it("항상 null을 반환해야 한다") {
            SvnCommit(entry(1L, "yona", "msg"), { null }).getAuthorTimezone().shouldBeNull()
        }
    }

    describe("getCommitterName()") {
        it("getAuthorName()과 동일한 값을 반환해야 한다") {
            val commit = SvnCommit(entry(1L, "yona", "msg"), { null })
            commit.getCommitterName() shouldBe commit.getAuthorName()
        }
    }

    describe("getCommitterEmail()") {
        it("항상 null을 반환해야 한다") {
            SvnCommit(entry(1L, "yona", "msg"), { null }).getCommitterEmail().shouldBeNull()
        }
    }

    describe("getCommitterDate()") {
        it("getAuthorDate()와 동일한 값을 반환해야 한다") {
            val date = Date(987654L)
            val commit = SvnCommit(entry(1L, "yona", "msg", date), { null })
            commit.getCommitterDate() shouldBe commit.getAuthorDate()
        }
    }

    describe("getCommitterTimezone()") {
        it("항상 null을 반환해야 한다") {
            SvnCommit(entry(1L, "yona", "msg"), { null }).getCommitterTimezone().shouldBeNull()
        }
    }

    describe("getParentCount()") {
        it("revision이 1보다 크면 1을 반환해야 한다") {
            SvnCommit(entry(2L, "yona", "msg"), { null }).getParentCount() shouldBe 1
        }

        it("revision이 1이면 0을 반환해야 한다") {
            SvnCommit(entry(1L, "yona", "msg"), { null }).getParentCount() shouldBe 0
        }

        it("revision이 0이면 0을 반환해야 한다") {
            SvnCommit(entry(0L, "yona", "msg"), { null }).getParentCount() shouldBe 0
        }
    }
})
