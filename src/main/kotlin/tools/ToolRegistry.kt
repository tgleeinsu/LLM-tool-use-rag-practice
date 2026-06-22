package tools

import kotlinx.serialization.json.JsonObject

/**
 * 도구 모음. 스키마 노출(schemas)과 이름 기반 실행 디스패치(execute)를 담당.
 * tool_result 의 is_error 가드레일도 여기서 처리한다.
 */
class ToolRegistry(tools: List<Tool>) {
    private val byName: Map<String, Tool> = tools.associateBy { it.name }

    /** Anthropic 요청의 tools 배열에 넣을 도구 정의 목록. */
    fun schemas(): List<JsonObject> = byName.values.map { it.toToolDefinition() }

    /** 모델이 호출 요청한 도구를 실제로 실행. 예외는 잡아서 에러 결과로 반환. */
    fun execute(name: String, input: JsonObject): ToolOutcome {
        val tool = byName[name]
            ?: return ToolOutcome("알 수 없는 도구: $name", isError = true)
        return try {
            ToolOutcome(tool.execute(input), isError = false)
        } catch (e: Exception) {
            ToolOutcome("도구 '$name' 실행 실패: ${e.message}", isError = true)
        }
    }

    /** 실행 결과 + 에러 여부 (tool_result is_error 에 매핑). */
    data class ToolOutcome(val content: String, val isError: Boolean)
}
