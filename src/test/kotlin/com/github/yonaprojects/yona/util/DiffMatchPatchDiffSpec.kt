package com.github.yonaprojects.yona.util

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.util.LinkedList

/**
 * diff_match_patch(Neil Fraser / Google, Apache License 2.0)의 diff_* 접두사 메서드 전용 커버리지 테스트.
 * 원본 프로젝트: https://github.com/google/diff-match-patch
 *
 * 대상 파일(src/main/java/.../diff_match_patch.java)은 위 저장소의 현재 마스터 브랜치가 아니라,
 * 아직 diff_bisect로 리팩토링되기 이전의 "맵(map) 기반" 초기 알고리즘
 * (diff_map / diff_path1 / diff_path2 / diff_footprint)을 그대로 담고 있는 구버전이다.
 * 반면 upstream 저장소가 현재 공개하는 공식 JUnit 테스트
 * (java/tests/name/fraser/neil/plaintext/diff_match_patch_test.java)는 diff_bisect 기반 최신
 * 알고리즘을 검증하도록 작성되어 있어 그대로 이식할 수 없는 부분이 있다.
 *
 * - diff_commonPrefix / diff_commonSuffix / diff_halfMatch(+halfMatchI) / diff_cleanupSemanticLossless
 *   (+cleanupSemanticScore) / diff_cleanupEfficiency / diff_cleanupMerge / diff_xIndex / diff_levenshtein /
 *   diff_prettyHtml / diff_text1 / diff_text2 / diff_toDelta / diff_fromDelta / diff_linesToChars
 *   (+linesToCharsMunge) / diff_charsToLines 는 이 파일과 upstream 최신판의 로직이 동일함을 코드를 직접
 *   대조해 확인했고, 공식 테스트 케이스(입력/기대출력)를 그대로 포팅했다.
 * - diff_cleanupSemantic 은 upstream 최신판에만 존재하는 "overlap elimination" 패스(diff_commonOverlap
 *   호출)가 이 구버전에는 없다(diff_commonOverlap 메서드 자체가 파일에 없음을 확인). 따라서 그 패스에
 *   의존하는 upstream 케이스는 이식하지 않고, 이 파일의 실제 로직(직전/직후 변경 길이 비교를 통한
 *   equality 제거)을 직접 추적해 새로 작성했다.
 * - diff_map / diff_path1 / diff_path2 / diff_footprint / diff_compute / diff_main 은 이 구버전에만
 *   존재하는(또는 시그니처/로직이 달라진) 코드라 upstream에 대응 테스트가 없으므로 전부 새로 작성했다.
 */
class DiffMatchPatchDiffSpec : DescribeSpec({
    val dmp = diff_match_patch()

    val EQUAL = diff_match_patch.Operation.EQUAL
    val DELETE = diff_match_patch.Operation.DELETE
    val INSERT = diff_match_patch.Operation.INSERT

    fun d(op: diff_match_patch.Operation, text: String) = diff_match_patch.Diff(op, text)
    fun diffList(vararg diffs: diff_match_patch.Diff): LinkedList<diff_match_patch.Diff> {
        val list = LinkedList<diff_match_patch.Diff>()
        diffs.forEach { list.add(it) }
        return list
    }

    beforeEach {
        // 각 테스트가 필드를 변경할 수 있으므로 클래스에 선언된 기본값으로 매번 복원한다.
        dmp.Diff_Timeout = 1.0f
        dmp.Diff_EditCost = 4.toShort()
        dmp.Diff_DualThreshold = 32.toShort()
    }

    describe("diff_commonPrefix") {
        it("공통 접두사가 전혀 없으면 0을 반환한다") {
            dmp.diff_commonPrefix("abc", "xyz") shouldBe 0
        }
        it("일부 접두사만 같으면 같은 길이를 반환한다") {
            dmp.diff_commonPrefix("1234abcdef", "1234xyz") shouldBe 4
        }
        it("한쪽 문자열 전체가 접두사이면 그 길이를 반환한다") {
            dmp.diff_commonPrefix("1234", "1234xyz") shouldBe 4
        }
    }

    describe("diff_commonSuffix") {
        it("공통 접미사가 전혀 없으면 0을 반환한다") {
            dmp.diff_commonSuffix("abc", "xyz") shouldBe 0
        }
        it("일부 접미사만 같으면 같은 길이를 반환한다") {
            dmp.diff_commonSuffix("abcdef1234", "xyz1234") shouldBe 4
        }
        it("한쪽 문자열 전체가 접미사이면 그 길이를 반환한다") {
            dmp.diff_commonSuffix("1234", "xyz1234") shouldBe 4
        }
    }

    describe("diff_halfMatch (및 private diff_halfMatchI)") {
        it("긴 문자열이 10자 미만이면 무조건 null이다 (pointless 분기)") {
            dmp.diff_halfMatch("12345", "23") shouldBe null
        }
        it("10자 이상이어도 절반 이상 겹치는 부분문자열이 전혀 없으면 null이다") {
            dmp.diff_halfMatch("1234567890", "abcdef") shouldBe null
        }
        it("짧은 쪽이 빈 문자열이면 무조건 null이다 (pointless 분기의 OR 두 번째 피연산자)") {
            dmp.diff_halfMatch("1234567890", "") shouldBe null
        }
        it("2/4 지점 시드로만 매치되는 단일 하프매치") {
            dmp.diff_halfMatch("1234567890", "a345678z")?.toList() shouldBe
                listOf("12", "90", "a", "z", "345678")
        }
        it("text1이 더 짧아 swap 분기(else 경로)를 타는 하프매치") {
            dmp.diff_halfMatch("a345678z", "1234567890")?.toList() shouldBe
                listOf("a", "z", "12", "90", "345678")
        }
        it("하프매치 #3") {
            dmp.diff_halfMatch("abc56789z", "1234567890")?.toList() shouldBe
                listOf("abc", "z", "1234", "0", "56789")
        }
        it("하프매치 #4") {
            dmp.diff_halfMatch("a23456xyz", "1234567890")?.toList() shouldBe
                listOf("a", "xyz", "1", "7890", "23456")
        }
        it("동일 시드가 여러 번 나타나도 최선의 매치를 찾는다 (halfMatchI while 루프 반복)") {
            dmp.diff_halfMatch("121231234123451234123121", "a1234123451234z")?.toList() shouldBe
                listOf("12123", "123121", "a", "z", "1234123451234")
        }
        it("접두/접미가 빈 문자열이 되는 경계 케이스 #1") {
            dmp.diff_halfMatch("x-=-=-=-=-=-=-=-=-=-=-=-=", "xx-=-=-=-=-=-=-=")?.toList() shouldBe
                listOf("", "-=-=-=-=-=", "x", "", "x-=-=-=-=-=-=-=")
        }
        it("접두/접미가 빈 문자열이 되는 경계 케이스 #2") {
            dmp.diff_halfMatch("-=-=-=-=-=-=-=-=-=-=-=-=y", "-=-=-=-=-=-=-=yy")?.toList() shouldBe
                listOf("-=-=-=-=-=", "", "", "y", "-=-=-=-=-=-=-=y")
        }
        it("2/4 지점과 3/4 지점 시드가 서로 다른 매치를 찾을 때 더 긴 쪽(hm1 vs hm2)을 선택한다") {
            dmp.diff_halfMatch("qHilloHelloHew", "xHelloHeHulloy")?.toList() shouldBe
                listOf("qHillo", "w", "x", "Hulloy", "HelloHe")
        }
    }

    describe("diff_cleanupSemanticScore (private, diff_cleanupSemanticLossless를 통해 검증)") {
        // 아래 diff_cleanupSemanticLossless 테스트들이 score 0(영숫자)/1(비영숫자)/2(공백)/3(개행)/
        // 4(빈 줄)/5(문자열 끝) 각 분기를 모두 실질적으로 태운다.
        it("경계 없음: score 계산 로직이 실제로 diff_cleanupSemanticLossless 결과에 반영된다") {
            val diffs = diffList(d(EQUAL, "The-c"), d(INSERT, "ow-and-the-c"), d(EQUAL, "at."))
            dmp.diff_cleanupSemanticLossless(diffs)
            diffs shouldBe diffList(d(EQUAL, "The-"), d(INSERT, "cow-and-the-"), d(EQUAL, "cat."))
        }
    }

    describe("diff_cleanupSemanticLossless") {
        it("빈 리스트는 아무 것도 하지 않는다 (null case)") {
            val diffs = diffList()
            dmp.diff_cleanupSemanticLossless(diffs)
            diffs shouldBe diffList()
        }
        it("직전 equality와 공통 접미사가 전혀 없으면 왼쪽 이동을 건너뛴다 (commonOffset==0 분기)") {
            val diffs = diffList(d(EQUAL, "xyz"), d(DELETE, "abc"), d(EQUAL, "def"))
            dmp.diff_cleanupSemanticLossless(diffs)
            diffs shouldBe diffList(d(EQUAL, "xyz"), d(DELETE, "abc"), d(EQUAL, "def"))
        }
        it("빈 줄 경계로 슬라이드한다") {
            val diffs = diffList(
                d(EQUAL, "AAA\r\n\r\nBBB"),
                d(INSERT, "\r\nDDD\r\n\r\nBBB"),
                d(EQUAL, "\r\nEEE"),
            )
            dmp.diff_cleanupSemanticLossless(diffs)
            diffs shouldBe diffList(
                d(EQUAL, "AAA\r\n\r\n"),
                d(INSERT, "BBB\r\nDDD\r\n\r\n"),
                d(EQUAL, "BBB\r\nEEE"),
            )
        }
        it("줄 경계로 슬라이드한다") {
            val diffs = diffList(d(EQUAL, "AAA\r\nBBB"), d(INSERT, " DDD\r\nBBB"), d(EQUAL, " EEE"))
            dmp.diff_cleanupSemanticLossless(diffs)
            diffs shouldBe diffList(d(EQUAL, "AAA\r\n"), d(INSERT, "BBB DDD\r\n"), d(EQUAL, "BBB EEE"))
        }
        it("단어 경계(공백)로 슬라이드한다") {
            val diffs = diffList(d(EQUAL, "The c"), d(INSERT, "ow and the c"), d(EQUAL, "at."))
            dmp.diff_cleanupSemanticLossless(diffs)
            diffs shouldBe diffList(d(EQUAL, "The "), d(INSERT, "cow and the "), d(EQUAL, "cat."))
        }
        it("영숫자 경계(하이픈)로 슬라이드한다") {
            val diffs = diffList(d(EQUAL, "The-c"), d(INSERT, "ow-and-the-c"), d(EQUAL, "at."))
            dmp.diff_cleanupSemanticLossless(diffs)
            diffs shouldBe diffList(d(EQUAL, "The-"), d(INSERT, "cow-and-the-"), d(EQUAL, "cat."))
        }
        it("시작점에 닿으면 앞 equality를 통째로 제거한다 (bestEquality1 길이 0 분기)") {
            val diffs = diffList(d(EQUAL, "a"), d(DELETE, "a"), d(EQUAL, "ax"))
            dmp.diff_cleanupSemanticLossless(diffs)
            diffs shouldBe diffList(d(DELETE, "a"), d(EQUAL, "aax"))
        }
        it("끝점에 닿으면 뒤 equality를 통째로 제거한다 (bestEquality2 길이 0 분기)") {
            val diffs = diffList(d(EQUAL, "xa"), d(DELETE, "a"), d(EQUAL, "a"))
            dmp.diff_cleanupSemanticLossless(diffs)
            diffs shouldBe diffList(d(EQUAL, "xaa"), d(DELETE, "a"))
        }
        it("upstream 최신판과 달리 문장 경계 가산점이 없어 이 위치에서 슬라이드하지 않는다 (버전 차이, 직접 실행해 확인)") {
            // upstream 최신판은 diff_cleanupSemanticScore에 마침표 등 문장부호 전용 가산점이 추가되어
            // "The xxx." / " The zzz." 로 슬라이드하지만, 이 구버전의 diff_cleanupSemanticScore는
            // 영숫자/공백/개행/빈줄 4단계 점수만 있어 이 입력에서는 현재 위치가 이미 동점 최고점이라
            // 이동이 일어나지 않는다. 실제로 실행해 확인한, 구버전 특유의 (버그가 아닌) 동작이다.
            val diffs = diffList(d(EQUAL, "The xxx. The "), d(INSERT, "zzz. The "), d(EQUAL, "yyy."))
            dmp.diff_cleanupSemanticLossless(diffs)
            diffs shouldBe diffList(d(EQUAL, "The xxx. The "), d(INSERT, "zzz. The "), d(EQUAL, "yyy."))
        }
    }

    describe("diff_cleanupSemantic") {
        // 이 구버전은 overlap elimination 패스가 없으므로, 아래 케이스들은 실제 소스의
        // "직전/직후 변경 길이가 equality 길이 이상이면 그 equality를 delete+insert로 쪼갠다" 로직만 검증한다.
        it("빈 리스트는 아무 것도 하지 않는다 (null case)") {
            val diffs = diffList()
            dmp.diff_cleanupSemantic(diffs)
            diffs shouldBe diffList()
        }
        it("제거할 것이 없으면 그대로 유지한다 #1") {
            val diffs = diffList(d(DELETE, "ab"), d(INSERT, "cd"), d(EQUAL, "12"), d(DELETE, "e"))
            dmp.diff_cleanupSemantic(diffs)
            diffs shouldBe diffList(d(DELETE, "ab"), d(INSERT, "cd"), d(EQUAL, "12"), d(DELETE, "e"))
        }
        it("직전+직후 변경 길이 합이 equality 길이 이상이면 제거된다 (upstream 최신판과 다른 지점, 직접 실행해 확인)") {
            // upstream 최신판은 직전/직후 각각 max(insertions, deletions)로 비교하지만, 이 구버전은
            // length_changes1/length_changes2 에 insert+delete 길이를 그냥 합산해 비교한다.
            // 이 입력은 직전 변경 길이 합(abc+ABC=6) >= equality 길이(4)이고, 직후 변경 길이(wxyz=4) >=
            // equality 길이(4)라서 upstream과 달리 실제로 제거가 일어난다. 직접 실행해 확인했다.
            val diffs = diffList(d(DELETE, "abc"), d(INSERT, "ABC"), d(EQUAL, "1234"), d(DELETE, "wxyz"))
            dmp.diff_cleanupSemantic(diffs)
            diffs shouldBe diffList(d(DELETE, "abc1234wxyz"), d(INSERT, "ABC1234"))
        }
        it("단순 제거: 짧은 equality가 delete+insert로 쪼개진다") {
            val diffs = diffList(d(DELETE, "a"), d(EQUAL, "b"), d(DELETE, "c"))
            dmp.diff_cleanupSemantic(diffs)
            diffs shouldBe diffList(d(DELETE, "abc"), d(INSERT, "b"))
        }
        it("백패스 제거: 스택을 비우고 안전 지점까지 되돌아간다") {
            val diffs = diffList(
                d(DELETE, "ab"), d(EQUAL, "cd"), d(DELETE, "e"), d(EQUAL, "f"), d(INSERT, "g"),
            )
            dmp.diff_cleanupSemantic(diffs)
            diffs shouldBe diffList(d(DELETE, "abcdef"), d(INSERT, "cdfg"))
        }
        it("여러 개의 equality가 연쇄적으로 제거된다 (equalities 스택 empty/비empty 분기 모두)") {
            val diffs = diffList(
                d(INSERT, "1"), d(EQUAL, "A"), d(DELETE, "B"), d(INSERT, "2"),
                d(EQUAL, "_"),
                d(INSERT, "1"), d(EQUAL, "A"), d(DELETE, "B"), d(INSERT, "2"),
            )
            dmp.diff_cleanupSemantic(diffs)
            diffs shouldBe diffList(d(DELETE, "AB_AB"), d(INSERT, "1A2_1A2"))
        }
        it("equality가 3단 이상 중첩되면 분할 후에도 스택에 안전한 equality가 남아 그 지점으로 되돌아간다") {
            // equalities 스택에 A,B,C 세 equality가 쌓인 상태에서 C가 분할되면, C를 pop하고
            // "재평가 필요"로 B도 함께 pop하지만 A는 여전히 스택에 남는다. 이때
            // equalities.empty()가 false가 되어 "안전한 이전 equality(A)로 되돌아가는" 분기
            // (line 829~835 else 블록)를 태운다.
            val diffs = diffList(
                d(EQUAL, "1111"), d(EQUAL, "2222"), d(DELETE, "aa"),
                d(EQUAL, "33"), d(DELETE, "b"), d(INSERT, "c"),
            )
            val text1Before = dmp.diff_text1(diffs)
            val text2Before = dmp.diff_text2(diffs)
            dmp.diff_cleanupSemantic(diffs)
            // 내부 재구성 과정에서 원본 소스/대상 텍스트는 보존되어야 한다.
            dmp.diff_text1(diffs) shouldBe text1Before
            dmp.diff_text2(diffs) shouldBe text2Before
        }
        it("단어 경계 정리는 내부적으로 cleanupSemanticLossless를 호출해 마무리한다") {
            val diffs = diffList(d(EQUAL, "The c"), d(DELETE, "ow and the c"), d(EQUAL, "at."))
            dmp.diff_cleanupSemantic(diffs)
            diffs shouldBe diffList(d(EQUAL, "The "), d(DELETE, "cow and the "), d(EQUAL, "cat."))
        }
        it("overlap elimination 패스가 없으므로 delete/insert만 겹쳐도 그대로 남는다 (upstream 최신판과의 차이점)") {
            // upstream 최신판(diff_commonOverlap 보유)이라면
            // [DELETE "abc", EQUAL "xxx", INSERT "def"] 로 정리되지만,
            // 이 구버전은 diff_commonOverlap이 없어 equality가 하나도 없는 이 입력에서는
            // length_changes 기반 분기 자체가 트리거되지 않아 원본 그대로 남는다.
            val diffs = diffList(d(DELETE, "abcxxx"), d(INSERT, "xxxdef"))
            dmp.diff_cleanupSemantic(diffs)
            diffs shouldBe diffList(d(DELETE, "abcxxx"), d(INSERT, "xxxdef"))
        }
    }

    describe("diff_cleanupEfficiency") {
        it("빈 리스트는 아무 것도 하지 않는다 (null case)") {
            val diffs = diffList()
            dmp.diff_cleanupEfficiency(diffs)
            diffs shouldBe diffList()
        }
        it("편집 비용이 충분히 크면 제거하지 않는다") {
            val diffs = diffList(
                d(DELETE, "ab"), d(INSERT, "12"), d(EQUAL, "wxyz"), d(DELETE, "cd"), d(INSERT, "34"),
            )
            dmp.diff_cleanupEfficiency(diffs)
            diffs shouldBe diffList(
                d(DELETE, "ab"), d(INSERT, "12"), d(EQUAL, "wxyz"), d(DELETE, "cd"), d(INSERT, "34"),
            )
        }
        it("4-edit 제거: 양쪽 모두 ins+del이 있는 짧은 equality를 제거한다") {
            val diffs = diffList(
                d(DELETE, "ab"), d(INSERT, "12"), d(EQUAL, "xyz"), d(DELETE, "cd"), d(INSERT, "34"),
            )
            dmp.diff_cleanupEfficiency(diffs)
            diffs shouldBe diffList(d(DELETE, "abxyzcd"), d(INSERT, "12xyz34"))
        }
        it("3-edit 제거") {
            val diffs = diffList(d(INSERT, "12"), d(EQUAL, "x"), d(DELETE, "cd"), d(INSERT, "34"))
            dmp.diff_cleanupEfficiency(diffs)
            diffs shouldBe diffList(d(DELETE, "xcd"), d(INSERT, "12x34"))
        }
        it("백패스 제거") {
            val diffs = diffList(
                d(DELETE, "ab"), d(INSERT, "12"), d(EQUAL, "xy"), d(INSERT, "34"),
                d(EQUAL, "z"), d(DELETE, "cd"), d(INSERT, "56"),
            )
            dmp.diff_cleanupEfficiency(diffs)
            diffs shouldBe diffList(d(DELETE, "abxyzcd"), d(INSERT, "12xy34z56"))
        }
        it("맨 앞의 equality는 직전 편집이 없어 후보에서 제외된다 (post_ins/post_del 모두 false인 분기)") {
            val diffs = diffList(d(EQUAL, "x"), d(DELETE, "a"))
            dmp.diff_cleanupEfficiency(diffs)
            diffs shouldBe diffList(d(EQUAL, "x"), d(DELETE, "a"))
        }
        it("다섯 가지 분할 패턴 중 pre_del만 있고 post_ins+post_del인 경우 (sum==3, type4)") {
            // <del>A</del>X<ins>C</ins><del>D</del> 패턴: pre_ins=false, pre_del=true,
            // post_ins=true, post_del=true 로 합이 3이 되어 분할 분기를 탄다.
            val diffs = diffList(d(DELETE, "A"), d(EQUAL, "x"), d(INSERT, "C"), d(DELETE, "D"))
            dmp.diff_cleanupEfficiency(diffs)
            diffs shouldBe diffList(d(DELETE, "AxD"), d(INSERT, "xC"))
        }
        it("다섯 가지 분할 패턴 중 pre_ins+pre_del이 있고 post_del만 있는 경우 (sum==3, type5)") {
            // <ins>A</ins><del>B</del>X<del>C</del> 패턴: pre_ins=true, pre_del=true,
            // post_ins=false, post_del=true 로 합이 3이 되어 분할 분기를 탄다.
            val diffs = diffList(d(INSERT, "A"), d(DELETE, "B"), d(EQUAL, "x"), d(DELETE, "C"))
            dmp.diff_cleanupEfficiency(diffs)
            diffs shouldBe diffList(d(DELETE, "BxC"), d(INSERT, "Ax"))
        }
        it("Diff_EditCost를 높이면 더 긴 equality도 제거 대상이 된다") {
            dmp.Diff_EditCost = 5.toShort()
            val diffs = diffList(
                d(DELETE, "ab"), d(INSERT, "12"), d(EQUAL, "wxyz"), d(DELETE, "cd"), d(INSERT, "34"),
            )
            dmp.diff_cleanupEfficiency(diffs)
            diffs shouldBe diffList(d(DELETE, "abwxyzcd"), d(INSERT, "12wxyz34"))
        }
    }

    describe("diff_cleanupMerge") {
        it("빈 리스트는 그대로 둔다 (null case)") {
            val diffs = diffList()
            dmp.diff_cleanupMerge(diffs)
            diffs shouldBe diffList()
        }
        it("변경 없음: 이미 D/I/E 순서면 그대로 둔다") {
            val diffs = diffList(d(EQUAL, "a"), d(DELETE, "b"), d(INSERT, "c"))
            dmp.diff_cleanupMerge(diffs)
            diffs shouldBe diffList(d(EQUAL, "a"), d(DELETE, "b"), d(INSERT, "c"))
        }
        it("연속된 equality를 병합한다") {
            val diffs = diffList(d(EQUAL, "a"), d(EQUAL, "b"), d(EQUAL, "c"))
            dmp.diff_cleanupMerge(diffs)
            diffs shouldBe diffList(d(EQUAL, "abc"))
        }
        it("연속된 delete를 병합한다") {
            val diffs = diffList(d(DELETE, "a"), d(DELETE, "b"), d(DELETE, "c"))
            dmp.diff_cleanupMerge(diffs)
            diffs shouldBe diffList(d(DELETE, "abc"))
        }
        it("연속된 insert를 병합한다") {
            val diffs = diffList(d(INSERT, "a"), d(INSERT, "b"), d(INSERT, "c"))
            dmp.diff_cleanupMerge(diffs)
            diffs shouldBe diffList(d(INSERT, "abc"))
        }
        it("엇갈린 delete/insert를 하나씩 병합한다") {
            val diffs = diffList(
                d(DELETE, "a"), d(INSERT, "b"), d(DELETE, "c"), d(INSERT, "d"), d(EQUAL, "e"), d(EQUAL, "f"),
            )
            dmp.diff_cleanupMerge(diffs)
            diffs shouldBe diffList(d(DELETE, "ac"), d(INSERT, "bd"), d(EQUAL, "ef"))
        }
        it("접두/접미 공통부분을 찾아 equality로 뽑아낸다") {
            val diffs = diffList(d(DELETE, "a"), d(INSERT, "abc"), d(DELETE, "dc"))
            dmp.diff_cleanupMerge(diffs)
            diffs shouldBe diffList(d(EQUAL, "a"), d(DELETE, "d"), d(INSERT, "b"), d(EQUAL, "c"))
        }
        it("주변에 equality가 있어도 접두/접미 공통부분을 찾는다") {
            val diffs = diffList(
                d(EQUAL, "x"), d(DELETE, "a"), d(INSERT, "abc"), d(DELETE, "dc"), d(EQUAL, "y"),
            )
            dmp.diff_cleanupMerge(diffs)
            diffs shouldBe diffList(d(EQUAL, "xa"), d(DELETE, "d"), d(INSERT, "b"), d(EQUAL, "cy"))
        }
        it("편집을 왼쪽으로 슬라이드한다") {
            val diffs = diffList(d(EQUAL, "a"), d(INSERT, "ba"), d(EQUAL, "c"))
            dmp.diff_cleanupMerge(diffs)
            diffs shouldBe diffList(d(INSERT, "ab"), d(EQUAL, "ac"))
        }
        it("편집을 오른쪽으로 슬라이드한다") {
            val diffs = diffList(d(EQUAL, "c"), d(INSERT, "ab"), d(EQUAL, "a"))
            dmp.diff_cleanupMerge(diffs)
            diffs shouldBe diffList(d(EQUAL, "ca"), d(INSERT, "ba"))
        }
        it("왼쪽 슬라이드가 재귀적으로 일어난다") {
            val diffs = diffList(
                d(EQUAL, "a"), d(DELETE, "b"), d(EQUAL, "c"), d(DELETE, "ac"), d(EQUAL, "x"),
            )
            dmp.diff_cleanupMerge(diffs)
            diffs shouldBe diffList(d(DELETE, "abc"), d(EQUAL, "acx"))
        }
        it("오른쪽 슬라이드가 재귀적으로 일어난다") {
            val diffs = diffList(
                d(EQUAL, "x"), d(DELETE, "ca"), d(EQUAL, "c"), d(DELETE, "b"), d(EQUAL, "a"),
            )
            dmp.diff_cleanupMerge(diffs)
            diffs shouldBe diffList(d(EQUAL, "xca"), d(DELETE, "cba"))
        }
        it("빈 병합 케이스") {
            val diffs = diffList(d(DELETE, "b"), d(INSERT, "ab"), d(EQUAL, "c"))
            dmp.diff_cleanupMerge(diffs)
            diffs shouldBe diffList(d(INSERT, "a"), d(EQUAL, "bc"))
        }
        it("빈 equality는 인접 equality와 병합되어 사라진다") {
            val diffs = diffList(d(EQUAL, ""), d(INSERT, "a"), d(EQUAL, "b"))
            dmp.diff_cleanupMerge(diffs)
            diffs shouldBe diffList(d(INSERT, "a"), d(EQUAL, "b"))
        }
    }

    describe("diff_xIndex") {
        it("equality 구간에서의 위치 변환") {
            val diffs = diffList(d(DELETE, "a"), d(INSERT, "1234"), d(EQUAL, "xyz"))
            dmp.diff_xIndex(diffs, 2) shouldBe 5
        }
        it("deletion 구간에서의 위치 변환 (lastDiff가 DELETE인 분기)") {
            val diffs = diffList(d(EQUAL, "a"), d(DELETE, "1234"), d(EQUAL, "xyz"))
            dmp.diff_xIndex(diffs, 3) shouldBe 1
        }
        it("모든 diff를 다 지나서 loc이 끝에 도달하면 lastDiff는 null로 남는다") {
            val diffs = diffList(d(EQUAL, "abc"), d(DELETE, "de"))
            // chars1 총합 = 3(EQUAL)+2(DELETE) = 5, loc=5이면 어떤 diff도 chars1>loc을 만들지 못해
            // lastDiff는 끝까지 null로 남고, 최종적으로 last_chars2 + (loc-last_chars1) 분기를 탄다.
            dmp.diff_xIndex(diffs, 5) shouldBe 3
        }
    }

    describe("diff_levenshtein") {
        it("뒤에 equality가 오는 경우") {
            val diffs = diffList(d(DELETE, "abc"), d(INSERT, "1234"), d(EQUAL, "xyz"))
            dmp.diff_levenshtein(diffs) shouldBe 4
        }
        it("앞에 equality가 오는 경우") {
            val diffs = diffList(d(EQUAL, "xyz"), d(DELETE, "abc"), d(INSERT, "1234"))
            dmp.diff_levenshtein(diffs) shouldBe 4
        }
        it("중간에 equality가 오는 경우 (삭제/삽입이 각각 별도로 누적된다)") {
            val diffs = diffList(d(DELETE, "abc"), d(EQUAL, "xyz"), d(INSERT, "1234"))
            dmp.diff_levenshtein(diffs) shouldBe 7
        }
    }

    describe("diff_prettyHtml") {
        it("INSERT/DELETE/EQUAL을 각각 다른 태그로 감싸고 특수문자를 이스케이프한다") {
            val diffs = diffList(d(EQUAL, "a\n"), d(DELETE, "<B>b</B>"), d(INSERT, "c&d"))
            dmp.diff_prettyHtml(diffs) shouldBe
                "<SPAN TITLE=\"i=0\">a&para;<BR></SPAN>" +
                "<DEL STYLE=\"background:#FFE6E6;\" TITLE=\"i=2\">&lt;B&gt;b&lt;/B&gt;</DEL>" +
                "<INS STYLE=\"background:#E6FFE6;\" TITLE=\"i=2\">c&amp;d</INS>"
        }
        it("빈 리스트는 빈 문자열을 반환한다 (for 루프 0회 반복)") {
            dmp.diff_prettyHtml(diffList()) shouldBe ""
        }
    }

    describe("diff_text1 / diff_text2") {
        it("text1은 EQUAL+DELETE만, text2는 EQUAL+INSERT만 모은다") {
            val diffs = diffList(
                d(EQUAL, "jump"), d(DELETE, "s"), d(INSERT, "ed"),
                d(EQUAL, " over "), d(DELETE, "the"), d(INSERT, "a"), d(EQUAL, " lazy"),
            )
            dmp.diff_text1(diffs) shouldBe "jumps over the lazy"
            dmp.diff_text2(diffs) shouldBe "jumped over a lazy"
        }
        it("빈 리스트는 빈 문자열이다") {
            dmp.diff_text1(diffList()) shouldBe ""
            dmp.diff_text2(diffList()) shouldBe ""
        }
    }

    describe("diff_toDelta / diff_fromDelta") {
        it("빈 리스트는 빈 delta 문자열이다 (delta.length()!=0 분기의 false 경로)") {
            dmp.diff_toDelta(diffList()) shouldBe ""
        }
        it("delta 문자열로 변환하고 다시 diff로 복원한다 (round trip)") {
            val diffs = diffList(
                d(EQUAL, "jump"), d(DELETE, "s"), d(INSERT, "ed"),
                d(EQUAL, " over "), d(DELETE, "the"), d(INSERT, "a"),
                d(EQUAL, " lazy"), d(INSERT, "old dog"),
            )
            val text1 = dmp.diff_text1(diffs)
            text1 shouldBe "jumps over the lazy"

            val delta = dmp.diff_toDelta(diffs)
            delta shouldBe "=4\t-1\t+ed\t=6\t-3\t+a\t=5\t+old dog"

            dmp.diff_fromDelta(text1, delta) shouldBe diffs
        }
        it("delta가 원본보다 길면 IllegalArgumentException (19 < 20)") {
            val diffs = diffList(
                d(EQUAL, "jump"), d(DELETE, "s"), d(INSERT, "ed"),
                d(EQUAL, " over "), d(DELETE, "the"), d(INSERT, "a"),
                d(EQUAL, " lazy"), d(INSERT, "old dog"),
            )
            val text1 = dmp.diff_text1(diffs)
            val delta = dmp.diff_toDelta(diffs)
            shouldThrow<IllegalArgumentException> {
                dmp.diff_fromDelta(text1 + "x", delta)
            }
        }
        it("delta가 원본보다 짧으면 IllegalArgumentException (19 > 18)") {
            val diffs = diffList(
                d(EQUAL, "jump"), d(DELETE, "s"), d(INSERT, "ed"),
                d(EQUAL, " over "), d(DELETE, "the"), d(INSERT, "a"),
                d(EQUAL, " lazy"), d(INSERT, "old dog"),
            )
            val text1 = dmp.diff_text1(diffs)
            val delta = dmp.diff_toDelta(diffs)
            shouldThrow<IllegalArgumentException> {
                dmp.diff_fromDelta(text1.substring(1), delta)
            }
        }
        it("잘못된 %-이스케이프는 IllegalArgumentException") {
            shouldThrow<IllegalArgumentException> {
                dmp.diff_fromDelta("", "+%c3%xy")
            }
        }
        it("유니코드 특수문자가 포함된 delta 왕복 변환") {
            val diffs = diffList(
                d(EQUAL, "ڀ   \t %"),
                d(DELETE, "ځ  \n ^"),
                d(INSERT, "ڂ  \\ |"),
            )
            val text1 = dmp.diff_text1(diffs)
            text1 shouldBe "ڀ   \t %ځ  \n ^"

            val delta = dmp.diff_toDelta(diffs)
            delta shouldBe "=7\t-7\t+%DA%82 %02 %5C %7C"

            dmp.diff_fromDelta(text1, delta) shouldBe diffs
        }
        it("인코딩되지 않는 문자 풀(unchanged characters)은 그대로 보존된다") {
            val diffs = diffList(d(INSERT, "A-Z a-z 0-9 - _ . ! ~ * ' ( ) ; / ? : @ & = + $ , # "))
            val text2 = dmp.diff_text2(diffs)
            text2 shouldBe "A-Z a-z 0-9 - _ . ! ~ * ' ( ) ; / ? : @ & = + $ , # "

            val delta = dmp.diff_toDelta(diffs)
            delta shouldBe "+A-Z a-z 0-9 - _ . ! ~ * ' ( ) ; / ? : @ & = + $ , # "

            dmp.diff_fromDelta("", delta) shouldBe diffs
        }
        it("아주 긴(160kb) 문자열도 왕복 변환된다") {
            var a = "abcdefghij"
            repeat(14) { a += a }
            val diffs = diffList(d(INSERT, a))
            val delta = dmp.diff_toDelta(diffs)
            delta shouldBe "+$a"
            dmp.diff_fromDelta("", delta) shouldBe diffs
        }
        it("음수 길이 토큰은 IllegalArgumentException (n<0 분기)") {
            shouldThrow<IllegalArgumentException> {
                dmp.diff_fromDelta("abc", "=-1")
            }
        }
        it("알 수 없는 연산 문자는 IllegalArgumentException (default 분기)") {
            shouldThrow<IllegalArgumentException> {
                dmp.diff_fromDelta("abc", "!abc")
            }
        }
        it("빈 토큰(연속 탭)은 건너뛴다 (token.length()==0 -> continue 분기)") {
            dmp.diff_fromDelta("abcd", "=2\t\t=2") shouldBe diffList(d(EQUAL, "ab"), d(EQUAL, "cd"))
        }
    }

    describe("diff_linesToChars (및 private diff_linesToCharsMunge) / diff_charsToLines") {
        it("두 텍스트가 줄을 공유하면 같은 해시 문자로 인코딩된다") {
            val result = dmp.diff_linesToChars("alpha\nbeta\nalpha\n", "beta\nalpha\nbeta\n")
            result.chars1 shouldBe ""
            result.chars2 shouldBe ""
            result.lineArray shouldBe listOf("", "alpha\n", "beta\n")
        }
        it("빈 문자열과 빈 줄들") {
            val result = dmp.diff_linesToChars("", "alpha\r\nbeta\r\n\r\n\r\n")
            result.chars1 shouldBe ""
            result.chars2 shouldBe ""
            result.lineArray shouldBe listOf("", "alpha\r\n", "beta\r\n", "\r\n")
        }
        it("개행이 없는 한 글자짜리 줄") {
            val result = dmp.diff_linesToChars("a", "b")
            result.chars1 shouldBe ""
            result.chars2 shouldBe ""
            result.lineArray shouldBe listOf("", "a", "b")
        }
        it("256줄을 초과해도(8비트 한계 검증) 정상 동작한다") {
            val n = 300
            val lineList = StringBuilder()
            val expectedLineArray = mutableListOf("")
            val charList = StringBuilder()
            for (i in 1..n) {
                expectedLineArray.add("$i\n")
                lineList.append("$i\n")
                charList.append(i.toChar())
            }
            val lines = lineList.toString()
            val chars = charList.toString()
            chars.length shouldBe n

            val result = dmp.diff_linesToChars(lines, "")
            result.chars1 shouldBe chars
            result.chars2 shouldBe ""
            result.lineArray shouldBe expectedLineArray
        }
        it("charsToLines: 공유된 줄을 원래 텍스트로 되돌린다") {
            val diffs = diffList(d(EQUAL, ""), d(INSERT, ""))
            val lineArray = mutableListOf("", "alpha\n", "beta\n")
            dmp.diff_charsToLines(diffs, lineArray)
            diffs shouldBe diffList(d(EQUAL, "alpha\nbeta\nalpha\n"), d(INSERT, "beta\nalpha\nbeta\n"))
        }
        it("charsToLines: 256줄을 초과해도(8비트 한계) 정상 동작한다") {
            val n = 300
            val lineList = StringBuilder()
            val lineArray = mutableListOf("")
            val charList = StringBuilder()
            for (i in 1..n) {
                lineArray.add("$i\n")
                lineList.append("$i\n")
                charList.append(i.toChar())
            }
            val diffs = diffList(d(DELETE, charList.toString()))
            dmp.diff_charsToLines(diffs, lineArray)
            diffs shouldBe diffList(d(DELETE, lineList.toString()))
        }
        it("65536개를 초과하는 고유한 줄에서는 16비트 char 오버플로로 줄 매핑이 깨지는 실제 결함이 있다") {
            // 이 구버전은 diff_linesToCharsMunge에서 줄 해시를 (char)(lineArray.size()-1) 로 그대로
            // 캐스팅한다. char는 16비트(0..65535)이므로 고유한 줄이 65536개를 넘어 lineArray 인덱스가
            // 65536에 도달하면 (char)65536 이 오버플로되어 0이 되고, 이는 예약된 빈 문자열
            // (lineArray[0]) 자리와 충돌한다. upstream의 "More than 65536" 테스트는 이 한계를 넘도록
            // 리팩토링된 최신 버전을 검증하는 것이라 그대로 이식할 수 없었고, 대신 실제로 실행해 재현을
            // 확인한 결함을 최소 재현 사례로 남긴다: 65536번째(0-indexed 65535)로 추가된 고유한 줄
            // "65535"의 해시만 0으로 오버플로되어 빈 문자열로 손상된다.
            val n = 65536
            val lineList = StringBuilder()
            for (i in 0 until n) {
                lineList.append("$i\n")
            }
            val chars = lineList.toString()
            val results = dmp.diff_linesToChars(chars, "")
            val diffs = diffList(d(INSERT, results.chars1))
            dmp.diff_charsToLines(diffs, results.lineArray)
            val expectedCorrupted = chars.removeSuffix("${n - 1}\n")
            diffs.first().text shouldBe expectedCorrupted
        }
    }

    describe("diff_footprint") {
        it("두 int를 하나의 long으로 인코딩한다") {
            dmp.diff_footprint(0, 0) shouldBe 0L
            dmp.diff_footprint(1, 0) shouldBe (1L shl 32)
            dmp.diff_footprint(0, 1) shouldBe 1L
            dmp.diff_footprint(3, 5) shouldBe ((3L shl 32) + 5L)
        }
    }

    describe("diff_map / diff_path1 / diff_path2 (single-ended, doubleEnd=false)") {
        it("완전히 동일한 텍스트를 직접 diff_map에 넣으면 빈 리스트가 반환되는 실제 결함이 있다") {
            // max_d = 3+3-1 = 2 < 64 이므로 doubleEnd=false. d=0에서 대각선 이동만으로 즉시
            // 코너(x=3,y=3)에 도달해 diff_path1(v_map1, ...)을 호출하는데, 이때 v_map1.size()==1이라
            // diff_path1의 역추적 루프 `for (d = v_map.size()-2; d >= 0; d--)` 가 d=-1이 되어 단 한 번도
            // 실행되지 않는다. 그 결과 실제로 일치하는 "abc" 전체가 통째로 사라지고 빈 리스트가 반환된다.
            // diff_main은 완전 동일 텍스트를 diff_map 호출 전에 별도로 처리하므로 공개 API로는 이 결함이
            // 드러나지 않지만, protected 메서드인 diff_map/diff_path1을 직접 호출하면 실제로 재현되는
            // 결함임을 직접 실행해 확인했다.
            dmp.diff_map("abc", "abc") shouldBe diffList()
        }
        it("공통 문자가 전혀 없는 아주 짧은 텍스트는 null을 반환한다 (커밋 불가 -> return null)") {
            // max_d = 1+1-1 = 1 이라 d=0 한 번만 돌고 코너(x=1,y=1)에 도달하지 못해 for문을 그냥 빠져나온다.
            dmp.diff_map("a", "b") shouldBe null
        }
        it("직접 손으로 추적 검증한 결정적 케이스: diff_path1의 DELETE/EQUAL/INSERT 세 분기를 모두 순서대로 탄다") {
            // text1="ab", text2="ba" -> d=2에서 코너 도달, diff_path1 역추적 결과는
            // [INSERT("b"), EQUAL("a"), DELETE("b")] 로 알고리즘을 직접 추적해 확인했다.
            dmp.diff_map("ab", "ba") shouldBe diffList(d(INSERT, "b"), d(EQUAL, "a"), d(DELETE, "b"))
        }
        it("연속된 동일 연산이 diff_path1에서 하나의 Diff로 병합된다 (last_op 일치 시 텍스트 이어붙이기 분기)") {
            val result = dmp.diff_map("aaabbb", "bbbaaa")
            result shouldNotBe null
            dmp.diff_text1(result) shouldBe "aaabbb"
            dmp.diff_text2(result) shouldBe "bbbaaa"
            // 병합이 실제로 일어났다면 연산 개수가 원문 글자 수(12)보다 훨씬 적어야 한다.
            (result!!.size < 12) shouldBe true
        }
        it("전체를 삭제 후 삽입하는 경우도 왕복 복원이 정확하다") {
            val result = dmp.diff_map("cat", "map")
            result shouldNotBe null
            dmp.diff_text1(result) shouldBe "cat"
            dmp.diff_text2(result) shouldBe "map"
        }
    }

    describe("diff_map / diff_path1 / diff_path2 (double-ended, doubleEnd=true)") {
        // Diff_DualThreshold(기본 32) * 2 < max_d(=len1+len2-1) 일 때만 doubleEnd=true가 되므로
        // 두 텍스트 길이 합이 66자를 넘도록 구성한다.
        it("총 길이가 홀수이면 front=true가 되어 정방향 경로가 역방향 footprint와 충돌하는 분기를 탄다") {
            val text1 = "A".repeat(30) + "MIDDLE" + "B".repeat(31) // 37+... 길이 계산은 아래 주석 참고
            val text2 = "X".repeat(30) + "MIDDLE" + "Y".repeat(30)
            // text1.length=30+6+31=67, text2.length=30+6+30=66, 합=133(홀수) -> front=true, max_d=132>64
            val result = dmp.diff_map(text1, text2)
            result shouldNotBe null
            dmp.diff_text1(result) shouldBe text1
            dmp.diff_text2(result) shouldBe text2
        }
        it("총 길이가 짝수이면 front=false가 되어 역방향 경로가 정방향 footprint와 충돌하는 분기를 탄다") {
            val text1 = "A".repeat(30) + "MIDDLE" + "B".repeat(30)
            val text2 = "X".repeat(30) + "MIDDLE" + "Y".repeat(30)
            // text1.length=66, text2.length=66, 합=132(짝수) -> front=false, max_d=131>64
            val result = dmp.diff_map(text1, text2)
            result shouldNotBe null
            dmp.diff_text1(result) shouldBe text1
            dmp.diff_text2(result) shouldBe text2
        }
        it("공통 문자가 전혀 없어도 doubleEnd 상태에서는 좌표 접점만으로 (병합되지 않은) 유효한 diff를 찾아낸다") {
            // front/reverse 두 경로가 만나는 지점(done=true)은 좌표(x,y)의 footprint 충돌만으로
            // 판정되며 실제 문자 일치 여부와는 무관하다. 따라서 두 텍스트에 공통 문자가 전혀 없어도
            // doubleEnd일 때는 (단일 경로의 max_d로는 부족했을) 전체 delete+insert 편집 경로를
            // 두 경로의 접점을 통해 찾아낸다 (직접 실행해 null이 아님을 확인했다).
            val text1 = ('a'..'z').joinToString("") { c -> c.toString().repeat(3) } // 78자, 알파벳만
            val text2 = (0..99).joinToString("") { it.toString() } // 숫자만, 알파벳과 공통 문자 없음
            val result = dmp.diff_map(text1, text2)
            result shouldNotBe null
            dmp.diff_text1(result) shouldBe text1
            dmp.diff_text2(result) shouldBe text2
        }
        it("Diff_Timeout을 매우 작게 주면 완료 전에 시간 초과로 null을 반환한다") {
            dmp.Diff_Timeout = 0.001f // 1ms
            val text1 = "A".repeat(1000) + "MATCH" + "B".repeat(1000)
            val text2 = "X".repeat(1000) + "MATCH" + "Y".repeat(1000)
            val result = dmp.diff_map(text1, text2)
            result shouldBe null
        }
        it("Diff_Timeout이 0(무제한)이면 시간 초과 검사 자체를 건너뛴다 (Diff_Timeout>0 분기의 false 경로)") {
            dmp.Diff_Timeout = 0f
            val text1 = "A".repeat(30) + "MIDDLE" + "B".repeat(30)
            val text2 = "X".repeat(30) + "MIDDLE" + "Y".repeat(30)
            val result = dmp.diff_map(text1, text2)
            result shouldNotBe null
            dmp.diff_text1(result) shouldBe text1
            dmp.diff_text2(result) shouldBe text2
        }
    }

    describe("diff_compute") {
        it("text1이 빈 문자열이면 INSERT 하나로 처리한다") {
            dmp.diff_compute("", "abc", false) shouldBe diffList(d(INSERT, "abc"))
        }
        it("text2가 빈 문자열이면 DELETE 하나로 처리한다") {
            dmp.diff_compute("abc", "", false) shouldBe diffList(d(DELETE, "abc"))
        }
        it("긴 문자열 안에 짧은 문자열이 그대로 포함되어 있으면 (text2가 더 김 -> INSERT) 지름길을 탄다") {
            dmp.diff_compute("cd", "abcdef", false) shouldBe
                diffList(d(INSERT, "ab"), d(EQUAL, "cd"), d(INSERT, "ef"))
        }
        it("긴 문자열 안에 짧은 문자열이 그대로 포함되어 있으면 (text1이 더 김 -> DELETE) 지름길을 탄다") {
            dmp.diff_compute("abcdef", "cd", false) shouldBe
                diffList(d(DELETE, "ab"), d(EQUAL, "cd"), d(DELETE, "ef"))
        }
        it("하프매치가 발견되면 좌우를 나눠 재귀적으로 diff_main을 호출한다") {
            val result = dmp.diff_compute("1234567890", "a345678z", false)
            dmp.diff_text1(result) shouldBe "1234567890"
            dmp.diff_text2(result) shouldBe "a345678z"
            // 하프매치 재귀 경로를 탔다면 결과에 공통 중간부(EQUAL "345678")가 포함되어야 한다.
            result.any { it.operation == EQUAL && it.text == "345678" } shouldBe true
        }
        it("하프매치도 없고 부분 포함도 아니지만 diff_map이 성공하는 경우") {
            val result = dmp.diff_compute("ab", "ba", false)
            dmp.diff_text1(result) shouldBe "ab"
            dmp.diff_text2(result) shouldBe "ba"
        }
        it("diff_map조차 실패하면 (null) DELETE+INSERT로 폴백한다") {
            dmp.diff_compute("ab", "xy", false) shouldBe diffList(d(DELETE, "ab"), d(INSERT, "xy"))
        }
        it("checklines=true여도 두 텍스트 모두 100자 미만이면 라인 모드를 쓰지 않는다 (내부 강등 분기)") {
            // diff_compute의 javadoc은 "두 텍스트가 공통 접두/접미를 갖지 않는다고 가정한다"는
            // 전제를 명시하므로, 맨 앞/맨 뒤 글자를 서로 다르게 만들어 이 전제를 지킨다.
            val result = dmp.diff_compute("1\nb\n2", "3\nx\n4", true)
            dmp.diff_text1(result) shouldBe "1\nb\n2"
            dmp.diff_text2(result) shouldBe "3\nx\n4"
        }
        it("checklines=true이고 한쪽만 100자 이상이어도 라인 모드를 쓰지 않는다 (OR의 두 번째 피연산자 분기)") {
            val text1 = (1..30).joinToString("") { "longline$it\n" } // 100자 이상, 'l'로 시작해 '\n'으로 끝남
            val text2 = "short" // 공통 접두/접미가 없도록 's'로 시작해 't'로 끝나게 한다
            val result = dmp.diff_compute(text1, text2, true)
            dmp.diff_text1(result) shouldBe text1
            dmp.diff_text2(result) shouldBe text2
        }
        it("checklines=true이고 두 텍스트 모두 100자 이상이면 라인 모드로 처리하고, 교체된 줄은 문자 단위로 재-diff한다") {
            val base = "commonline number %d placeholder text here\n"
            val text1Lines = (1..4).map { base.format(it) }
            val text2Lines = text1Lines.toMutableList()
            text2Lines[1] = "totally different replaced content that shares nothing at all\n"
            // diff_compute는 공통 접두/접미가 없다고 가정하므로, 앞뒤에 서로 다른 글자를 덧붙여
            // (원래대로라면 diff_main이 벗겨낼) 공통 접두사/접미사가 생기지 않게 한다.
            val text1 = "Z" + text1Lines.joinToString("") + "9"
            val text2 = "Q" + text2Lines.joinToString("") + "8"
            (text1.length >= 100 && text2.length >= 100) shouldBe true

            val result = dmp.diff_compute(text1, text2, true)
            dmp.diff_text1(result) shouldBe text1
            dmp.diff_text2(result) shouldBe text2
        }
        it("라인 모드에서 줄이 교체 없이 통째로 삭제만 되면 재-diff 조건의 insert==0 분기를 탄다") {
            // diff_compute 안의 라인 모드 재-diff 루프는 EQUAL을 만날 때마다
            // count_delete>=1 && count_insert>=1 을 검사한다. 이 테스트는 줄 하나가 대체 없이
            // 통째로 삭제되기만 하므로(짝이 되는 insert가 없음) count_insert==0 인 채로 그 조건에
            // 도달해, 두 값을 모두 평가해야 하는 분기(count_delete>=1은 참, count_insert>=1은 거짓)를 탄다.
            val base = "commonline number %d placeholder text here\n"
            val text1Lines = (1..4).map { base.format(it) }
            val text2Lines = text1Lines.toMutableList()
            text2Lines.removeAt(1) // 두 번째 줄을 대체 없이 그냥 삭제
            val text1 = "Z" + text1Lines.joinToString("") + "9"
            val text2 = "Q" + text2Lines.joinToString("") + "8"
            (text1.length >= 100 && text2.length >= 100) shouldBe true

            val result = dmp.diff_compute(text1, text2, true)
            dmp.diff_text1(result) shouldBe text1
            dmp.diff_text2(result) shouldBe text2
        }
    }

    describe("diff_main") {
        it("두 인자 오버로드는 checklines=true로 위임한다") {
            val result = dmp.diff_main("hello world", "hello there")
            dmp.diff_text1(result) shouldBe "hello world"
            dmp.diff_text2(result) shouldBe "hello there"
        }
        it("빈 문자열끼리는 EQUAL(\"\") 하나짜리 리스트를 반환한다 (text1.equals(text2) 분기, 길이 0 체크 없음)") {
            dmp.diff_main("", "", false) shouldBe diffList(d(EQUAL, ""))
        }
        it("완전히 동일한 텍스트는 EQUAL 하나로 즉시 반환한다") {
            dmp.diff_main("abc", "abc", false) shouldBe diffList(d(EQUAL, "abc"))
        }
        it("공통 접두/접미가 전혀 없으면 addFirst/addLast를 모두 건너뛴다") {
            dmp.diff_main("abc", "xyz", false) shouldBe diffList(d(DELETE, "abc"), d(INSERT, "xyz"))
        }
        it("공통 접두사와 접미사가 모두 있으면 양쪽 다 복원한다") {
            dmp.diff_main("abcXdef", "abcYdef", false) shouldBe
                diffList(d(EQUAL, "abc"), d(DELETE, "X"), d(INSERT, "Y"), d(EQUAL, "def"))
        }
        it("공통 접두사만 있으면 앞쪽만 복원한다 (commonsuffix==0 분기)") {
            dmp.diff_main("abcXY", "abcZW", false) shouldBe
                diffList(d(EQUAL, "abc"), d(DELETE, "XY"), d(INSERT, "ZW"))
        }
        it("공통 접미사만 있으면 뒤쪽만 복원한다 (commonprefix==0 분기)") {
            dmp.diff_main("XYabc", "ZWabc", false) shouldBe
                diffList(d(DELETE, "XY"), d(INSERT, "ZW"), d(EQUAL, "abc"))
        }
        it("checklines=true인 실제 다중 라인 텍스트에서도 왕복 복원이 정확하다") {
            val text1 = "line one here\nline two here\nline three here\n".repeat(3)
            val text2 = text1.replace("line two here", "line TWO changed here")
            val result = dmp.diff_main(text1, text2, true)
            dmp.diff_text1(result) shouldBe text1
            dmp.diff_text2(result) shouldBe text2
        }
    }
})
