package search

import model.Chunk
import model.json
import java.io.File

/**
 * index/embeddings.json 을 메모리에 로드하고 코사인 top-k 검색을 수행한다 (= RAG 핵심).
 * 서버·벡터DB 없이 단일 JSON 파일로 처리.
 */
class VectorStore private constructor(private val chunks: List<Chunk>) {

    val size: Int get() = chunks.size

    /** 쿼리 벡터와 가장 가까운 조각 top-k. */
    fun search(queryVec: List<Double>, k: Int = 4): List<Chunk> =
        chunks
            .map { it to Similarity.cosine(queryVec, it.embedding) }
            .sortedByDescending { it.second }
            .take(k)
            .map { it.first }

    companion object {
        const val DEFAULT_PATH = "index/embeddings.json"

        /** 저장된 인덱스를 로드. 없으면 명확한 안내와 함께 실패. */
        fun load(path: String = DEFAULT_PATH): VectorStore {
            val file = File(path)
            if (!file.exists()) {
                error("인덱스($path)가 없습니다. 먼저 `ingest` 모드로 색인을 생성하세요.")
            }
            val chunks = json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(Chunk.serializer()),
                file.readText(),
            )
            return VectorStore(chunks)
        }

        /** 조각 목록을 JSON 파일로 저장 (ingest 산출물). */
        fun save(chunks: List<Chunk>, path: String = DEFAULT_PATH) {
            val file = File(path)
            file.parentFile?.mkdirs()
            file.writeText(
                json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(Chunk.serializer()),
                    chunks,
                )
            )
        }
    }
}
