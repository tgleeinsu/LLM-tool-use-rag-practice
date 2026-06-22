package model

import kotlinx.serialization.Serializable

/**
 * 문서 조각 + 임베딩 벡터.
 * ingest 단계에서 만들어 index/embeddings.json 에 저장하고,
 * search_docs 도구가 코사인 top-k 검색에 사용한다.
 */
@Serializable
data class Chunk(
    val id: String,
    val source: String,          // 원본 파일명 (출처 표기에 사용)
    val text: String,
    val embedding: List<Double>, // Voyage 임베딩 벡터
)
