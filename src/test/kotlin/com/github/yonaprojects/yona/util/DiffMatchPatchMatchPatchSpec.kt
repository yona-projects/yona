package com.github.yonaprojects.yona.util

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import java.util.LinkedList

/**
 * diff_match_patch.java 의 match_ / patch_ 접두사 메서드에 대한 테스트.
 *
 * 이 파일은 diff_ 접두사 메서드(다른 에이전트가 병렬로 테스트 작성 중)를 절대 수정하지 않으며,
 * 단지 이미 구현되어 있는 diff_main 등을 내부적으로 호출하는 match_/patch_ 메서드들만을 대상으로 한다.
 *
 * 상당수 테스트 케이스는 Neil Fraser의 공식 google/diff-match-patch 저장소
 * (java/tests/name/fraser/neil/plaintext/diff_match_patch_test.java, Apache-2.0)에 있는
 * testMatchAlphabet / testMatchBitap / testMatchMain / testPatchAddContext / testPatchMake /
 * testPatchSplitMax / testPatchAddPadding / testPatchApply / testPatchToText / testPatchFromText
 * 를 이식한 것이다. 단, 업스트림 최신 버전에는 있지만 이 저장소의 diff_match_patch.java(legacy 포팅본)에는
 * 존재하지 않는 null 입력 가드(match_main, patch_make(LinkedList) 의 "Null inputs" 케이스)는
 * 실제 소스를 직접 읽어 해당 null 체크 코드가 없음을 확인했으므로 이식하지 않았다(이식 시
 * NullPointerException 이 발생해 업스트림이 기대하는 IllegalArgumentException 과 달라짐).
 */
class DiffMatchPatchMatchPatchSpec : DescribeSpec({

    val EQUAL = diff_match_patch.Operation.EQUAL
    val INSERT = diff_match_patch.Operation.INSERT
    val DELETE = diff_match_patch.Operation.DELETE

    fun diffsOf(vararg pairs: Pair<diff_match_patch.Operation, String>): LinkedList<diff_match_patch.Diff> {
        val list = LinkedList<diff_match_patch.Diff>()
        pairs.forEach { (op, text) -> list.add(diff_match_patch.Diff(op, text)) }
        return list
    }

    // match_bitapScore, unescapeForEncodeUriCompatability 는 private 메서드라 리플렉션으로 호출한다.
    fun callBitapScore(dmp: diff_match_patch, e: Int, x: Int, loc: Int, pattern: String): Double {
        val method = diff_match_patch::class.java.getDeclaredMethod(
            "match_bitapScore",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            String::class.java
        )
        method.isAccessible = true
        return method.invoke(dmp, e, x, loc, pattern) as Double
    }

    fun callUnescape(str: String): String {
        val method = diff_match_patch::class.java.getDeclaredMethod(
            "unescapeForEncodeUriCompatability",
            String::class.java
        )
        method.isAccessible = true
        return method.invoke(null, str) as String
    }

    describe("match_alphabet") {
        it("각 문자가 유일할 때 마지막 비트만 설정된다 (업스트림: Unique)") {
            val dmp = diff_match_patch()
            val result = dmp.match_alphabet("abc")
            result shouldBe mapOf('a' to 4, 'b' to 2, 'c' to 1)
        }

        it("문자가 중복되면 비트가 OR 로 누적된다 (업스트림: Duplicates)") {
            val dmp = diff_match_patch()
            val result = dmp.match_alphabet("abcaba")
            result shouldBe mapOf('a' to 37, 'b' to 18, 'c' to 8)
        }

        it("빈 패턴이면 두 for 루프 모두 0회 반복하고 빈 맵을 반환한다 (루프 진입/미진입 분기 커버)") {
            val dmp = diff_match_patch()
            val result = dmp.match_alphabet("")
            result shouldBe emptyMap()
        }
    }

    describe("match_bitapScore (private, 리플렉션으로 직접 호출)") {
        it("Match_Distance != 0 이면 accuracy + proximity/distance 공식을 사용한다") {
            val dmp = diff_match_patch()
            dmp.Match_Distance = 1000
            callBitapScore(dmp, 0, 5, 5, "abc") shouldBe (0.0 plusOrMinus 0.0001)
            callBitapScore(dmp, 1, 10, 5, "abcd") shouldBe (0.255 plusOrMinus 0.0001)
        }

        it("Match_Distance == 0 이고 proximity == 0 이면 accuracy 를 그대로 반환한다") {
            val dmp = diff_match_patch()
            dmp.Match_Distance = 0
            callBitapScore(dmp, 2, 7, 7, "abcd") shouldBe (0.5 plusOrMinus 0.0001)
        }

        it("Match_Distance == 0 이고 proximity != 0 이면 1.0(최악)을 반환한다") {
            val dmp = diff_match_patch()
            dmp.Match_Distance = 0
            callBitapScore(dmp, 1, 9, 7, "abcd") shouldBe (1.0 plusOrMinus 0.0001)
        }
    }

    describe("match_bitap (업스트림 testMatchBitap 이식)") {
        it("정확/퍼지/임계값/거리 관련 케이스들을 순차적으로 검증한다") {
            val dmp = diff_match_patch()
            dmp.Match_Distance = 100
            dmp.Match_Threshold = 0.5f

            dmp.match_bitap("abcdefghijk", "fgh", 5) shouldBe 5
            dmp.match_bitap("abcdefghijk", "fgh", 0) shouldBe 5
            dmp.match_bitap("abcdefghijk", "efxhi", 0) shouldBe 4
            dmp.match_bitap("abcdefghijk", "cdefxyhijk", 5) shouldBe 2
            dmp.match_bitap("abcdefghijk", "bxy", 1) shouldBe -1
            dmp.match_bitap("123456789xx0", "3456789x0", 2) shouldBe 2
            dmp.match_bitap("abcdef", "xxabc", 4) shouldBe 0
            dmp.match_bitap("abcdef", "defyy", 4) shouldBe 3
            dmp.match_bitap("abcdef", "xabcdefy", 0) shouldBe 0

            dmp.Match_Threshold = 0.4f
            dmp.match_bitap("abcdefghijk", "efxyhi", 1) shouldBe 4

            dmp.Match_Threshold = 0.3f
            dmp.match_bitap("abcdefghijk", "efxyhi", 1) shouldBe -1

            dmp.Match_Threshold = 0.0f
            dmp.match_bitap("abcdefghijk", "bcdef", 1) shouldBe 1

            dmp.Match_Threshold = 0.5f
            dmp.match_bitap("abcdexyzabcde", "abccde", 3) shouldBe 0
            dmp.match_bitap("abcdexyzabcde", "abccde", 5) shouldBe 8

            dmp.Match_Distance = 10
            dmp.match_bitap("abcdefghijklmnopqrstuvwxyz", "abcdefg", 24) shouldBe -1
            dmp.match_bitap("abcdefghijklmnopqrstuvwxyz", "abcdxxefg", 1) shouldBe 0

            dmp.Match_Distance = 1000
            dmp.match_bitap("abcdefghijklmnopqrstuvwxyz", "abcdefg", 24) shouldBe 0
        }
    }

    describe("match_main (업스트림 testMatchMain 이식, null 입력 케이스는 이 저장소 코드에 가드가 없어 제외)") {
        it("동일 텍스트/빈 텍스트/정확한 위치/범위초과/퍼지 매칭을 검증한다") {
            val dmp = diff_match_patch()
            dmp.match_main("abcdef", "abcdef", 1000) shouldBe 0
            dmp.match_main("", "abcdef", 1) shouldBe -1
            dmp.match_main("abcdef", "", 3) shouldBe 3
            dmp.match_main("abcdef", "de", 3) shouldBe 3
            dmp.match_main("abcdef", "defy", 4) shouldBe 3
            dmp.match_main("abcdef", "abcdefy", 0) shouldBe 0

            dmp.Match_Threshold = 0.7f
            dmp.match_main(
                "I am the very model of a modern major general.",
                " that berry ",
                5
            ) shouldBe 4
        }
    }

    describe("patch_addContext (업스트림 testPatchAddContext 이식)") {
        it("충분한 컨텍스트, 부족한 앞/뒤 컨텍스트, 모호한 경우를 검증한다") {
            val dmp = diff_match_patch()
            dmp.Patch_Margin = 4

            var p = dmp.patch_fromText("@@ -21,4 +21,10 @@\n-jump\n+somersault\n")[0]
            dmp.patch_addContext(p, "The quick brown fox jumps over the lazy dog.")
            p.toString() shouldBe "@@ -17,12 +17,18 @@\n fox \n-jump\n+somersault\n s ov\n"

            p = dmp.patch_fromText("@@ -21,4 +21,10 @@\n-jump\n+somersault\n")[0]
            dmp.patch_addContext(p, "The quick brown fox jumps.")
            p.toString() shouldBe "@@ -17,10 +17,16 @@\n fox \n-jump\n+somersault\n s.\n"

            p = dmp.patch_fromText("@@ -3 +3,2 @@\n-e\n+at\n")[0]
            dmp.patch_addContext(p, "The quick brown fox jumps.")
            p.toString() shouldBe "@@ -1,7 +1,8 @@\n Th\n-e\n+at\n  qui\n"

            p = dmp.patch_fromText("@@ -3 +3,2 @@\n-e\n+at\n")[0]
            dmp.patch_addContext(p, "The quick brown fox jumps.  The quick brown fox crashes.")
            p.toString() shouldBe "@@ -1,27 +1,28 @@\n Th\n-e\n+at\n  quick brown fox jumps. \n"
        }

        it("빈 텍스트가 주어지면 즉시 반환하고 patch 를 변경하지 않는다 (text.length()==0 분기)") {
            val dmp = diff_match_patch()
            val p = dmp.patch_fromText("@@ -3 +3,2 @@\n-e\n+at\n")[0]
            val before = p.toString()
            dmp.patch_addContext(p, "")
            p.toString() shouldBe before
        }
    }

    describe("patch_make (업스트림 testPatchMake 이식, 오버로드 4종 전부 포함)") {
        it("빈 입력이면 빈 패치를 반환한다 (diffs.isEmpty() 분기)") {
            val dmp = diff_match_patch()
            val patches = dmp.patch_make("", "")
            dmp.patch_toText(patches) shouldBe ""
        }

        it("text1/text2 순서를 바꾸면 rolling context 로 인해 서로 다른 헤더가 나온다") {
            val dmp = diff_match_patch()
            val text1 = "The quick brown fox jumps over the lazy dog."
            val text2 = "That quick brown fox jumped over a lazy dog."

            val expectedReverse =
                "@@ -1,8 +1,7 @@\n Th\n-at\n+e\n  qui\n@@ -21,17 +21,18 @@\n jump\n-ed\n+s\n  over \n-a\n+the\n  laz\n"
            val patchesReverse = dmp.patch_make(text2, text1)
            dmp.patch_toText(patchesReverse) shouldBe expectedReverse

            val expectedForward =
                "@@ -1,11 +1,12 @@\n Th\n-e\n+at\n  quick b\n@@ -22,18 +22,17 @@\n jump\n-s\n+ed\n  over \n-the\n+a\n  laz\n"

            val patchesForward = dmp.patch_make(text1, text2)
            dmp.patch_toText(patchesForward) shouldBe expectedForward

            val diffs = dmp.diff_main(text1, text2, false)

            val patchesFromDiff = dmp.patch_make(diffs)
            dmp.patch_toText(patchesFromDiff) shouldBe expectedForward

            val patchesFromText1Diff = dmp.patch_make(text1, diffs)
            dmp.patch_toText(patchesFromText1Diff) shouldBe expectedForward

            @Suppress("DEPRECATION")
            val patchesDeprecated = dmp.patch_make(text1, text2, diffs)
            dmp.patch_toText(patchesDeprecated) shouldBe expectedForward
        }

        it("특수문자는 %XX 형식으로 인코딩된다") {
            val dmp = diff_match_patch()
            val patches = dmp.patch_make("`1234567890-=[]\\;',./", "~!@#$%^&*()_+{}|:\"<>?")
            dmp.patch_toText(patches) shouldBe
                "@@ -1,21 +1,21 @@\n-%601234567890-=%5B%5D%5C;',./\n+~!@#\$%25%5E&*()_+%7B%7D%7C:%22%3C%3E?\n"
        }

        it("긴 반복 문자열에서도 올바른 위치에 패치를 만든다") {
            val dmp = diff_match_patch()
            var text1 = ""
            repeat(100) { text1 += "abcdef" }
            val text2 = text1 + "123"
            val patches = dmp.patch_make(text1, text2)
            dmp.patch_toText(patches) shouldBe
                "@@ -573,28 +573,31 @@\n cdefabcdefabcdefabcdefabcdef\n+123\n"
        }
    }

    describe("patch_deepCopy") {
        it("깊은 복사본을 변경해도 원본 patch/diff 는 영향받지 않는다") {
            val dmp = diff_match_patch()
            val original = dmp.patch_make("hello world", "hello brave world")
            val copy = dmp.patch_deepCopy(original)

            copy.size shouldBe original.size
            copy[0].diffs[0].text shouldBe original[0].diffs[0].text

            val originalStart1Before = original[0].start1
            val originalDiffTextBefore = original[0].diffs[0].text

            // 복사본만 변경
            copy[0].start1 = 999
            copy[0].diffs[0].text = "mutated"

            // 원본은 복사본 변경의 영향을 받지 않아야 한다.
            original[0].start1 shouldBe originalStart1Before
            original[0].diffs[0].text shouldBe originalDiffTextBefore
            copy[0].start1 shouldBe 999
            copy[0].diffs[0].text shouldBe "mutated"
        }

        it("원본 필드를 바꿔도 복사본에는 반영되지 않는다 (얕은 복사가 아님을 검증)") {
            val dmp = diff_match_patch()
            val original = dmp.patch_make("hello world", "hello brave world")
            val copy = dmp.patch_deepCopy(original)

            val originalStart1Before = original[0].start1
            copy[0].start1 = originalStart1Before + 12345

            original[0].start1 shouldBe originalStart1Before
        }

        it("빈 patch 목록이면 빈 목록을 반환한다 (바깥 for 루프 0회 반복)") {
            val dmp = diff_match_patch()
            val copy = dmp.patch_deepCopy(LinkedList())
            copy.size shouldBe 0
        }
    }

    describe("patch_splitMax (업스트림 testPatchSplitMax 이식, Match_MaxBits=32 가정)") {
        it("삽입 위주의 긴 패치는 여러 개의 작은 패치로 쪼개진다") {
            val dmp = diff_match_patch()
            val patches = dmp.patch_make(
                "abcdefghijklmnopqrstuvwxyz01234567890",
                "XabXcdXefXghXijXklXmnXopXqrXstXuvXwxXyzX01X23X45X67X89X0"
            )
            dmp.patch_splitMax(patches)
            dmp.patch_toText(patches) shouldBe (
                "@@ -1,32 +1,46 @@\n+X\n ab\n+X\n cd\n+X\n ef\n+X\n gh\n+X\n ij\n+X\n kl\n+X\n mn\n+X\n op\n+X\n " +
                    "qr\n+X\n st\n+X\n uv\n+X\n wx\n+X\n yz\n+X\n 012345\n@@ -25,13 +39,18 @@\n zX01\n+X\n 23\n+X\n " +
                    "45\n+X\n 67\n+X\n 89\n+X\n 0\n"
                )
        }

        it("Match_MaxBits 이하 길이의 패치는 쪼개지지 않고 그대로 남는다") {
            val dmp = diff_match_patch()
            val patches = dmp.patch_make(
                "abcdef1234567890123456789012345678901234567890123456789012345678901234567890uvwxyz",
                "abcdefuvwxyz"
            )
            val before = dmp.patch_toText(patches)
            dmp.patch_splitMax(patches)
            dmp.patch_toText(patches) shouldBe before
        }

        it("거대한 삭제는 하나의 청크로 통째로 넘어간다") {
            val dmp = diff_match_patch()
            val patches = dmp.patch_make(
                "1234567890123456789012345678901234567890123456789012345678901234567890",
                "abc"
            )
            dmp.patch_splitMax(patches)
            dmp.patch_toText(patches) shouldBe
                "@@ -1,32 +1,4 @@\n-1234567890123456789012345678\n 9012\n@@ -29,32 +1,4 @@\n-9012345678901234567890123456\n " +
                "7890\n@@ -57,14 +1,3 @@\n-78901234567890\n+abc\n"
        }

        it("여러 개의 작은 수정이 반복되는 문자열에서도 올바르게 분할된다") {
            val dmp = diff_match_patch()
            val patches = dmp.patch_make(
                "abcdefghij , h : 0 , t : 1 abcdefghij , h : 0 , t : 1 abcdefghij , h : 0 , t : 1",
                "abcdefghij , h : 1 , t : 1 abcdefghij , h : 1 , t : 1 abcdefghij , h : 0 , t : 1"
            )
            dmp.patch_splitMax(patches)
            dmp.patch_toText(patches) shouldBe
                "@@ -2,32 +2,32 @@\n bcdefghij , h : \n-0\n+1\n  , t : 1 abcdef\n@@ -29,32 +29,32 @@\n bcdefghij , h : \n" +
                "-0\n+1\n  , t : 1 abcdef\n"
        }

        it("직접 만든 [DELETE(28의 배수)+EQUAL] 패치에서, 남은 EQUAL 만으로 구성된 조각은 버려진다 (!empty 분기)") {
            // patch_size(=Match_MaxBits=32) 단위로 DELETE 가 정확히 28자씩 소진되도록 84자로 구성하면,
            // 마지막 DELETE 청크(3번째 patch) 생성 시 postcontext 로 남은 EQUAL("eee")을 미리보기(peek)
            // 형태로 한 번 덧붙이지만 bigpatch.diffs 에서 소비하지는 않는다. 그래서 바깥 while 루프가
            // 한 번 더 돌며 그 EQUAL 만으로 새 patch(4번째)를 만드는데, INSERT/DELETE 가 전혀 없어
            // empty==true 로 남아 patch_splitMax 내부에서 그 조각은 결과에 추가되지 않고 버려진다.
            // 즉 "eee" 는 3번째 patch 의 postcontext 로 정확히 1번만 나타나야 하고, 버려진 4번째 patch로
            // 인한 중복은 없어야 한다.
            val dmp = diff_match_patch()
            val bigDelete = "D".repeat(84)
            val trailingEqual = "eee"
            val bigPatch = diff_match_patch.Patch()
            bigPatch.start1 = 0
            bigPatch.start2 = 0
            bigPatch.length1 = bigDelete.length + trailingEqual.length
            bigPatch.length2 = trailingEqual.length
            bigPatch.diffs.add(diff_match_patch.Diff(DELETE, bigDelete))
            bigPatch.diffs.add(diff_match_patch.Diff(EQUAL, trailingEqual))

            val patches = LinkedList<diff_match_patch.Patch>()
            patches.add(bigPatch)

            dmp.patch_splitMax(patches)

            // 버려지는 patch 가 없었다면 4개가 되었을 것이므로, 3개라는 것 자체가 !empty 분기(버려짐)의 증거다.
            patches.size shouldBe 3
            patches.sumOf { p -> p.diffs.filter { it.operation == DELETE }.sumOf { it.text.length } } shouldBe 84

            val text = dmp.patch_toText(patches)
            val occurrences = Regex(Regex.escape(trailingEqual)).findAll(text).count()
            occurrences shouldBe 1
        }
    }

    describe("patch_addPadding (업스트림 testPatchAddPadding 이식)") {
        it("양쪽 끝 모두 컨텍스트가 없을 때 nullPadding 전체를 새 EQUAL 로 추가한다") {
            val dmp = diff_match_patch()
            val patches = dmp.patch_make("", "test")
            dmp.patch_toText(patches) shouldBe "@@ -0,0 +1,4 @@\n+test\n"
            dmp.patch_addPadding(patches)
            dmp.patch_toText(patches) shouldBe "@@ -1,8 +1,12 @@\n %01%02%03%04\n+test\n %01%02%03%04\n"
        }

        it("양쪽 끝 컨텍스트가 부분적일 때 기존 EQUAL 을 늘린다") {
            val dmp = diff_match_patch()
            val patches = dmp.patch_make("XY", "XtestY")
            dmp.patch_toText(patches) shouldBe "@@ -1,2 +1,6 @@\n X\n+test\n Y\n"
            dmp.patch_addPadding(patches)
            dmp.patch_toText(patches) shouldBe "@@ -2,8 +2,12 @@\n %02%03%04X\n+test\n Y%01%02%03\n"
        }

        it("양쪽 끝 컨텍스트가 이미 충분할 때는 늘리지 않는다") {
            val dmp = diff_match_patch()
            val patches = dmp.patch_make("XXXXYYYY", "XXXXtestYYYY")
            dmp.patch_toText(patches) shouldBe "@@ -1,8 +1,12 @@\n XXXX\n+test\n YYYY\n"
            dmp.patch_addPadding(patches)
            dmp.patch_toText(patches) shouldBe "@@ -5,8 +5,12 @@\n XXXX\n+test\n YYYY\n"
        }

        it("diffs 가 비어있는 patch 를 직접 만들면 diffs.isEmpty() 분기를 탄다") {
            // patch_make 를 통해서는 diffs 가 빈 Patch 가 만들어지지 않으므로, public API 계약을 이용해
            // 직접 Patch 를 구성하여 diffs.isEmpty() || first/last.operation != EQUAL 의 참 분기를 검증한다.
            val dmp = diff_match_patch()
            val emptyPatch = diff_match_patch.Patch()
            emptyPatch.start1 = 5
            emptyPatch.start2 = 5
            val patches = LinkedList<diff_match_patch.Patch>()
            patches.add(emptyPatch)

            val nullPadding = dmp.patch_addPadding(patches)

            nullPadding.length shouldBe 4
            patches[0].diffs.size shouldBe 1
            patches[0].diffs[0].operation shouldBe EQUAL
            patches[0].diffs[0].text shouldBe nullPadding
        }
    }

    describe("patch_apply (업스트림 testPatchApply 이식)") {
        it("빈 패치, 완전/부분 일치, 실패, 대형 삭제 등 주요 시나리오를 검증한다") {
            val dmp = diff_match_patch()
            dmp.Match_Distance = 1000
            dmp.Match_Threshold = 0.5f
            dmp.Patch_DeleteThreshold = 0.5f

            var patches = dmp.patch_make("", "")
            var results = dmp.patch_apply(patches, "Hello world.")
            var boolArray = results[1] as BooleanArray
            "${results[0]}\t${boolArray.size}" shouldBe "Hello world.\t0"

            patches = dmp.patch_make(
                "The quick brown fox jumps over the lazy dog.",
                "That quick brown fox jumped over a lazy dog."
            )
            results = dmp.patch_apply(patches, "The quick brown fox jumps over the lazy dog.")
            boolArray = results[1] as BooleanArray
            "${results[0]}\t${boolArray[0]}\t${boolArray[1]}" shouldBe
                "That quick brown fox jumped over a lazy dog.\ttrue\ttrue"

            results = dmp.patch_apply(patches, "The quick red rabbit jumps over the tired tiger.")
            boolArray = results[1] as BooleanArray
            "${results[0]}\t${boolArray[0]}\t${boolArray[1]}" shouldBe
                "That quick red rabbit jumped over a tired tiger.\ttrue\ttrue"

            results = dmp.patch_apply(patches, "I am the very model of a modern major general.")
            boolArray = results[1] as BooleanArray
            "${results[0]}\t${boolArray[0]}\t${boolArray[1]}" shouldBe
                "I am the very model of a modern major general.\tfalse\tfalse"

            patches = dmp.patch_make(
                "x1234567890123456789012345678901234567890123456789012345678901234567890y",
                "xabcy"
            )
            results = dmp.patch_apply(
                patches,
                "x123456789012345678901234567890-----++++++++++-----123456789012345678901234567890y"
            )
            boolArray = results[1] as BooleanArray
            "${results[0]}\t${boolArray[0]}\t${boolArray[1]}" shouldBe "xabcy\ttrue\ttrue"

            patches = dmp.patch_make(
                "x1234567890123456789012345678901234567890123456789012345678901234567890y",
                "xabcy"
            )
            results = dmp.patch_apply(
                patches,
                "x12345678901234567890---------------++++++++++---------------12345678901234567890y"
            )
            boolArray = results[1] as BooleanArray
            "${results[0]}\t${boolArray[0]}\t${boolArray[1]}" shouldBe
                "xabc12345678901234567890---------------++++++++++---------------12345678901234567890y\tfalse\ttrue"

            dmp.Patch_DeleteThreshold = 0.6f
            patches = dmp.patch_make(
                "x1234567890123456789012345678901234567890123456789012345678901234567890y",
                "xabcy"
            )
            results = dmp.patch_apply(
                patches,
                "x12345678901234567890---------------++++++++++---------------12345678901234567890y"
            )
            boolArray = results[1] as BooleanArray
            "${results[0]}\t${boolArray[0]}\t${boolArray[1]}" shouldBe "xabcy\ttrue\ttrue"
            dmp.Patch_DeleteThreshold = 0.5f

            dmp.Match_Threshold = 0.0f
            dmp.Match_Distance = 0
            patches = dmp.patch_make(
                "abcdefghijklmnopqrstuvwxyz--------------------1234567890",
                "abcXXXXXXXXXXdefghijklmnopqrstuvwxyz--------------------1234567YYYYYYYYYY890"
            )
            results = dmp.patch_apply(patches, "ABCDEFGHIJKLMNOPQRSTUVWXYZ--------------------1234567890")
            boolArray = results[1] as BooleanArray
            "${results[0]}\t${boolArray[0]}\t${boolArray[1]}" shouldBe
                "ABCDEFGHIJKLMNOPQRSTUVWXYZ--------------------1234567YYYYYYYYYY890\tfalse\ttrue"
            dmp.Match_Threshold = 0.5f
            dmp.Match_Distance = 1000

            patches = dmp.patch_make("", "test")
            var patchStr = dmp.patch_toText(patches)
            dmp.patch_apply(patches, "")
            dmp.patch_toText(patches) shouldBe patchStr

            patches = dmp.patch_make("The quick brown fox jumps over the lazy dog.", "Woof")
            patchStr = dmp.patch_toText(patches)
            dmp.patch_apply(patches, "The quick brown fox jumps over the lazy dog.")
            dmp.patch_toText(patches) shouldBe patchStr

            patches = dmp.patch_make("", "test")
            results = dmp.patch_apply(patches, "")
            boolArray = results[1] as BooleanArray
            "${results[0]}\t${boolArray[0]}" shouldBe "test\ttrue"

            patches = dmp.patch_make("XY", "XtestY")
            results = dmp.patch_apply(patches, "XY")
            boolArray = results[1] as BooleanArray
            "${results[0]}\t${boolArray[0]}" shouldBe "XtestY\ttrue"

            patches = dmp.patch_make("y", "y123")
            results = dmp.patch_apply(patches, "x")
            boolArray = results[1] as BooleanArray
            "${results[0]}\t${boolArray[0]}" shouldBe "x123\ttrue"
        }
    }

    describe("patch_toText") {
        it("단일/복수 패치를 텍스트로 직렬화한다") {
            val dmp = diff_match_patch()
            var strp = "@@ -21,18 +22,17 @@\n jump\n-s\n+ed\n  over \n-the\n+a\n  laz\n"
            var patches = dmp.patch_fromText(strp)
            dmp.patch_toText(patches) shouldBe strp

            strp = "@@ -1,9 +1,9 @@\n-f\n+F\n oo+fooba\n@@ -7,9 +7,9 @@\n obar\n-,\n+.\n  tes\n"
            patches = dmp.patch_fromText(strp)
            dmp.patch_toText(patches) shouldBe strp
        }

        it("빈 patch 목록이면 빈 문자열을 반환한다") {
            val dmp = diff_match_patch()
            dmp.patch_toText(LinkedList()) shouldBe ""
        }
    }

    describe("patch_fromText (업스트림 testPatchFromText 이식 + 추가 분기 커버)") {
        it("빈 문자열이면 빈 목록을 반환한다") {
            val dmp = diff_match_patch()
            dmp.patch_fromText("").isEmpty() shouldBe true
        }

        it("start1/start2 의 길이 생략(1), 0, 명시값 조합을 모두 왕복 파싱한다") {
            val dmp = diff_match_patch()
            val strp = "@@ -21,18 +22,17 @@\n jump\n-s\n+ed\n  over \n-the\n+a\n %0Alaz\n"
            dmp.patch_fromText(strp)[0].toString() shouldBe strp

            dmp.patch_fromText("@@ -1 +1 @@\n-a\n+b\n")[0].toString() shouldBe "@@ -1 +1 @@\n-a\n+b\n"
            dmp.patch_fromText("@@ -1,3 +0,0 @@\n-abc\n")[0].toString() shouldBe "@@ -1,3 +0,0 @@\n-abc\n"
            dmp.patch_fromText("@@ -0,0 +1,3 @@\n+abc\n")[0].toString() shouldBe "@@ -0,0 +1,3 @@\n+abc\n"
        }

        it("헤더 형식이 잘못되면 IllegalArgumentException 을 던진다") {
            val dmp = diff_match_patch()
            shouldThrow<IllegalArgumentException> {
                dmp.patch_fromText("Bad\nPatch\n")
            }
        }

        it("퍼센트 인코딩된 특수문자를 올바르게 복원한다") {
            val dmp = diff_match_patch()
            val expected = diffsOf(
                DELETE to "`1234567890-=[]\\;',./",
                INSERT to "~!@#\$%^&*()_+{}|:\"<>?"
            )
            dmp.patch_fromText(
                "@@ -1,21 +1,21 @@\n-%601234567890-=%5B%5D%5C;',./\n+~!@#\$%25%5E&*()_+%7B%7D%7C:%22%3C%3E?\n"
            )[0].diffs shouldBe expected
        }

        it("빈 줄(공백 라인)은 IndexOutOfBoundsException 으로 잡혀 조용히 건너뛴다") {
            val dmp = diff_match_patch()
            val patches = dmp.patch_fromText("@@ -1,3 +1,4 @@\n abc\n\n+d\n")
            patches[0].diffs shouldBe diffsOf(EQUAL to "abc", INSERT to "d")
        }

        it("잘못된 % 이스케이프는 IllegalArgumentException 으로 다시 던져진다") {
            val dmp = diff_match_patch()
            shouldThrow<IllegalArgumentException> {
                dmp.patch_fromText("@@ -1,2 +1,2 @@\n-a%\n")
            }
        }

        it("알 수 없는 접두 문자(sign)는 IllegalArgumentException 을 던진다") {
            val dmp = diff_match_patch()
            shouldThrow<IllegalArgumentException> {
                dmp.patch_fromText("@@ -1,1 +1,1 @@\nXbad\n")
            }
        }
    }

    describe("unescapeForEncodeUriCompatability (private, 리플렉션 직접 호출 + Patch.toString 경유 간접 커버)") {
        it("URLEncoder 가 남긴 %XX 코드를 원래 문자로 되돌린다") {
            val input = "%21%7E%27%28%29%3B%2F%3F%3A%40%26%3D%2B%24%2C%23"
            callUnescape(input) shouldBe "!~'();/?:@&=+\$,#"
        }

        it("대상이 아닌 문자는 그대로 남는다") {
            callUnescape("hello%20world") shouldBe "hello%20world"
        }
    }
})
