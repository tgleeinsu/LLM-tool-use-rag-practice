import eval.EvalRunner
import generate.AnthropicClient
import ingest.Indexer
import search.VectorStore
import search.VoyageClient
import tools.CalculatorTool
import tools.GetDateTool
import tools.SearchDocsTool
import tools.ToolRegistry

/**
 * CLI 진입점. 모드 분기:
 *   ingest → 문서 색인 (1회 준비)
 *   chat   → 대화 (tool_use 루프)
 *   eval   → 평가 하네스
 */
fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        "ingest" -> runIngest()
        "chat" -> runChat()
        "eval" -> runEval()
        else -> {
            println("사용법: <ingest | chat | eval>")
            println("  ingest : docs/ 를 색인해 index/embeddings.json 생성")
            println("  chat   : 질문을 입력하면 도구를 골라 답함")
            println("  eval   : eval/cases.json 으로 채점")
        }
    }
}

private fun runIngest() {
    try {
        Indexer.run()
    } catch (e: Exception) {
        System.err.println("[ingest 실패] ${e.message}")
        kotlin.system.exitProcess(1)
    }
}

private fun runChat() {
    val loop = try {
        buildToolLoop()
    } catch (e: Exception) {
        System.err.println("[초기화 실패] ${e.message}")
        kotlin.system.exitProcess(1)
    }

    println("질문을 입력하세요 (빈 줄 또는 'exit' 로 종료):")
    while (true) {
        print("\n> ")
        val line = readlnOrNull()?.trim()
        if (line.isNullOrEmpty() || line == "exit") {
            println("종료합니다.")
            break
        }
        try {
            val result = loop.runConversation(line)
            println(result.finalText)
        } catch (e: Exception) {
            System.err.println("[오류] ${e.message}")
        }
    }
}

private fun runEval() {
    try {
        EvalRunner.run(buildToolLoop())
    } catch (e: Exception) {
        System.err.println("[eval 실패] ${e.message}")
        kotlin.system.exitProcess(1)
    }
}

/** 도구 3개를 묶어 ToolLoop 을 구성. chat/eval 공용. */
fun buildToolLoop(): ToolLoop {
    val anthropic = AnthropicClient()
    val voyage = VoyageClient()
    val store = VectorStore.load()
    val registry = ToolRegistry(
        listOf(
            SearchDocsTool(store, voyage),
            CalculatorTool,
            GetDateTool,
        )
    )
    return ToolLoop(anthropic, registry)
}
