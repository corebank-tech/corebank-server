# 타행이체 API ↔ FE 연동 인계 문서 (PH-33-② → FE B-8)

> **작성**: P4 (이체) · **대상**: FE B-8 (P3)
> **관련 이슈**: #609 · **서버 구현**: #654 ([tasks.md §PH-33-②](tasks.md#ph-33-대외계-①-모의-서버-s1--②-타행-이체-s3), 10/23 머지 예정)
>
> 이 문서는 "서버가 이 모양으로 응답한다"는 약속이다. #654가 이 모양 그대로 구현했다. FE는 이 문서로 목업을 만들고, 머지 뒤 실제 API로 바꾼다.
> 서버가 모양을 바꿔야 하면 **이 문서를 먼저 고치고 P3에 알린다.**

관련 문서: [공통 API 규칙](../api_conventions.md) · [전문 규격](fixed_length_message_spec.md) · [용어집](glossary.md) 9 처리 불명 · 10 조회거래

---

## 1. 세 줄 요약

1. 타행이체는 당행 이체와 **엔드포인트가 따로다** — 은행 목록 `GET /transfers/banks`, 실행 `POST /transfers/interbank`. 당행 이체(`POST /transfers`)와 B-9 화면은 바뀌지 않는다.
2. 결과 상태는 당행과 같은 **`SUCCESS` · `ERROR` · `TIMEOUT`** 셋이다. 미결제(회계 내부 기표)는 화면에 나오지 않는다. `TIMEOUT`은 실패가 아니라 **처리 불명**이고, 재시도 버튼을 두지 않는다.
3. **받는 분 이름을 이체 전에 확인할 수 없다.** 당행처럼 예금주 조회(`GET /transfers/payee`)가 없고, 예금주는 이체 응답(`SUCCESS`)으로만 온다.

---

## 2. 엔드포인트

| Method | URL | 인증 | Idempotency-Key | 비고 |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/transfers/banks` | Y | N | 타행 은행 목록 |
| POST | `/api/v1/transfers/interbank` | Y | **Y** | 타행이체 실행. 계좌 비밀번호 · OTP 토큰 필요 |
| GET | `/api/v1/transfers/{transactionNumber}` | Y | N | **기존 API.** 처리 불명 건의 결과 다시 보기(§7) |

공통 봉투는 `{ "code": "0000", "message": ..., "data": ... }`이고 성공은 항상 HTTP `200`이다([api_conventions.md](../api_conventions.md) §1).

---

## 3. 은행 목록 — `GET /transfers/banks`

```json
{
  "code": "0000",
  "message": "정상 처리되었습니다.",
  "data": [
    { "bankCode": "004", "bankName": "KB국민은행" },
    { "bankCode": "011", "bankName": "NH농협은행" },
    { "bankCode": "020", "bankName": "우리은행" },
    { "bankCode": "081", "bankName": "하나은행" },
    { "bankCode": "090", "bankName": "카카오뱅크" }
  ]
}
```

- 5개이고 `bankCode` 오름차순으로 온다. 화면 순서를 바꾸고 싶으면 FE에서 정렬한다.
- **`bankCode`는 문자열이다.** `"004"`를 숫자로 바꾸면 앞의 `0`이 사라진다.
- **당행(`088`)은 목록에 없다.** B-8은 타행 전용 화면이다. 당행으로 보내는 건 기존 이체 화면이다.
- 은행 목록을 FE 코드에 박아 두지 않는다. 화면을 열 때 이 API로 받는다([tasks.md §FE B-8](tasks.md#fe-b-8-타행이체-입력결과) 완료 기준).

---

## 4. 타행이체 실행 — `POST /transfers/interbank`

### 4-1. 헤더

당행 이체(`POST /transfers`)와 같다.

| 헤더 | 값 |
| --- | --- |
| `Idempotency-Key` | **이체 1건마다 새 UUID v4.** 응답을 못 받아 같은 이체를 다시 보낼 때만 같은 키를 쓴다. 같은 키 · 같은 본문이면 처리가 끝난 뒤에는 처음 응답을 다시 돌려주고, 아직 처리 중이면 `CMN0301`(409)다. 같은 키에 본문이 다르면 `CMN0302`(409)다([api_conventions.md](../api_conventions.md) §7-2) |
| `Account-Password-Auth-Token` | 계좌 비밀번호 인증 뒤 받은 1회용 토큰 |
| `Otp-Auth-Token` | OTP 인증 뒤 받은 1회용 토큰(§4-3) |

### 4-2. 요청 본문

```json
{
  "withdrawalAccountId": 1001,
  "payeeBankCode": "004",
  "depositAccountNumber": "1234567890123",
  "amount": 50000,
  "myPassbookMemo": "생활비",
  "recipientPassbookMemo": "홍길동"
}
```

| 필드 | 타입 | 규칙 |
| --- | --- | --- |
| `withdrawalAccountId` | number | 내 출금계좌 ID. 당행과 같다 |
| `payeeBankCode` | string | §3 목록의 `bankCode` 중 하나 |
| `depositAccountNumber` | string | **숫자만 1~16자리, 하이픈 없이.** 은행마다 자릿수가 달라 당행(12자리)과 검증이 다르다. 16자리는 전문 계좌번호 칸 크기다([전문 규격](fixed_length_message_spec.md) §2) |
| `amount` | number | 1원 이상 정수. 수수료는 0원이다 |
| `myPassbookMemo` | string | 최대 10자. 당행과 같다 |
| `recipientPassbookMemo` | string | 최대 10자. **이모지 등 일부 특수문자는 거부된다** — 상대 은행에 보내는 전문이 한글 2바이트 문자셋이라 담을 수 없는 글자가 있다 |

### 4-3. OTP 발급 · 검증

`POST /otp/issue`의 `transactionType`은 당행과 같은 **`IMMEDIATE_TRANSFER`**를 쓴다. `transactionData`에는 **`payeeBankCode`를 더한다.**

```json
{
  "transactionType": "IMMEDIATE_TRANSFER",
  "transactionData": {
    "withdrawalAccountId": 1001,
    "payeeBankCode": "004",
    "depositAccountNumber": "1234567890123",
    "amount": 50000
  }
}
```

- 발급 · 검증 · 이체 실행의 `transactionData`가 **완전히 같아야** 한다. 하나라도 다르면 `OTP0102`다([otp_integration_guide.md](../otp_integration_guide.md) §8).
- `payeeBankCode`가 들어가서 당행 이체용으로 받은 OTP는 타행이체에 쓸 수 없고, 반대도 마찬가지다.

---

## 5. 응답

이체 요청이 서버에 닿아 처리되면 결과와 상관없이 HTTP `200`이다. **성공 여부는 `data.status`로 본다.** 필드는 당행 응답(`TransferResponse`)에 `payeeBankCode` · `payeeName`을 더한 모양이다.

| 필드 | `SUCCESS` | `ERROR` | `TIMEOUT` |
| --- | --- | --- | --- |
| `status` | `SUCCESS` | `ERROR` | `TIMEOUT` |
| `transactionNumber` | 있음 | 채번 뒤 실패면 있음 · 그 전 실패면 `null` | 있음 — §7 재조회에 쓴다 |
| `transferredAt` | 있음. **오프셋 포함**(`+09:00`, [api_conventions.md](../api_conventions.md) REQ-CMN-016) — 당행 응답은 오프셋이 없으니 같은 파서를 쓰지 않는다 | `null` | `null` |
| `payeeBankCode` | 있음 | 있음 | 있음 |
| `payeeName` | 있음(마스킹, 예 `김*수`) — 상대 은행이 돌려준 이름 | `null` | `null` |
| `withdrawalBalanceAfter` | 있음 | `null` | `null` |
| `errorCode` · `errorMessage` | `null` | 있음(§6) | `null` |

**성공**

```json
{
  "code": "0000",
  "message": "정상 처리되었습니다.",
  "data": {
    "status": "SUCCESS",
    "transactionNumber": "20261023WB0000000123",
    "transferredAt": "2026-10-23T10:30:16+09:00",
    "payeeBankCode": "004",
    "payeeName": "김*수",
    "withdrawalBalanceAfter": 950000,
    "errorCode": null,
    "errorMessage": null
  }
}
```

**실패** — 상대 은행에 수취 계좌가 없음

```json
{
  "code": "0000",
  "message": "정상 처리되었습니다.",
  "data": {
    "status": "ERROR",
    "transactionNumber": "20261023WB0000000124",
    "transferredAt": null,
    "payeeBankCode": "004",
    "payeeName": null,
    "withdrawalBalanceAfter": null,
    "errorCode": "TRF0201",
    "errorMessage": "입금계좌를 찾을 수 없습니다."
  }
}
```

**처리 불명** — 상대 은행 응답이 없음

```json
{
  "code": "0000",
  "message": "정상 처리되었습니다.",
  "data": {
    "status": "TIMEOUT",
    "transactionNumber": "20261023WB0000000125",
    "transferredAt": null,
    "payeeBankCode": "004",
    "payeeName": null,
    "withdrawalBalanceAfter": null,
    "errorCode": null,
    "errorMessage": null
  }
}
```

### 5-1. 상태별 화면

| `status` | 화면 | 버튼 |
| --- | --- | --- |
| `SUCCESS` | 완료 — 받는 분 `payeeName`, 은행, 금액, 이체 후 잔액 | 확인 |
| `ERROR` | 실패 — `errorMessage`. 출금계좌 잔액은 이체 전과 같다(빠져나갔던 돈은 서버가 되돌린 뒤 `ERROR`로 확정한다) | 다시 입력 |
| `TIMEOUT` | **처리 불명** — "상대 은행의 응답이 늦어 결과를 확인하고 있습니다. 이체 내역에서 결과를 확인해 주세요." | 이체 내역으로 이동. **다시 보내기 버튼을 두지 않는다** |

`TIMEOUT`에 재시도 버튼을 두지 않는 이유: 상대 은행이 이미 입금했을 수 있다. 다시 보내면 두 번 입금된다. 결과는 서버가 조회거래로 확인한다([용어집](glossary.md) 9 · 10).

---

## 6. 오류코드

### 6-1. `200` + `status=ERROR`

| `errorCode` | 뜻 | 당행과 |
| --- | --- | --- |
| `TRF0001` | 등록되지 않은 출금계좌 | 같음 |
| `TRF0304` | 거래정지 · 해지 상태의 출금계좌 | 같음 |
| `TRF0303` · `LMT0001` | 잔액 부족 · 출금가능금액 부족 — 잔액이 모자라면 판정 단계에 따라 둘 중 하나가 온다. 화면 처리는 같다 | 같음 |
| `LMT0002` | 1회 이체한도 초과 | 같음 |
| `LMT0003` | 1일 이체한도 초과 | 같음 |
| `OTP0101` · `OTP0102` | OTP 토큰 무효 · 인증한 거래 내용과 요청이 다름 | 같음 |
| `TRF0201` | 입금계좌를 찾을 수 없음 — 상대 은행 응답 `1001` | 같은 코드, 판정 주체만 상대 은행 |
| `TRF0301` | 거래정지 · 해지 상태의 입금계좌 — 상대 은행 응답 `1002` | 같은 코드, 판정 주체만 상대 은행 |
| `TRF0308` | 상대 은행이 처리하지 못함 — 상대 은행 응답 `9999` · `1003`, 또는 상대 은행 장애로 보내지 못함 | **타행 전용 신규** |

상대 은행 응답 `2001`(중복 거래)은 오류로 내려가지 않는다. 같은 일련번호를 이미 처리했다는 뜻일 뿐 입금됐는지는 알 수 없으므로 **`TIMEOUT`으로 내려가고** §7처럼 조회거래로 확정한다([전문 규격](fixed_length_message_spec.md) §3). 당행은 응답이 없어도 같은 이체 전문을 다시 보내지 않으므로 정상 흐름에서는 `2001`이 오지 않는다.

### 6-2. `400` (요청 단계에서 거부)

| `errorCode` | 뜻 | 당행과 |
| --- | --- | --- |
| `TRF0003` | 이체금액 형식 오류 | 같음 |
| `TRF0005` | 통장 표시내용 길이 초과 | 같음 |
| `TRF0006` | 지원하지 않는 은행코드 — §3 목록에 없는 값 | **타행 전용 신규** |
| `TRF0007` | 타행 계좌번호 형식 오류 — 숫자 1~16자리가 아님 | **타행 전용 신규** |
| `TRF0008` | 통장 표시내용에 보낼 수 없는 문자(이모지 등) | **타행 전용 신규** |

`409` `CMN0301` · `CMN0302`(멱등키 충돌)는 당행과 같다.

**타행 전용 코드 4개는 [api_conventions.md](../api_conventions.md) §4 오류코드 마스터에 등록해 확정했다(#654).** 가칭이던 `TRF0401`은 번호 구간 규칙(§3-3, 상태 충돌 `03xx`)에 맞춰 `TRF0308`이 됐다. FE는 코드값으로 분기하지 말고 `errorMessage`를 그대로 보여 주는 것을 권한다.

---

## 7. 처리 불명 건의 결과 다시 보기

1. 이체 응답이 `TIMEOUT`이면 `transactionNumber`를 들고 이체 내역 상세로 보낸다.
2. 상세는 **기존 API** `GET /transfers/{transactionNumber}`다. 서버가 조회거래로 결과를 확인하기 전까지 `status`는 `TIMEOUT`이다. 확인되면 `SUCCESS`나 `ERROR`로 바뀐다.
3. 확인은 서버가 한다. FE가 조회거래를 부르는 API는 없다. FE는 자동 반복 조회(polling)를 하지 않아도 되고, 사용자가 상세를 다시 열거나 새로고침하면 된다.
4. 이체 내역 목록(`GET /transfers`)의 `status` 필터에 `TIMEOUT`이 이미 있다(PH-80, #564).

### 7-1. 상세 · 목록 응답의 타행 건

상세(`GET /transfers/{transactionNumber}`)와 목록(`GET /transfers`)은 당행 건과 타행 건을 같은 모양으로 내려 준다. 타행 건에서 값이 달라지는 필드는 셋이다.

| 필드 | 당행 건 | 타행 건 |
| --- | --- | --- |
| `accountNumber` | 12자리 마스킹 `110******877` | 아래 규칙으로 마스킹. 자릿수가 은행마다 달라 마스킹 모양도 다르다 |
| `payeeName` | 항상 있음(마스킹) | `SUCCESS`면 상대 은행이 돌려준 이름(마스킹). **`ERROR` · `TIMEOUT`이면 `null`** — 예금주를 받지 못했다 |
| `payeeBankCode` | `null` | 있음. **상세에만** 있다 |

**타행 계좌번호 마스킹:** 자릿수를 4로 나눈 몫(나머지 버림)을 `k`라 하면, 앞 `k`자리와 뒤 `k`자리만 남기고 가운데를 `*`로 가린다. 자릿수는 그대로라 항상 절반 이상이 가려진다. 12자리면 당행 규칙과 결과가 같다.

| 자릿수 | 원문 | 응답 |
| --- | --- | --- |
| 16 | `1234567890123456` | `1234********3456` |
| 13 | `1234567890123` | `123*******123` |
| 12 | `110123456877` | `110******877` |
| 10 | `1234567890` | `12******90` |
| 3 이하 | `123` | `***` |

FE는 받은 문자열을 그대로 보여 준다. 마스킹을 FE에서 다시 하거나 풀지 않는다.

---

## 8. 범위 밖

- 이체 전 예금주 확인 — 타행은 예금주를 이체 응답으로만 받는다([전문 규격](fixed_length_message_spec.md) §6)
- 타행 계좌의 즐겨찾기 · 자동이체 · 예약이체 등록
- 타행 수수료 — 0원 고정
- 미결제타점권 — 회계(GL) 내부 기표라 FE 응답에 나오지 않는다
