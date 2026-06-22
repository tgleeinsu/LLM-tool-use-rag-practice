package generate

import kotlinx.serialization.json.JsonObject
import model.CreateMessageRequest
import model.Message
import model.MessageResponse
import model.json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Duration

/**
 * Anthropic Messages API 래퍼 (raw HTTP, 공식 SDK 미사용).
 *
 * - 엔드포인트: POST https://api.anthropic.com/v1/messages
 * - 헤더 3종(필수): x-api-key / anthropic-version / content-type
 * - 모델: claude-haiku-4-5-20251001 (Haiku 4.5 — effort/thinking 미지원이라 단순 호출)
 */
class AnthropicClient(
    private val apiKey: String = System.getenv("ANTHROPIC_API_KEY")
        ?: error("ANTHROPIC_API_KEY 환경변수가 설정되지 않았습니다."),
    private val model: String = "claude-haiku-4-5-20251001",
    private val maxTokens: Int = 4096,
) {
    private val http = OkHttpClient.Builder()
        .callTimeout(Duration.ofSeconds(60))
        .connectTimeout(Duration.ofSeconds(15))
        .readTimeout(Duration.ofSeconds(60))
        .build()

    fun createMessage(
        messages: List<Message>,
        tools: List<JsonObject>? = null,
        system: String? = null,
    ): MessageResponse {
        val payload = CreateMessageRequest(
            model = model,
            max_tokens = maxTokens,
            messages = messages,
            system = system,
            tools = tools,
        )
        val body = json.encodeToString(CreateMessageRequest.serializer(), payload)
            .toRequestBody(JSON_MEDIA)

        val request = Request.Builder()
            .url(ENDPOINT)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", ANTHROPIC_VERSION)
            .addHeader("content-type", "application/json")
            .post(body)
            .build()

        http.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                error("Anthropic API 오류 (HTTP ${resp.code}): $text")
            }
            return json.decodeFromString(MessageResponse.serializer(), text)
        }
    }

    private companion object {
        const val ENDPOINT = "https://api.anthropic.com/v1/messages"
        const val ANTHROPIC_VERSION = "2023-06-01"
        val JSON_MEDIA = "application/json".toMediaType()
    }
}
