package com.github.yonaprojects.yona.web

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.string.shouldContain
import java.io.File

// P3-69: legacy service/yobi.git.View.js(10초 간격 폴링으로 #state + Accept 버튼 자동 갱신)에
// 대응하는 클라이언트측 배선이 실제로 정적 자산에 존재하는지 검증한다. 이 스펙은
// PullRequestViewControllerSpec처럼 컨트롤러/모델을 mockk로 검증하는 것과는 성격이 달라 —
// "서버가 올바른 모델을 만드는가"가 아니라 "템플릿과 JS 파일이 서로 올바르게 연결돼 있는가"를
// 원본 파일 텍스트로 직접 확인한다(GnbUserMenuDropdownColorSpec과 동일한 File(...).readText()
// 패턴, Spring 컨텍스트 불필요).
class PullRequestStatePollingWiringSpec : DescribeSpec({
    val viewHtml = File("src/main/resources/templates/pullrequest/view.html").readText()
    val partialInfoHtml = File("src/main/resources/templates/pullrequest/partial_info.html").readText()
    val partialStatePollHtml = File("src/main/resources/templates/pullrequest/partial_state_poll.html").readText()
    val pollingJs = File("src/main/resources/static/javascripts/service/yona.pullrequest.View.js").readText()

    describe("pullrequest/view.html — 폴링 모듈 로드 배선") {
        it("yona.pullrequest.View.js를 <script>로 포함해야 한다") {
            viewHtml shouldContain "/javascripts/service/yona.pullrequest.View.js"
        }

        it("overview 탭에서만(tab === 'overview') pullrequest.View 모듈을 초기화해야 한다") {
            viewHtml shouldContain "tab === 'overview'"
            viewHtml shouldContain "\$yona.loadModule(\"pullrequest.View\""
        }

        it("새 폴링 엔드포인트(.../pull/{number}/state)를 stateUrl로 넘겨야 한다") {
            viewHtml shouldContain "/pull/{number}/state(owner=\${project.owner}, projectName=\${project.name}, number=\${pr.number})"
        }

        it("PR이 CLOSED/MERGED거나 병합 진행 중(isMerging)이면 shouldPoll=false가 되도록 조건을 넘겨야 한다") {
            viewHtml shouldContain "pr.state.name() != 'CLOSED' and pr.state.name() != 'MERGED' and pr.isMerging != true"
        }

        it("#btnAccept 클릭 핸들러는 폴링에 의한 DOM 교체 이후에도 계속 동작하도록 위임(delegated) 방식으로 바인딩해야 한다") {
            viewHtml shouldContain "\$(document).on(\"click\", \"#btnAccept\""
        }

        it("#state 안의 [data-request-method] 버튼(브랜치 삭제/복구 등)도 폴링에 의한 DOM 교체 이후 계속 동작하도록 위임 방식이어야 한다") {
            viewHtml shouldContain "\$(document).on('click', '[data-request-method]:not(#btnAccept)'"
        }
    }

    describe("pullrequest/partial_info.html — Accept 버튼의 독립 재렌더링 가능 여부") {
        it("Accept 버튼을 감싸는 안정적인 컨테이너 id(pr-accept-button)가 있어야 한다") {
            partialInfoHtml shouldContain "id=\"pr-accept-button\""
        }

        it("Accept 버튼 블록이 독립적으로 참조 가능한 named fragment(acceptButton)로 노출돼야 한다") {
            partialInfoHtml shouldContain "th:fragment=\"acceptButton(project, pull, currentUser, isAcceptable, disabledAcceptReason)\""
        }
    }

    describe("pullrequest/partial_state_poll.html — #state + Accept 버튼 합성 프래그먼트") {
        it("partial_state 프래그먼트를 재사용해야 한다") {
            partialStatePollHtml shouldContain "pullrequest/partial_state :: state("
        }

        it("partial_info의 acceptButton 프래그먼트를 재사용해야 한다") {
            partialStatePollHtml shouldContain "pullrequest/partial_info :: acceptButton("
        }

        it("클라이언트가 부분을 나눠 옮겨 담을 수 있도록 안정적인 id(pr-state-poll-body/pr-accept-button-poll-body)를 노출해야 한다") {
            partialStatePollHtml shouldContain "id=\"pr-state-poll-body\""
            partialStatePollHtml shouldContain "id=\"pr-accept-button-poll-body\""
        }

        it("폴링 중단 여부 판단에 쓸 data-pr-state/data-pr-merging 속성을 노출해야 한다") {
            partialStatePollHtml shouldContain "data-pr-state="
            partialStatePollHtml shouldContain "data-pr-merging="
        }
    }

    describe("service/yona.pullrequest.View.js — legacy yobi.git.View.js 동치 동작") {
        it("legacy와 동일한 10초(10000ms) 기본 폴링 주기를 써야 한다") {
            pollingJs shouldContain "10000"
        }

        it("setInterval/clearInterval 기반 타이머여야 한다(legacy yobi.Interval 포팅 대상 아님, P3-52 결론)") {
            pollingJs shouldContain "setInterval("
            pollingJs shouldContain "clearInterval("
        }

        it("이전 응답과 HTML이 동일하면 DOM을 건드리지 않아야 한다(legacy 'update state only HTML has changed' 최적화)") {
            pollingJs shouldContain "html === vars.stateHTML"
        }

        it("PR이 CLOSED/MERGED거나 병합 진행 중이면 폴링 타이머를 멈춰야 한다") {
            pollingJs shouldContain "\"CLOSED\""
            pollingJs shouldContain "\"MERGED\""
            pollingJs shouldContain "prMerging"
        }

        it("#state와 #pr-accept-button 두 컨테이너를 모두 갱신해야 한다") {
            pollingJs shouldContain "#state"
            pollingJs shouldContain "#pr-accept-button"
        }
    }
})
