package tools

import kotlinx.serialization.json.JsonObject

/**
 * 도구 인터페이스. Claude 에게는 (name, description, schema) 가 노출되고,
 * 실제 실행은 execute() 가 담당한다.
 */
interface Tool {
    val name: String
    val description: String

    /** JSON Schema (input_schema 의 내용). type:"object" + properties + required. */
    val schema: JsonObject

    /** 도구 실행. 입력은 Claude 가 채운 JSON, 반환은 tool_result content 문자열. */
    fun execute(input: JsonObject): String

    /** Anthropic tools 배열에 넣을 도구 정의 JSON 으로 변환. */
    fun toToolDefinition(): JsonObject = kotlinx.serialization.json.buildJsonObject {
        put("name", kotlinx.serialization.json.JsonPrimitive(name))
        put("description", kotlinx.serialization.json.JsonPrimitive(description))
        put("input_schema", schema)
    }
}
