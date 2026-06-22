package ingest

import search.VectorStore
import search.VoyageClient

/**
 * 준비 단계(1회): docs/ → 조각내기 → Voyage 임베딩 → index/embeddings.json 저장.
 */
object Indexer {
    fun run(
        docsDir: String = "docs",
        indexPath: String = VectorStore.DEFAULT_PATH,
        voyage: VoyageClient = VoyageClient(),
    ) {
        println("[ingest] 문서 조각내는 중: $docsDir")
        val chunks = Chunker.chunk(docsDir)
        if (chunks.isEmpty()) error("조각이 0개입니다. $docsDir 에 문서가 있는지 확인하세요.")
        println("[ingest] 조각 ${chunks.size}개 생성")

        println("[ingest] Voyage 임베딩 중...")
        val embeddings = voyage.embedBatch(chunks.map { it.text }, inputType = "document")
        val embedded = chunks.mapIndexed { i, c -> c.copy(embedding = embeddings[i]) }

        VectorStore.save(embedded, indexPath)
        println("[ingest] 완료 → $indexPath (${embedded.size}개 조각)")
    }
}
