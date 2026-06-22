# LLM-tool-use-rag-practice

LLM 기반으로 질문을 받아, **내 문서를 검색하거나(RAG)** 혹은 **계산·조회 도구를 쓰거나(tool_use)** 를 스스로 골라서 답하는 **CLI 콘솔 프로그램**입니다.

RAG를 독립 파이프라인이 아니라 `search_docs`라는 **tool_use 도구 중 하나**로 통합한 것이 핵심입니다. 모델 입장에서는 검색도 그냥 하나의 도구일 뿐이며, RAG의 복잡함은 도구 내부에 숨겨집니다.

---

## ▶️ 실행 방법

### 1. API 키 설정 (최초 1회)

프로젝트 루트에 `.env` 파일을 만들어 키 2개를 넣습니다. (`.env`는 git에 커밋되지 않음)

```bash
cp .env.example .env
# .env 를 열어 실제 키 입력:
#   ANTHROPIC_API_KEY=sk-ant-...
#   VOYAGE_API_KEY=pa-...
```

### 2. 문서 색인 (최초 1회 + 문서가 바뀔 때마다)

```bash
./gradlew run --args="ingest"
# docs/ → 조각내기 → Voyage 임베딩 → index/embeddings.json
```

### 3. 대화 실행

```bash
./gradlew run --args="chat"
```

`>` 프롬프트에 질문 입력 (종료: 빈 줄 또는 `exit`):

| 질문 예시 | 동작 |
| --- | --- |
| `X 에러 어떻게 고쳤지?` | `search_docs` 로 문서 검색 |
| `1024 곱하기 768은?` | `calculator` 로 계산 |
| `지금 몇 시야?` | `get_date` 로 시각 조회 |
| `안녕` | 도구 없이 바로 답 |

### 4. 평가 (선택)

```bash
./gradlew run --args="eval"
# eval/cases.json 으로 도구선택/검색적중/답변충실 3층위 채점
```

> **주의**: `chat`/`eval` 전에 반드시 `ingest` 를 먼저 실행해야 합니다 (색인이 없으면 `search_docs` 가 실패).
> `.env` 자동 주입은 `gradlew run` 에만 적용됩니다. `java -jar` 로 직접 실행하려면 셸 환경변수(`export`)로 키를 설정하세요.

---

## ✨ 특징

- **자율적 도구 선택** — Claude가 매 턴 어느 도구를 쓸지(혹은 안 쓸지) 스스로 판단
- **RAG를 도구로 통합** — `search_docs` 도구가 내부에서 Voyage 임베딩 + 코사인 top-k 검색 수행
- **4단계 tool_use 루프** — `while` 연쇄 루프로 도구 호출·결과 반환을 반복
- **환각 가드레일** — `is_error` 처리 + 출처 표기 / 모르면 모른다고 답
- **자체 eval 하네스** — 도구 선택 / 검색 적중 / 답변 충실 3개 층위로 채점
- **로컬 전용** — 서버·벡터 DB 없이 내 기기에서만 실행

---

## 🏗️ 아키텍처

```
[Kotlin CLI 앱]  ← ANTHROPIC_API_KEY, VOYAGE_API_KEY (환경변수)
   │
   ├─[준비 1회]→ 내 문서 → 조각내기 → Voyage 임베딩 → index/embeddings.json
   │
   └─[질문마다]→ Claude 4단계 루프 (while)
                   ├─ tool: search_docs   → Voyage 임베딩 + 코사인 top-k (= RAG)
                   ├─ tool: calculator    → 수식 계산
                   └─ tool: get_date      → 날짜/시각 조회
                          │
                          └→ tool_result 되돌리기 → 최종 답
```

RAG가 루프 바깥의 별도 단계가 아니라, **루프 안의 도구 하나로 들어오는 것**이 설계의 핵심입니다.

### 호출 지점

| 호출 | 누가 | 언제 |
| --- | --- | --- |
| 생성 / 도구 결정 | Claude | 매 턴 (어느 도구 쓸지 판단) |
| 임베딩 | Voyage AI | `search_docs` 도구가 실행될 때만 |
| 도구 실행 | Kotlin 코드 | 모델이 호출 요청한 도구를 실제로 |

---

## 🛠️ 기술 스택

| 항목 | 사용 기술 |
| --- | --- |
| 언어 | Kotlin (JVM) |
| 빌드 | Gradle (Kotlin DSL, `build.gradle.kts`) |
| HTTP | OkHttp (Anthropic + Voyage) |
| JSON | kotlinx.serialization |
| 저장 | 로컬 JSON 파일 (조각 + 벡터) |
| 유사도 | 코사인 직접 구현 |
| 외부 연동 | Anthropic API(생성) + Voyage API(임베딩) |

```kotlin
dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}
```

---

## 📁 프로젝트 구조

```
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

---

## 🚀 시작하기

### 사전 요구사항

| 항목 | 권장 |
| --- | --- |
| JDK | 17 이상 (LTS, Temurin/Adoptium 추천) |
| IDE | IntelliJ IDEA Community (VS Code도 가능) |
| 빌드 도구 | Gradle (IntelliJ가 자동 셋업) |

### 1. 계정·API 키 발급

| 키 | 발급처 | 비고 |
| --- | --- | --- |
| `ANTHROPIC_API_KEY` | console.anthropic.com | 생성 호출용, 유료 크레딧 충전 필요 |
| `VOYAGE_API_KEY` | voyageai.com | 임베딩용(RAG), 무료 한도 있음 |

### 2. 환경변수 설정

키는 **환경변수로만** 관리합니다. 코드 하드코딩·git 커밋은 금지입니다.

```bash
export ANTHROPIC_API_KEY="sk-ant-..."
export VOYAGE_API_KEY="pa-..."
```

### 3. 내 문서 준비

`docs/` 폴더에 검색할 텍스트 파일을 넣습니다. 평문 `.md`/`.txt`가 가장 쉽고, 수십~수백 조각이면 충분합니다 (벡터 DB 없이 메모리 처리).

### 4. 인덱스 생성 (준비 1회)

```bash
./gradlew run --args="ingest"
# 내 문서 → 조각내기 → Voyage 임베딩 → index/embeddings.json
```

### 5. 실행

```bash
./gradlew run --args="chat"
# 터미널에 질문 입력 → 답 출력
```

또는 빌드 후 jar 실행:

```bash
./gradlew build
java -jar build/libs/hybrid-assistant.jar chat
```

---

## 🔁 핵심 코드

### RAG를 도구로 감싸기

`search_docs` 도구의 `execute`가 내부에서 RAG를 돌립니다.

```kotlin
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

### 4단계 tool_use 루프

도구 목록에 `search_docs`가 끼어있을 뿐, tool_use 버전과 동일한 `while` 구조입니다.

```kotlin
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

---

## 🧪 평가 (eval)

3개 층위로 채점합니다.

| 층위 | 무엇을 재나 | 채점법 |
| --- | --- | --- |
| 도구 선택 | 질문에 맞는 도구 골랐나 | 호출된 도구명 일치? (규칙) |
| 검색 적중 | `search_docs`가 맞는 조각 찾았나 | 정답 출처가 결과에 포함? (규칙) |
| 답변 충실 | 근거 있나 / 환각 없나 | LLM 채점 |

검색해야 할 질문 / 계산해야 할 질문 / 아무 도구도 안 써야 할 질문을 **라우팅 케이스로 꼭 섞어**, 모델이 올바르게 분기하는지를 확인합니다.

```jsonc
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
  { "input": "그냥 인사야 안녕", "expect_tool": null },
  { "input": "문서에 없는 주제 질문", "expect_answer": "모른다고 답해야 함" }
]
```

실행:

```bash
./gradlew run --args="eval"
```

---

## 💰 비용 (참고)

Claude Haiku 4.5 기준(입력 $1 / 출력 $5 per MTok)으로 추정한 5주 스터디 비용입니다.

| 항목 | 횟수 | 비용 |
| --- | --- | --- |
| 개발 중 수동 테스트 | ~500회 | $3.9 |
| eval 하네스 | ~400회 | $3.1 |
| 곁다리 실험 | ~100회 | $0.8 |
| 여유분 | — | ~$2 |
| **합계** | **~1,000회** | **≈ $10** |

**권장 세팅**: $10 충전 + 자동충전 끄기 + 콘솔 월 지출 한도 $15 설정. RAG 검색 결과를 매 호출 입력에 주입하므로 입력 토큰이 비용의 주범입니다. (Voyage 임베딩 비용은 무료 한도 안에서 사실상 0)

> 단가·정책은 변동될 수 있으니 [공식 가격 페이지](https://platform.claude.com/docs/ko/about-claude/pricing)에서 최신 정보를 확인하세요.

---

## ✅ 체크리스트

- [ ] "이 앱은 ___를 한다" 한 줄 정의
- [ ] 4개 부품(LLM·tool_use·RAG·eval) 다 넣기 + 이유 (RAG를 도구로 통합)
- [ ] 키 2개 환경변수 (하드코딩 금지)
- [ ] ingest → 인덱스 저장
- [ ] `search_docs` 도구로 RAG 감싸기
- [ ] `while` 연쇄 루프에 도구 3개 연결
- [ ] eval 세 층위(도구선택/검색적중/답변충실) 채점
- [ ] 환각 가드레일 2종 (`is_error` + 출처/모르면 모른다)
- [ ] 기본 UX (예외·타임아웃·MAX_STEPS)
- [ ] "만든 것 + 배운 것" 회고 1페이지

---

## 📚 챕터별 학습 내용

앱 제작을 통해 챕터 1~5 체크리스트에서 얻을 수 있는 항목들입니다.
✅는 앱 본체로 자동 커버, ⚠️는 곁다리 미니 실험을 따로 챙겨야 하는 항목입니다.

### 챕터 1 — LLM 기초

| 체크리스트 | 커버? | 어디서 |
| --- | --- | --- |
| "LLM은 ___ 내놓는 함수" 빈칸 채우기 | ✅ | 회고에 작성 |
| 용어 6개 설명 | ✅ | 회고/주석 |
| temperature 0/0.3/0.7/1.0 비교 | ⚠️ 별도 실험 | `AnthropicClient`로 파라미터만 바꿔 4번 호출 |
| 모델 바꿔 환각 차이 기록 | ⚠️ 별도 실험 | `kModel`만 교체해 비교 |
| messages 누적 → input_tokens 관찰 | ✅ | 4단계 루프가 매 턴 history 통째로 전송 |
| 관찰 1페이지 정리 | ✅ | 회고 |

### 챕터 2 — tool_use

| 체크리스트 | 커버? | 어디서 |
| --- | --- | --- |
| 4단계 루프 안 보고 그리기 | ✅ | `ToolLoop.kt` 직접 작성하며 체화 |
| 1차 호출에서 최종 답 안 나오는 이유 | ✅ | `stop_reason: tool_use` 직접 처리 |
| 히스토리 누적 순서 + role | ✅ | user→assistant(tool_use)→user(tool_result) 구현 |
| tool_use_id 어긋나면 증상 | ✅ | id 짝 맞추는 코드 (일부러 깨보면 더 확실) |
| 단일→도구 추가→while 연쇄 | ✅ | calculator 1개 → 도구 3개 → while |
| 병렬 vs 연쇄 차이 | ⚠️ 병렬은 케이스 필요 | "두 도구 동시에" 질문 1개 던지기 |
| tool_use vs MCP 레이어 구분 | ⚠️ 개념만 | 회고에서 설명으로 커버 |

### 챕터 3 — RAG

| 체크리스트 | 커버? | 어디서 |
| --- | --- | --- |
| "RAG = ___ 찾아 끼워넣고 답" 빈칸 | ✅ | 회고 |
| 단어 매칭 안 되는 이유 (강아지/반려견) | ✅ | 회고/설명 |
| "의미 비슷 = 좌표 가까움" 설명 | ✅ | 회고 |
| 임베딩 호출과 생성 호출 왜 따로인지 | ✅ | Voyage + Anthropic 두 클라이언트로 경험 |
| 문장 2개 임베딩해 거리 재보기 | ⚠️ 별도 실습 | `VoyageClient`로 5분 워밍업 |
| 내 문서로 미니 RAG 돌리기 | ✅ | `search_docs` 도구 = 미니 RAG 그 자체 |

### 챕터 4 — eval

| 체크리스트 | 커버? | 어디서 |
| --- | --- | --- |
| "좋다"를 판정 규칙으로 만들기 | ✅ | `cases.json`의 expect 기준 |
| 테스트 입력 10~20개 | ✅ | eval 케이스 작성 |
| 자동 채점 → 통과율 | ✅ | `EvalRunner.kt` |
| A vs B 점수 비교로 선택 | ✅ | 시스템 프롬프트 A/B 비교 |
| temperature 바꿔 점수 흔들림 관찰 | ⚠️ 케이스 필요 | 같은 eval을 temp 0 vs 1로 두 번 |
| 측정법 1페이지 정리 | ✅ | 회고 |

### 챕터 5 — 캡스톤

| 체크리스트 | 커버? |
| --- | --- |
| 한 줄 정의 | ✅ |
| 부품 넣고 뺄지 결정 + 이유 | ✅ (4개 다 넣기로 결정한 것 자체가 답) |
| 키 환경변수 처리 | ✅ |
| LLM 최소 경로 | ✅ |
| tool_use 또는 RAG 연결 | ✅ (둘 다) |
| eval 하네스 연결 | ✅ |
| 실패/로딩 UX | ✅ (예외·타임아웃·MAX_STEPS) |
| 환각 가드레일 1개+ | ✅ (2종) |
| 회고 1페이지 | ✅ |

### 일부러 챙겨야 할 곁다리 실험 (⚠️ 모음)

앱 본체가 약 70%를 자동 커버하고, 나머지 30%는 아래 6개 미니 실험을 끼워야 채워집니다.
6개 모두 앱에 이미 만든 클라이언트(Anthropic·Voyage)를 재활용하는 짧은 실습입니다.

| # | 실험 | 챕터 | 방법 |
| --- | --- | --- | --- |
| 1 | temperature 0/0.3/0.7/1.0 출력 비교 | 1 | `AnthropicClient` 파라미터만 변경, 5분 |
| 2 | model 바꿔 환각 차이 기록 | 1 | `kModel`만 교체 |
| 3 | 병렬 호출 관찰 | 2 | "두 도구 동시에" 질문 1개 |
| 4 | 문장 2개 임베딩 거리 재보기 | 3 | `VoyageClient`로 5분 워밍업 |
| 5 | temperature 바꿔 eval 점수 흔들림 | 4 | 같은 eval 두 번 |
| 6 | MCP 개념 설명 | 2 | 회고에서 글로 |

---

## 📝 라이선스

개인 학습용 캡스톤 프로젝트입니다.


