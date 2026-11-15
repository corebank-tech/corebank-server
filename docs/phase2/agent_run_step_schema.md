# Agent 실행 단계 이벤트 형식

AI Agent가 한 번 실행되는 동안 무엇을 했는지 단계별로 남기는 형식이다. 화면은 이 기록을 받아 실행 타임라인을 그린다.
지급정지 이의제기 AI 심사 지원([D-36](README.md#d-36--113--리드))이 먼저 쓰고, P6 FDS Agent도 같은 형식과 화면 타임라인을 쓰도록 제안한다.
용어는 [glossary.md](glossary.md) 15번(판정안, Agent 실행, 단계)을 따른다.

구현 전 설계다. AGENT-OBJ 구현 PR에서 형식이 바뀌면 이 문서를 같은 PR에서 고친다.

## 1. 원칙

- Agent는 단계 이벤트를 남기고 판정안을 제출하는 것만 쓸 수 있다. 해제, 확정, 계좌 변경 권한은 없다.
- 단계 이벤트에는 원문 개인정보(이름, 계좌번호, 연락처, 주민등록번호)를 남기지 않는다. 거래는 거래 ID로, 상대방은 가명 키로 적는다.
- `title`, `summary`, `detail`의 모든 텍스트(OCR 텍스트, 요약, 근거 문장)는 LLM에 보내는 입력과 같은 규칙으로 가명 처리한 값만 남긴다. 원문 대조는 담당자 화면에서 원본 서류로 한다.
- 화면은 1초 폴링으로 새 이벤트만 받는다(`after`=마지막 순번). SSE는 쓰지 않는다.
- 재생 모드는 저장된 이벤트를 원래 시간 간격대로 다시 보여 주는 화면 기능이다. 서버는 전체 이벤트 조회만 제공한다.

## 2. `agent_run` — 실행 한 번

| 필드 | 뜻 |
|---|---|
| `run_id` | 실행 ID |
| `objection_id` | 대상 이의제기 ID. 다른 Agent는 자기 대상 ID를 쓴다 |
| `status` | `QUEUED`, `RUNNING`, `COMPLETED`, `FAILED` |
| `attempt` | 시도 횟수. 3회 실패하면 `FAILED` |
| `lease_until` | 워커 점유 만료 시각(5분) |
| `model`, `prompt_version` | 사용한 LLM 모델명과 프롬프트 버전 |
| `input_tokens`, `output_tokens` | 토큰 수 |
| `cost_micro_usd` | 비용, 백만분의 1달러 정수(`BIGINT`). `BigDecimal`은 금리 전용이다(AGENTS.md 규칙 5) |
| `requested_by` | 분석을 넣은 관리자. 접수 때 자동으로 들어간 실행은 접수한 관리자 |
| `queued_at`, `started_at`, `ended_at` | 대기열에 들어간 시각, 처음 가져간 시각, 끝난 시각 |
| `failure_reason` | 실패하거나 대기열로 돌아간 사유(가명 처리) |

## 3. `agent_run_step` — 실행 안의 단계

| 필드 | 뜻 |
|---|---|
| `step_id` | 단계 행 ID. 화면에는 쓰지 않는다 |
| `run_id`, `seq` | 실행 ID와 실행 안 순번. 두 값의 조합이 유일하다. 같은 `seq`를 다시 보내면 무시한다(멱등). 다만 이미 저장된 단계와 `type`이나 내용이 다르면 무시하지 않고 충돌로 거절한다. 점유 만료로 서버가 `FAILED`를 먼저 남긴 뒤 Agent가 같은 번호를 보내는 경우다. `seq`는 실행마다 1부터 빈 번호 없이 매긴다. Agent는 앞 단계가 저장된 뒤 다음 번호를 보내고, 서버 단계(`GATE_EVALUATED`)는 판정안 제출을 처리한 트랜잭션에서 그다음 번호로 남긴다. 그래서 큰 번호가 작은 번호보다 먼저 저장되지 않는다 |
| `type` | 단계 종류(§4) |
| `title` | 화면에 보일 한 줄 |
| `summary` | 짧은 설명 |
| `detail` | 종류별 상세(JSON) |
| `status` | `RUNNING`, `DONE`, `FAILED` |
| `started_at`, `ended_at` | 시작과 종료 시각 |

## 4. 단계 종류

| `type` | 남기는 주체 | `detail` |
|---|---|---|
| `CLAIMED` | Agent | 작업을 가져온 시각과 시도 횟수 |
| `CONTEXT_LOADED` | Agent | 읽은 입력의 종류와 건수 |
| `OCR` | Agent | 글자 영역 ID와 좌표, 가명 처리한 텍스트, 인식 신뢰도 |
| `TOOL_CALL` | Agent | 도구 이름, 부른 이유, 가명 처리한 인자 요약과 결과 요약, 거래 ID |
| `CLAIM_EXTRACTED` | Agent | 주장 목록과 근거(가명 처리한 문장 또는 OCR 영역 ID) |
| `CONTRADICTION` | Agent | 주장, 원장 사실, 거래 ID |
| `DRAFT_SUBMITTED` | Agent | 제출한 판정안 요약 |
| `GATE_EVALUATED` | 서버 | 해제 게이트 규칙별 결과 |
| `FAILED` | Agent 또는 서버 | 실패 단계와 사유 |

다른 Agent가 쓸 때는 `OCR`, `CLAIM_EXTRACTED`, `CONTRADICTION`, `GATE_EVALUATED`처럼 업무에 묶인 종류를 자기 종류로 바꾸고, 나머지 필드와 `CLAIMED`, `TOOL_CALL`, `FAILED`는 그대로 쓴다.

## 5. 조회

- 관리자 화면: `GET /admin/objections/{id}/agent-runs/{runId}/steps?after={seq}` — `seq`가 `after`보다 큰 단계만 순서대로 준다. 실행 목록은 `GET /admin/objections/{id}/agent-runs`다.
- 내부 API 전체 계약은 [objection_agent_contract.md](objection_agent_contract.md)다.
