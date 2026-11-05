# 이의제기 Agent 내부 API 계약

지급정지 이의제기 AI 심사 지원([D-36](README.md#d-36--113--리드))의 Agent 워커가 부르는 서버 내부 API다.
단계 이벤트의 필드와 종류는 [agent_run_step_schema.md](agent_run_step_schema.md)를 따른다. 용어는 [glossary.md](glossary.md) 13~15번이다.

## 1. 원칙

- Agent는 읽기, 단계 이벤트 기록, 판정안 제출만 한다. 해제, 확정, 계좌 변경 권한은 없다.
- 판정안은 심사 참고안이다. 서버는 판정안을 받으면 해제 게이트를 원장으로 다시 계산해 `GATE_EVALUATED` 단계로 남긴다. 해제 여부는 게이트와 담당자가 정한다.
- Agent에 가는 값과 단계 이벤트에 남는 값은 가명 처리한다. 주민등록번호, 연락처, 이메일, 계좌번호 형태는 자리표시로 바꾸고, 당행 상대 계좌는 가명 키(`CP-` + 16진수 8자리)로만 준다. 가명 키는 같은 이의제기 안에서만 같다.
- `/internal/**`는 세션을 쓰지 않고 ALB에 공개하지 않는다. CSRF 검사 대상도 아니다. 관리자 세션으로 불러도 열리지 않는다.
- 응답 형식은 다른 API와 같다(HTTP 200 + `code="0000"`). 오류는 `PST` 코드다.

## 2. 인증

| 대상 | 헤더 | 실패 |
|---|---|---|
| 작업 가져가기 | `X-Worker-Key` 워커 서비스 키 | `PST0101` (401) |
| 그 밖의 내부 API | `X-Job-Token` 작업 토큰 | `PST0102` (403) |

- 워커 서비스 키는 서버 설정 `app.payment-stop.agent.worker-key`(환경변수 `PAYMENT_STOP_WORKER_KEY`)와 같아야 한다. 비어 있으면 모든 내부 API를 거부한다.
- 작업 토큰은 작업을 가져갈 때 받는다. 실행 하나, 이의제기 하나, 시도 하나에만 유효하고 10분 뒤 만료된다(`token-ttl`). 서명은 HMAC-SHA256이고 키는 워커 서비스 키다.
- 점유가 만료돼 다른 워커가 다시 가져가면 시도 번호가 바뀌어 앞선 토큰은 `PST0102`가 된다. 서명 오류, 만료, 대상 불일치, 실행 종료, 시도 불일치를 구분하지 않고 모두 같은 코드로 거부한다.

## 3. 대기열과 점유

- 대기열은 `agent_run`의 `QUEUED` 행이다. 이의제기를 접수하면 같은 트랜잭션에서 하나가 들어간다. 관리자가 재분석을 요청해도 들어간다.
- 워커가 가져가면 `RUNNING`이 되고 시도 번호가 1 늘며 5분 동안 점유한다(`lease`). 단계를 남길 때마다 점유가 5분 연장된다.
- 점유가 만료되면 다른 워커가 다시 가져갈 수 있다. 이때 서버가 앞선 시도의 `FAILED` 단계를 남긴다.
- 3회째 시도도 끝나지 않으면 `FAILED`로 닫고 서버가 `FAILED` 단계를 남긴다(`max-attempts`).
- 킬스위치 `app.payment-stop.agent.enabled=false`(환경변수 `PAYMENT_STOP_AGENT_ENABLED`)면 작업 가져가기가 빈 응답이다. 기본값은 꺼짐이다.

## 4. 엔드포인트

### `POST /internal/review-jobs/claim`

대기 중인 분석 하나를 가져간다. 대기열이 비었거나 킬스위치가 꺼졌으면 `data`가 `null`이다.

```json
{ "runId": 41, "objectionId": 7, "attempt": 1, "nextSeq": 1, "jobToken": "..." }
```

`nextSeq`는 이 시도가 처음 보낼 단계 순번이다. 재시도면 앞선 시도와 서버가 남긴 단계 뒤로 이어진다.

### `GET /internal/objections/{objectionId}/context`

가명 처리한 소명과 접수 정보.

| 필드 | 뜻 |
|---|---|
| `clause` | 사유 조항(법 제7조제1항 1~3호) |
| `statement` | 가명 처리한 소명 |
| `simplifiedRequested`, `idCopyConfirmed`, `litigationPending` | 간소화 신청, 신분증 사본 확인 여부(내용은 주지 않는다), 소송 계속 |
| `objectionDeadline` | 신청 기한 |
| `disputedTransfers` | 다툼 거래. 원장 항목 형식(아래) |
| `commonDocuments` | 누락 서류를 짚을 때 기준으로 쓰는 공통 소명자료 목록(내부 목록) |
| `ledgerFrom`, `ledgerTo` | 원장 조회와 판정안 근거가 허용되는 기간. `ledgerTo`는 미포함 |

### `GET /internal/objections/{objectionId}/ledger?from&to&counterparty`

지급정지 계좌의 원장 거래. `from`, `to`는 ISO 일시이고 생략하면 허용 기간 전체다. 허용 기간 밖은 기간 안으로 줄인다. `counterparty`에 가명 키를 주면 그 상대와의 거래만 준다.

| 필드 | 뜻 |
|---|---|
| `transactionNumber` | 거래번호. 판정안 근거로 쓰는 ID |
| `direction` | `IN` / `OUT` |
| `interbank` | 상대가 타행이면 true |
| `counterpartyKey` | 당행 상대의 가명 키. 타행이면 null |
| `amount`, `occurredAt` | 금액(원), 원장 시각 |
| `disputed` | 다툼 거래 |
| `afterDispute` | 첫 다툼 거래 이후 거래. 정상 근거로 쓰지 않는다 |

### `POST /internal/agent-runs/{runId}/steps`

단계 이벤트 하나를 남긴다. 본문은 `seq`, `type`, `title`(200자), `summary`(500자), `detail`(JSON), `status`, `startedAt`, `endedAt`.

- `seq`는 지금까지 저장된 가장 큰 순번 + 1이어야 한다. 아니면 `PST0305`.
- 같은 `seq`를 같은 내용으로 다시 보내면 무시한다. 내용이 다르면 `PST0307`.
- `GATE_EVALUATED`는 서버만 남긴다. Agent가 보내면 `CMN0001`.
- `FAILED`를 보내면 시도가 남아 있을 때 대기열로 돌아가고, 마지막 시도면 실행이 `FAILED`로 끝난다.
- 텍스트와 `detail`의 문자열 값은 서버가 저장 전에 다시 가명 처리한다.

### `PUT /internal/objections/{objectionId}/review-draft`

판정안을 낸다.

| 필드 | 뜻 |
|---|---|
| `branch` | Agent 갈래. `FRAUD_PATTERN` / `NORMAL_ACCEPT` / `NORMAL_PARTIAL` / `UNDETERMINED` |
| `evidenceTransactionNumbers` | 근거 거래번호. 허용 기간 안 거래여야 한다. 아니면 `PST0005` |
| `missingDocuments`, `followUps` | 누락 소명자료, 추가 확인 항목 |
| `claims` | 추출한 주장. 주장마다 근거 원문(`quote`)을 붙인다 |
| `contradictions` | 주장과 원장의 모순. `transactionNumbers`도 허용 기간 안이어야 한다 |
| `noticeDraft` | 안내문 초안 |
| `model`, `promptVersion`, `inputTokens`, `outputTokens`, `costMicroUsd` | 사용량. 비용은 백만분의 1달러 정수 |

응답은 `{ "runId", "agentBranch", "gateBranch" }`다. 서버는 같은 트랜잭션에서 판정안 저장, 게이트 계산 기록(`gate_evaluation`의 `ANALYSIS`), `GATE_EVALUATED` 단계, 실행 종료(`COMPLETED`)를 남긴다. 같은 토큰으로 다시 내면 처음 결과를 돌려주고 아무것도 더 남기지 않는다.

## 5. 관리자 화면용

| API | 뜻 |
|---|---|
| `GET /admin/objections/{id}/agent-runs` | 실행 목록(최근부터). 상태, 시도, 사용량, 실패 사유 |
| `GET /admin/objections/{id}/agent-runs/{runId}/steps?after={seq}` | 실행 타임라인. 1초 폴링으로 새 단계만 받는다. 실행이 끝나면 화면이 멈춘다 |
| `GET /admin/objections/{id}/review-draft` | 최근 판정안. 없으면 `data`가 null |
| `POST /admin/objections/{id}/review-requests` | 재분석 요청. 멱등키 필수. 판정 확정 뒤면 `PST0302`, 진행 중 실행이 있으면 `PST0306` |

권한은 다른 이의제기 API와 같다(조회 `PAYMENT_STOP_READ`, 요청 `PAYMENT_STOP_WRITE`).
