package ingest

import model.Chunk
import java.io.File

/**
 * docs/ 의 텍스트 파일을 검색 단위 조각으로 자른다.
 * 빈 줄(문단 경계)로 나누되, 너무 짧은 조각은 합쳐 의미 단위를 유지한다.
 */
object Chunker {
    private const val MIN_CHARS = 80   // 이보다 짧으면 다음 문단과 합침

    fun chunk(docsDir: String): List<Chunk> {
        val dir = File(docsDir)
        if (!dir.exists() || !dir.isDirectory) {
            error("문서 폴더($docsDir)가 없습니다. .md / .txt 파일을 넣어주세요.")
        }
        val files = dir.walkTopDown()
            .filter { it.isFile && (it.extension == "md" || it.extension == "txt") }
            .sortedBy { it.name }
            .toList()

        val chunks = mutableListOf<Chunk>()
        for (file in files) {
            val paragraphs = splitParagraphs(file.readText())
            paragraphs.forEachIndexed { i, text ->
                chunks += Chunk(
                    id = "${file.name}#$i",
                    source = file.name,
                    text = text,
                    embedding = emptyList(), // Indexer 가 채움
                )
            }
        }
        return chunks
    }

    private fun splitParagraphs(content: String): List<String> {
        val raw = content.split(Regex("\\n\\s*\\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        // 너무 짧은 조각은 뒤 문단과 병합
        val merged = mutableListOf<String>()
        val buffer = StringBuilder()
        for (p in raw) {
            if (buffer.isEmpty()) {
                buffer.append(p)
            } else {
                buffer.append("\n\n").append(p)
            }
            if (buffer.length >= MIN_CHARS) {
                merged += buffer.toString()
                buffer.clear()
            }
        }
        if (buffer.isNotEmpty()) merged += buffer.toString()
        return merged
    }
}
