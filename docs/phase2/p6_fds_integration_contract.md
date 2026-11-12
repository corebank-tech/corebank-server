# P6 FDS FE–BE–Agent 연동 계약 초안 (#854)

> 상태: **검토 초안**. 이 문서는 구현된 API를 선언하지 않는다. 이체 보류·토큰·실행의 정본은 [transfer_seam.md §9](transfer_seam.md#9-훅-c--이체-보류-771)이다. 정책 임계값과 판정 사례는 [#855 정책 사례표](p6_fds_policy_cases.md)에 정리한다.
> 기준: #827이 `dev`에 머지된 뒤 [명세서](p6_fds_ai_agent_spec.md) §4·§7·§9와 [제안서](p6_fds_ai_agent_proposal.md) §7의 연동 표를 다시 대조했다(2026-11-03). 두 문서의 URL 초안과 `flowId`·`HELD` 의미는 일치한다. 현재 이체 API 구현과의 차이는 §4에 적었다.
> **담당 범위 보정:** P6 담당자가 FDS 화면 구현과 FE–BE API 연결, 인증 경계 설계까지 수행한다. 아래의 FE는 실행 위치를 뜻하며 별도 FE 담당자의 구현을 전제하지 않는다.
> API별 요청·응답·오류 예시와 노션 이관 형식은 [FDS API 명세 초안](p6_fds_api_spec.md)에 둔다. FDS 전용 오류는 [오류코드 마스터 §4-17](../api_conventions.md#4-17-fds-이상거래탐지보류-사건-조사--p6-854)에 등록했다. 두 문서의 제안 필드는 구현·팀 합의 전 확정 계약이 아니다.

## 1. URL 검증 결과와 상태

서버의 `server.servlet.context-path`는 `/api/v1`이다. 아래 경로의 왼쪽은 **컨트롤러 매핑**, 오른쪽은 FE·Agent가 호출하는 **전체 경로**다. `/api/v1`을 두 번 붙이지 않는다. 기존 `TransferController`는 `@RequestMapping("/transfers")`와 `@PostMapping`을 사용한다. **P6 신규 FDS 경로는 아래 경로로 결정한다.** 아직 구현된 API라는 뜻은 아니다. 기존 이체의 보류 응답은 `TransferResponse.transferHold` 중첩 구조로 구현돼 있으며, FDS `ON_APPROVAL` 응답은 P4 구현 뒤 확인한다.

| 호출 | 컨트롤러 매핑 | 전체 호출 경로 | 상태·소유자 |
|---|---|---|---|
| FE → BE 행동 제출 | `POST /fds/behavior-flows/{flowId}/events` | `POST /api/v1/fds/behavior-flows/{flowId}/events` | P6 경로 결정·미구현 |
| FE → BE 즉시이체 | `POST /transfers` | `POST /api/v1/transfers` | P4 **기존 구현**. `flowId` 선택 필드·멱등 지문 포함 구현됨 |
| Agent → BE 거래 이력 | `GET /internal/fds/holds/{transferHoldId}/history` | `GET /api/v1/internal/fds/holds/{transferHoldId}/history` | P6 경로 결정·미구현; P4 보류 원본·상태 조회 계약 필요 |
| Agent → BE 수취인 맥락 | `GET /internal/fds/holds/{transferHoldId}/recipient-context` | `GET /api/v1/internal/fds/holds/{transferHoldId}/recipient-context` | P6 경로 결정·미구현 |
| Agent → BE 보고서 제출 | `PUT /internal/fds/investigations/{investigationId}/report` | `PUT /api/v1/internal/fds/investigations/{investigationId}/report` | P6 경로 결정·미구현 |
| 관리자 목록·상세 | `GET /admin/fds/holds`, `GET /admin/fds/holds/{transferHoldId}` | `GET /api/v1/admin/fds/holds`, `GET /api/v1/admin/fds/holds/{transferHoldId}` | P6 경로 결정·미구현 |
| 관리자 콜 기록 | `POST /admin/fds/holds/{transferHoldId}/call-record` | `POST /api/v1/admin/fds/holds/{transferHoldId}/call-record` | P6 경로 결정·미구현 |
| 관리자 승인·거절 | `POST /admin/fds/holds/{transferHoldId}/approve`, `POST /admin/fds/holds/{transferHoldId}/reject` | `POST /api/v1/admin/fds/holds/{transferHoldId}/approve`, `POST /api/v1/admin/fds/holds/{transferHoldId}/reject` | P6 경로 결정·미구현; 실행·거절은 P4 공개 API 호출 |

`/internal/**`는 현재 내부 서비스 인증이 설정된 경로가 아니다. 경로 이름만으로 내부 호출이 되지 않는다. §6의 전용 인증 경계를 구현·검증하기 전에는 실제 API로 열지 않는다. 현재 `/admin/**`의 미등록 경로는 `denyAll`이므로 §6의 FDS 관리자 권한 matcher를 명시적으로 추가해야 한다.

## 2. 책임과 호출 순서

| 담당 | 제공·검증할 것 |
|---|---|
| P6 FE 작업 | 즉시이체 화면에 `flowId` 생성·7종 이벤트 계측·제출·이체 API 연결, 정상/보류 안내, 관리자 목록·상세·콜·승인/거절 화면과 실제 API 연결 |
| P6 Spring Boot | 인증 고객의 `flowId` 소유권·이벤트 유효성, 행동 요약, A/B 판정과 근거, 훅 C 구현, 조사 작업·Tool·보고서·관리자 권한/콜/감사 |
| P4 transfer | 이체 요청과 멱등 지문, 훅 C 호출, 보류 원본 저장·`TransferHeld`, 보류 조회와 Agent Tool용 이체 목록·수취인 집계 공개 API, `release()`·`reject()`·1회 실행 |
| 인증 담당 | 고객 세션에서 고객 ID 제공, 관리자 세션·권한 제공, 내부 서비스 인증 방식 검토 |
| Python Agent | `TransferHeld` 뒤 조사 작업 수신, 인증된 읽기 Tool 호출, 구조화 보고서 제출. 보류 결정·승인·기표 권한 없음 |

1. P6은 FE 즉시이체 화면 진입마다 새 UUID `flowId`를 만들도록 구현한다. **새 이체하기**는 새 ID다. 동일 이체의 화면 왕복은 같은 ID를 유지한다. 같은 탭 새로고침은 `sessionStorage`의 `flowId`와 FE 생성 시각을 확인해 30분 이내면 복원한다. 서버의 30분 만료 판정은 첫 수신 시각이 정본이다. 만료·새 이체 시작·이체 완료 또는 보류 확정 시 저장한 ID를 지우고 새 흐름을 만든다. 새로고침으로 복원되지 않은 미전송 행동은 추정해 재구성하지 않는다. 이벤트 전송 실패 시 같은 본문과 `Idempotency-Key`로 재시도하고, 이체 실패와 별도로 표시한다.
2. P6은 FE에서 OTP 성공 뒤 실제 이체 요청 직전에 이벤트 묶음을 제출하고, 기존 `POST /transfers` 본문에 같은 `flowId`를 넣는다. 이벤트 제출 성공을 이체 성공으로 표시하지 않는다. 행동 데이터가 없으면 A의 망설임 근거로 쓰지 않지만 B 판정은 계속한다.
3. P6은 `flowId`를 서버에서 확인한 로그인 고객에게 귀속시키고, 이체 요청 고객과 일치하는지 검사한다. FE가 보낸 고객 ID·위험 점수·금액을 신뢰하지 않는다. P4의 기존 이체 요청 지문은 `flowId`가 있을 때 포함한다. 같은 멱등 키로 다른 `flowId`를 보내면 기존 멱등 충돌 규칙을 따른다.
4. 계좌비밀번호·OTP 토큰은 훅 C 판정 **전에** 소비한다. HOLD면 기표 트랜잭션을 롤백하고 P4가 원본 요청과 `TransferHeld`를 새 트랜잭션에 저장한다. 보류 저장 실패는 `transfer` ERROR와 실패 응답으로 끝나며 고객은 재인증한다. P6 Agent는 `TransferHeld` 이후 비동기 조사한다.
5. 관리자는 권한과 콜 결과 확인 후 승인·거절한다. 승인된 `ON_APPROVAL` 보류는 P4 `release()`가 선점한 원본 요청만 토큰 재사용 없이 한 번 실행한다. Agent의 조사 실패는 보류를 풀지 않으며 서버 근거와 콜 결과로 수동 검토할 수 있다.

## 3. FE 행동 이벤트 계약 초안

이벤트 종류는 `TRANSFER_ENTER`, `RECIPIENT_CONFIRMED`, `AMOUNT_EDITED`, `STEP_BACK`, `CONFIRM_ENTER`, `CONFIRM_LEAVE`, `SUBMIT_REQUESTED`의 7개다. `AMOUNT_EDITED`는 최초 확정 뒤 재수정만, `CONFIRM_ENTER/LEAVE`는 방문마다 한 쌍이다. 활성 체류는 보이는 화면의 경과 시간만 센다. 계좌번호·메모·비밀번호·OTP 원문은 이벤트에 넣지 않는다.

```http
POST /api/v1/fds/behavior-flows/3f1c2b7e-8a4d-4f0e-9c1a-2b6d5e7f8a90/events
Content-Type: application/json
Cookie: JSESSIONID=<고객 세션>
```

```json
{
  "events": [
    {"flowId": "3f1c2b7e-8a4d-4f0e-9c1a-2b6d5e7f8a90", "seqNo": 1, "eventType": "TRANSFER_ENTER", "occurredAt": "2026-11-02T13:00:00+09:00", "step": "D-01"},
    {"flowId": "3f1c2b7e-8a4d-4f0e-9c1a-2b6d5e7f8a90", "seqNo": 2, "eventType": "RECIPIENT_CONFIRMED", "occurredAt": "2026-11-02T13:00:15+09:00", "step": "D-01"},
    {"flowId": "3f1c2b7e-8a4d-4f0e-9c1a-2b6d5e7f8a90", "seqNo": 3, "eventType": "CONFIRM_ENTER", "occurredAt": "2026-11-02T13:00:30+09:00", "step": "D-02"},
    {"flowId": "3f1c2b7e-8a4d-4f0e-9c1a-2b6d5e7f8a90", "seqNo": 4, "eventType": "CONFIRM_LEAVE", "occurredAt": "2026-11-02T13:01:35+09:00", "step": "D-02"},
    {"flowId": "3f1c2b7e-8a4d-4f0e-9c1a-2b6d5e7f8a90", "seqNo": 5, "eventType": "SUBMIT_REQUESTED", "occurredAt": "2026-11-02T13:01:40+09:00", "step": "D-02"}
  ],
  "confirmActiveSeconds": 65
}
```

위 배열은 **전송 형식 예시**이며 한 흐름에 7종이 모두 발생해야 한다는 뜻이 아니다. 명세서 §4의 형식을 유지해 **각 이벤트에도 `flowId`를 담고 경로의 `flowId`와 일치하는지 검증한다.** 중복 필드는 의도적이며, 불일치하면 `CMN0001`로 묶음 전체를 거부한다. `occurredAt`은 달력 시각, `confirmActiveSeconds`는 FE의 `performance.now()`로 계산한 활성 경과 시간이다.

**P6 이벤트 입력 계약:** 한 흐름에 이벤트는 최대 100개(7종 반복 허용), 한 요청의 본문은 최대 32 KiB다. 흐름은 첫 서버 수신 시각부터 30분 유효하다. 각 `occurredAt`은 해당 요청의 서버 수신 시각보다 최대 30분 이전·5분 이후여야 한다. 순서와 만료는 클라이언트 시계가 아닌 서버 수신 시각으로 판정하고, `seqNo`는 1부터 빈 번호 없이 증가시킨다. 이는 입력 제한이며 사기 탐지 임계값이 아니다.

FE는 visible 구간의 `performance.now()` 밀리초 차이를 모두 합산한 뒤 마지막에 1000으로 나누어 소수 초를 버린다(`Math.floor`). 59.999초는 `confirmActiveSeconds=59`, 60.000초는 `60`으로 제출하며 60초 망설임 조건은 후자부터 성립한다. 최종 배치가 `CMN0001`·`FDS0001` 등 검증 오류로 거부되면 이벤트·흐름 마감 상태·체류값은 모두 변경하지 않는다. 검증·이벤트 저장·체류값 확정·흐름 마감은 하나의 원자적 전이로 처리한다.

`confirmActiveSeconds`는 0~1800의 정수이며, FE는 D-02가 보이는 동안 `performance.now()`로 잰 구간만 합산한다. `SUBMIT_REQUESTED`를 담은 최종 배치에서만 선택해 흐름당 한 번 확정한다. 최종 배치에서 누락되면 미확인으로 확정하고, 같은 이벤트·같은 값 재전송은 성공 처리한다. 다른 값 제출이나 최종 배치 이후 새 이벤트는 `FDS0301`로 거부한다. 서버는 값의 범위와 `CONFIRM_ENTER`·`CONFIRM_LEAVE` 쌍의 존재·순서를 확인하고, 클라이언트가 보낸 시간만으로 인증이나 보류를 결정하지 않는다. 새로고침으로 진행 중이던 구간은 종료된 시간으로 추정하지 않는다.

같은 `Idempotency-Key`·본문의 재전송은 저장된 응답을 반환하고, 같은 키에 다른 본문은 `CMN0302`다. 다른 키로 재전송해도 같은 `(flowId, seqNo)`·같은 내용이면 중복 저장 없이 성공(`acceptedCount=0`)한다. 같은 번호에 다른 내용 또는 마감된 흐름의 추가 이벤트는 `FDS0301`, 번호 누락·역전이나 시각 위반은 `FDS0001`이다. 이벤트 묶음은 **전부 저장하거나 전부 거부**한다. 같은 묶음에 새 이벤트와 동일 재전송이 섞여 있으면 `acceptedCount`는 새로 저장한 건수다.

## 4. 이체 요청과 FE 응답

기존 `POST /api/v1/transfers`는 `Idempotency-Key`, `Account-Password-Auth-Token`, `Otp-Auth-Token` 헤더를 요구한다. 요청 본문의 기존 출금계좌·수취계좌·금액·메모에 선택 `flowId`가 추가돼 있다. 아래는 **요청 필드 예시**다.

```json
{
  "withdrawalAccountId": 101,
  "depositAccountNumber": "110222222222",
  "amount": 500000,
  "myPassbookMemo": "이체",
  "recipientPassbookMemo": "입금",
  "flowId": "3f1c2b7e-8a4d-4f0e-9c1a-2b6d5e7f8a90"
}
```

현재 `TransferResponse`에는 `status`, `transactionNumber`, `transferredAt`, `errorCode`, `errorMessage`, `withdrawalBalanceAfter`와 중첩 `transferHold`가 있다. 보류 시 `status`·`transactionNumber`는 null이고 `transferHold`에 `transferHoldId`·`status="HELD"`·`releaseAt`·`cancellableUntil`이 담긴다. `ON_APPROVAL`에서 두 시각은 null인 예상값이며, 실제 FDS 보류 응답은 P4 구현 뒤 확인한다. `releaseMode`는 현재 DTO에 없으므로 필요하면 P4와 별도 확장 계약을 정한다. `HELD`는 `transfer.status`나 기존 `ProcessResultStatus` 값이 아니다.

```json
{
  "code": "0000",
  "message": "정상 처리되었습니다.",
  "data": {
    "status": null,
    "transactionNumber": null,
    "transferredAt": null,
    "errorCode": null,
    "errorMessage": null,
    "withdrawalBalanceAfter": null,
    "transferHold": {
      "transferHoldId": 123,
      "status": "HELD",
      "releaseAt": null,
      "cancellableUntil": null
    }
  }
}
```

P6은 FE에서 보류를 이체 완료로 표시하지 않는다. 승인 대기와 시각 해제는 안내 문구가 다르다. 이체 HTTP 오류, `data.status=ERROR`, `data.transferHold.status=HELD`, 행동 이벤트 전송 실패를 각각 별도 상태로 처리한다. 보류 저장 실패 뒤 같은 멱등 키로 자동 재시도하지 않는다.

## 5. Agent Tool·보고서 계약 초안

`TransferHeld`는 비동기 조사 시작 신호다. P6은 같은 `transferHoldId`의 조사 작업을 중복 등록하지 않고, Agent에는 `investigationId`, 정수 `transferHoldId`, P6의 정책 근거·행동 요약·마스킹된 이체 요약만 제공한다. P4는 `transfer.api`를 통해 보류 원본·상태를 제공하고 P6은 그 영속성 구현을 직접 읽지 않는다.

| API | 요청 입력 | 응답의 최소 내용 | 실패 처리 |
|---|---|---|---|
| `GET .../history` | `transferHoldId`; 보류 직전 최대 24시간 | 최근 성공 이체 최신순 최대 100건의 `refId`·시각·금액·마스킹 수취계좌와 잘림 여부 `hasMore` | P4 `transfer.api`의 이체 목록 조회 인계 대기. 권한·보류 조회 실패면 조사 실패; 거래가 없음은 빈 배열 |
| `GET .../recipient-context` | `transferHoldId` | 해당 고객·수취인의 **전체 과거 성공 이력** 존재 여부·최초 성공 시각·현재 보류 건 출금계좌를 제외한 다른 출금계좌 수(`withdrawalAccountId` DISTINCT)의 좁은 집계(목록 미반환) | P4 `transfer.api`의 수취인 집계 조회 인계 대기. 정보 없음과 조회 장애 구분; 최근 목록에 없다는 이유로 신규 수취인으로 취급하지 않음 |
| `PUT .../report` | 경로 `investigationId`, 본문 `transferHoldId`, 조사 상태·`secondaryFindings`·근거 ID | 저장된 조사 ID·상태 | 같은 조사 ID 재제출은 멱등; 근거 ID가 허용된 조회 결과 밖이면 거부 |

```json
{
  "transferHoldId": 123,
  "status": "COMPLETED",
  "secondaryFindings": [{
    "code": "REGULAR_SPLIT_AMOUNTS",
    "description": "최근 동일 수취인 송금액이 일정하게 반복됨",
    "evidenceTransferIds": [101, 102],
    "confidence": "OBSERVED"
  }],
  "missingInformation": [],
  "recommendedAction": "MANUAL_CUSTOMER_CONFIRMATION",
  "suggestedQuestions": ["반복 송금 목적을 확인해 주세요."],
  "summary": "서버 보류 사유 외에 반복 금액 패턴이 확인됨."
}
```

이 JSON은 제안서·명세서의 보고서 형식을 따른 **예시**다. `evidenceTransferIds`는 실제 조회 결과의 해당 고객 이체 `refId`인지 BE에서 재검증한다. Agent가 보류 해제·승인·원장 변경 API를 호출할 권한은 없다. 내부 인증·네트워크는 §6-2에 정하고, 작업 타임아웃·재시도는 아래에 정한다. 로그에는 토큰·원문 계좌번호·보고서 자유 텍스트를 남기지 않는다.

**P6 조사 작업 실행 계약:** 한 `transferHoldId`에 조사 작업 1개와 안정적인 `investigationId` 1개를 둔다. **60초는 각 시도별 Agent 실행 제한이며 재시도 대기 시간은 제외한다.** 각 BE Tool HTTP 호출 제한은 5초다. 실패하면 같은 조사 ID로 총 3회까지 시도한다(첫 시도 즉시, 이후 1분·5분 뒤). 실패한 중간 시도는 보고서를 제출하지 않는다. 성공 시 `COMPLETED` 보고서를 한 번 멱등 저장하고, 최종 실패 시 BE가 조사 작업 상태를 `FAILED`로 기록한다. **P4의 이체 보류 상태는 `HELD` 그대로** 두고 관리자가 서버 A/B 근거와 콜 결과로 수동 검토한다. `HELD`는 `transfer.status`가 아니다. 제한 시간·횟수는 시연 환경의 실행 보호값이며 사기 판정 기준이 아니다.

### 5-1. 고객 취소·만료 종료 이벤트 제안 — P4 합의 전

P4가 `transfer_hold`를 `CANCELLED`(고객 취소) 또는 `EXPIRED`(`ON_APPROVAL` 만료)로 바꾸는 **같은 업무 트랜잭션 안에서** `transfer.api.TransferHoldClosed`를 발행하는 안을 제안한다. Spring Modulith Event Publication Registry([ADR-0010](../adr/0010-outbox-spring-modulith-event-publication-registry.md))로 전달 실패·재전달을 관리한다. 상태 변경이 롤백되면 종료 이벤트도 남지 않아야 한다. 관리자 `REJECTED`는 P6이 직접 조치하므로 이 이벤트의 범위에서 제외한다. `EXECUTED`·`FAILED`도 기존 P4 실행 결과 조회로 처리한다.

```java
// P4 transfer.api 공개 이벤트 제안. 이름·필드는 P4 합의 후 확정한다.
public record TransferHoldClosed(
        long transferHoldId,
        TransferHoldCloseReason reason, // CANCELLED 또는 EXPIRED
        LocalDateTime closedAt) {}

public enum TransferHoldCloseReason { CANCELLED, EXPIRED }
```

예: `TransferHoldClosed(123, CANCELLED, 2026-11-03T15:00:00)`을 받으면 P6은 보류 ID 123의 조사 작업을 닫고 예약된 재시도를 중단한다. 이벤트 중복은 `transferHoldId`와 종료 사유로 멱등 처리하고, 늦게 도착한 Agent 보고서는 관리자에게 활성 조사 결과로 표시하지 않는다. 이벤트와 승인·거절이 경합하면 P4의 현재 보류 상태를 다시 확인해 이미 종료된 건을 실행하지 않는다. P6의 사건 종료 기록은 멱등하게 저장하고, 리스너 저장 실패는 이벤트 재전달로 복구한다. P4가 종료 이벤트를 제공할 수 없으면 보류 상태 조회 기반 동기화로 전환하되, 취소·만료를 즉시 반영하는 보장은 별도로 검증한다.

**보고서 입력 상한:** JSON 본문 최대 16 KiB, `secondaryFindings` 최대 4건, 각 `description` 1~300자, 항목별 `evidenceTransferIds` 최대 10개다. `missingInformation` 최대 5개(각 1~100자), `suggestedQuestions` 최대 3개(각 1~200자), `summary` 1~500자다. 배열이 비어 있으면 `[]`를 보낸다. 근거 ID가 있는 경우 Tool에서 실제 조회한 해당 고객 이체만 허용하고, 근거가 집계값뿐이면 ID를 만들지 않고 빈 배열과 `missingInformation`으로 한계를 설명한다. Agent의 문자열은 관리자 화면에서 텍스트로 렌더링하고 실행 지시로 처리하지 않는다.

## 6. 관리자 API 계약 초안

| 화면·행동 | 제안 요청 | 필수 검증·응답 |
|---|---|---|
| 목록 | `GET /api/v1/admin/fds/holds` | 권한, 페이지·상태 조건, 마스킹, 정책 A/B 근거와 AI 조사 상태 분리 |
| 상세 | `GET /api/v1/admin/fds/holds/{transferHoldId}` | 권한, 원본 이체·서버 판정 근거·행동 요약·추가 발견·콜 이력 분리 |
| 콜 결과 | `POST /api/v1/admin/fds/holds/{transferHoldId}/call-record` | 권한, 콜 결과·사유·담당자·시각의 감사 기록 |
| 승인 | `POST /api/v1/admin/fds/holds/{transferHoldId}/approve` | 관리자 권한·콜 결과·`ON_APPROVAL`·상태 확인 뒤 P4 `release()` 호출 |
| 거절 | `POST /api/v1/admin/fds/holds/{transferHoldId}/reject` | 관리자 권한·사유 확인 뒤 P4 `reject()` 호출 |

목록·상세는 고객 개인정보 조회이므로 기존 관리자 조회 규칙의 `X-Access-Reason` 유효값 검사와 조회 감사를 적용한다. 콜 결과도 고객 확인 결과의 변경이므로 같은 사유 헤더·검증과 기존 `AuditLog`를 적용한다. 승인·거절은 개인정보를 응답으로 반환하지 않는 관리자 변경으로 설계하고 기존 `AuditLog`를 적용한다. **P6이 소유한 콜 기록 변경과 감사 기록은 같은 트랜잭션으로 처리한다.** P4의 `release()`·`reject()`는 별도 소유 경계이므로 P6 감사 기록과 한 트랜잭션이라고 약속하지 않는다. 승인·거절 전에 P6 조치 정보를 내구화하고, P4 호출 결과를 확인한 뒤 감사를 멱등 기록한다(§6-1). `PUT /report`와 관리자 변경 응답도 공통 HTTP 200·`code="0000"` 형식 및 오류코드 마스터를 따른다.

**P6 콜 결과 계약:** `CUSTOMER_CONFIRMED`, `CUSTOMER_DENIED`, `NO_ANSWER`, `INCONCLUSIVE` 네 값만 받는다. 콜 기록은 덮어쓰지 않고 추가하며 서버 기록 시각으로 최신 결과를 정한다. 해당 보류 건의 최신 결과가 `CUSTOMER_CONFIRMED`일 때만 P4 `release()`를 호출한다. 최신 결과가 `CUSTOMER_DENIED`일 때만 사유 `CUSTOMER_DENIED`로 P4 `reject()`를 호출한다. `NO_ANSWER`·`INCONCLUSIVE`는 보류를 유지해 추가 확인한다. AI 보고서는 승인 요건이 아니다. P4와는 이 호출 조건 및 `actorId`·사유 전달을 대조한다.

### 6-0. 합성 위험 계좌 조회와 전화 없는 거절 (#946, P4 인계 전)

위 콜 결과 계약에 추가할 **제안 경로**다. 현재 API와 P4 공개 조회 계약에 구현된 기능으로 취급하지 않는다. 실제 고객이 접속하지 않는 프로젝트 시연용 운영 환경에서 P6 소유 `fds_risk_account`의 **합성 계좌 행을 실제 DB에 적재·조회**한다. 이는 외부 기관의 확인 자료나 실제 사기 확정 정보가 아니다. 관리자 등록·정정·해제와 외부 정보 연동은 이번 범위 밖이다. 일치 키는 `은행코드 + 계좌번호`의 정확한 일치이며, 현재 당행 보류의 은행코드는 `088`이다. 조회 결과 `MATCH`에서는 전화 없이 **거절만** 허용하고 승인은 금지한다. `NO_MATCH`·`UNAVAILABLE`은 기존 콜 결과 계약을 따른다. 조회 장애를 `NO_MATCH`로 바꾸지 않는다. 고객 화면에는 위험 계좌 조회 결과를 표시하지 않는다.

**데이터셋 수명:** 첫 버전은 `demo-risk-v1`이다. P6이 전용 QA 수취계좌와 위험 계좌 행을 `src/main/resources/db/seed/local-demo-data.sql`에 함께 추가하고 PR로 검증한다. 로컬은 `local`, 시연용 운영 DB는 `prod,qa-seed` 기동에서 기존 `DemoDataLoader` 경로로 적재하며 `prod` 단독 기동은 합성 행을 적재하지 않는다([Flyway 파일 역할 가이드](../flyway_file_role_guide.md) §3). 다음 버전은 기존 행을 수정하지 않고 시드 파일에 전체 행을 추가한다. 적재된 새 버전의 건수·형식·중복·해시를 검증한 **뒤에만** 단일 활성 포인터를 원자적으로 전환한다. 버전 전환도 시드 변경 PR과 다음 배포를 통해 수행하며 수동 DB 적재·전환은 하지 않는다. 현재 `DemoDataLoader`의 일반 SQL 오류 전파와 QA 계좌 소유 검증만으로는 위험 데이터셋 검증·원자적 활성화가 보장되지 않으므로 P6 구현에서 기동 검증과 활성화 순서를 추가한다. 활성 버전은 하나이며 미적재·검증 실패 시 `UNAVAILABLE`이다. **0건 버전의 활성화는 금지한다.** 시연 기간에는 이전 버전을 보존해 과거 거절을 재현한다. 프로젝트 종료 시 P4가 합성 행과 활성 포인터의 삭제를 별도 정리 PR·배포 절차로 실행하고 완료를 검증한다. 시드 파일에서 INSERT만 제거해도 이미 적재된 DB 행은 삭제되지 않는다. 감사 기록은 기존 감사 보존 기준을 따른다. 합성 행은 Flyway `V__`·`R__`에 넣지 않는다. P6이 스키마·판정·시드·검증·전환 계약을 소유하고 배포 담당자와 배포 절차를 확인한다.

**관리자 조치와 버전 전환 경합:** P6은 기존 §6-1 방식으로 PENDING 조치를 먼저 커밋한다. 승인·콜 기반 거절·전화 없는 거절 모두 활성 데이터셋 제어 행을 `SELECT ... FOR UPDATE`로 잠근 뒤 보류 원본과 현재 활성 버전의 위험 상태를 최종 재검증한다. 전화 없는 거절은 `MATCH`에서만, 승인·콜 기반 거절은 `NO_MATCH`·`UNAVAILABLE`에서만 진행한다. `MATCH` 거절에서는 잠금 아래 확인한 위험 기록 ID와 데이터셋 버전을 P4 호출 **전에 PENDING 조치에 내구화**하고, 이후 거절 결과·감사는 이 최종 근거를 사용한다. 재검증 실패로 P4를 호출하지 않은 조치는 실패 결과와 ABORTED를 기록해 미완료 조치 차단을 해제한다. 모든 경로에서 P4 상태 전이 커밋까지 제어 행 잠금을 유지하고, 시드 배포에 따른 활성 버전 전환도 같은 잠금 아래 수행한다. 승인에서는 P4의 `HELD → EXECUTING` 선점이 실행보다 먼저 커밋되어야 하므로 잠금 해제 시점과 P4 선점 커밋의 순서를 P4와 합의한다. P4 상태 전이 결과가 모호하면 잠금만 무기한 유지하지 않고 PENDING을 남겨 §6-1의 결과 대조 경로를 따른다. 재조회로 조치 결과를 확인하기 전까지 같은 보류의 새 조치는 차단한다. 잠금 대기·P4 호출 타임아웃과 승인·거절·전환 동시성 테스트를 인계한다. 이 잠금·트랜잭션 순서는 P4 수락 전 제안이며, 확정 전에는 해당 관리자 조치 경로를 구현 완료로 취급하지 않는다.

**P4 인계 요청:** P6이 P4의 저장소를 직접 읽지 않도록 #951에서 추가한 읽기 전용 `transfer.api.findForFds(transferHoldId)`를 확장해 재사용한다. #946에서 별도 `findHoldForAdmin`을 중복 추가하지 않는다. 반환 계약에 `recipientBankCode`, `releaseMode`를 추가하고, 거절 결과 대조용 `actorId`·`reasonCode`도 함께 제공하거나 별도 읽기 전용 결과 조회 API로 제공한다. 복구 시 `REJECTED` 상태만으로 성공 처리하지 않고 실제 행위자·사유를 PENDING과 대조한다. 현재 당행 보류의 `recipientBankCode`는 `088`로 명시한다. P4는 새 사유 `RISK_ACCOUNT_MATCH`의 수용·`transfer_hold.reason_code` 보존 및 거절 후 상태 재조회 계약을 확인한다. 기존 `reject(id, actorId, reasonCode)` 시그니처는 유지할 수 있다. `CUSTOMER_DENIED`는 기존 최신 콜 결과와 콜 기록 ID가 필요한 사유로 유지한다. P4의 필드·사유 수락 전에는 제안으로 표시한다.

**#946 P4와 합의할 항목 (인계 요청, 수락 전):**

| 항목 | P4에 확인할 결정 |
|---|---|
| 보류 원본·결과 조회 | 기존 `findForFds` 확장 필드의 의미·당행 은행코드 `088` 표현, 종료된 보류의 조회 가능 여부, 거절 결과의 `actorId`·`reasonCode` 제공 방식 |
| 거절 사유·결과 | `RISK_ACCOUNT_MATCH` 수용과 `reason_code` 보존, 같은 거절 재시도의 결과 및 최종 상태 조회 방식 |
| 경합·장애 | P6 승인·콜 기반 거절·전화 없는 거절과 버전 전환이 공유할 제어 행 잠금의 트랜잭션 경계, 승인 선점 커밋 순서, 잠금·호출 타임아웃, P4 호출 실패·응답 유실 시 재조회·복구 방식 |

**P6 판정·조치 결정:** 검증 완료된 활성 버전에서 `(bank_code, account_number)`가 정확히 한 행이면 `MATCH`, 정상 조회 결과 행이 없으면 `NO_MATCH`다. 활성 버전 없음·미적재·검증 미완료, 위험 계좌 조회 장애·시간 초과, 중복·손상 등 데이터 무결성 위반은 `UNAVAILABLE`이다. 유니크 제약 위반은 적재를 실패시키고, 읽는 중 손상을 발견해도 `NO_MATCH`로 대체하지 않는다. `UNAVAILABLE`은 위험 계좌 조회만 실패하고 보류 원본·관리자 인증·조회 감사는 정상인 경우의 업무 상태다. 다른 필수 조회나 감사 저장이 실패하면 관리자 API 자체가 실패한다. `MATCH`는 콜 없이 거절만 허용하고 승인 요청은 거부한다. `NO_MATCH`·`UNAVAILABLE`은 기존 최신 콜 결과 조건으로 승인·거절한다. 화면의 이전 조회 결과는 조치 근거가 아니며, 서버가 조치 직전에 다시 확인한다.

**P6 데이터 구조·검증안:** `fds_risk_account`에는 기록 ID, 불변 데이터셋 버전, 은행코드, 숫자만 저장한 계좌번호, `SYNTHETIC` 출처, 적재 시각을 둔다. `(dataset_version, bank_code, account_number)`는 유니크다. 활성 버전 제어 테이블은 고정 PK 한 행에 활성 버전, 검증 상태, 전환 시각을 보존한다. 활성화 전 1건 이상·중복 없음·은행코드와 계좌번호 형식·예상 건수 또는 체크섬을 검증한다. DDL 구현 시 Flyway 증분 파일·`schema_reference.md`·`corebank_erd.md`를 함께 갱신한다. 이 문단은 아직 생성되지 않은 테이블의 설계 계약이며 적용된 DB 스키마 설명은 아니다.

**`demo-risk-v1` 권장 구성:** P6 시연에서 수취인으로 쓸 **전용 당행 QA 계좌 1건**만 위험 목록에 넣고, 다른 시연 수취계좌는 넣지 않아 `MATCH`·`NO_MATCH`를 모두 재현한다. 기존 팀 공용 QA 계좌를 임의로 위험 계좌로 표시하지 않는다. 전용 계좌는 QA 시드와 실제 DB 존재·수취 가능 상태를 확인한 뒤 번호를 확정한다. P6은 `local-demo-data.sql`에 계좌와 위험 행을 추가하고 버전 manifest에 계좌 키, `expectedCount=1`, 정렬된 `088|계좌번호` 한 줄의 SHA-256을 고정한다. P6의 기동 검증은 활성화 전에 DB의 행 수·중복·형식·해시가 manifest와 같은지 확인한다. 계좌번호가 아직 정해지지 않았으므로 실제 해시값은 시드 PR에서 산출해 기록한다. 빈 버전은 `NO_MATCH` 검증용으로 활성화하지 않는다.

**일치 키 형식:** 은행코드는 앞의 0을 보존하는 숫자 문자열 3자리이며 현재 보류 범위는 당행 `088`이다. 당행 계좌번호는 하이픈 없는 숫자 12자리다. 관리자 API는 키를 클라이언트에서 받지 않고 P4 보류 원본의 `recipientBankCode`·`depositAccountNumber`를 사용한다. 시드 입력은 하이픈·공백·문자·길이 오류를 **거부**하며 서버가 조용히 제거·보정하지 않는다. FE가 표시용 하이픈을 사용하더라도 이체 요청 전 제거해 기존 이체 API의 숫자 전용 계약을 따른다. 타행으로 범위를 넓힐 때는 은행코드와 1~16자리 숫자 계좌번호를 별도 계약으로 검증한다. 형식 오류가 있는 보류 원본은 `NO_MATCH`로 취급하지 않고 위험 조회 `UNAVAILABLE`로 표시하며 조치 전 원본 조회 경계를 대조한다.

**관리자 표시·감사안:** 목록은 `heldAt` 오래된 순, 동시각이면 `transferHoldId` 오름차순으로 정렬한다. 목록의 위험 상태는 안내용이며 상세에 `MATCH`이면 **“위험 계좌 목록과 일치”**, `NO_MATCH`이면 **“위험 계좌 목록과 일치하지 않음”**, `UNAVAILABLE`이면 **“위험 계좌 조회 불가 · 고객 확인 절차 필요”**를 표시한다. `MATCH`에는 위험 기록 ID·데이터셋 버전·조회 시각과 목록의 출처가 합성 데이터라는 설명을 상세 근거 영역에 표시한다. 일치가 실제 사기 확정을 뜻한다고 표현하지 않는다. 고객 화면에는 위험 조회 결과를 표시하지 않는다. 관리자 목록·상세 계좌번호는 정상 형식일 때 기존 `MaskingUtil` 규칙(당행 12자리면 앞 3·뒤 3자리 표시)을 따른다. 보류 원본의 계좌번호 형식이 잘못되면 `MaskingUtil`을 호출하지 않고 필수 문자열 `recipientAccountMasked`를 `"확인 불가"`로 반환하며 위험 상태는 `UNAVAILABLE`로 둔다. 전체 번호 확인이 정말 필요하면 별도 권한·조회 사유·감사가 있는 상세 조회로 설계한다. 현재 계약에 없는 수취인 이름·생년월일은 생성하거나 표시하지 않는다. 조회 감사에는 관리자 ID·보류 ID·조회 사유·시각, 거절 감사에는 조치 ID·`RISK_ACCOUNT_MATCH` 또는 `CUSTOMER_DENIED`·위험 기록 ID·버전(해당 시)·처리 결과를 남긴다. 감사 상세에는 계좌번호 원문·이름·생년월일을 복제하지 않고 기록 ID로 원본을 추적한다.

**P6 멱등·FE안:** 거절 요청은 서버가 보류 원본·활성 버전·계좌 일치·현재 보류 상태를 처리 직전에 재확인한다. 동일 멱등 키·본문의 재요청은 같은 조치 결과를 재사용하고, 처리 중이거나 본문이 다르면 기존 `CMN0301`·`CMN0302`를 따른다. 응답 유실 시 새 키로 조치를 만들지 않고 기존 키로 결과를 확인하거나 상세를 재조회한다. FE는 `MATCH`에 거절만, `NO_MATCH`·`UNAVAILABLE`에 콜 기록 후 승인·거절을 제공한다. 조치 직전 상태가 달라지면 화면을 재조회한다. P4 호출·버전 전환 경합의 실제 트랜잭션 경계와 결과 조회는 위 P4 인계가 선행한다. 오류코드와 JSON 예시는 [API 명세 §11](p6_fds_api_spec.md#11-구현-전-확정할-항목)을 따른다.

### 6-1. 관리자 권한·CSRF·조회 감사 결정

기존 5종 `AdminPermission`을 재사용한다. FDS 보류 목록·상세 조회에는 **`CUSTOMER_READ`**, 콜 결과 기록·승인·거절에는 **`CUSTOMER_WRITE`**를 요구한다. 보류 건이 고객의 이체 요청과 개인정보를 포함하므로 기존 고객 조회·변경 권한과 같은 경계로 둔다. FDS 전용 권한을 추가하면 `AdminPermission`·DB CHECK·공통코드·권한 부여·회수 API까지 바뀌므로 현재 범위에서는 만들지 않는다. 관리자 역할만으로 허용하지 않고 각 authority를 검사한다.

**BE 구현 재개 시(11/7 이후)** `SecurityConfig`의 `/admin/**` 기본 `denyAll`보다 앞에 `GET /admin/fds/** → CUSTOMER_READ`, 필요한 `POST /admin/fds/** → CUSTOMER_WRITE` matcher를 이 순서로 둔다. `GET` 외 조회나 허용하지 않은 메서드는 계속 거부한다. 실제 구현에서는 콜·승인·거절 경로만 POST로 허용한다. 고객·관리자 브라우저는 기존 `JSESSIONID`, `XSRF-TOKEN` 쿠키와 `X-XSRF-TOKEN` 헤더를 그대로 사용하고 FDS 관리자 변경 요청에 CSRF 예외를 추가하지 않는다.

이 경로를 구현하는 PR에서 [admin_auth.md §권한 매핑표](admin_auth.md#권한-매핑표)에 `/admin/fds/holds`의 GET/POST 권한을 추가하고 `SecurityConfig`와 함께 갱신한다. 기존 관리자 권한은 로그인 때 세션에 적재되므로 `CUSTOMER_READ`·`CUSTOMER_WRITE`를 회수해도 이미 로그인한 세션에는 즉시 반영되지 않는다. 즉시 차단이 필요한 회수에서는 `admin_auth.md`의 `CustomerSessionInvalidator` 경계를 따른다.

목록·상세 조회와 콜 기록에는 `X-Access-Reason`을 필수로 받고 `AccessReason` 값(`CUSTOMER_REQUEST`, `INCIDENT`, `AUDIT`, `OPERATION`)만 허용한다. 누락·무효는 `CMN0007`이다. 조회는 누가·언제·어떤 보류 건을·왜 열었는지 감사 기록을 남긴다. 콜 기록·승인·거절은 기존 `AuditLog`에 행위·대상·행위자·시각을 남긴다. 승인·거절 응답에 고객 개인정보가 추가되면 사유 헤더 적용 범위를 다시 검토한다.

**감사 분기:** 목록·상세·콜 기록은 `X-Access-Reason`을 받고 `AuditLogService.recordPersonalDataAccess(...)` 경로를 쓴다. 구현 PR에서 해당 `PersonalDataAccessEventType`을 추가한다. 승인·거절 요청은 현재 계약상 `X-Access-Reason`을 요구하지 않고, 별도 `AuditEventType`을 추가해 `AuditLogService.record(...)`로 기록한다. 승인에는 확인된 최신 콜 기록 ID, 거절에는 `CUSTOMER_DENIED` 사유와 콜 기록 ID를 감사 상세에 남긴다. 승인·거절 응답에 개인정보를 추가하거나 사유 헤더를 요구하도록 정책을 바꾸면 이 분기와 API 명세를 함께 바꾼다.

**승인·거절 감사의 재시작 복구:** P6은 P4 호출 **전** 별도 트랜잭션에서 `actionId`, `transferHoldId`, 세션의 `actorId`, `APPROVE`/`REJECT`, 거절 `reasonCode`, 최신 `callRecordId`, `Idempotency-Key`, 요청 지문을 PENDING 조치 행으로 커밋한다. 같은 관리자·엔드포인트·멱등 키의 재요청은 같은 행을 사용하고, 같은 보류 건에 동시에 미완료 조치가 둘 생기지 않게 DB 제약 또는 행 락으로 막는다. 모든 조치는 잠금 아래 최종 위험 상태·보류 상태를 재검증한다. `RISK_ACCOUNT_MATCH`에서는 최종 위험 기록 ID·데이터셋 버전을 P4 호출 전에 PENDING에 내구화하고 이후 거절 감사에 그 값을 사용한다. 재검증 실패로 P4를 호출하지 않았음이 확실하면 실패 결과·ABORTED를 함께 기록하고 해당 미완료 조치 차단을 해제한다. 이후 P4 `release()`/`reject()`를 호출한다. P4 결과를 보류 ID로 재조회해 조치와 일치함을 확인한 다음, P6 조치 행을 잠그고 기존 `AuditLog` 성공 기록·FDS 보조 기록·조치 APPLIED 표시를 **한 트랜잭션**에 저장한다. 재시작한 작업자는 PENDING 행에서 행위자·사유·최종 위험 근거·멱등 키를 복원하고 P4 결과의 상태와 거절 행위자·사유를 대조해 같은 트랜잭션을 재시도한다. 이미 APPLIED면 감사 행을 다시 만들지 않고 저장된 결과를 반환한다. P4가 실행 전 거부해 상태 변경이 없었다고 확인되면 실패 감사와 ABORTED를 기록한다. `HELD`·`EXECUTING` 또는 취소·만료 등 결과가 모호하거나 다른 조치와 맞지 않으면 성공 감사로 임의 확정하지 않고 재조회·수동 대조 대상으로 남긴다. P4 결과 조회에서 최종 상태·실행 이체 ID·거절 행위자·사유를 받을 수 있는지는 인계 대기다. 이 조치 행은 기존 `AuditLog`를 대체하지 않는다.

### 6-2. Agent 내부 API 인증 결정

FastAPI Agent는 고객·관리자 `JSESSIONID`나 CSRF 쿠키를 전달받지 않는다. Agent 전용 **서비스 Bearer 토큰**을 비밀 환경변수로 각 서비스에 주입하고, `Authorization: Bearer <token>`을 `GET /internal/fds/holds/**` 두 Tool과 `PUT /internal/fds/investigations/{investigationId}/report`에만 허용한다. 발표 VM에는 Git 저장소 밖의 비공개 `env_file`을 배치하고 파일 접근 권한을 제한한다. 배포용 Compose가 그 파일을 BE·Agent 서비스의 환경변수로 주입하며, 두 서비스는 같은 토큰을 읽는다. 토큰 값은 문서·Git·Compose YAML·요청 로그에 남기지 않는다. 서버는 토큰을 안전하게 비교하고 인증 성공 시 이 경로에서만 통하는 서비스 principal을 부여한다. 이 principal에 고객·관리자 권한이나 다른 API 접근 권한은 주지 않는다.

**BE → FastAPI 점수 추론(#932):** 반대 방향의 `POST /internal/fds/behavior-score/predict`는 Python 서비스의 사설 경로이며 위 Agent→BE 토큰을 재사용하지 않는다. 별도 `FDS_SCORE_SERVICE_TOKEN`을 양 서비스에 비공개 환경변수로 주입하고 Python이 Bearer 토큰을 검증한다. 이 토큰으로 BE의 Tool·보고서 API를 호출할 수 없고, Agent 토큰으로 Python 추론 API를 호출할 수 없다. 고객·관리자 세션과 CSRF 쿠키는 이 호출에 사용하지 않는다. 네트워크·TLS·비밀값 보관·로그 제외 기준은 이 절의 내부 서비스 기준을 동일하게 적용한다. 요청·오류·점수 저장 책임은 [API 명세 §12](p6_fds_api_spec.md#12-내부-fastapi-행동-점수-추론-932)를 따른다.

Spring Security에는 `/internal/fds/**` 전용 **우선 순위가 높은 stateless filter chain**을 두고, 허용한 메서드·경로 외에는 거부한다. 이 체인에서만 CSRF 검사를 제외하고 브라우저 CORS를 허용하지 않는다. 기존 세션 기반 체인의 `anyRequest().authenticated()`만으로 내부 API를 보호하지 않는다. 발표 VM의 BE·Agent는 같은 Docker Compose 사설 브리지 네트워크에서 서비스 이름으로 통신한다. Agent 포트와 BE의 직접 포트는 호스트에 공개하지 않고, 외부 HTTPS는 리버스 프록시에서 종료한다. 프록시는 `/api/v1/internal/fds/**`를 외부로 라우팅하지 않는다. **같은 VM 내부 브리지 통신만 HTTP**로 두며, 두 서비스를 다른 호스트로 옮기면 내부 연결에도 TLS를 적용한다. 서비스 토큰이 없거나 틀리면 401, 권한 밖 경로는 403으로 처리한다. 토큰 교체는 비공개 환경 파일 갱신 후 두 컨테이너를 재생성해 반영한다(`docker restart`만으로는 환경변수가 갱신되지 않는다). 실제 VM의 프록시·Compose 서비스 이름과 접근 규칙은 배포 시 확인한다.

## 7. FE 화면 인계 예시 (#854)

관리자 로그인은 FDS가 새로 만들지 않는다. 기존 [admin_auth.md §5](admin_auth.md#5-fe-계약-fe-136)의 `POST /api/v1/admin/auth/login`으로 관리자 세션을 받고, FDS 화면의 조회·변경 요청에 그 세션과 CSRF 처리를 적용한다. 권한 표시는 기존 `GET /api/v1/customers/me`의 `role`·`permissions`를 사용한다. FDS 경로는 §6-1에서 결정한 `CUSTOMER_READ`·`CUSTOMER_WRITE`를 요구한다.

 ```http
POST /api/v1/admin/auth/login
```

```json
{"userId":"<관리자-아이디>","password":"<관리자-비밀번호>"}
```

```json
{"code":"0000","message":"정상 처리되었습니다.","data":{"customerId":4,"userName":"운영담당자","sessionExpiresAt":"2026-11-03T14:30:00+09:00"}}
```

로그인 예시는 기존 인증 계약을 인용한 것이다. 관리자 로그인 실패는 세 경우 모두 동일한 `ATH0101` 응답이며, 관리자 계정은 고객 `POST /api/v1/auth/login`이 아닌 위 경로를 사용한다. `GET /api/v1/customers/me`의 `permissions`로 FDS 화면 접근을 제어하되 BE 권한 검사를 대체하지 않는다.

**즉시이체 화면 → 행동 이벤트 제출** — FE가 만든 같은 `flowId`를 이벤트 경로·본문과 이체 요청에 사용한다. 아래 FDS 이벤트 API는 아직 미구현이다.

```http
POST /api/v1/fds/behavior-flows/3f1c2b7e-8a4d-4f0e-9c1a-2b6d5e7f8a90/events
Idempotency-Key: 6fa61214-4817-47ad-b08f-5279af78df40
```

```json
{
  "events": [{
    "flowId": "3f1c2b7e-8a4d-4f0e-9c1a-2b6d5e7f8a90",
    "seqNo": 1,
    "eventType": "TRANSFER_ENTER",
    "occurredAt": "2026-11-03T13:00:00+09:00",
    "step": "D-01"
  }]
}
```

```json
{"code":"0000","message":"정상 처리되었습니다.","data":{"flowId":"3f1c2b7e-8a4d-4f0e-9c1a-2b6d5e7f8a90","acceptedCount":1}}
```

위 응답도 신규 API의 제안이다. 이벤트 재전송 시 `Idempotency-Key`와 `(flowId, seqNo)`의 중복 처리를 서버가 확인해야 한다.

**이체 요청 → 정상/보류 분기** — 기존 `POST /api/v1/transfers`의 본문에 선택 `flowId`를 보낸다. `Idempotency-Key`, `Account-Password-Auth-Token`, `Otp-Auth-Token` 헤더는 기존대로 필수다. `flowId`는 이미 P4의 요청 지문에 포함된다.

```json
{
  "withdrawalAccountId": 101,
  "depositAccountNumber": "110222222222",
  "amount": 500000,
  "myPassbookMemo": "이체",
  "recipientPassbookMemo": "입금",
  "flowId": "3f1c2b7e-8a4d-4f0e-9c1a-2b6d5e7f8a90"
}
```

```json
{
  "code": "0000",
  "message": "정상 처리되었습니다.",
  "data": {
    "status": null,
    "transactionNumber": null,
    "transferredAt": null,
    "errorCode": null,
    "errorMessage": null,
    "withdrawalBalanceAfter": null,
    "transferHold": {
      "transferHoldId": 123,
      "status": "HELD",
      "releaseAt": null,
      "cancellableUntil": null
    }
  }
}
```

정상 이체도 같은 `TransferResponse` 형식이며 `transferHold`는 null이다.

```json
{"code":"0000","message":"정상 처리되었습니다.","data":{"status":"SUCCESS","transactionNumber":"202611030000001","transferredAt":"2026-11-03T13:02:00","errorCode":null,"errorMessage":null,"withdrawalBalanceAfter":1000000}}
```

위 보류 응답은 **기존 중첩 DTO 구조를 따른 `ON_APPROVAL` 예상값**이다. 실제 FDS 응답은 P4 구현 뒤 확인한다. FE는 `data.status`에 `HELD`가 온다고 가정하지 않고 `data.transferHold?.status`로 보류를 구분한다. `SUCCESS`는 기존 이체 완료 화면, 보류는 승인 대기 안내, `ERROR`는 실패 안내로 구분한다. 행동 이벤트 전송 실패를 이체 성공이나 실패로 오인하지 않는다.

**관리자 화면 → 조회·콜·조치** — 관리자 세션으로 `GET /api/v1/admin/fds/holds`와 `GET /api/v1/admin/fds/holds/{transferHoldId}`를 호출해 서버 A/B 근거와 AI 추가 발견을 별도 영역에 표시한다. 두 조회와 콜 기록에는 `X-Access-Reason`을 전송한다. 현행 경로는 콜 결과를 먼저 기록한 뒤 승인하거나 거절한다. #946의 합성 위험 계좌 `MATCH` 전화 없는 거절은 §6-0의 P4 인계와 API 계약 확정 후에만 예외로 추가한다. 다음은 **경로·콜 enum이 결정된 신규 FDS API의 미구현 예시**이며, P4 실행 결과를 담는 응답 필드는 P4 인계 대기다.

```http
GET /api/v1/admin/fds/holds?page=0&size=10&holdStatus=HELD
X-Access-Reason: INCIDENT
```

```json
{"code":"0000","message":"정상 처리되었습니다.","data":{"page":0,"size":10,"totalCount":1,"totalPages":1,"items":[{"transferHoldId":123,"heldAt":"2026-11-03T13:02:00+09:00","holdStatus":"HELD","policyCode":"A","investigationStatus":"QUEUED","amount":500000,"recipientAccountMasked":"***-**-1234"}]}}
```

```http
GET /api/v1/admin/fds/holds/123
X-Access-Reason: INCIDENT
```

```json
{"code":"0000","message":"정상 처리되었습니다.","data":{"transferHoldId":123,"holdStatus":"HELD","amount":500000,"recipientAccountMasked":"***-**-1234","serverPolicy":{"policyCode":"A","count10m":3,"amount10m":1500000,"hesitationInWindow":true,"newRecipientAtWindowStart":true},"behaviorSummary":{"hesitationWarning":true},"investigationStatus":"COMPLETED","secondaryFindings":[{"code":"REGULAR_SPLIT_AMOUNTS","evidenceTransferIds":[101,102],"confidence":"OBSERVED"}],"callRecords":[]}}
```

`investigationStatus=QUEUED`·`RUNNING`·`FAILED`여도 서버 판정 근거와 콜 결과가 있으면 관리자 수동 검토가 가능하다. 목록·상세의 고객정보와 계좌번호는 필요한 범위만 마스킹해 표시한다.

```http
POST /api/v1/admin/fds/holds/123/call-record
Idempotency-Key: 7fa61214-4817-47ad-b08f-5279af78df41
X-Access-Reason: INCIDENT
```

```json
{"outcome":"CUSTOMER_CONFIRMED","note":"고객에게 송금 목적을 확인함"}
```

```json
{"code":"0000","message":"정상 처리되었습니다.","data":{"callRecordId":501,"recordedAt":"2026-11-03T14:00:00+09:00"}}
```

```http
POST /api/v1/admin/fds/holds/123/approve
Idempotency-Key: 8fa61214-4817-47ad-b08f-5279af78df42
```

```json
{"code":"0000","message":"정상 처리되었습니다.","data":{"transferHoldId":123,"holdStatus":"EXECUTED","transactionNumber":"202611030000001","errorCode":null}}
```

승인 실행이 실패하면 HTTP 요청 처리 성공과 기표 성공을 구분한다. 다음 `FAILED` 응답도 P4 결과 계약을 확인해야 하는 **제안 예시**다.

```json
{"code":"0000","message":"정상 처리되었습니다.","data":{"transferHoldId":123,"holdStatus":"FAILED","transactionNumber":null,"errorCode":"LMT0001"}}
```

관리자가 거절을 선택한 경우에는 승인 API를 호출하지 않고 별도 경로로 보낸다.

```http
POST /api/v1/admin/fds/holds/123/reject
Idempotency-Key: 9fa61214-4817-47ad-b08f-5279af78df43
```

```json
{"reasonCode":"CUSTOMER_DENIED","note":"본인 요청이 아님을 확인"}
```

```json
{"code":"0000","message":"정상 처리되었습니다.","data":{"transferHoldId":123,"holdStatus":"REJECTED"}}
```

**화면 오류 분기 예시** — 세션 만료는 로그인 화면, 관리자 권한 부족은 접근 거부, 고객 콜 결과 누락은 승인 전 안내, 종료된 보류는 상세 재조회로 처리한다. 오류 본문은 공통 `ErrorResponse` 형식이다.

```json
{"code":"CMN0101","message":"인증정보가 없거나 세션이 만료되었습니다.","data":null}
```

```json
{"code":"CMN0102","message":"해당 자원에 접근할 권한이 없습니다.","data":null}
```

```json
{"code":"FDS0302","message":"고객 확인 결과를 기록한 뒤 승인할 수 있습니다.","data":null}
```

```json
{"code":"FDS0301","message":"현재 상태에서는 요청을 처리할 수 없습니다.","data":null}
```

관리자 권한 없음은 `CMN0102`, 콜 결과가 없어 승인할 수 없음은 제안 코드 `FDS0302`, 이미 종료된 보류의 재조치는 제안 코드 `FDS0301`로 구별한다. 승인 후 응답 유실 시에는 새 이체를 시작하지 않고 보류 상세를 다시 조회한다. 상세 필드·오류·내부 Agent API는 [FDS API 명세 초안](p6_fds_api_spec.md)에 적었다.

## 8. 합의·인계 체크리스트

| 확인할 결정 | 담당 | 현재 상태 |
|---|---|---|
| FE `flowId` 생성·새 흐름·새로고침·만료, 이벤트 재전송 | P6 FE·BE | §2·§3 결정 완료; 구현 대기 |
| 이벤트 제출 API 필드·시각·크기·오류 응답 및 화면 연결 | P6 FE·BE | 경로·입력 제한·재전송 결정 완료; 구현 대기 |
| 실제 `HELD` 응답 필드·승인 실행 실패·응답 유실 뒤 결과 조회 | P4 → P6 | 공용 보류 저장·실행 부품 #838(#772 PR-a)은 11/3 머지 완료. `TransferResponse.transferHold` 중첩 DTO는 구현됨. FDS `ON_APPROVAL` 실제 응답·추가 필드 필요 여부와 결과 조회 형태는 P4 인계 대기 |
| 보류 원본·상태 `transfer.api` 조회 | P4 → P6 | `transfer_seam.md` §10의 P4 소유 경계는 확정; 공개 조회 메서드·반환 필드 구현 인계 대기 |
| #946 위험 계좌 데이터셋·거절 인계 | P6 스키마·`qa-seed` 시드·검증·전환, P4 보류 조회·거절 계약 | §6-0의 합성 데이터 버전·전환 잠금은 P6 시드 배포 경로에 반영하고, 기존 `findForFds` 확장 필드·`RISK_ACCOUNT_MATCH` 저장·승인 선점 순서를 P4와 합의 후 구현. P4 수락·동시성 검증 대기 |
| Agent Tool용 이체 목록·수취인 집계 `transfer.api` 조회 | P4 → P6 | §5와 API 명세 §3·§4의 24시간·최대 100건 목록(`refId`·금액·시각·마스킹 수취계좌, `hasMore`), 최초 SUCCESS 시각, 현재 보류 건 출금계좌 외 다른 출금계좌 수(DISTINCT)는 기존 `RecentTransferQuery`의 건수·합계·존재 여부만으로 만들 수 없음. P4 공개 조회 계약·성능 검증 인계 대기; P6은 `transfer` 테이블 직접 조회 금지 |
| 고객 취소·`ON_APPROVAL` 만료 종료 신호 | P4·P6 | `transfer_seam.md` §10 미결. 이 문서 §5-1 `TransferHoldClosed`는 P6 제안이며 P4 합의 전; 리스너 미구현 |
| `TransferSettled.flowId` 성공 이벤트와 `refId` 멱등 저장 | P4 → P6 | `transfer_seam.md` §9-2 연결 순서·`refId` 멱등 규칙 확정; P4 필드 PR 머지 뒤 P6 리스너 연결. P6 저장·조회 부품은 선행 가능 |
| 내부 Tool 인증·네트워크·CSRF·권한 | P6·인프라 | §6-2의 환경변수 주입·같은 VM 브리지·외부 HTTPS·내부 HTTP 결정; 실제 VM 설정 검증·코드 구현 대기 |
| FDS 관리자 권한 matcher·사유·감사·콜 결과와 화면 API 연결 | P6 FE·BE | §6의 권한·CSRF·사유·감사·콜 결과 4종과 승인 허용 조건 결정; 코드·화면 연결 대기 |
| 관리자 승인·거절의 PENDING 조치와 감사 복구 | P6 FE·BE, P4 결과 조회 인계 | §6-1의 사전 내구화·멱등 재생 계약 작성; 조치 행 스키마·P4 결과 대조 필드·장애 주입 검증 대기. 상태만으로 행위자·사유를 추정하지 않음 |
| 조사 작업 재시도·보고서 멱등·AI 장애 시 수동 검토 화면 | P6 FE·BE·AI | §5의 조사 ID 멱등·3회 재시도·시도별 60초(대기 제외)/Tool 5초 제한·보고서 상한·수동 검토 원칙 결정; 코드·화면 검증 대기 |

P4 인계·합의 대기 항목은 실제 계약이 정해지면 상태와 관련 PR을 갱신한다. P6이 결정한 항목도 구현·검증 전에는 완료로 표시하지 않는다. #855는 이벤트·피처 계약을 정책 사례와 최종 대조한다.
