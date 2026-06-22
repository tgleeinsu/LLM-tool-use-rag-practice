package util

import java.io.File

/**
 * 환경변수 조회. 셸 환경변수를 먼저 보고, 없으면 프로젝트 루트의 .env 를 읽는다.
 * 덕분에 `./gradlew run` 과 `java -jar` 모두에서 .env 가 동작한다.
 * (셸 export 가 있으면 그게 우선 — CI 등에서 유용)
 */
object Env {
    private val dotenv: Map<String, String> by lazy { loadDotenv() }

    fun get(key: String): String? = System.getenv(key) ?: dotenv[key]

    /** 필수 키 조회. 없으면 명확한 안내와 함께 실패. */
    fun require(key: String): String =
        get(key) ?: error("$key 가 설정되지 않았습니다. .env 에 넣거나 환경변수로 export 하세요.")

    private fun loadDotenv(path: String = ".env"): Map<String, String> {
        val file = File(path)
        if (!file.exists()) return emptyMap()
        return file.readLines().mapNotNull { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) return@mapNotNull null
            val (k, v) = line.split("=", limit = 2)
            k.trim() to v.trim().trim('"')
        }.toMap()
    }
}
