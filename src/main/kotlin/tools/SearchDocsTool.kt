package tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import search.VectorStore
import search.VoyageClient

/**
 * ★ RAG 를 도구 하나로 감싼다.
 * execute() 내부에서 Voyage 임베딩 + 코사인 top-k 검색을 돌리고,
 * 출처를 포함한 검색 결과 문자열을 반환한다. (환각 가드레일: 출처 표기)
 */
class SearchDocsTool(
    private val store: VectorStore,
    private val voyage: VoyageClient,
    private val topK: Int = 4,
) : Tool {
    override val name = "search_docs"
    override val description = "내 로컬 문서에서 질문과 관련된 내용을 검색한다. 문서에 있을 법한 사실·설정·과거 기록을 물을 때 사용한다."

    override val schema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("query", buildJsonObject {
                put("type", "string")
                put("description", "검색할 자연어 질의")
            })
        })
        put("required", buildJsonArray { add(JsonPrimitive("query")) })
    }

    override fun execute(input: JsonObject): String {
        val query = input["query"]?.jsonPrimitive?.content
            ?: return "오류: query 인자가 없습니다."

        val queryVec = voyage.embed(query, inputType = "query") // 검색용 임베딩
        val top = store.search(queryVec, k = topK)              // 코사인 top-k = RAG

        if (top.isEmpty()) return "검색 결과가 없습니다."

        return top.joinToString("\n---\n") {
            "[출처: ${it.source}]\n${it.text}"
        }
    }
}
