# P6 비동기 행동 점수 로컬 실행 (#951)

`TransferHeld`가 커밋되면 Spring Modulith 리스너가 P4 공개 조회 계약으로 보류 원본을 확인하고, P6 행동 flow에서 피처를 계산한다. 유효한 flow가 없으면 Python을 호출하지 않고 `UNAVAILABLE`로 저장한다. 점수 호출은 이체 훅 C와 분리되어 있다.

## 설정

Python 저장소에서 FastAPI를 먼저 실행한다. 모델 경로와 해시는 Python 저장소의 `fds_agent/inference_config.json`을 따른다.

```powershell
$env:FDS_SCORE_SERVICE_TOKEN = '<로컬에서만 쓰는 비공개 토큰>'
.venv/Scripts/python.exe -m uvicorn fds_agent.api:app --host 127.0.0.1 --port 8000
```

Spring Boot 프로세스에도 같은 토큰을 비공개 환경변수로 주입한다. 주소·토큰을 Git에 저장하지 않는다.

```powershell
$env:APP_FDS_SCORE_ENABLED = 'true'
$env:APP_FDS_SCORE_BASE_URL = 'http://127.0.0.1:8000'
$env:FDS_SCORE_SERVICE_TOKEN = '<Python과 같은 비공개 토큰>'
./gradlew bootRun
```

BE → Python 연결 제한은 1초, 호출 전체 제한은 5초다. Python 추론 자체는 3초 제한이다. 같은 보류 ID의 추론 시작 표식을 먼저 커밋하며 재전달 시 다시 호출하지 않는다. 1분 이상 `PENDING`인 시작 작업은 1분 간격 복구 작업이 `SCORING_INTERRUPTED`로 닫는다. `APP_FDS_SCORE_ENABLED` 기본값은 `false`다.

```powershell
./gradlew test --tests '*BehaviorFlowServiceTest' --tests '*BehaviorScoreServiceTest' --tests '*FastApiBehaviorScoreClientTest'
./gradlew test --tests '*ArchitectureTest'
```

관리자 상세와 Agent 입력은 `BehaviorScoreQuery`의 저장 결과를 사용한다. 해당 화면·Agent 조사 API가 아직 구현되지 않아 최종 연결은 그 구현과 함께 검증해야 한다. Docker가 있어야 Flyway·JDBC 통합 테스트를 실행할 수 있다.
