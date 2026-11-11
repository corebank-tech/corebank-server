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

관리자 상세 `GET /api/v1/admin/fds/holds/{transferHoldId}`와 `FdsInvestigationInputService`는 `fds_behavior_score`에 저장된 결과만 읽는다. 상세 조회는 `CUSTOMER_READ`와 `X-Access-Reason`이 필요하고 조회 감사를 남긴다. 실제 LangGraph Agent 실행 API·조사 워커·보고서 저장은 아직 구현되지 않았다.

실제 Python 모델과 MySQL을 함께 검증할 때는 두 서비스를 띄운 뒤 같은 토큰을 주입한다. 아래 테스트는 보류 상태 유지, 관리자 상세·Agent 입력의 점수 일치를 확인한다.

```powershell
$env:FDS_SCORE_TEST_URL = 'http://127.0.0.1:8000'
$env:FDS_SCORE_SERVICE_TOKEN = '<Python과 같은 비공개 토큰>'
./gradlew test --tests '*FdsPythonLiveFlowIntegrationTest' --tests '*FastApiPythonLiveIntegrationTest'
```

Docker가 있어야 Flyway·JDBC 통합 테스트를 실행할 수 있다. 환경변수가 없으면 실제 Python 통합 테스트는 건너뛴다.

## 2026-11-09 검증 기록

- 실제 `corebank-fds-agent #6` FastAPI와 Testcontainers MySQL을 연결해 `FastApiPythonLiveIntegrationTest`, `FdsPythonLiveFlowIntegrationTest`를 실행했다. 유효한 flow와 테스트가 준비한 `HELD` 행에서는 점수가 `AVAILABLE`로 저장되고 관리자 상세·Agent 입력에 같은 점수·버전이 보였다. 실제 Python에 잘못된 서비스 토큰을 보낸 경우 `UNAVAILABLE / INFERENCE_SERVICE_UNAVAILABLE`이 저장되고 재전달 뒤에도 `HELD`가 유지됐다. 두 테스트 클래스 모두 건너뛰기 없이 통과했다.
- `BehaviorPersistenceIntegrationTest`에서 동시 두 작업자의 점수 시작 선점이 한 건만 성공하고, 중단 복구 후 늦게 도착한 성공 응답이 `UNAVAILABLE`을 덮어쓰지 못함을 MySQL에서 확인했다.
- 실행 명령: `./gradlew test --tests '*FdsPythonLiveFlowIntegrationTest' --tests '*FastApiPythonLiveIntegrationTest' spotlessCheck jacocoTestReport` 및 `./gradlew test --tests '*BehaviorPersistenceIntegrationTest' --tests '*BehaviorScoreServiceTest' --tests '*FastApiBehaviorScoreClientTest' --tests '*FdsScorePresentationTest' --tests '*ArchitectureTest' spotlessCheck jacocoTestReport`. 로컬에서는 기존 Gradle 9.5.1 배포본을 `--offline --no-daemon`으로 실행했다. 두 실행 모두 성공했다.
- Python 저장소 #6에서 `.venv/Scripts/python.exe -m unittest discover -s tests -p test_api.py -v`를 실행해 실제 모델 로딩·인증·모델 부재·추론 실패·시간 초과 등 7개 FastAPI 계약 테스트가 통과했다. 모델 부재·추론 실패·시간 초과는 Python TestClient의 주입된 scorer로 재현했다.
- 당시 테스트는 실제 이체 API 대신 `HELD` 행을 직접 준비했다. 당시 서버에는 FDS 정책과 조사 작업 워커·Agent Tool·보고서 API가 없어 실제 이체→보류 및 점수 실패 후 Agent 조사 계속 경로는 검증되지 않았다. Python 조사 실행 입력에는 `policyEvidence`가 필수이나 현재 서버 `FdsInvestigationInput`에는 없다. 잘못된 토큰의 실제 Python 장애 전후에는 원장 행 수·계좌 잔액 합계·한도 사용액 합계를 새 JDBC 조회로 비교했다. 모델 부재·추론 실패·시간 초과·잘못된 성공 응답은 HTTP 스텁→BE→MySQL로 검증했으며 실제 Python 장애→MySQL 전체 경로로 확인한 것은 아니다.

## 2026-11-09 추가 검증

- `TransferExecutionServiceHookTest.actualTransferHoldToPythonScoreAndStoredPresentation`: 선행 SUCCESS 2건을 MySQL에 둔 뒤 실제 이체 서비스의 FDS 정책 B로 `HELD`를 저장했다. 같은 테스트 안에서 생성된 보류 ID와 flow ID로 실제 FastAPI를 호출해 `AVAILABLE`을 저장하고, 관리자 상세와 Agent 입력의 저장 점수·모델/피처 버전 일치를 확인했다. 1개 실행, 건너뜀·실패 0개.
- 이 테스트는 테스트가 `BehaviorScoreService.onHeld`를 명시적으로 호출한다. 운영의 Spring Modulith 이벤트 리스너 자동 전달까지 함께 입증한 것은 아니다. 조사 워커는 후속 B4 범위이며 실제 Agent 조사 지속은 검증하지 않았다.

## 2026-11-10 자동 전달 검증

- `FdsAutomaticScoreIntegrationTest.committedHeldEventAutomaticallyStoresPythonScore`: 실제 이체 서비스가 정책 B로 보류를 커밋하고 발행한 `TransferHeld`를 Spring Modulith 리스너가 자동 수신했다. 실행 중인 FastAPI 응답이 MySQL의 `fds_behavior_score`에 `AVAILABLE`로 저장된 뒤 관리자 상세·Agent 입력의 점수 및 모델·피처 버전이 일치했다. `./gradlew test --tests '*FdsAutomaticScoreIntegrationTest' --offline --no-daemon`: 1개 실행, 실패·건너뜀 0개.
- 로컬 Oracle JDK 21의 Mockito 에이전트 초기화 오류를 피하려고 실행 중에만 테스트 대역 방식을 `mock-maker-subclass`로 지정했고 실행 후 제거했다. 점수 호출·저장은 정상 운영 리스너 경로로 실행했으며 조사 워커는 포함하지 않았다.

## 2026-11-10 마지막 코드 상태 재검증

- 로컬 FastAPI와 Testcontainers MySQL을 실행하고 `spotlessCheck test --tests '*FdsAutomaticScoreIntegrationTest' --tests '*FdsPythonLiveFlowIntegrationTest' --tests '*FastApiPythonLiveIntegrationTest' --tests '*BehaviorPersistenceIntegrationTest' --tests '*BehaviorScoreServiceTest' --tests '*FastApiBehaviorScoreClientTest' --tests '*FdsScorePresentationTest' --tests '*FdsRepeatTransferHoldPolicyTest' --tests '*TransferExecutionServiceHookTest.fdsRepeatPolicyHoldsActualTransferBeforePosting' --tests '*ArchitectureTest' jacocoTestReport --offline --no-daemon`을 실행했다. Gradle 성공, 81개 테스트, 실패·오류·건너뜀 모두 0개. FDS 패키지 JaCoCo 라인 커버리지는 394/436(90.37%)이다.
- 실제 FastAPI→BE→MySQL 정상 자동 전달과 실제 Python 인증 실패를 확인했다. 모델 부재·추론 오류·시간 초과는 Python TestClient의 주입 scorer 및 BE HTTP 계약 테스트로, 연결 실패·잘못된 성공 응답은 BE HTTP 계약 테스트로 확인했다. 503/500/504/잘못된 200 응답의 MySQL `UNAVAILABLE` 저장은 HTTP 스텁으로 검증했다. 실제 Python 장애를 각각 발생시켜 MySQL까지 잇는 라이브 테스트로 표시하지 않는다.
- Python 저장소에서 `.venv/Scripts/python.exe -m unittest discover -s tests -p test_api.py -v`를 재실행해 7개 테스트가 모두 통과했다. 재전달·동시 선점·확정 상태 불변은 `BehaviorPersistenceIntegrationTest`와 `BehaviorScoreServiceTest`로 확인했다. Mockito 대역 방식은 이 로컬 실행 중에만 임시 변경하고 제거했다.
