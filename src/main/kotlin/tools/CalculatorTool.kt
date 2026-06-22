package tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 수식 계산 도구. + - * / 와 괄호를 지원하는 간단한 재귀 하향 파서를 직접 구현.
 */
object CalculatorTool : Tool {
    override val name = "calculator"
    override val description = "수학 수식을 계산한다. 예: \"1024 * 768\", \"(3 + 4) / 2\". 사칙연산과 괄호를 지원한다."

    override val schema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("expression", buildJsonObject {
                put("type", "string")
                put("description", "계산할 수식 (예: 1024 * 768)")
            })
        })
        put("required", kotlinx.serialization.json.buildJsonArray {
            add(kotlinx.serialization.json.JsonPrimitive("expression"))
        })
    }

    override fun execute(input: JsonObject): String {
        val expr = input["expression"]?.jsonPrimitive?.content
            ?: return "오류: expression 인자가 없습니다."
        return try {
            val result = Parser(expr).parse()
            // 정수면 소수점 제거해서 깔끔하게
            if (result == result.toLong().toDouble()) result.toLong().toString()
            else result.toString()
        } catch (e: Exception) {
            "계산 오류: ${e.message}"
        }
    }

    /** + - * / 와 괄호를 처리하는 재귀 하향 파서. */
    private class Parser(private val s: String) {
        private var pos = 0

        fun parse(): Double {
            val result = parseExpression()
            skipSpaces()
            require(pos >= s.length) { "수식 끝에 잘못된 문자: '${s.substring(pos)}'" }
            return result
        }

        private fun parseExpression(): Double {
            var value = parseTerm()
            while (true) {
                skipSpaces()
                when (peek()) {
                    '+' -> { pos++; value += parseTerm() }
                    '-' -> { pos++; value -= parseTerm() }
                    else -> return value
                }
            }
        }

        private fun parseTerm(): Double {
            var value = parseFactor()
            while (true) {
                skipSpaces()
                when (peek()) {
                    '*' -> { pos++; value *= parseFactor() }
                    '/' -> {
                        pos++
                        val divisor = parseFactor()
                        require(divisor != 0.0) { "0 으로 나눌 수 없습니다." }
                        value /= divisor
                    }
                    else -> return value
                }
            }
        }

        private fun parseFactor(): Double {
            skipSpaces()
            return when (peek()) {
                '(' -> {
                    pos++
                    val value = parseExpression()
                    skipSpaces()
                    require(peek() == ')') { "닫는 괄호가 없습니다." }
                    pos++
                    value
                }
                '-' -> { pos++; -parseFactor() }
                '+' -> { pos++; parseFactor() }
                else -> parseNumber()
            }
        }

        private fun parseNumber(): Double {
            skipSpaces()
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
            require(pos > start) { "숫자를 기대했지만 '${peekStr()}' 를 만났습니다." }
            return s.substring(start, pos).toDouble()
        }

        private fun skipSpaces() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        private fun peek(): Char? = if (pos < s.length) s[pos] else null
        private fun peekStr(): String = if (pos < s.length) s[pos].toString() else "<끝>"
    }
}
