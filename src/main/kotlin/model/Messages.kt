package model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** 프로젝트 전역 공용 Json (모르는 필드는 무시 — API 응답에 안 쓰는 필드가 많다). */
val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

// ---------------------------------------------------------------------------
// 요청 DTO  (POST /v1/messages 바디)
// ---------------------------------------------------------------------------

@Serializable
data class CreateMessageRequest(
    val model: String,
    val max_tokens: Int,
    val messages: List<Message>,
    val system: String? = null,
    val tools: List<JsonObject>? = null,
    val tool_choice: JsonObject? = null,
)

/**
 * 한 턴의 메시지. content 는 문자열 또는 블록 배열(text / tool_use / tool_result)을
 * 담을 수 있어 JsonElement 로 둔다. (stateless 누적이라 매 턴 전체를 다시 보낸다)
 */
@Serializable
data class Message(
    val role: String,
    val content: JsonElement,
)

// ---------------------------------------------------------------------------
// 응답 DTO
// ---------------------------------------------------------------------------

@Serializable
data class MessageResponse(
    val id: String? = null,
    val role: String = "assistant",
    val content: List<JsonObject> = emptyList(),
    val stop_reason: String? = null,
)

/** 응답 안의 tool_use 블록 한 개. */
data class ToolUse(
    val id: String,
    val name: String,
    val input: JsonObject,
)

// ---------------------------------------------------------------------------
// 헬퍼
// ---------------------------------------------------------------------------

/** user 텍스트 메시지. */
fun userMessage(text: String): Message =
    Message(role = "user", content = JsonPrimitive(text))

/** user 메시지를 블록 배열로 (tool_result 묶음 반환에 사용). */
fun userMessage(blocks: List<JsonObject>): Message =
    Message(role = "user", content = JsonArray(blocks))

/** tool_result 블록 하나 생성 (id 짝 맞추기 ★ + is_error 가드레일). */
fun toolResult(toolUseId: String, content: String, isError: Boolean = false): JsonObject =
    buildJsonObject {
        put("type", "tool_result")
        put("tool_use_id", toolUseId)
        put("content", content)
        if (isError) put("is_error", true)
    }

/** 응답을 assistant 메시지로 변환해 히스토리에 누적 (content 전체 보존 — tool_use 포함). */
fun MessageResponse.toAssistantMessage(): Message =
    Message(role = "assistant", content = JsonArray(content))

/** 응답의 text 블록을 모아 최종 답 텍스트로. */
fun MessageResponse.text(): String =
    content
        .filter { it["type"]?.jsonPrimitive?.content == "text" }
        .joinToString("\n") { it["text"]?.jsonPrimitive?.content ?: "" }
        .trim()

/** 응답의 tool_use 블록들 추출. */
fun MessageResponse.toolUseBlocks(): List<ToolUse> =
    content
        .filter { it["type"]?.jsonPrimitive?.content == "tool_use" }
        .map {
            ToolUse(
                id = it["id"]!!.jsonPrimitive.content,
                name = it["name"]!!.jsonPrimitive.content,
                input = it["input"]?.jsonObject ?: JsonObject(emptyMap()),
            )
        }
