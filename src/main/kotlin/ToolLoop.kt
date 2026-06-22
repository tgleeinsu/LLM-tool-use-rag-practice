import generate.AnthropicClient
import model.Message
import model.text
import model.toAssistantMessage
import model.toolResult
import model.toolUseBlocks
import model.userMessage
import tools.ToolRegistry

/**
 * ★ 4단계 tool_use 연쇄 루프.
 *
 * user → assistant(tool_use) → user(tool_result) → ... → assistant(end_turn)
 * search_docs 가 도구 목록에 끼어 있을 뿐, calculator/get_date 와 동일하게 다뤄진다.
 * (RAG 의 복잡함은 search_docs 도구 내부에 숨겨져 있다)
 */
class ToolLoop(
    private val anthropic: AnthropicClient,
    private val registry: ToolRegistry,
    private val maxSteps: Int = 8,
) {
    /** 한 질문에 대한 전체 대화 결과 (chat 은 finalText, eval 은 나머지도 사용). */
    data class ConversationResult(
        val finalText: String,
        val toolsUsed: List<String>,     // 호출된 도구명 (순서대로)
        val searchOutputs: List<String>, // search_docs 결과 문자열 (검색 적중 채점용)
    )

    fun runConversation(userInput: String): ConversationResult {
        val history = mutableListOf<Message>(userMessage(userInput))
        val toolsUsed = mutableListOf<String>()
        val searchOutputs = mutableListOf<String>()

        var steps = 0
        while (steps++ < maxSteps) {                       // 무한 루프 가드 (MAX_STEPS)
            val resp = anthropic.createMessage(
                messages = history,
                tools = registry.schemas(),                // search_docs + calculator + get_date
                system = SYSTEM_PROMPT,
            )
            history.add(resp.toAssistantMessage())         // stateless 누적 (content 전체 보존)

            // 도구 호출이 아니면 최종 답
            if (resp.stop_reason != "tool_use") {
                return ConversationResult(resp.text(), toolsUsed, searchOutputs)
            }

            // 도구 실행 → 결과를 한 user 메시지에 모아 반환 (id 짝 맞추기 ★)
            val results = resp.toolUseBlocks().map { call ->
                toolsUsed += call.name
                val outcome = registry.execute(call.name, call.input)
                if (call.name == "search_docs" && !outcome.isError) {
                    searchOutputs += outcome.content
                }
                toolResult(
                    toolUseId = call.id,
                    content = outcome.content,
                    isError = outcome.isError,             // 환각 가드레일 ① is_error
                )
            }
            history.add(userMessage(results))
        }
        return ConversationResult("최대 단계(${maxSteps})를 초과했습니다.", toolsUsed, searchOutputs)
    }

    companion object {
        // 환각 가드레일 ②: 모르면 모른다 / 출처 표기
        val SYSTEM_PROMPT = """
            너는 도구를 활용해 정확하게 답하는 한국어 어시스턴트다.
            - 문서에 있을 법한 사실·설정·과거 기록은 search_docs 도구로 검색해서 답해라.
            - 계산은 calculator, 현재 날짜/시각은 get_date 도구를 사용해라.
            - 단순 인사나 잡담은 도구 없이 바로 답해도 된다.
            - search_docs 결과에 근거가 없으면 모른다고 솔직히 답하고 지어내지 마라.
            - 문서를 근거로 답할 때는 출처(파일명)를 함께 밝혀라.
        """.trimIndent()
    }
}
