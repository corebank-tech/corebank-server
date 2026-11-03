# FDS API 명세 초안 (#854)

> **상태: 구현 전 검토 초안.** 첨부된 노션 API 명세의 속성 8종과 본문 순서를 따른다. P6 신규 경로는 외부 호출 기준 `/api/v1/fds/**`·`/api/v1/admin/fds/**`·`/api/v1/internal/fds/**`로 결정했으나 아직 구현되지 않았다. 전체 연동 책임과 미결 사항은 [연동 계약](p6_fds_integration_contract.md), 보류·실행의 정본은 [이체 계약 §9](transfer_seam.md#9-훅-c--이체-보류-771)이다.
> **오류코드:** 기존 `CMN`·`TRF`·`OTP`·`APW`는 재사용하고 FDS 고유 조건만 [api_conventions.md §4-17](../api_conventions.md#4-17-fds-이상거래탐지보류-사건-조사--p6-854)에 등록했다. 새 코드의 Java 구현과 관리자 권한 설정은 별도 구현 작업이다. 아래 성공 예시는 모두 HTTP 200·`code="0000"`; 오류는 `{ "code": "...", "message": "...", "data": null }`이다.
> **인증 계약 결정:** 고객 API는 로그인 고객 세션, 관리자 조회는 기존 `CUSTOMER_READ`, 콜·승인·거절은 기존 `CUSTOMER_WRITE`를 쓴다. 관리자 목록·상세·콜 기록은 `X-Access-Reason`과 감사 기록이 필수다. 관리자 변경은 기존 CSRF 보호를 유지한다. `/admin/fds/**` matcher는 기본 `denyAll`보다 앞에 11/7 이후 구현한다. Agent 내부 API는 고객·관리자 세션을 받지 않고 서비스 Bearer 토큰·전용 stateless 보안 체인·사설망 접근 제한을 사용한다. 발표 VM에서는 Git 밖의 접근 제한된 `env_file`로 양 서비스에 토큰 환경변수를 주입하고, 동일 Compose 사설 브리지로 연결한다. Agent·BE 직접 포트와 내부 API 경로는 외부에 공개하지 않는다. 외부 HTTPS는 프록시에서 종료하고 VM 내부 브리지는 HTTP, 호스트 분리 시 내부 TLS를 적용한다. 구현·배포 전에는 열지 않는다. 상세 결정은 [연동 계약 §6](p6_fds_integration_contract.md#6-관리자-api-계약-초안)을 따른다.

## 1. 행동 이벤트 일괄 제출

카테고리: FDS
설명: 로그인 고객의 즉시이체 행동 이벤트를 `flowId`별로 저장한다. 서버가 소유권·순서·시각을 검증한다.
Method: POST
URL: `/api/v1/fds/behavior-flows/{flowId}/events`
사용자: 유저
인증 필요: Y
Idempotency-Key: Y
기타: `Idempotency-Key`는 같은 이벤트 묶음 재전송에 쓴다. `(flowId, seqNo)`도 유니크다. 계좌번호·메모·인증 토큰 원문을 받지 않는다. 한 흐름 최대 100개, 한 요청 본문 최대 32 KiB, 첫 서버 수신부터 30분 유효. 아직 미구현.

### 요구사항 ID

#854 · #827, 명세서 §4

### Path parameter

| key | 설명 | value 타입 | 옵션 | Nullable | 예시 |
|---|---|---|---|---|---|
| `flowId` | FE가 이체 시도마다 만든 흐름 ID | String(UUID) | 이체 요청의 `flowId`와 동일 | X | `"3f1c2b7e-8a4d-4f0e-9c1a-2b6d5e7f8a90"` |

### Request

| key | 설명 | value 타입 | 옵션 | Nullable | 예시 |
|---|---|---|---|---|---|
| `events` | 시간순 행동 이벤트 | Array | 1건 이상; 흐름 전체 최대 100건, 7종 반복 가능 | X | `[{...}]` |
| `events[].flowId` | 흐름 ID | String(UUID) | 각 이벤트에 유지; 경로 값과 다르면 묶음 전체 `CMN0001` | X | `"3f1c..."` |
| `events[].seqNo` | 흐름 안에서 증가하는 순번 | Integer | 1부터 빈 번호 없이 증가, `(flowId, seqNo)` 중복 방지 | X | `1` |
| `events[].eventType` | 행동 유형 | String(ENUM) | 아래 7종 | X | `"TRANSFER_ENTER"` |
| `events[].occurredAt` | FE 발생 시각 | String(DateTime) | ISO-8601 오프셋 포함; 서버 수신보다 최대 30분 이전·5분 이후 | X | `"2026-11-03T13:00:00+09:00"` |
| `events[].step` | 화면 단계 | String(ENUM) | `D-01`, `D-02` 제안 | X | `"D-01"` |
| `confirmActiveSeconds` | 보이는 확인 화면의 활성 체류 | Integer | 0~1800초, `performance.now()` 경과값. `SUBMIT_REQUESTED`를 담은 최종 배치에서만 선택해 흐름당 한 번 확정; 서버는 범위와 확인 화면 진입·이탈 쌍을 검사 | O | `65` |

`eventType`: `TRANSFER_ENTER`, `RECIPIENT_CONFIRMED`, `AMOUNT_EDITED`, `STEP_BACK`, `CONFIRM_ENTER`, `CONFIRM_LEAVE`, `SUBMIT_REQUESTED`.

**Example**

```json
{"events":[{"flowId":"3f1c2b7e-8a4d-4f0e-9c1a-2b6d5e7f8a90","seqNo":1,"eventType":"TRANSFER_ENTER","occurredAt":"2026-11-03T13:00:00+09:00","step":"D-01"},{"flowId":"3f1c2b7e-8a4d-4f0e-9c1a-2b6d5e7f8a90","seqNo":2,"eventType":"RECIPIENT_CONFIRMED","occurredAt":"2026-11-03T13:00:15+09:00","step":"D-01"},{"flowId":"3f1c2b7e-8a4d-4f0e-9c1a-2b6d5e7f8a90","seqNo":3,"eventType":"CONFIRM_ENTER","occurredAt":"2026-11-03T13:00:30+09:00","step":"D-02"},{"flowId":"3f1c2b7e-8a4d-4f0e-9c1a-2b6d5e7f8a90","seqNo":4,"eventType":"CONFIRM_LEAVE","occurredAt":"2026-11-03T13:01:35+09:00","step":"D-02"},{"flowId":"3f1c2b7e-8a4d-4f0e-9c1a-2b6d5e7f8a90","seqNo":5,"eventType":"SUBMIT_REQUESTED","occurredAt":"2026-11-03T13:01:40+09:00","step":"D-02"}],"confirmActiveSeconds":65}
```

### Query parameter

없음

### Response

| key | 설명 | value 타입 | 옵션 | Nullable | 예시 |
|---|---|---|---|---|---|
| `code` | 성공 코드 | String | `0000` | X | `"0000"` |
| `message` | 응답 메시지 | String | 공통 메시지 | X | `"정상 처리되었습니다."` |
| `data.flowId` | 저장한 흐름 ID | String(UUID) |  | X | `"3f1c..."` |
| `data.acceptedCount` | 이번 요청에 새로 저장한 이벤트 수 | Integer | 재전송이면 0 가능 | X | `5` |

**Example**

```json
{"code":"0000","message":"정상 처리되었습니다.","data":{"flowId":"3f1c2b7e-8a4d-4f0e-9c1a-2b6d5e7f8a90","acceptedCount":5}}
```

### Status

| status | response content |
|---|---|
| `200` | 이벤트 저장 성공; 같은 키·본문 재전송은 저장된 응답 반환, 다른 키로 동일 이벤트 재전송 시 새 저장 0건 |
| `400` | `[CMN0001]` 필드 형식·지원하지 않는 이벤트 유형 |
| `400` | `[CMN0002]` 필수 필드·헤더 누락 |
| `400` | `[FDS0001]` 번호 누락·역전, 시각·단계 위반 또는 이벤트 수·본문 크기 제한 초과 |
| `401` | `[CMN0101]` 고객 세션 없음·만료 |
| `403` | `[CMN0102]` 다른 고객의 흐름 접근 |
| `409` | `[CMN0301]` 같은 멱등 키의 요청 처리 중 |
| `409` | `[CMN0302]` 같은 멱등 키의 다른 요청 |
| `409` | `[FDS0301]` 만료·마감된 흐름의 새 이벤트, 최종 배치의 체류값과 다른 값 제출 또는 기존 `seqNo`와 내용 충돌 |
| `500` | `[CMN9999]` 예상하지 못한 서버 오류 |

### 비고

이벤트 제출 실패와 이체 실패는 화면에서 구분한다. 행동 정보가 없으면 정책 A의 망설임은 추정하지 않고 정책 B는 계속 검사한다. 30분 유효기간과 이벤트 순서는 서버 수신 시각으로 판정한다. FE는 같은 탭 새로고침에서 `sessionStorage`의 흐름을 복원하고 만료·새 이체·완료·보류 확정 시 지운다. 미전송 행동은 새로고침 후 추정해 재구성하지 않는다. 동일 `(flowId, seqNo)`·동일 내용은 중복 저장 없이 성공하고 다른 내용은 충돌로 거부한다. `confirmActiveSeconds`는 `SUBMIT_REQUESTED`를 담은 최종 배치에서만 선택해 한 번 확정한다. 그 배치에서 누락되면 해당 흐름의 체류값은 미확인으로 확정한다. 최종 배치의 같은 값·같은 이벤트 재전송은 성공, 다른 값 제출이나 최종 배치 이후 새 이벤트는 `FDS0301`로 거부한다. 묶음은 전부 저장하거나 전부 거부하며, 새 이벤트와 재전송이 섞인 경우 `acceptedCount`는 새로 저장한 건수다. 이 값은 시연 시스템의 입력 제한이지 사기 판정 임계값이 아니다.

## 2. 당행 즉시이체의 FDS 확장 필드

카테고리: 이체
설명: 기존 즉시이체 요청에 선택 `flowId`를 연결하고, 정책 성립 시 보류 ID를 반환한다.
Method: POST
URL: `/api/v1/transfers`
사용자: 유저
인증 필요: Y
Idempotency-Key: Y
기타: **기존 API**. `flowId` 본문 필드와 멱등 지문 포함, `data.transferHold` 중첩 보류 응답은 구현됨. FDS `ON_APPROVAL`에서 추가로 필요한 필드는 P4와 합의 대기. `Account-Password-Auth-Token`·`Otp-Auth-Token` 헤더 필수.

### 요구사항 ID

#771 · #772 · #854, [이체 계약 §9](transfer_seam.md#9-훅-c--이체-보류-771)

### Path parameter

없음

### Request

| key | 설명 | value 타입 | 옵션 | Nullable | 예시 |
|---|---|---|---|---|---|
| `withdrawalAccountId` | 출금계좌 ID | Long | 본인 계좌 | X | `101` |
| `depositAccountNumber` | 수취계좌번호 | String | 하이픈 없음 | X | `"110222222222"` |
| `amount` | 이체금액 | Long | 1원 이상 | X | `500000` |
| `myPassbookMemo` | 내 통장 표시 | String | 최대 10자 | O | `"이체"` |
| `recipientPassbookMemo` | 수취 통장 표시 | String | 최대 10자 | O | `"입금"` |
| `flowId` | FE 행동 흐름 ID | String(UUID) | 선택, 최대 64자; 있으면 멱등 지문에 포함 | O | `"3f1c..."` |

**Example**

```json
{"withdrawalAccountId":101,"depositAccountNumber":"110222222222","amount":500000,"myPassbookMemo":"이체","recipientPassbookMemo":"입금","flowId":"3f1c2b7e-8a4d-4f0e-9c1a-2b6d5e7f8a90"}
```

### Query parameter

없음

### Response

| key | 설명 | value 타입 | 옵션 | Nullable | 예시 |
|---|---|---|---|---|---|
| `code` | 성공 코드 | String | `0000` | X | `"0000"` |
| `message` | 응답 메시지 | String | 공통 메시지 | X | `"정상 처리되었습니다."` |
| `data.status` | **기존 이체 결과** | String(ENUM) | `SUCCESS`, `ERROR`, `TIMEOUT`; 보류면 null | O | `"SUCCESS"` |
| `data.transactionNumber` | 거래번호 | String | 기존 계약 | O | `"202611030000001"` |
| `data.transferredAt` | 성공 이체 시각 | String(DateTime) | 성공만 | O | `"2026-11-03T13:02:00"` |
| `data.errorCode` | 기존 이체 오류코드 | String | `ERROR`일 때 | O | `null` |
| `data.errorMessage` | 기존 이체 오류 메시지 | String | `ERROR`일 때 | O | `null` |
| `data.withdrawalBalanceAfter` | 출금 후 잔액 | Long | 성공만 | O | `1000000` |
| `data.transferHold` | 기존 중첩 보류 응답 | Object | 보류 시에만 채워짐; 정상 실행이면 null | O | `{...}` |
| `data.transferHold.transferHoldId` | 보류 ID | Long | 보류일 때 | O | `123` |
| `data.transferHold.status` | 보류 상태 | String | 보류 응답에서는 `HELD`; `transfer.status`와 별개 | O | `"HELD"` |
| `data.transferHold.releaseAt` | 시각 해제 예정 | String(DateTime) | 승인 해제 건은 null | O | `null` |
| `data.transferHold.cancellableUntil` | 취소 가능 종료 시각 | String(DateTime) | 승인 해제 건은 null | O | `null` |

**Example — 기존 중첩 보류 응답 구조에 맞춘 FDS `ON_APPROVAL` 예상값. P4 구현 전이므로 이 방식의 응답은 검증 대기**

```json
{"code":"0000","message":"정상 처리되었습니다.","data":{"status":null,"transactionNumber":null,"transferredAt":null,"errorCode":null,"errorMessage":null,"withdrawalBalanceAfter":null,"transferHold":{"transferHoldId":123,"status":"HELD","releaseAt":null,"cancellableUntil":null}}}
```

### Status

| status | response content |
|---|---|
| `200` | 기존 이체 결과 또는 보류 접수; 둘 다 `code="0000"`. FDS 필수 근거 조회 실패는 `data.status="ERROR"`, `data.errorCode="FDS9001"`이며 기표하지 않음 |
| `400` | `[TRF0003]` 금액 위반, `[TRF0005]` 메모 제한, `[CMN0001]` 형식 오류 |
| `401` | `[CMN0101]` 고객 세션 없음·만료 |
| `403` | `[OTP0101]` OTP 토큰 무효, `[OTP0102]` 거래 내용 불일치, `[APW0102]` 계좌비밀번호 토큰 무효 |
| `409` | `[CMN0301]` 처리 중, `[CMN0302]` 같은 키의 다른 요청 |
| `500` | `[CMN9999]` 이체 결과로 확정하지 못한 예상 밖 서버 오류 |

### 비고

P4가 보류 응답을 확정하기 전까지 제안 필드로 FE를 고정 구현하지 않는다. `HELD`는 `transfer.status` 값이 아니다. 보류 저장 실패는 기존 계약대로 이체 `ERROR`·재인증 경로이며 같은 멱등 키의 자동 재시도를 하지 않는다. 정책의 필수 근거 조회 실패는 `FDS9001`로 이체 `ERROR`를 확정한다. 현재 `TransferExecutionService`의 `BusinessException` 처리와 `TransferController` 응답 경로에 맞춰 HTTP 200·`code="0000"`·`data.status="ERROR"`·`data.errorCode="FDS9001"`을 계약으로 둔다. 오류코드의 Java 구현과 실제 응답은 FDS 훅 C 구현 시 검증한다. 이체 결과 기록 자체에 실패해 예외가 전파되는 경우는 별도 HTTP 500 경로다.

## 3. Agent 거래 이력 조회

카테고리: FDS
설명: 보류 사건 고객에게 허용된 과거 이체만 Agent에 제공한다.
Method: GET
URL: `/api/v1/internal/fds/holds/{transferHoldId}/history`
사용자: 시스템
인증 필요: Y
Idempotency-Key: N
기타: `Authorization: Bearer <Agent 서비스 토큰>` 필수. 전용 stateless 체인·발표 VM의 사설 Compose 브리지로 보호한다. 호스트 분리 시 내부 TLS를 적용한다. `transferHoldId`로 조회 고객을 제한하고 계좌번호를 마스킹한다. 이 Tool은 보류 시각 직전 최대 24시간의 성공 이체를 최신순으로 최대 100건 보여 준다. 100건을 넘어 잘렸으면 `hasMore=true`로 알린다. 수취인과의 전체 과거 거래 여부는 §4의 별도 집계로 확인한다. 아직 미구현.

### 요구사항 ID

#854 · #827, 명세서 §7

### Path parameter

| key | 설명 | value 타입 | 옵션 | Nullable | 예시 |
|---|---|---|---|---|---|
| `transferHoldId` | 보류 ID | Long | 양의 정수 | X | `123` |

### Request

요청 본문 없음

### Query parameter

| key | 설명 | value 타입 | 옵션 | Nullable | 예시 |
|---|---|---|---|---|---|
| `fromAt` | 최근 패턴 조회 시작 | String(DateTime) | 없으면 보류 시각 24시간 전; 보류 시각보다 오래된 24시간 전은 허용하지 않음; 수취인 전체 과거 거래 여부 판정에는 사용하지 않음 | O | `"2026-11-02T13:00:00+09:00"` |

### Response

| key | 설명 | value 타입 | 옵션 | Nullable | 예시 |
|---|---|---|---|---|---|
| `code` | 성공 코드 | String | `0000` | X | `"0000"` |
| `message` | 응답 메시지 | String |  | X | `"정상 처리되었습니다."` |
| `data.transferHoldId` | 보류 ID | Long |  | X | `123` |
| `data.items` | 최근 패턴 조회 거래 | Array | 없으면 `[]`; 보류 시각 이전 최대 24시간·최신순 최대 100건 | X | `[{...}]` |
| `data.hasMore` | 기간 안에 반환하지 못한 거래가 더 있는지 | Boolean | 최대 100건 밖의 거래가 있으면 `true`; 전체 이력 존재 여부와 무관 | X | `false` |
| `data.items[].refId` | 성공 이체 ID | Long | 보고서 근거 ID | X | `101` |
| `data.items[].amount` | 금액, 원 | Long |  | X | `500000` |
| `data.items[].transferredAt` | 이체 시각 | String(DateTime) | ISO-8601 | X | `"2026-11-03T12:55:00+09:00"` |
| `data.items[].recipientAccountMasked` | 마스킹 수취계좌 | String | 원문 미반환 | X | `"***-**-1234"` |

**Example**

```json
{"code":"0000","message":"정상 처리되었습니다.","data":{"transferHoldId":123,"items":[{"refId":101,"amount":500000,"transferredAt":"2026-11-03T12:55:00+09:00","recipientAccountMasked":"***-**-1234"}],"hasMore":false}}
```

### Status

| status | response content |
|---|---|
| `200` | 조회 성공; 거래가 없으면 `items: []` |
| `400` | `[CMN0001]` 조회 조건 형식 오류 |
| `401` | `[CMN0101]` Agent 서비스 토큰 없음·무효 |
| `403` | `[CMN0102]` Tool 호출 권한 없음 |
| `404` | `[FDS0201]` 보류 사건 없음 또는 공개 불가 |
| `500` | `[FDS9001]` 근거 이력 조회 실패 |

## 4. Agent 수취인 맥락 조회

카테고리: FDS
설명: 보류 사건 고객의 수취인 관계와 다른 출금계좌 송금 맥락을 추가 조사한다.
Method: GET
URL: `/api/v1/internal/fds/holds/{transferHoldId}/recipient-context`
사용자: 시스템
인증 필요: Y
Idempotency-Key: N
기타: `Authorization: Bearer <Agent 서비스 토큰>` 필수. 전용 stateless 체인·발표 VM의 사설 Compose 브리지로 보호한다. 호스트 분리 시 내부 TLS를 적용한다. P4 보류 원본·상태 조회 계약 선행. 해당 수취인과의 과거 성공 이체 존재 여부는 최근 이력 Tool의 조회 기간과 무관하게 전체 과거 성공 이력의 좁은 집계로 조회한다. 거래 목록은 반환하지 않는다. 최근 이력 Tool에서 거래가 없다는 이유만으로 신규 수취인이나 사기를 판정하지 않는다. 아직 미구현.

### 요구사항 ID

#854 · #827, 명세서 §7

### Path parameter

| key | 설명 | value 타입 | 옵션 | Nullable | 예시 |
|---|---|---|---|---|---|
| `transferHoldId` | 보류 ID | Long | 양의 정수 | X | `123` |

### Request

요청 본문 없음

### Query parameter

없음

### Response

| key | 설명 | value 타입 | 옵션 | Nullable | 예시 |
|---|---|---|---|---|---|
| `code` | 성공 코드 | String | `0000` | X | `"0000"` |
| `message` | 응답 메시지 | String |  | X | `"정상 처리되었습니다."` |
| `data.transferHoldId` | 보류 ID | Long |  | X | `123` |
| `data.hasSucceededBeforeWindow` | 반복 묶음 첫 성공 이체 이전의 동일 수취인 성공 이력 | Boolean | 전체 과거 이력 조회; `RecentTransferQuery.hasSucceededBefore`와 같은 기준 | X | `false` |
| `data.firstSucceededAt` | 해당 고객의 모든 출금계좌에서 이 수취인으로 보낸 최초 SUCCESS 시각 | String(DateTime) | 성공 이력이 전혀 없으면 null. `hasSucceededBeforeWindow=false`여도 반복 묶음 안에 성공 이력이 있으면 시각을 반환 | O | `"2026-11-03T12:55:00+09:00"` |
| `data.otherWithdrawalAccountCount` | 현재 보류 건의 출금계좌를 제외하고, 동일 수취인에게 성공 이체한 서로 다른 출금계좌 수 | Integer | 고객의 전체 이력에서 `withdrawalAccountId` DISTINCT 기준; 이체 건수가 아님 | X | `0` |

**Example**

```json
{"code":"0000","message":"정상 처리되었습니다.","data":{"transferHoldId":123,"hasSucceededBeforeWindow":false,"firstSucceededAt":"2026-11-03T12:55:00+09:00","otherWithdrawalAccountCount":0}}
```

### Status

| status | response content |
|---|---|
| `200` | 조회 성공; 이력이 없어도 성공 응답 |
| `400` | `[CMN0001]` 보류 ID 형식 오류 |
| `401` | `[CMN0101]` Agent 서비스 토큰 없음·무효 |
| `403` | `[CMN0102]` Tool 호출 권한 없음 |
| `404` | `[FDS0201]` 보류 사건 없음 또는 공개 불가 |
| `500` | `[FDS9001]` 수취인 맥락 조회 실패 |

## 5. Agent 조사 보고서 제출

카테고리: FDS
설명: Agent의 추가 발견 사항을 조사 작업에 멱등 저장한다. 보류 해제·승인은 수행하지 않는다.
Method: PUT
URL: `/api/v1/internal/fds/investigations/{investigationId}/report`
사용자: 시스템
인증 필요: Y
Idempotency-Key: Y
기타: `Authorization: Bearer <Agent 서비스 토큰>` 필수. 전용 stateless 체인·발표 VM의 사설 Compose 브리지로 보호하고 호스트 분리 시 내부 TLS를 적용한다. 이 경로에서만 CSRF 제외. 본문 최대 16 KiB. 같은 조사 ID·같은 본문은 재전달해도 보고서 1개, 다른 본문은 충돌. 실제 근거 이체 ID를 BE에서 검증한다. 조사 작업은 같은 ID로 총 3회(즉시·1분 뒤·5분 뒤) 시도한다. **60초는 각 시도의 Agent 실행 제한이며 재시도 대기 시간은 제외한다.** 각 Tool HTTP 호출 제한은 5초다. 최종 실패는 **P6 조사 상태만 `FAILED`**, P4 이체 보류 상태는 `HELD`를 유지한다. `HELD`는 `transfer.status`가 아니다. 아직 미구현.

### 요구사항 ID

#854 · #827, 명세서 §7

### Path parameter

| key | 설명 | value 타입 | 옵션 | Nullable | 예시 |
|---|---|---|---|---|---|
| `investigationId` | 조사 작업 ID | String | 보류 ID당 처음 생성한 값 재사용 | X | `"inv-123"` |

### Request

| key | 설명 | value 타입 | 옵션 | Nullable | 예시 |
|---|---|---|---|---|---|
| `transferHoldId` | 보류 ID | Long | 조사 작업과 일치 | X | `123` |
| `status` | 제출된 조사 결과 | String(ENUM) | `COMPLETED`만 허용. 시도 실패는 보고서를 제출하지 않고, 최종 `FAILED`는 BE가 조사 작업에 기록 | X | `"COMPLETED"` |
| `secondaryFindings` | 서버 보류 사유 외의 발견 | Array | 최대 4건, 없으면 `[]` | X | `[{...}]` |
| `secondaryFindings[].code` | 추가 발견 유형 | String(ENUM) | `REGULAR_SPLIT_AMOUNTS`, `MULTIPLE_SOURCE_ACCOUNTS`, `LIMITED_RECIPIENT_HISTORY`, `BEHAVIOR_TRANSFER_MISMATCH` | X | `"REGULAR_SPLIT_AMOUNTS"` |
| `secondaryFindings[].description` | 근거 설명 | String | 1~300자, 개인정보 최소화 | X | `"반복 금액 패턴"` |
| `secondaryFindings[].evidenceTransferIds` | 조회한 성공 이체 ID | Array<Long> | 최대 10개, 실제 허용 조회 결과만; 집계 근거뿐이면 `[]` | X | `[101,102]` |
| `secondaryFindings[].confidence` | 근거 성격 | String(ENUM) | `OBSERVED`, `INFERRED` | X | `"OBSERVED"` |
| `missingInformation` | 확인하지 못한 정보 | Array<String> | 최대 5개, 각 1~100자; 없으면 `[]` | X | `[]` |
| `recommendedAction` | 관리자 참고 권고 | String(ENUM) | `MANUAL_CUSTOMER_CONFIRMATION`만 허용, 자동 조치 없음 | X | `"MANUAL_CUSTOMER_CONFIRMATION"` |
| `suggestedQuestions` | 관리자 콜 질문 | Array<String> | 최대 3개, 각 1~200자; 없으면 `[]` | X | `["송금 목적을 확인해 주세요."]` |
| `summary` | 조사 요약 | String | 1~500자, 사기 확정 표현 금지 | X | `"반복 금액 패턴 확인"` |

**Example**

```json
{"transferHoldId":123,"status":"COMPLETED","secondaryFindings":[{"code":"REGULAR_SPLIT_AMOUNTS","description":"반복 금액 패턴","evidenceTransferIds":[101,102],"confidence":"OBSERVED"}],"missingInformation":[],"recommendedAction":"MANUAL_CUSTOMER_CONFIRMATION","suggestedQuestions":["송금 목적을 확인해 주세요."],"summary":"서버 보류 사유 외에 반복 금액 패턴 확인"}
```

### Query parameter

없음

### Response

| key | 설명 | value 타입 | 옵션 | Nullable | 예시 |
|---|---|---|---|---|---|
| `code` | 성공 코드 | String | `0000` | X | `"0000"` |
| `message` | 응답 메시지 | String |  | X | `"정상 처리되었습니다."` |
| `data.investigationId` | 저장한 조사 ID | String |  | X | `"inv-123"` |
| `data.status` | 조사 상태 | String(ENUM) |  | X | `"COMPLETED"` |

**Example**

```json
{"code":"0000","message":"정상 처리되었습니다.","data":{"investigationId":"inv-123","status":"COMPLETED"}}
```

### Status

| status | response content |
|---|---|
| `200` | 저장 또는 동일 본문 재전달 |
| `400` | `[FDS0002]` 허용 범위 밖 근거 이체 ID, `[CMN0001]` 본문 형식 오류 |
| `401` | `[CMN0101]` Agent 서비스 토큰 없음·무효 |
| `403` | `[CMN0102]` 제출 권한 없음 |
| `404` | `[FDS0201]` 조사 작업 없음·공개 불가 |
| `409` | `[CMN0301]` 같은 멱등 키 처리 중, `[CMN0302]` 같은 키의 다른 본문, `[FDS0301]` 완료된 조사와 다른 재제출 |
| `500` | `[CMN9999]` 저장 중 예상하지 못한 오류 |

## 6. 관리자 보류 목록 조회

카테고리: FDS
설명: 관리자에게 보류 건과 서버 판정·AI 조사 상태를 페이지로 보여준다.
Method: GET
URL: `/api/v1/admin/fds/holds`
사용자: 관리자
인증 필요: Y
Idempotency-Key: N
기타: `CUSTOMER_READ` 권한. `X-Access-Reason` 유효값 필수, 조회 감사 기록. 수취계좌 마스킹. 아직 미구현.

### 요구사항 ID

#854 · #827, 명세서 §8·§9

### Path parameter

없음

### Request

요청 본문 없음

### Query parameter

| key | 설명 | value 타입 | 옵션 | Nullable | 예시 |
|---|---|---|---|---|---|
| `page` | 페이지 번호 | Integer | 0부터, 기본 0 | O | `0` |
| `size` | 페이지 크기 | Integer | 5·10·20·30·50, 기본 10 | O | `10` |
| `holdStatus` | 보류 상태 필터 | String(ENUM) | P4 상태값과 확정 | O | `"HELD"` |

### Response

| key | 설명 | value 타입 | 옵션 | Nullable | 예시 |
|---|---|---|---|---|---|
| `code` | 성공 코드 | String | `0000` | X | `"0000"` |
| `message` | 응답 메시지 | String |  | X | `"정상 처리되었습니다."` |
| `data.page` | 페이지 번호 | Integer |  | X | `0` |
| `data.size` | 페이지 크기 | Integer |  | X | `10` |
| `data.totalCount` | 전체 건수 | Long |  | X | `1` |
| `data.totalPages` | 전체 페이지 | Integer |  | X | `1` |
| `data.items` | 보류 목록 | Array | 0건이면 `[]` | X | `[{...}]` |
| `data.items[].transferHoldId` | 보류 ID | Long |  | X | `123` |
| `data.items[].heldAt` | 보류 시각 | String(DateTime) |  | X | `"2026-11-03T13:02:00+09:00"` |
| `data.items[].holdStatus` | 보류 상태 | String(ENUM) |  | X | `"HELD"` |
| `data.items[].policyCode` | 서버 A/B 정책 | String |  | X | `"A"` |
| `data.items[].investigationStatus` | AI 조사 상태 | String(ENUM) | `QUEUED`, `RUNNING`, `COMPLETED`, `FAILED` | O | `"QUEUED"` |
| `data.items[].amount` | 이체금액, 원 | Long |  | X | `500000` |
| `data.items[].recipientAccountMasked` | 마스킹 수취계좌 | String |  | X | `"***-**-1234"` |

**Example**

```json
{"code":"0000","message":"정상 처리되었습니다.","data":{"page":0,"size":10,"totalCount":1,"totalPages":1,"items":[{"transferHoldId":123,"heldAt":"2026-11-03T13:02:00+09:00","holdStatus":"HELD","policyCode":"A","investigationStatus":"QUEUED","amount":500000,"recipientAccountMasked":"***-**-1234"}]}}
```

### Status

| status | response content |
|---|---|
| `200` | 목록 조회 성공 |
| `400` | `[CMN0001]` 페이지 형식·상태 필터 오류, `[CMN0005]` 미지원 페이지 크기, `[CMN0007]` 조회 사유 오류 |
| `401` | `[CMN0101]` 관리자 세션 없음·만료 |
| `403` | `[CMN0102]` 조회 권한 없음 |
| `500` | `[CMN9999]` 예상하지 못한 조회 오류 |

## 7. 관리자 보류 상세 조회

카테고리: FDS
설명: 보류 원본·서버 근거·AI 추가 발견·콜 기록을 분리해 보여준다.
Method: GET
URL: `/api/v1/admin/fds/holds/{transferHoldId}`
사용자: 관리자
인증 필요: Y
Idempotency-Key: N
기타: `CUSTOMER_READ` 권한·`X-Access-Reason` 필수, 조회 감사. P4 원본·상태 조회 공개 API 선행. 아직 미구현.

### 요구사항 ID

#854 · #827, 명세서 §8·§9

### Path parameter

| key | 설명 | value 타입 | 옵션 | Nullable | 예시 |
|---|---|---|---|---|---|
| `transferHoldId` | 보류 ID | Long | 양의 정수 | X | `123` |

### Request

요청 본문 없음

### Query parameter

없음

### Response

| key | 설명 | value 타입 | 옵션 | Nullable | 예시 |
|---|---|---|---|---|---|
| `code` | 성공 코드 | String | `0000` | X | `"0000"` |
| `message` | 응답 메시지 | String |  | X | `"정상 처리되었습니다."` |
| `data.transferHoldId` | 보류 ID | Long |  | X | `123` |
| `data.holdStatus` | 보류 상태 | String(ENUM) |  | X | `"HELD"` |
| `data.amount` | 원 단위 금액 | Long | 수정 불가 | X | `500000` |
| `data.recipientAccountMasked` | 마스킹 수취계좌 | String |  | X | `"***-**-1234"` |
| `data.serverPolicy` | A/B·임계값·판정 근거 | Object | AI 발견과 구분 | X | `{...}` |
| `data.behaviorSummary` | 행동 요약 | Object | 없으면 null | O | `{...}` |
| `data.investigationStatus` | AI 조사 상태 | String(ENUM) |  | O | `"COMPLETED"` |
| `data.secondaryFindings` | AI 추가 발견 | Array | 없으면 `[]` | X | `[{...}]` |
| `data.callRecords` | 콜 기록 | Array | 없으면 `[]` | X | `[]` |

**Example**

```json
{"code":"0000","message":"정상 처리되었습니다.","data":{"transferHoldId":123,"holdStatus":"HELD","amount":500000,"recipientAccountMasked":"***-**-1234","serverPolicy":{"policyCode":"A","count10m":3,"amount10m":1500000},"behaviorSummary":{"hesitationWarning":true},"investigationStatus":"COMPLETED","secondaryFindings":[{"code":"REGULAR_SPLIT_AMOUNTS","evidenceTransferIds":[101,102],"confidence":"OBSERVED"}],"callRecords":[]}}
```

### Status

| status | response content |
|---|---|
| `200` | 상세 조회 성공 |
| `400` | `[CMN0001]` ID 형식 오류, `[CMN0007]` 조회 사유 오류 |
| `401` | `[CMN0101]` 관리자 세션 없음·만료 |
| `403` | `[CMN0102]` 조회 권한 없음 |
| `404` | `[FDS0201]` 보류 사건 없음·공개 불가 |
| `500` | `[CMN9999]` 예상하지 못한 조회 오류 |

## 8. 관리자 콜 결과 기록

카테고리: FDS
설명: 관리자가 고객에게 직접 연락한 결과를 기록한다. AI 보고서 없이도 기록할 수 있다.
Method: POST
URL: `/api/v1/admin/fds/holds/{transferHoldId}/call-record`
사용자: 관리자
인증 필요: Y
Idempotency-Key: Y
기타: `CUSTOMER_WRITE` 권한, `X-Access-Reason` 필수. 기존 세션·CSRF 보호를 적용한다. 관리자 ID·시각은 세션·서버에서 정한다. 기존 `AuditLog`와 업무 변경을 함께 커밋한다. 아직 미구현.

콜 결과는 기존 기록을 수정하지 않고 추가한다. 해당 보류 건의 최신 서버 기록 시각 기준 `CUSTOMER_CONFIRMED`일 때만 승인 가능하다. `CUSTOMER_DENIED`는 거절 근거이고 `NO_ANSWER`·`INCONCLUSIVE`는 추가 확인할 때까지 보류를 유지한다.

### 요구사항 ID

#854 · #827, 명세서 §8

### Path parameter

| key | 설명 | value 타입 | 옵션 | Nullable | 예시 |
|---|---|---|---|---|---|
| `transferHoldId` | 보류 ID | Long | 양의 정수 | X | `123` |

### Request

| key | 설명 | value 타입 | 옵션 | Nullable | 예시 |
|---|---|---|---|---|---|
| `outcome` | 고객 확인 결과 | String(ENUM) | `CUSTOMER_CONFIRMED`, `CUSTOMER_DENIED`, `NO_ANSWER`, `INCONCLUSIVE` | X | `"CUSTOMER_CONFIRMED"` |
| `note` | 콜 기록 요약 | String | 1~500자, 개인정보·민감정보 최소화 | X | `"고객에게 송금 목적을 확인함"` |

**Example**

```json
{"outcome":"CUSTOMER_CONFIRMED","note":"고객에게 송금 목적을 확인함"}
```

### Query parameter

없음

### Response

| key | 설명 | value 타입 | 옵션 | Nullable | 예시 |
|---|---|---|---|---|---|
| `code` | 성공 코드 | String | `0000` | X | `"0000"` |
| `message` | 응답 메시지 | String |  | X | `"정상 처리되었습니다."` |
| `data.callRecordId` | 콜 기록 ID | Long |  | X | `501` |
| `data.recordedAt` | 서버 기록 시각 | String(DateTime) |  | X | `"2026-11-03T14:00:00+09:00"` |

**Example**

```json
{"code":"0000","message":"정상 처리되었습니다.","data":{"callRecordId":501,"recordedAt":"2026-11-03T14:00:00+09:00"}}
```

### Status

| status | response content |
|---|---|
| `200` | 기록 성공 또는 동일 멱등 요청 재응답 |
| `400` | `[CMN0001]` 결과·내용 형식 오류, `[CMN0002]` 필수값 누락, `[CMN0007]` 사유 오류 |
| `401` | `[CMN0101]` 관리자 세션 없음·만료 |
| `403` | `[CMN0102]` 기록 권한 없음 |
| `404` | `[FDS0201]` 보류 사건 없음·공개 불가 |
| `409` | `[FDS0301]` 종료 사건에 기록 불가, `[CMN0301]` 처리 중, `[CMN0302]` 멱등 본문 충돌 |
| `500` | `[CMN9999]` 예상하지 못한 저장 오류 |

## 9. 관리자 보류 승인

카테고리: FDS
설명: 관리자 권한과 고객 콜 결과를 확인한 뒤 P4의 `release()`를 호출한다. 보류 원본을 수정하지 않는다.
Method: POST
URL: `/api/v1/admin/fds/holds/{transferHoldId}/approve`
사용자: 관리자
인증 필요: Y
Idempotency-Key: Y
기타: `CUSTOMER_WRITE` 권한과 기존 세션·CSRF 보호. `X-Access-Reason`은 현재 승인 요청의 필수 헤더가 아니다. `ON_APPROVAL`·당일 유효·해당 보류 건의 최신 콜 결과 `CUSTOMER_CONFIRMED` 확인 뒤 P6이 관리자 ID·행위·콜 기록 ID·멱등 키를 PENDING 조치로 먼저 커밋하고 P4 `release()`를 호출한다. 다른 콜 결과 또는 기록이 없으면 `FDS0302`다. 결과 확인 뒤 기존 `AuditLog`를 멱등 기록한다([연동 계약 §6-1](p6_fds_integration_contract.md#6-1-관리자-권한csrf조회-감사-결정)). AI 보고서 완료는 승인 필수 조건이 아니다. 응답 유실 시 결과 재조회가 가능해야 한다. 아직 미구현.

### 요구사항 ID

#854 · #827, 명세서 §8·이체 계약 §9-4

### Path parameter

| key | 설명 | value 타입 | 옵션 | Nullable | 예시 |
|---|---|---|---|---|---|
| `transferHoldId` | 승인할 보류 ID | Long | 양의 정수 | X | `123` |

### Request

요청 본문 없음. 관리자는 저장된 원본 계좌·수취인·금액을 변경할 수 없다.

### Query parameter

없음

### Response

| key | 설명 | value 타입 | 옵션 | Nullable | 예시 |
|---|---|---|---|---|---|
| `code` | 성공 코드 | String | `0000` | X | `"0000"` |
| `message` | 응답 메시지 | String |  | X | `"정상 처리되었습니다."` |
| `data.transferHoldId` | 보류 ID | Long |  | X | `123` |
| `data.holdStatus` | 실행 후 보류 상태 | String(ENUM) | `EXECUTED`, `FAILED` 등 P4 계약 | X | `"EXECUTED"` |
| `data.transactionNumber` | 실행 거래번호 | String | 채번 전 실패면 null | O | `"202611030000001"` |
| `data.errorCode` | 실행 실패 코드 | String | 실패면 코드 | O | `null` |

**Example**

```json
{"code":"0000","message":"정상 처리되었습니다.","data":{"transferHoldId":123,"holdStatus":"EXECUTED","transactionNumber":"202611030000001","errorCode":null}}
```

### Status

| status | response content |
|---|---|
| `200` | 승인 처리 결과; 원본 실행 실패는 `data.holdStatus=FAILED`로 구분하는 안 |
| `400` | `[CMN0001]` ID 형식 오류, `[CMN0002]` 멱등 헤더 누락 |
| `401` | `[CMN0101]` 관리자 세션 없음·만료 |
| `403` | `[CMN0102]` 승인 권한 없음 |
| `404` | `[FDS0201]` 보류 사건 없음·공개 불가 |
| `409` | `[FDS0302]` 승인 가능한 콜 결과 없음, `[FDS0301]` 승인할 수 없는 보류 상태·만료, `[CMN0301]` 처리 중, `[CMN0302]` 멱등 본문 충돌 |
| `500` | `[CMN9999]` 예상하지 못한 승인 처리 오류 |

### 비고

실행 중 응답 유실의 반환 정책은 P4 결과 조회 계약과 함께 확정한다. 보류 승인 호출에 OTP를 다시 보내지 않는다. `X-Access-Reason`은 응답에 개인정보를 넣는 경우 추가 적용 여부를 검토한다.

## 10. 관리자 보류 거절

카테고리: FDS
설명: 관리자가 사유를 남기고 미실행 보류를 거절한다.
Method: POST
URL: `/api/v1/admin/fds/holds/{transferHoldId}/reject`
사용자: 관리자
인증 필요: Y
Idempotency-Key: Y
기타: `CUSTOMER_WRITE` 권한과 기존 세션·CSRF 보호. `X-Access-Reason`은 현재 거절 요청의 필수 헤더가 아니다. P6이 관리자 ID·행위·거절 사유·콜 기록 ID·멱등 키를 PENDING 조치로 먼저 커밋한 뒤 P4 `reject()`를 호출하고, 결과 확인 뒤 기존 `AuditLog`를 멱등 기록한다([연동 계약 §6-1](p6_fds_integration_contract.md#6-1-관리자-권한csrf조회-감사-결정)). 아직 미구현.

### 요구사항 ID

#854 · #827, 명세서 §8·이체 계약 §9-4

### Path parameter

| key | 설명 | value 타입 | 옵션 | Nullable | 예시 |
|---|---|---|---|---|---|
| `transferHoldId` | 거절할 보류 ID | Long | 양의 정수 | X | `123` |

### Request

| key | 설명 | value 타입 | 옵션 | Nullable | 예시 |
|---|---|---|---|---|---|
| `reasonCode` | 관리자 거절 사유 | String(ENUM) | 현재 계약은 `CUSTOMER_DENIED`만 허용하며 해당 보류 건의 최신 콜 결과가 동일해야 함 | X | `"CUSTOMER_DENIED"` |
| `note` | 보조 설명 | String | 최대 500자, 개인정보 최소화 | O | `"본인 요청이 아님을 확인"` |

**Example**

```json
{"reasonCode":"CUSTOMER_DENIED","note":"본인 요청이 아님을 확인"}
```

### Query parameter

없음

### Response

| key | 설명 | value 타입 | 옵션 | Nullable | 예시 |
|---|---|---|---|---|---|
| `code` | 성공 코드 | String | `0000` | X | `"0000"` |
| `message` | 응답 메시지 | String |  | X | `"정상 처리되었습니다."` |
| `data.transferHoldId` | 보류 ID | Long |  | X | `123` |
| `data.holdStatus` | 보류 상태 | String(ENUM) | `REJECTED` | X | `"REJECTED"` |

**Example**

```json
{"code":"0000","message":"정상 처리되었습니다.","data":{"transferHoldId":123,"holdStatus":"REJECTED"}}
```

### Status

| status | response content |
|---|---|
| `200` | 거절 성공 또는 같은 멱등 요청 재응답 |
| `400` | `[CMN0001]` 사유 형식 오류, `[CMN0002]` 사유·멱등 헤더 누락 |
| `401` | `[CMN0101]` 관리자 세션 없음·만료 |
| `403` | `[CMN0102]` 거절 권한 없음 |
| `404` | `[FDS0201]` 보류 사건 없음·공개 불가 |
| `409` | `[FDS0301]` 이미 실행·거절·만료된 보류, `[CMN0301]` 처리 중, `[CMN0302]` 멱등 본문 충돌 |
| `500` | `[CMN9999]` 예상하지 못한 거절 처리 오류 |

## 11. 구현 전 확정할 항목

**P4 합의 전 종료 이벤트 제안:** 고객 취소(`CANCELLED`) 또는 `ON_APPROVAL` 만료(`EXPIRED`)가 확정되는 같은 트랜잭션에서 P4가 `transfer.api.TransferHoldClosed(transferHoldId, reason, closedAt)`를 발행하고, P6은 중복 전달을 멱등 처리해 조사 작업·재시도를 닫는다. 늦게 도착한 Agent 보고서는 활성 조사 결과로 표시하지 않는다. 관리자 거절은 P6이 직접 처리하므로 이 이벤트에서 제외한다. 이벤트 확정 전까지 이 항목을 구현 완료 계약으로 취급하지 않는다. 상세 예시는 [연동 계약 §5-1](p6_fds_integration_contract.md#5-1-고객-취소만료-종료-이벤트-제안--p4-합의-전)을 따른다.

1. **P4 소유·구현:** 현재 `TransferResponse.transferHold` 중첩 필드와 `TransferHoldResponse`의 `transferHoldId`·`status`·`releaseAt`·`cancellableUntil`은 구현돼 있다. FDS `ON_APPROVAL` 보류의 실제 응답 및 해제 방식 표시가 추가로 필요한지는 P4와 맞춘다. `transfer.api` 원본·상태 조회, 승인 실행 실패·응답 유실 시 결과 조회 계약도 P4가 결정·제공한다. P6은 FDS 관리자 API·FE 화면에 이를 연결하고 응답 유실 시 보류 상세를 재조회한다.
2. §6의 인증 계약대로 **기존 관리자 권한**(`CUSTOMER_READ`·`CUSTOMER_WRITE`)을 재사용한다. 11/7 이후 `/admin/fds/**` matcher와 Agent 서비스 Bearer 토큰·전용 stateless 체인·CSRF 분리·네트워크 접근 제한을 구현하고 테스트한다. `AdminPermission`은 5종 고정이므로 새 권한을 추가하지 않는다. 실제 배포의 비밀값 주입·TLS 경로는 인프라 설정과 대조한다.
3. P6은 확정한 이벤트 입력 제한·재전송 규칙·콜 결과 4종, 새로고침 복원, `confirmActiveSeconds` 검증, 보고서 발견 코드·권고값 및 거절 enum을 구현한다. 각 이벤트의 `flowId`는 경로와 대조하고, 보고서 16 KiB·문자열·배열 상한과 조사 작업 3회·**시도별 실행 60초(대기 제외)**·Tool 호출별 5초 제한을 적용한다. 최근 이력 Tool은 24시간·100건과 `hasMore`를 적용하고, 수취인 전체 과거 거래 여부는 §4의 별도 집계로 확인한다.
4. `FDS` 새 오류코드를 Java `ErrorCode`에 구현할 때 API 명세·[오류코드 마스터](../api_conventions.md#4-17-fds-이상거래탐지보류-사건-조사--p6-854)와 일치시키고 Swagger UI로 실제 응답을 확인한다.
5. 노션 페이지로 옮길 때는 API별로 별도 페이지를 만들고 속성 8종과 본문 섹션 순서를 유지한다. **이 문서의 제안 응답을 구현 완료로 표시하지 않는다.**
