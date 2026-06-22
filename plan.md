## 목표 결과물

```kotlin
LLM기반으로 질문을 받아, 내 문서를 검색하거나(RAG) 계산·조회 도구를 쓰거나(tool_use)를 스스로 골라서 답하는 CLI 콘솔 프로그램
```

| 항목 | 이 앱 |
| --- | --- |
| 결과물 종류 | **콘솔(CLI) 애플리케이션** |
| 실행 위치 | 내 PC의 터미널 (macOS/Windows/Linux 어디든) |
| 화면 | GUI 창 없음. 터미널에 질문 입력 → 답 출력 |
| 배포 | 안 함. 내 기기에서만 실행 (커리큘럼: "백엔드 불필요") |
| 산출물 파일 | `.jar` (JVM에서 `java -jar`로 실행) 또는 IDE에서 직접 Run |

| 부품 | 넣나? | 이유 |
| --- | --- | --- |
| **LLM 생성** | ✅ 필수 | 답 생성·도구 선택 판단 |
| **tool_use** | ✅ 핵심 | 4단계 루프가 앱의 뼈대. 도구 여러 개 |
| **RAG** | ✅ 핵심 | **검색을 도구 중 하나로** 통합
RAG를 독립 파이프라인이 아니라 `search_docs`라는 tool_use 도구로 만든다 |
| **eval** | ✅ 필수 | "맞는 도구 골랐나 + 검색 적중 + 근거 충실" 측정 |
- 왜 CLI인가
    - 커리큘럼이 "기능 하나만, 작게"를 강조.
    - GUI(Compose, Swift UI)나 안드로이드를 붙이면 화면 코드가 앱 본질(4단계 루프·RAG)을 가림.
    - 터미널 입출력이 가장 군더더기 없고 나중에 원하면 같은 로직 위에 GUI를 얹을 수 있음

### 개발 도구

| 항목 | 권장 | 비고 |
| --- | --- | --- |
| **IDE** | **IntelliJ IDEA** (Community 무료) | Kotlin 공식 IDE. JetBrains가 Kotlin 제작사라 가장 매끄러움 |
| **IDE** | VS Code + Kotlin 확장 | 가능하지만 Gradle/Kotlin 지원이 IntelliJ만 못함 |
| **JDK** | **JDK 17 이상** (LTS) | Kotlin/JVM 실행에 필수. Temurin(Adoptium) 추천 |
| **빌드 도구** | **Gradle** (Kotlin DSL, `build.gradle.kts`) | IntelliJ가 프로젝트 생성 시 자동 셋업 |

### 통신 방식

결론: 외부 API와 HTTP(S) 통신, JSON 주고받기.

| 무엇 | 어떻게 |
| --- | --- |
| Anthropic API | HTTPS POST → `https://api.anthropic.com/v1/messages` |
| Voyage API | HTTPS POST → 임베딩 엔드포인트 |
| 통신 라이브러리 | **OkHttp** (HTTP 클라이언트) |
| 데이터 형식 | **JSON** (요청·응답 모두). kotlinx.serialization으로 직렬화 |
| 로컬 저장 | 인덱스는 HTTP 아님 — 그냥 로컬 **JSON 파일** 읽기/쓰기 |

### 필요한 계정·키

| 항목 | 어디서 | 비고 |
| --- | --- | --- |
| **Anthropic API 키** | console.anthropic.com | 생성 호출용. **유료 크레딧 충전 필요** |
| **Voyage AI API 키** | voyageai.com | 임베딩용(RAG). 무료 한도 있음 |
| 키 보관 | **환경변수** | `ANTHROPIC_API_KEY`, `VOYAGE_API_KEY`. 코드 하드코딩·깃 커밋 금지 |

### 갖춰야 할 입력 데이터 (RAG용)

| 항목 | 내용 |
| --- | --- |
| **내 문서** | RAG가 검색할 실제 텍스트. md/txt 파일 몇 개~수십 개 |
| 예시 | 내 트러블슈팅 노트, 회의록, 개인 위키, 학습 정리 |
| 형식 | 일단 평문(.md/.txt)이 가장 쉬움. PDF는 텍스트 추출 한 단계 추가 |
| 규모 | 수십~수백 조각이면 충분. 벡터 DB 없이 메모리로 처리 가능 |

### 한 장 요약

```kotlin
결과물:   터미널에서 도는 CLI 콘솔 앱 (.jar)
플랫폼:   JVM (macOS/Windows/Linux 공통)
IDE:      IntelliJ IDEA Community (VS Code도 가능)
JDK:      17 이상
빌드:     Gradle (Kotlin DSL)
통신:     HTTPS + JSON, OkHttp 클라이언트
외부연동: Anthropic API(생성) + Voyage API(임베딩)
저장:     로컬 JSON 파일 (벡터 DB 없음)
키관리:   환경변수 2개 (하드코딩 금지)
입력데이터: 내 문서 md/txt 몇 개
배포:     안 함 (내 기기 로컬 전용)
```

---

## 플래닝

### 🗺️ 아키텍처 (로컬, 서버 없음)

```kotlin
[Kotlin CLI 앱]  ← ANTHROPIC_API_KEY, VOYAGE_API_KEY 환경변수
   │
   ├─[준비 1회]→ 내 문서 → 조각내기 → Voyage 임베딩 → index/embeddings.json
   │
   └─[질문마다]→ Claude 4단계 루프 (while)
                   ├─ tool: search_docs   → Voyage 임베딩 + 코사인 top-k (= RAG)
                   ├─ tool: calculator     → 수식 계산
                   └─ tool: get_date       → 날짜/시각 조회
                          │
                          └→ tool_result 되돌리기 → 최종 답
                          
RAG가 루프 바깥의 별도 단계가 아니라, 루프 안의 도구 하나로 들어오는 게 핵심
```

### 🔌 호출 지점 정리

| 호출 | 누가 | 언제 |
| --- | --- | --- |
| **생성 / 도구 결정** | Claude | 매 턴 (어느 도구 쓸지 판단) |
| **임베딩** | Voyage AI | `search_docs` 도구가 실행될 때만 |
| **도구 실행** | 내 Kotlin 코드 | 모델이 호출 요청한 도구를 실제로 |

> 모델 입장에선 `search_docs`도 그냥 tool_use.
(3주차에서 본 "MCP tool도 모델 입장엔 그냥 tool_use"와 같은 원리). 
RAG의 복잡함이 도구 안에 숨겨짐
> 

### 🛠️ 기술 스택

```kotlin
- 언어:      Kotlin (JVM)
- 빌드:      Gradle (Kotlin DSL)
- HTTP:      OkHttp        (Anthropic + Voyage)
- JSON:      kotlinx.serialization
- 저장:      로컬 JSON (조각+벡터)
- 유사도:    코사인 직접 구현

kotlindependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}
```

### 📁 프로젝트 구조

```kotlin
hybrid-assistant/
├── build.gradle.kts
├── docs/                        # 내 실제 문서
├── index/embeddings.json        # 준비 단계 산출물
├── src/main/kotlin/
│   ├── Main.kt                  # CLI: ingest 모드 / chat 모드
│   ├── ToolLoop.kt              # ★ 4단계 왕복 (while = 연쇄)
│   ├── ingest/
│   │   ├── Chunker.kt
│   │   └── Indexer.kt           # 조각 → Voyage 임베딩 → 저장
│   ├── tools/
│   │   ├── Tool.kt              # 도구 인터페이스 + JSON Schema
│   │   ├── SearchDocsTool.kt    # ★ RAG를 도구로 감쌈
│   │   ├── CalculatorTool.kt
│   │   ├── GetDateTool.kt
│   │   └── ToolRegistry.kt
│   ├── search/
│   │   ├── VoyageClient.kt
│   │   ├── VectorStore.kt       # JSON 로드 + 코사인 top-k
│   │   └── Similarity.kt
│   ├── generate/AnthropicClient.kt
│   └── model/{Messages.kt, Chunk.kt}
└── eval/
    ├── cases.json
    └── EvalRunner.kt
```

### 🔁 핵심 코드: RAG를 도구로 감싸기

```kotlin
이게 이 앱의 심장입니다. search_docs 도구의 execute가 내부에서 RAG를 돌립니다.

// SearchDocsTool.kt — RAG가 도구 안에 들어감
object SearchDocsTool : Tool {
    override val name = "search_docs"
    override val description = "내 로컬 문서에서 질문과 관련된 내용을 검색한다"
    override val schema = jsonSchema {
        "query" to string("검색할 자연어 질의")
    }

    override fun execute(input: JsonObject): String {
        val query = input["query"]!!.jsonPrimitive.content
        val qVec = voyage.embed(query)              // 임베딩 (검색용)
        val top = store.search(qVec, k = 4)         // 코사인 top-k = RAG
        return top.joinToString("\n---\n") {
            "[출처: ${it.source}]\n${it.text}"      // 출처 포함
        }
    }
}
```

```kotlin
그리고 루프는 tool_use 버전과 동일한 while 구조 — 도구 목록에 search_docs가 끼어있을 뿐입니다:

fun runConversation(userInput: String): String {
    val history = mutableListOf(userMessage(userInput))
    var steps = 0

    while (steps++ < MAX_STEPS) {               // 무한 루프 가드
        val resp = anthropic.createMessage(
            tools = registry.schemas(),         // search_docs + calculator + get_date
            messages = history,
            system = SYSTEM_PROMPT,
        )
        history.add(resp.toAssistantMessage())  // stateless 누적

        if (resp.stopReason != "tool_use") return resp.text()

        val results = resp.toolUseBlocks().map { call ->
            val output = registry.execute(call.name, call.input)  // search_docs면 RAG 실행
            toolResult(toolUseId = call.id, content = output)     // id 짝 맞추기 ★
        }
        history.add(userMessage(results))
    }
    return "최대 단계 초과"
}
```

### 🧪 eval 하네스

| 층위 | 무엇을 재나 | 채점법 |
| --- | --- | --- |
| **도구 선택** | 질문에 맞는 도구 골랐나 | 호출된 도구명 일치? (규칙) |
| **검색 적중** | `search_docs`가 맞는 조각 찾았나 | 정답 출처가 결과에 포함? (규칙) |
| **답변 충실** | 근거 있나 / 환각 없나 | LLM 채점 |

```kotlin
// eval/cases.json
[
  {
    "input": "X 에러 어떻게 고쳤지?",
    "expect_tool": "search_docs",
    "expect_source": "troubleshooting.md",
    "expect_answer_contains": "캐시 삭제"
  },
  {
    "input": "1024 곱하기 768은?",
    "expect_tool": "calculator",
    "expect_answer_contains": "786432"
  },
  {
    "input": "그냥 인사야 안녕",
    "expect_tool": null
  },
  {
    "input": "문서에 없는 주제 질문",
    "expect_answer": "모른다고 답해야 함"
  }
]

라우팅 케이스를 꼭 섞으세요: 검색해야 할 질문 / 계산해야 할 질문 / 아무 도구도 안 써야 할 질문. 모델이 올바르게 분기하는지가 하이브리드 앱의 핵심 평가 지점입니다.
```

### ✅ 캡스톤 체크리스트

- [ ]  "이 앱은 ___를 한다" 한 줄 정의 ✓
- [ ]  4개 부품 다 넣기로 결정 + 이유 ✓ (RAG를 도구로 통합)
- [ ]  키 2개 환경변수 (하드코딩 금지)
- [ ]  ingest → 인덱스 저장
- [ ]  `search_docs` 도구로 RAG 감싸기
- [ ]  `while` 연쇄 루프에 도구 3개 연결
- [ ]  eval 세 층위(도구선택/검색적중/답변충실) 채점
- [ ]  환각 가드레일 2종 (`is_error` + 출처/모르면 모른다)
- [ ]  기본 UX (예외·타임아웃·MAX_STEPS)
- [ ]  "만든 것 + 배운 것" 회고 1페이지
