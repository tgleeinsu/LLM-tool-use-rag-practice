package search

import kotlin.math.sqrt

/** 코사인 유사도 (의미 비슷 = 좌표 가까움). 1.0 에 가까울수록 유사. */
object Similarity {
    fun cosine(a: List<Double>, b: List<Double>): Double {
        require(a.size == b.size) { "벡터 차원이 다릅니다: ${a.size} vs ${b.size}" }
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom == 0.0) 0.0 else dot / denom
    }
}
