package eval

import ToolLoop
import generate.AnthropicClient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import model.json
import model.text
import model.userMessage
import java.io.File

/**
 * 3개 층위 채점:
 *   ① 도구 선택  — 질문에 맞는 도구를 골랐나 (규칙)
 *   ② 검색 적중  — search_docs 가 정답 출처를 찾았나 (규칙)
 *   ③ 답변 충실  — 근거 있나 / 환각 없나 (규칙 or LLM 채점)
 *
 * 검색/계산/도구 없음/모름 케이스를 섞어 라우팅 분기를 확인한다.
 */
object EvalRunner {
    fun run(loop: ToolLoop, casesPath: String = "eval/cases.json") {
        val file = File(casesPath)
        if (!file.exists()) error("평가 케이스($casesPath)가 없습니다.")
        val cases = json.decodeFromString(JsonArray.serializer(), file.readText())
        val judge = AnthropicClient()

        var toolPass = 0; var toolTotal = 0
        var searchPass = 0; var searchTotal = 0
        var answerPass = 0; var answerTotal = 0

        cases.forEachIndexed { i, raw ->
            val c = raw as JsonObject
            val input = c["input"]!!.jsonPrimitive.content
            println("\n[케이스 ${i + 1}] $input")

            val result = loop.runConversation(input)
            println("  도구: ${result.toolsUsed} | 답: ${result.finalText.take(60)}...")

            // ① 도구 선택
            if (c.containsKey("expect_tool")) {
                toolTotal++
                val tv = c["expect_tool"]!!
                val expected = if (tv is JsonNull) null else tv.jsonPrimitive.content
                val ok = if (expected == null) result.toolsUsed.isEmpty()
                else result.toolsUsed.contains(expected)
                if (ok) toolPass++
                println("  ① 도구선택: ${mark(ok)} (기대=$expected)")
            }

            // ② 검색 적중
            c["expect_source"]?.let { src ->
                searchTotal++
                val source = src.jsonPrimitive.content
                val joined = result.searchOutputs.joinToString("\n")
                val ok = joined.contains("[출처: $source")
                if (ok) searchPass++
                println("  ② 검색적중: ${mark(ok)} (정답출처=$source)")
            }

            // ③ 답변 충실
            when {
                c.containsKey("expect_answer_contains") -> {
                    answerTotal++
                    val needle = c["expect_answer_contains"]!!.jsonPrimitive.content
                    val ok = result.finalText.contains(needle)
                    if (ok) answerPass++
                    println("  ③ 답변충실: ${mark(ok)} (포함기대=$needle)")
                }
                c.containsKey("expect_answer") -> {
                    answerTotal++
                    val criterion = c["expect_answer"]!!.jsonPrimitive.content
                    val ok = judgeByLlm(judge, input, result.finalText, criterion)
                    if (ok) answerPass++
                    println("  ③ 답변충실(LLM): ${mark(ok)} (기준=$criterion)")
                }
            }
        }

        println("\n===== 채점 결과 =====")
        println("① 도구 선택 : $toolPass/$toolTotal")
        println("② 검색 적중 : $searchPass/$searchTotal")
        println("③ 답변 충실 : $answerPass/$answerTotal")
    }

    /** 자연어 기준은 LLM 으로 채점 (PASS/FAIL 한 단어). */
    private fun judgeByLlm(
        judge: AnthropicClient,
        question: String,
        answer: String,
        criterion: String,
    ): Boolean {
        val prompt = """
            아래 [답변]이 [기준]을 만족하면 PASS, 아니면 FAIL 한 단어만 출력해라.

            [질문] $question
            [답변] $answer
            [기준] $criterion
        """.trimIndent()
        val resp = judge.createMessage(messages = listOf(userMessage(prompt)))
        return resp.text().uppercase().contains("PASS")
    }

    private fun mark(ok: Boolean) = if (ok) "PASS" else "FAIL"
}
