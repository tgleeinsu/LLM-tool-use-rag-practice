package search

import kotlinx.serialization.Serializable
import model.json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Duration

/**
 * Voyage 임베딩 API 래퍼 (RAG 검색/색인용).
 *
 * - 엔드포인트: POST https://api.voyageai.com/v1/embeddings
 * - 헤더: Authorization: Bearer $VOYAGE_API_KEY
 * - input_type: "document"(색인) / "query"(검색) 로 구분해 정확도 향상
 */
class VoyageClient(
    private val apiKey: String = System.getenv("VOYAGE_API_KEY")
        ?: error("VOYAGE_API_KEY 환경변수가 설정되지 않았습니다."),
    private val model: String = "voyage-3",
) {
    private val http = OkHttpClient.Builder()
        .callTimeout(Duration.ofSeconds(60))
        .connectTimeout(Duration.ofSeconds(15))
        .readTimeout(Duration.ofSeconds(60))
        .build()

    /** 검색용 단일 쿼리 임베딩. */
    fun embed(text: String, inputType: String = "query"): List<Double> =
        embedBatch(listOf(text), inputType).first()

    /** 색인용 배치 임베딩 (조각 여러 개를 한 번에). */
    fun embedBatch(texts: List<String>, inputType: String = "document"): List<List<Double>> {
        require(texts.isNotEmpty()) { "임베딩할 텍스트가 비어 있습니다." }
        val payload = EmbeddingRequest(input = texts, model = model, input_type = inputType)
        val body = json.encodeToString(EmbeddingRequest.serializer(), payload)
            .toRequestBody(JSON_MEDIA)

        val request = Request.Builder()
            .url(ENDPOINT)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("content-type", "application/json")
            .post(body)
            .build()

        http.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                error("Voyage API 오류 (HTTP ${resp.code}): $text")
            }
            val parsed = json.decodeFromString(EmbeddingResponse.serializer(), text)
            // index 순서대로 정렬해 입력 순서와 일치시킨다.
            return parsed.data.sortedBy { it.index }.map { it.embedding }
        }
    }

    @Serializable
    private data class EmbeddingRequest(
        val input: List<String>,
        val model: String,
        val input_type: String,
    )

    @Serializable
    private data class EmbeddingResponse(
        val data: List<EmbeddingData>,
    )

    @Serializable
    private data class EmbeddingData(
        val embedding: List<Double>,
        val index: Int = 0,
    )

    private companion object {
        const val ENDPOINT = "https://api.voyageai.com/v1/embeddings"
        val JSON_MEDIA = "application/json".toMediaType()
    }
}
