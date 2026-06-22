# 설치 및 설정 가이드

## 사전 요구사항

- JDK 17 이상 (Temurin/Adoptium 권장)
- ANTHROPIC_API_KEY (console.anthropic.com 에서 발급)
- VOYAGE_API_KEY (voyageai.com 에서 발급, 임베딩용)

## 환경변수 설정

API 키는 환경변수로만 관리합니다. 코드에 하드코딩하거나 git 에 커밋하지 마세요.

```bash
export ANTHROPIC_API_KEY="sk-ant-..."
export VOYAGE_API_KEY="pa-..."
```

## 인덱스 생성

문서를 검색하려면 먼저 ingest 모드로 인덱스를 만들어야 합니다.
docs 폴더에 텍스트 파일을 넣고 다음을 실행하세요.

```bash
./gradlew run --args="ingest"
```

이 명령은 문서를 조각내고 Voyage 임베딩을 거쳐 index/embeddings.json 에 저장합니다.

## 채팅 실행

인덱스를 만든 뒤에는 chat 모드로 질문할 수 있습니다.

```bash
./gradlew run --args="chat"
```
