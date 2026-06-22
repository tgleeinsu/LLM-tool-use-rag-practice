package tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 현재 날짜/시각 조회 도구. 모델은 학습 시점 이후의 "지금"을 모르므로 도구로 제공.
 */
object GetDateTool : Tool {
    override val name = "get_date"
    override val description = "현재 날짜와 시각을 조회한다. 오늘이 며칠인지, 지금 몇 시인지 물을 때 사용한다."

    override val schema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject { })
    }

    override fun execute(input: JsonObject): String {
        val now = LocalDateTime.now()
        val formatted = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        return "현재 날짜/시각: $formatted"
    }
}
