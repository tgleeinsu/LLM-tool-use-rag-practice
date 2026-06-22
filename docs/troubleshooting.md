# 문제 해결 가이드

## X 에러: 앱이 시작 시 멈춤

앱이 시작하자마자 멈추는 X 에러는 대부분 손상된 로컬 캐시 때문에 발생합니다.
해결 방법은 캐시 삭제입니다. `~/.hybrid-assistant/cache` 디렉터리를 삭제하고
앱을 다시 실행하면 정상 동작합니다.

## 빌드 실패: 의존성을 찾을 수 없음

Gradle 빌드 중 의존성을 찾지 못하는 경우, 네트워크 연결을 확인하고
`./gradlew build --refresh-dependencies` 로 의존성 캐시를 갱신하세요.

## API 키 인증 실패 (401)

ANTHROPIC_API_KEY 또는 VOYAGE_API_KEY 환경변수가 설정되지 않았거나
잘못된 값일 때 401 에러가 발생합니다. 환경변수를 다시 export 하세요.
