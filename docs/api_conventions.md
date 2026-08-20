# CoreBank 공통 API 규칙 (응답 형식 · 오류코드 · Enum · 필드명)

> **관리**: P5 (공통기반 · REQ-CMN)
> **대상**: P1~P6 전원
> **아키텍처**: 헥사고날 + Spring Modulith (REQ-NFR-014)
>
> 이 문서는 **응답 형식·오류코드·Enum·필드명의 유일한 원본**입니다.
> 개별 API 명세서가 이 문서와 다르면 개별 명세서를 고칩니다.
> 새 값이 필요하면 이 문서에 등록 요청합니다.

관련 문서: [오류코드 공통 인프라 사용 가이드](error_handling_guide.md)

---

## 0. 공통 규칙 양식 (API 명세서 템플릿)

각 API 명세서는 아래 양식을 그대로 따릅니다.

### 0-1. 페이지 속성 8종 — 순서 고정

| 순서 | 속성 | 타입 | 값 규칙 |
| --- | --- | --- | --- |
| 1 | 카테고리 | Select | `인증` / `OTP` / `계좌` / `조회` / `상품` / `이체` / `예약 이체` / `자동 이체` / `마이페이지` |
| 2 | 설명 | Text | 1~2문장. 무엇을 하는 API인지 + 핵심 제약 |
| 3 | Method | Select | `GET` / `POST` / `PUT` / `PATCH` / `DELETE` |
| 4 | URL | Text | `/api/v1/...` |
| 5 | 사용자 | Select | `유저` / `관리자` / `시스템` |
| 6 | 인증 필요 | Select | `Y` / `N` — 공란 금지 |
| 7 | Idempotency-Key | Select | `Y` / `TBD` / `N` — 공란 금지 |
| 8 | 기타 | Text | 별도 검증 로직·길이 제한 등 |

### 0-2. 본문 섹션 — 순서 고정

```
요구사항 ID
Path parameter      ← 없으면 "없음"
Request             ← 없으면 "요청 본문 없음" / 있으면 표 + Example(JSON)
Query parameter     ← 없으면 "없음"
Response            ← 표 + Example(JSON)
Status
비고                 ← 없으면 생략
```

- Header 섹션은 두지 않습니다. 인증·멱등키는 속성으로 표현하고, 세부 조건은 `기타`에 적습니다.
- Request / Response / parameter 표 컬럼: `key` · `설명` · `value 타입` · `옵션` · `Nullable` · `예시`
- Status 표 컬럼: `status` · `response content`. 오류는 ``[코드]` 설명` 형식으로 적습니다.

### 0-3. 명세서 작성 체크리스트

- [ ] 속성 8종이 모두 채워졌는가 (공란 금지)
- [ ] JSON 성공은 HTTP `200` + `code="0000"` 인가
- [ ] CSV 파일 성공은 HTTP `200` + 명세된 파일 Content-Type인가
- [ ] `data`에 `result` 같은 판정 필드를 넣지 않았는가
- [ ] 오류코드가 §4 마스터에 등록된 것인가
- [ ] 401·403·조회기간·페이지크기 오류에 `CMN` 코드를 썼는가
- [ ] Enum 값이 §5 마스터와 일치하는가
- [ ] 필드명이 §6 통일 규칙과 일치하는가
- [ ] 계좌번호를 하이픈 없이 표기했는가
- [ ] 상태 변경 API에 `Idempotency-Key: Y`를 설정했는가
- [ ] 최종 거래 API가 `otpCode` 대신 `otpAuthToken`을 받는가

---

## 1. 공통 응답 형식 (REQ-CMN-007)

모든 REST API는 성공·실패를 불문하고 아래 구조로 응답합니다.

| key | 설명 | value 타입 | 옵션 | Nullable |
| --- | --- | --- | --- | --- |
| `code` | 응답 코드 | String | 성공은 항상 `"0000"` | X |
| `message` | 응답 메시지 | String | 사용자에게 노출 가능한 문장 | X |
| `data` | 응답 데이터 | Object | 실패 시 `null` | O |

**성공**

```json
{
  "code": "0000",
  "message": "정상 처리되었습니다.",
  "data": { "autoTransferId": 55021 }
}
```

**실패**

```json
{
  "code": "AUT0301",
  "message": "동일 조건의 자동이체가 이미 등록되어 있습니다.",
  "data": null
}
```

### 파일 다운로드 응답 예외

CSV 다운로드 API의 응답 규칙은 다음과 같습니다.

| 결과 | HTTP Status | Content-Type | Response Body |
| --- | --- | --- | --- |
| 성공 | `200` | `text/csv; charset=UTF-8` | CSV 파일 |
| 실패 | `400~599` | `application/json` | 공통 오류 응답 |

성공 응답에는 `code`, `message`, `data`가 존재하지 않습니다. 프론트는 HTTP Status로 성공 여부를 판단하고, 성공 시 응답을 Blob으로 처리하며 실패 시 JSON의 `code`와 `message`를 읽습니다.

### 규칙

1. **성공은 항상 HTTP `200` + `code="0000"`.** `201` · `204`는 사용하지 않습니다.
2. **`data`에 성공/실패를 다시 담지 않습니다.** `data.result = "SUCCESS"/"FAIL"` 패턴은 폐기합니다. 판정값이 데이터로서 의미 있으면 의미 있는 boolean 필드로 표현합니다.
   ```json
   { "code": "0000", "message": "사용 가능한 아이디입니다.", "data": { "available": true } }
   ```
3. `message`는 서버 Enum이 단일 관리합니다. 화면에 문자열을 하드코딩하지 않습니다. (REQ-CMN-008)
4. 응답에 스택트레이스·SQL·내부 경로를 노출하지 않습니다. (REQ-NFR-017)

### 구현 형태

```java
// 성공 응답 전용 봉투. 실패 응답은 ErrorResponse(ApiExceptionHandler)가 전담한다
public record ApiResponse<T>(String code, String message, T data) {

    private static final String SUCCESS_CODE = "0000";
    private static final String SUCCESS_MESSAGE = "정상 처리되었습니다.";

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(SUCCESS_CODE, SUCCESS_MESSAGE, data);
    }
    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(SUCCESS_CODE, message, data);
    }
    public static ApiResponse<Void> success() {
        return new ApiResponse<>(SUCCESS_CODE, SUCCESS_MESSAGE, null); // 직렬화 시 {} 로 변환
    }
}
```

### 예외 규정 — 실패 응답에 `data`를 허용하는 3건

원칙은 실패 시 `data: null`이지만, 잔여 시도 횟수는 화면이 반드시 표시해야 하는 값이라 예외로 허용합니다.

| API | 실패 시 `data` |
| --- | --- |
| `POST /api/v1/otp/verify` | `errorCount`, `remainingAttempts` |
| `POST /api/v1/accounts/{accountId}/password/verify` | `errorCount`, `remainingAttempts` |
| `POST /api/v1/auth/login` | `errorCount`, `remainingAttempts` |

이 3건 외에는 실패 응답의 `data`가 항상 `null`입니다. 새 예외가 필요하면 이 표에 등록해야 합니다.

---

## 2. HTTP 상태코드 사용 규칙

| status | 사용 상황 |
| --- | --- |
| `200` | 모든 성공. 조회 결과 0건도 `200` + 빈 배열 |
| `400` | 입력값 검증 실패, 정책 위반(한도 초과 등) |
| `401` | 인증정보 없음 또는 세션 만료 |
| `403` | 인증은 됐으나 권한 없음, 인증 토큰 무효, 사용자 계정 또는 인증 수단 잠금 |
| `404` | 리소스 없음 |
| `409` | 상태 충돌, 중복 등록, 멱등키 재요청 처리 중 |
| `500` | 서버 내부 오류 |

`201` · `204` · `422`는 사용하지 않습니다.

---

## 3. 오류코드 체계 (REQ-CMN-008)

### 3-1. 형식

```
<도메인 영문 3자리><일련번호 4자리>     예) AUT0301, CMN0101
```

- 4자리 접두어 금지 — `AUTH` · `ACCT` 폐기
- 문장형 코드 금지 — `ACCOUNT_NOT_FOUND` 형태 폐기
- 배정표에 없는 접두어 사용 금지

### 3-2. 접두어 배정 — 누가 어떤 코드를 쓰는가

| 접두어 | 도메인 | 담당 | 근거 요구사항 |
| --- | --- | --- | --- |
| `CMN` | 공통기반 | **P5** | REQ-CMN |
| `ATH` | 인증·회원 | P6 | REQ-AUTH |
| `OTP` | OTP 거래인증 | P6 | REQ-OTP |
| `APW` | 계좌비밀번호 | P6 | REQ-ACCT-006~009 |
| `MYP` | 마이페이지·알림 | P6 | REQ-MYPG |
| `ACC` | 계좌 | P2 | REQ-ACCT-001~005·010~015 |
| `INQ` | 조회(계좌·거래내역) | P2 | REQ-INQR |
| `PRD` | 상품·상품가입 | P3 | REQ-PRDT |
| `SCD` | 예약이체 | P5 | REQ-SCD |
| `TRF` | 이체·원장 | P4 | REQ-TRSF-001~009·014~023·028·030~036 |
| `FAV` | 자주 쓰는 계좌 | P4 | REQ-TRSF-026·034~036 |
| `LMT` | 이체한도 | P1 | REQ-TRSF-010~013·024·025·029 |
| `AUT` | 자동이체 | **P5** | REQ-AUTO |

**배정 원칙**

- 한 접두어는 한 사람만 소유합니다. 담당자가 다르면 요구사항 ID가 같아도 쪼갭니다.
  - `REQ-ACCT` → P2(계좌) `ACC` / P6(계좌비밀번호) `APW`
  - `REQ-TRSF` → P4(이체) `TRF` / P1(한도) `LMT`
- `REQ-AUTH`를 3자리로 줄이면 `AUT`(자동이체)와 충돌하므로 **`ATH`** 를 씁니다.
- 코드 소유와 던지는 위치는 다를 수 있습니다. 이체 실행 중 한도 초과가 나면 P4의 서비스가 `LMT0002`를 던집니다.

### 3-3. 번호 구간

| 구간 | 용도 |
| --- | --- |
| `0001~0099` | 입력값 검증·정책 위반 (400) |
| `0100~0199` | 인증·권한 (401 · 403) |
| `0200~0299` | 리소스 없음 (404) |
| `0300~0399` | 상태 충돌·중복 (409) |
| `9000~9999` | 도메인 내부 오류 (500) |

---

## 4. 오류코드 마스터 — 전체 목록

각 파트는 아래 정의된 코드만 사용합니다. 새 코드가 필요하면 이 문서에 추가 등록합니다.

### 4-1. `CMN` 공통 — P5 (전 파트 공용)

**아래 상황에 도메인 코드를 새로 만들지 마십시오.**

| 코드        | HTTP | 메시지                                      | 의미                                             | 대체된 기존 코드                               |
|-----------|------|------------------------------------------|------------------------------------------------|-----------------------------------------|
| `CMN0001` | 400  | 입력값이 올바르지 않습니다.                          | Bean Validation·Enum 매핑 실패 등 범용 검증 실패          | `INQ0003`, `INQ0004`, `INVALID_ID_FORMAT` |
| `CMN0002` | 400  | 필수 입력값이 누락되었습니다.                         | 필수 파라미터·헤더 누락                                  | —                                       |
| `CMN0003` | 400  | 조회 시작일이 종료일보다 늦습니다.                      | 조회기간 역전                                        | `INQ0001`                               |
| `CMN0004` | 400  | 조회기간이 최대 1년을 초과했습니다.                     | 조회기간 상한 초과                                     | `INQ0002`                               |
| `CMN0005` | 400  | 지원하지 않는 페이지 크기입니다.                       | 5·10·20·30·50 외의 값                             | `INQ0005`                               |
| `CMN0101` | 401  | 인증정보가 없거나 세션이 만료되었습니다.                   | 세션 없음 또는 10분 무조작 만료                            | `ATH0001`(세션), `ATH0002`(세션), `AUTH0001` |
| `CMN0102` | 403  | 해당 자원에 접근할 권한이 없습니다.                     | 타 고객 자원 접근(IDOR)                               | `ATH0003`, `ACC0002`, `ACCT0002`        |
| `CMN0201` | 404  | 잘못된 요청 주소입니다.                            | 존재하지 않는 엔드포인트 및 지원하지 않는 HTTP 메서드(405도 404로 통일) | —                                       |
| `CMN0301` | 409  | 동일 요청이 처리 중입니다.                          | 멱등키 중복, 처리 미완료                                 | —                                       |
| `CMN0302` | 409  | 동일한 멱등키가 다른 요청에 사용되었습니다.                 | 동일한 Idempotency-Key에 요청 해시가 일치하지 않음            | —                                       |
| `CMN0303` | 409  | 다른 요청에 의해 정보가 변경되었습니다. 다시 조회한 후 시도해 주세요. | 낙관적 락 충돌 등 동시 수정                               | —                                       |
| `CMN9999` | 500  | 일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.        | 예상하지 못한 서버 오류                                  | `INQ9999`, `TRANSACTION_ROLLBACK_ERROR` |

> `CMN0001`, `CMN0201`, `CMN0303`,`CMN9999`는 `ApiExceptionHandler`가 자동 반환하므로 직접 던지지 않아도 됩니다.

### 4-2. `ATH` 인증·회원 — P6

| 코드 | HTTP | 메시지 | 의미 |
| --- | --- | --- | --- |
| `ATH0001` | 400 | 비밀번호 규칙에 맞지 않습니다. | 영문·숫자·특수문자 조합 위반 |
| `ATH0002` | 400 | 비밀번호와 확인값이 일치하지 않습니다. | 비밀번호 확인 불일치 |
| `ATH0003` | 400 | 직전 비밀번호는 재사용할 수 없습니다. | 비밀번호 재사용 제한 |
| `ATH0004` | 400 | 아이디 형식이 올바르지 않습니다. | 아이디 규칙 위반 |
| `ATH0005` | 400 | 아이디 중복확인을 완료해 주세요. | 중복확인 미수행 상태로 가입 시도 |
| `ATH0006` | 400 | 필수 약관에 동의하지 않았습니다. | 필수 약관 미동의 |
| `ATH0007` | 400 | 인증번호가 일치하지 않습니다. | 이메일·비밀번호 재설정 인증번호 불일치 |
| `ATH0008` | 400 | 인증번호가 만료되었습니다. | 인증번호 유효시간(180초) 초과 |
| `ATH0009` | 400 | 실명 또는 계좌 정보가 일치하지 않습니다. | 실명계좌 인증 실패 |
| `ATH0101` | 401 | 아이디 또는 비밀번호가 일치하지 않습니다. | 로그인 실패. 존재하는 고객의 비밀번호 불일치에만 `data`에 `errorCount`·`remainingAttempts` 포함 |
| `ATH0102` | 403 | 비밀번호 5회 오류로 계정이 잠겼습니다. | 계정 잠금 |
| `ATH0103` | 403 | 이메일 인증 토큰이 유효하지 않습니다. | `emailVerificationToken` 무효·만료 |
| `ATH0104` | 403 | 약관 동의 토큰이 유효하지 않습니다. | `termsAuthToken` 무효·만료 |
| `ATH0105` | 403 | 계좌 인증 토큰이 유효하지 않습니다. | `accountAuthToken` 무효·만료 |
| `ATH0201` | 404 | 존재하지 않는 사용자입니다. | 아이디 찾기·비밀번호 재설정 대상 없음 |
| `ATH0202` | 404 | 인증 요청을 찾을 수 없습니다. | `emailVerificationId`·`passwordResetRequestId` 없음 |
| `ATH0301` | 409 | 이미 사용 중인 아이디입니다. | 아이디 중복 |
| `ATH0302` | 409 | 이미 가입된 이메일입니다. | 이메일 중복 |

> 세션 만료(401)는 `ATH`가 아니라 `CMN0101`을 씁니다.

### 4-3. `OTP` OTP 거래인증 — P6

| 코드 | HTTP | 메시지 | 의미 |
| --- | --- | --- | --- |
| `OTP0001` | 400 | OTP 번호가 일치하지 않습니다. | 검증 실패. `data`에 `errorCount`·`remainingAttempts` 포함 |
| `OTP0101` | 403 | OTP 인증 토큰이 유효하지 않습니다. | `otpAuthToken` 무효·만료·사용됨 |
| `OTP0102` | 403 | 인증한 거래 내용과 요청 내용이 일치하지 않습니다. | 토큰에 묶인 `transactionData`와 최종 요청 불일치 |
| `OTP0103` | 403 | OTP 오류 횟수(5회)를 초과하여 잠금 처리되었습니다. OTP를 재발급받아 주세요. | 5회 초과로 요청 무효화 |
| `OTP0104` | 403 | OTP가 만료되었습니다. 재발급받아 주세요. | 180초 유효시간 초과 |
| `OTP0201` | 404 | OTP 요청을 찾을 수 없습니다. | `otpRequestId` 없음 |

### 4-4. `APW` 계좌비밀번호 — P6

| 코드 | HTTP | 메시지 | 의미 |
| --- | --- | --- | --- |
| `APW0001` | 400 | 계좌비밀번호가 일치하지 않습니다. | 검증 실패. `data`에 `errorCount`·`remainingAttempts` 포함 |
| `APW0002` | 400 | 신규 비밀번호와 확인값이 일치하지 않습니다. | 변경 시 확인값 불일치 |
| `APW0101` | 403 | 계좌비밀번호 5회 오류로 거래가 정지되었습니다. | 계좌 잠금 |
| `APW0102` | 403 | 계좌비밀번호 인증 토큰이 유효하지 않습니다. | `accountPasswordAuthToken` 무효·만료·사용됨 |

### 4-5. `MYP` 마이페이지·알림 — P6

| 코드 | HTTP | 메시지 | 의미 |
| --- | --- | --- | --- |
| `MYP0001` | 400 | 휴대폰 번호 형식이 올바르지 않습니다. | 고객정보 변경 시 형식 위반 |
| `MYP0201` | 404 | 알림을 찾을 수 없습니다. | `notificationId` 없음 |

### 4-6. `ACC` 계좌 — P2

| 코드        | HTTP | 메시지 | 의미 |
|-----------|  | --- | --- |
| `ACC0001` | 400 | 계좌별명 길이 제한을 초과했습니다. | 한글 12자·영숫자 24자 초과 |
| `ACC0002` | 400 | 표시 순서 정보가 올바르지 않습니다. | 계좌 표시순서 저장 시 누락·중복 |
| `ACC0003` | 400 | 입출금계좌만 출금계좌로 등록할 수 있습니다. | 예·적금 계좌를 출금계좌로 등록하려는 경우 |
| `ACC0201` | 404 | 계좌를 찾을 수 없거나 접근할 수 없습니다. | 미존재와 타인 계좌를 구분하지 않음(§8-3) |
| `ACC0301` | 409 | 거래정지 또는 해지 상태의 계좌입니다. | 계좌 상태 위반 |
| `ACC0302` | 409 | 예약이체 또는 자동이체가 등록된 계좌는 삭제할 수 없습니다. | 출금계좌 삭제 제한 |
| `ACC0303` | 409 | 발급 가능한 계좌번호가 모두 사용되었습니다. | 일련번호 9999999까지 모두 사용 |
| `ACC9001` | 500 | 계좌번호 발급 처리 중 오류가 발생했습니다. | 조건에 맞는 채번 기준 행이 없음 |


### 4-7. `INQ` 조회 — P2

현재 조회 도메인 고유 오류코드는 없습니다. 조회기간 역전, 최대 조회기간 초과, 페이지 크기 오류 등은 `CMN` 공통 오류코드를 사용합니다.

### 4-8. `LMT` 이체한도 — P1

| 코드 | HTTP | 메시지 | 의미 |
| --- | --- | --- | --- |
| `LMT0001` | 400 | 출금가능금액이 부족합니다. | 잔액 부족. REQ-TRSF-013. 금액 비교 전용이며 계좌 상태 위반은 `TRF0304` |
| `LMT0002` | 400 | 1회 이체한도를 초과했습니다. | REQ-TRSF-010 · POL-015 |
| `LMT0003` | 400 | 1일 이체한도를 초과했습니다. | REQ-TRSF-011 · POL-016 |
| `LMT0004` | 400 | 1회 한도는 1일 한도를 초과할 수 없습니다. | 한도 변경 시 정합성 위반 |

### 4-9. `TRF` 이체·원장 — P4

| 코드 | HTTP | 메시지 | 의미 |
| --- | --- | --- | --- |
| `TRF0001` | 400 | 등록되지 않은 출금계좌입니다. | 출금계좌 미등록 |
| `TRF0002` | 400 | 출금계좌와 입금계좌가 동일합니다. | 동일계좌 이체 차단 |
| `TRF0003` | 400 | 이체금액은 1원 이상의 정수여야 합니다. | 금액 형식·범위 위반 |
| `TRF0004` | 400 | 입금계좌로 지정할 수 없는 상품 유형입니다. | REQ-TRSF-030 |
| `TRF0005` | 400 | 통장 표시내용 길이 제한을 초과했습니다. | 최대 10자 |
| `TRF0201` | 404 | 입금계좌를 찾을 수 없습니다. | 예금주 조회·이체 실행 |
| `TRF0202` | 404 | 거래내역을 찾을 수 없습니다. | 이체 상세 조회 |
| `TRF0301` | 409 | 거래정지 또는 해지 상태의 입금계좌입니다. | 입금계좌 상태 위반 |
| `TRF0302` | 409 | 이미 처리 완료(SUCCESS/ERROR)된 이체는 상태를 변경할 수 없습니다. | `Transfer.complete`/`fail`의 상태 전이 가드 |
| `TRF0304` | 409 | 거래정지 또는 해지 상태의 출금계좌입니다. | 출금계좌 상태 위반 |
| `TRF9001` | 500 | 이체 처리 중 계좌 정보를 확인할 수 없습니다. | 계좌 비관적 락 대상 조회 실패(FK·상위 검증으로 정상 흐름에선 도달 불가) |
| `TRF9002` | 500 | 거래번호 일련번호 채번 가능 범위를 초과했습니다. | 일자·채널당 10자리 일련번호 소진(실질적으로 도달 불가능한 불변식 위반) |

> 예금주 조회의 문장형 코드 매핑: `ACCOUNT_NOT_FOUND`→`TRF0201` · `ACCOUNT_SUSPENDED`/`ACCOUNT_CLOSED`→`TRF0301` · `UNSUPPORTED_ACCOUNT_TYPE`→`TRF0004` · `SAME_ACCOUNT_TRANSFER`→`TRF0002`

> `TRF0303`(잔액 부족)은 사용하지 않습니다. 같은 의미의 `LMT0001`(§4-8)로 통일했습니다 — REQ-TRSF-013이 P1 배정이고, 잔액 부족은 요청 금액 오류에 가까워 `400`이 맞습니다. 계좌 상태 위반은 `TRF0304`로 계속 구분합니다.

### 4-10. `FAV` 자주 쓰는 계좌 — P4

| 코드 | HTTP | 메시지 | 의미 |
| --- | --- | --- | --- |
| `FAV0001` | 400 | 별칭 길이 제한을 초과했습니다. | 한글 12자·영숫자 24자 초과 |
| `FAV0201` | 404 | 등록된 계좌를 찾을 수 없습니다. | `favoriteAccountId` 없음 |
| `FAV0301` | 409 | 이미 등록된 계좌입니다. | 중복 등록 |
| `FAV0302` | 409 | 자주 쓰는 계좌는 최대 20건까지 등록할 수 있습니다. | 건수 상한 |

### 4-11. `PRD` 상품·상품가입 — P3

| 코드 | HTTP | 메시지 | 의미 |
| --- | --- | --- | --- |
| `PRD0001` | 400 | 가입금액이 상품 한도 범위를 벗어났습니다. | 최소·최대 가입금액 위반 |
| `PRD0002` | 400 | 가입기간이 상품 허용 범위를 벗어났습니다. | `termMonths` 범위 위반 |
| `PRD0003` | 400 | 필수 약관에 동의하지 않았습니다. | 상품 필수 약관 미동의 |
| `PRD0004` | 400 | 가입금액이 상품의 입력 단위에 맞지 않습니다. | amountUnit 배수 위반 |
| `PRD0005` | 400 | 약관 전문을 확인한 후 동의해 주세요. | viewRequired=true 약관 열람 이력 없음·만료 |
| `PRD0006` | 400 | 약관이 변경되었습니다. 다시 확인해 주세요. | 동의한 약관 버전과 현재 버전 불일치 |
| `PRD0007` | 400 | 판매중인 상품이 아닙니다. | 판매중지·판매종료 상품 가입 시도 |
| `PRD0201` | 404 | 상품을 찾을 수 없습니다. | `productId` 없음 |
| `PRD0202` | 404 | 약관을 찾을 수 없습니다. | `termsId` 없음 |
| `PRD0203` | 404 | 가입 내역을 찾을 수 없습니다. | `subscriptionId` 없음 |
| `PRD0301` | 409 | 이미 가입한 상품입니다. | 1인 1계좌 제한 상품 중복 가입 |

> `PRD0001`~`PRD0007`은 가입 사전 검증(`POST /product-subscriptions/validation`)에서는 예외로 던지지 않고 `200` + `valid=false` + `violations[].code`로 반환합니다 — 필드별 오류를 화면에 동시에 표시해야 해서 하나만 틀려도 `400`을 던지면 나머지 검증 결과를 알 수 없기 때문입니다. HTTP 열의 `400`은 검증 실패가 곧 요청 거부인 엔드포인트(실제 가입 실행 등)에서 던질 때 적용됩니다.

### 4-12. `SCD` 예약이체 — P5

| 코드 | HTTP | 메시지 | 의미 |
| --- | --- | --- | --- |
| `SCD0001` | 400 | 예약일자는 익일부터 1년 이내여야 합니다. | 예약일 범위 위반 |
| `SCD0002` | 400 | 예약이체 실행일이 지났습니다. | 배치 미실행 상태로 실행일 경과 |
| `SCD0005` | 400 | 이체금액은 0보다 커야 합니다. | 등록 시 금액 검증 (AUT0008과 동일 패턴) |
| `SCD0006` | 400 | 통장 표시내용은 10자 이내여야 합니다. | 등록 시 메모 길이 검증 (AUT0009와 동일 패턴) |
| `SCD0007` | 400 | 입금계좌로 지정할 수 없는 계좌 유형입니다. | REQ-SCD-006 입금계좌 유형 검증 (AUT0005와 동일 패턴) |
| `SCD0008` | 400 | 1회 이체한도를 초과했습니다. | REQ-SCD-006 1회한도 검증 (AUT0006과 동일 패턴) |
| `SCD0201` | 404 | 예약이체를 찾을 수 없습니다. | `scheduledTransferId` 없음 |
| `SCD0202` | 404 | 계좌를 확인할 수 없습니다. | 출금계좌 소유·상태, 입금계좌 실존 (AUT0202와 동일 패턴, §8-3) |
| `SCD0301` | 409 | 동일 조건의 예약이체가 이미 등록되어 있습니다. | 중복 등록 |
| `SCD0302` | 409 | 대기 상태가 아닌 예약이체는 취소할 수 없습니다. | `WAITING` 외 상태 |
| `SCD0303` | 409 | 실행 예정일 당일에는 취소할 수 없습니다. | 전일 23:59:59까지만 가능 |
| `SCD0304` | 409 | 이미 취소된 예약이체입니다. | 취소 재요청 — 200 처리로 갈지 결정 후 확정(§10 결정 대기) |

### 4-13. `AUT` 자동이체 — P5

| 코드        | HTTP | 메시지                                                | 의미 |
|-----------| --- |----------------------------------------------------| --- |
| `AUT0001` | 400 | 이체지정일은 1~31 사이여야 합니다.                              | REQ-AUTO-003 |
| `AUT0002` | 400 | 이체 기간이 유효하지 않습니다. 시작일은 익일부터 1년 이내, 종료일은 시작일 이후 60개월 이내여야 합니다. | REQ-AUTO-004 |
| `AUT0003` | 400 | 변경할 수 없는 항목입니다.                                    | 출금·입금계좌·이체지정일 변경 시도 |
| `AUT0004` | 400 | 최초 이체 예정일이 종료일 이후입니다. 이체 기간 내에 최소 1회 이상 실행되어야 합니다. | 시작일·종료일·이체지정일 조합상 실행 가능한 회차가 없음 |
| `AUT0005` | 400 | 입금계좌로 지정할 수 없는 계좌 유형입니다.                            | REQ-AUTO-006 |
| `AUT0006` | 400 | 1회 이체한도를 초과했습니다.                                    | ⚠️ 임시 코드. REQ-AUTO-006 등록 시점 한도 검증용. 의미상 `LMT0002`와 중복 — P1이 `LmtErrorCode` 구현하면 `LMT0002`로 교체 예정 |
| `AUT0007` | 400 | 이체주기는 1개월, 3개월, 6개월 중 하나여야 합니다.                     | REQ-AUTO-001, POL-033 |
| `AUT0008` | 400 | 이체금액은 0보다 커야 합니다.                                   | REQ-CMN-012 |
| `AUT0009` | 400 | 통장 표시내용은 10자 이내여야 합니다.                              | DB `VARCHAR(10)` 제약과 동일. `transfer`의 `MEMO_LENGTH_EXCEEDED`와 같은 패턴 |
| `AUT0201` | 404 | 자동이체 등록 건을 찾을 수 없습니다.                              | `autoTransferId` 없음 |
| `AUT0202` | 404 | 계좌를 확인할 수 없습니다.                                     | REQ-AUTO-006. 출금계좌 정지/해지·입금계좌 미존재를 구분하지 않고 동일 응답(§8-3, 계좌번호 스캐닝 방지) |
| `AUT0301` | 409 | 동일 조건의 자동이체가 이미 등록되어 있습니다.                         | REQ-AUTO-007 |
| `AUT0302` | 409 | 정상 상태가 아닌 자동이체입니다.                                 | 종료·해지 건 변경·해지 시도 |
| `AUT0303` | 409 | 실행 예정일 당일에는 해지할 수 없습니다.                            | REQ-AUTO-011 |

---

## 5. Enum 마스터

각 파트는 아래 값을 문자열 그대로 사용합니다.

### 5-1. 계좌 — P2

| Enum | 값 | 의미 |
| --- | --- | --- |
| `AccountStatus` | `ACTIVE` | 정상 |
|  | `SUSPENDED` | 거래정지 |
|  | `CLOSED` | 해지 |
| `AccountType` | `DEMAND_DEPOSIT` | 입출금계좌 |
|  | `TIME_DEPOSIT` | 정기예금 |
|  | `INSTALLMENT_SAVINGS` | 정기적금 |
| `AccountGroup` | `DEMAND_DEPOSIT` | 입출금계좌 (화면 그룹) |
|  | `DEPOSIT_SAVINGS` | 예금·적금 (화면 그룹) |

> `AccountGroup`은 `AccountType`과 다른 개념입니다. 화면 그룹핑 전용이며 값 종류가 다릅니다.

### 5-2. 조회 — P2

| Enum | 값 | 의미 |
| --- | --- | --- |
| `TransactionDirection` | `DEPOSIT` | 입금 |
|  | `WITHDRAWAL` | 출금 |
| `TransactionSortOrder` | `LATEST` | 최신순 |
|  | `OLDEST` | 과거순 |
| `AccountDisplayOrder` | `CUSTOM` | 사용자 지정 순서 |
|  | `OPENED_DATE_ASC` | 개설일 오름차순 |

### 5-3. 상품 — P3

| Enum | 값 | 의미 |
| --- | --- | --- |
| `ProductGroup` | `SAVINGS` | 정기적금 |
|  | `DEPOSIT` | 정기예금 |
| `ProductSaleStatus` | `ON_SALE` | 판매중 |
|  | `SUSPENDED` | 판매중지 |

| ProductGroup (P3) | AccountType (P2) | AccountGroup (P2, 화면) |
| --- | --- | --- |
| - | `DEMAND_DEPOSIT` | `DEMAND_DEPOSIT` |
| `SAVINGS` | `INSTALLMENT_SAVINGS` | `DEPOSIT_SAVINGS` |
| `DEPOSIT` | `TIME_DEPOSIT` | `DEPOSIT_SAVINGS` |

### 5-4. 거래 처리결과 — 공용

| Enum | 값 | 의미 |
| --- | --- | --- |
| `ProcessResultStatus` | `SUCCESS` | 정상 |
|  | `ERROR` | 오류 |
|  | `PROCESSING` | 처리중 (응답 유실·타임아웃 시에만) |

**사용 도메인**: 즉시이체 · 자동이체 회차 실행결과 · 상품가입 결과

> 상품가입이 쓰던 `SUCCESS`/`FAILED`/`PROCESSING`은 이 Enum으로 통일합니다. (REQ-TRSF-017)

### 5-5. 이체 — P4

| Enum | 값 | 의미 |
| --- | --- | --- |
| `TransferType` | `IMMEDIATE` | 즉시이체 |
|  | `SCHEDULED` | 예약이체 |
|  | `AUTO` | 자동이체 |
| `TransferChannel` | `WB` | 인터넷뱅킹 (거래번호 채널코드) |
|  | `BT` | 배치 (거래번호 채널코드), 예약 이체 |

### 5-6. 예약이체 — P3

| Enum | 값 | 의미 |
| --- | --- | --- |
| `ScheduledTransferStatus` | `WAITING` | 대기 |
|  | `PROCESSING` | 처리중 |
|  | `SUCCESS` | 정상 |
|  | `FAILED` | 오류 |
|  | `CANCELED` | 취소 |

> `ProcessResultStatus`와 별개 Enum입니다. `WAITING`이 포함된 생명주기 상태라 `NORMAL`로 치환하면 "정상 대기 중"과 "정상 완료"가 구분되지 않습니다. 철자는 `CANCELED`(L 하나)로 고정합니다.

### 5-7. 자동이체 — P5

| Enum | 값 | 의미 |
| --- | --- | --- |
| `AutoTransferStatus` | `NORMAL` | 정상 |
|  | `EXPIRED` | 종료 (기간 만료로 시스템 자동 전환) |
|  | `TERMINATED` | 해지 (고객 직접) |
| `TransferCycle` | `1` / `3` / `6` | 이체주기(개월). Integer |

> 등록 건의 상태(`AutoTransferStatus`)와 회차 실행결과(`ProcessResultStatus`)는 전혀 다른 개념입니다. 폐기: `CANCELLED`, 소문자 `expired`

### 5-8. 인증·OTP — P6

| Enum | 값 | 의미 |
| --- | --- | --- |
| `OtpTransactionType` | `IMMEDIATE_TRANSFER` | 즉시이체 |
|  | `SCHEDULED_TRANSFER` | 예약이체 |
|  | `AUTO_TRANSFER` | 자동이체 |
|  | `PRODUCT_SUBSCRIPTION` | 상품가입 |
|  | `TRANSFER_LIMIT_CHANGE` | 이체한도 변경 |
|  | `ACCOUNT_PASSWORD_CHANGE` | 계좌비밀번호 변경 |
|  | `WITHDRAWAL_ACCOUNT_REGISTER` | 출금계좌 등록 |
| `EmailVerificationPurpose` | `SIGN_UP` | 회원가입 |
|  | `EMAIL_CHANGE` | 이메일 변경 |

> 폐기: `TRANSFER`, `PRODUCT_SIGN`, `LIMIT_CHANGE`, `ACC_REGISTER`, `PROFILE_CHANGE` (기존 OTP 발급 명세의 약어)

### 5-9. 알림 — P6

| Enum | 값 | 의미 |
| --- | --- | --- |
| `NotificationType` | `TRANSFER` | 즉시이체 결과 |
|  | `SCHEDULED_TRANSFER` | 예약이체 결과 |
|  | `AUTO_TRANSFER` | 자동이체 결과 |
|  | `PRODUCT_SUBSCRIPTION` | 상품가입 결과 |
| `NotificationReadStatus` | `READ` | 읽음 |
|  | `UNREAD` | 안읽음 |

### 5-10. 조회 필터의 "전체"

`ALL`은 도메인 상태가 아니라 조회 조건 전용 값입니다.

- 도메인 Enum에 `ALL`을 넣지 않습니다.
- 조회 API 필터에서만 허용하고 기본값으로 씁니다.
- 서버는 `ALL`과 파라미터 미전달을 동일하게 "조건 없음"으로 처리합니다.

---

## 6. 데이터 표기 규약

### 6-1. 값 형식

| 항목 | 규칙 | 예시 | 근거 |
| --- | --- | --- | --- |
| 금액 | 정수 `Long`, 원 단위, 소수점·통화기호 없음 | `300000` | REQ-CMN-015 |
| 계좌번호 | 하이픈 없는 숫자 12자리 | `"110550051877"` | REQ-CMN-017 |
| 계좌번호 마스킹 | 하이픈 없이 마스킹 | `"110******877"` | REQ-CMN-017·018 |
| 거래번호 | `YYYYMMDD` + 채널 2자리 + 일련 10자리 = 20자 | `"20260730WB0000000123"` | REQ-TRSF-028 |
| 일시 | ISO-8601 오프셋 포함, 필드명 `~At` | `"2026-07-30T14:30:00+09:00"` | REQ-CMN-016 |
| 일자 | `YYYY-MM-DD`, 필드명 `~Date` | `"2026-08-25"` | REQ-CMN-016 |
| 월 | `YYYY-MM` | `"2026-07"` | — |
| 성명 | 가운데 1자 마스킹 | `"홍*동"` | REQ-CMN-018 |
| 이메일 | 로컬파트 4번째 문자 이후 마스킹. 로컬파트가 4자 이하라면 최소 1자 이상 마스킹 | `"user****@mail.com"` | REQ-CMN-018 |
| 휴대폰 | 중간 4자리 마스킹, 하이픈 없음 | `"010****5678"` | REQ-CMN-018 |
| 수수료 | 당행이체 `0` 고정 | `0` | POL-028 |

성명 마스킹은 이름 길이에 따라 다음 규칙을 적용합니다.

| 이름 길이 | 규칙 | 예시 |
| --- | --- | --- |
| 2자 | 첫 글자만 노출 | `이*` |
| 3자 | 첫 글자와 마지막 글자 노출 | `홍*동` |
| 4자 이상 | 첫 글자와 마지막 글자만 노출 | `남**수` |

> 콤마·하이픈·`원` 붙이기는 프론트 담당입니다. 서버는 원시 형식만 내려보냅니다. 단, 서버가 직접 생성하는 CSV 등 파일 응답의 표시 형식은 각 파일 API 명세를 따릅니다. 계좌번호 마스킹은 서버가 수행합니다.

### 6-2. 식별자 규칙

| 개념 | 필드명 | 타입 | 비고 |
| --- | --- | --- | --- |
| 로그인 아이디 (고객 입력) | `userId` | String | `username`·`loginId` 폐기 |
| 내부 고객 PK | `customerId` | Long | 관리자 경로도 `/customers/{customerId}` |
| 내 계좌 | `accountId` | Long | 목록 조회로 ID를 이미 받았으므로 |
| 상대방 계좌 | `accountNumber` | String(12) | 고객이 번호를 직접 입력하므로 |

> 타 고객 계좌의 내부 PK를 클라이언트에 노출하지 않습니다. 자동이체·예약이체 등록의 입금계좌는 `depositAccountNumber`를 씁니다.

### 6-3. 인증 토큰 규칙

사용자의 평문 비밀번호나 OTP 코드를 최종 거래 API에 직접 전송하지 않고, 사전 검증 단계에서 발급받은 일회성 난수 토큰(Opaque Token)을 전달하여 권한을 증명합니다.

| 토큰명 (발급 API) | 유효시간         | 사용처                                                           |
| --- |------------------|------------------------------------------------------------------|
| `otpAuthToken` (`POST /otp/verify`) | 300초            | OTP 필요 거래 전체                                               |
| `accountPasswordAuthToken` (`POST /accounts/{accountId}/password/verify`) | 300초            | 이체, 이체한도 변경, 상품가입, 예약이체, 자동이체, 출금계좌 등록 |
| `emailVerificationToken` (`POST /auth/email-verifications/{id}/verify`) | 1800초           | 회원가입, 이메일 변경                                            |
| `termsAuthToken` (`POST /auth/terms/check`) | 1800초           | signup/validate 성공 시 1회 소비                                 |
| `accountAuthToken` (`POST /auth/verify-account`) | 회원가입 세션 내 | 회원가입 입력검증                                                |

**명명 및 형식 규칙**

- 키 명명: 검증 결과 토큰은 `~AuthToken`, 리소스 식별자는 `~Id`를 사용합니다.
- 토큰 값 형식: `<용도 Prefix>_<CSPRNG 기반 난수>`. 시간이나 일련번호 등 예측 가능한 값을 배제하고, `UUIDv4` 또는 `SecureRandom`을 활용하여 최소 256bit 이상의 난수를 생성한 뒤 Base62(또는 Base64-urlsafe)로 인코딩합니다.
  - 예시: `OTP_AUTH_7xP9qK2RmY5vLw8ZbC6dE4fG1hH0jM3n`
  - 예시: `ACC_PWD_9aB3cF8dE2xY7zL1kM0pN4qR5sT6uV`

**보안 및 검증 지침**

1. 상태 저장 (Stateful 검증): 토큰 자체(Payload)에는 어떠한 민감 정보(비밀번호, 검증 키 등)도 담지 않습니다.
2. 단기 만료 (TTL): 발급된 토큰은 인메모리 저장소(예: Redis)에 설정된 유효시간(예: 300초)만큼만 저장되며, 기한이 지나면 자동 소멸해야 합니다.
3. 일회성 사용: 거래 API 등 목적된 사용처에서 토큰 검증이 성공적으로 완료되면, 재사용을 막기 위해 저장소에서 즉시 파기(Revoke)해야 합니다.

### 6-4. 필드명 통일

| 개념 | 확정 | 폐기 |
| --- | --- | --- |
| 거래번호 | `transactionNumber` | `transactionId` |
| 실행일시 | `executedAt` | `processedAt` |
| 처리결과 | `status` | `resultStatus` |
| 실패 건수 | `failureCount` | `failCount` |
| 실패 금액 | `failureAmount` | `failAmount` |
| 실패 사유 | `failureReason` | — |
| 전체 건수 | `totalCount` | `totalElements` |
| 전체 페이지 수 | `totalPages` | — |
| 페이지 번호 (0-base) | `page` | `pageNumber` |
| 페이지 크기 | `size` | `pageSize` |
| 목록 배열 | `items` | `list`, `content`, 도메인별 임의 이름 |
| 조회 시작일 | `fromDate` | `periodFrom` |
| 조회 종료일 | `toDate` | `periodTo` |
| 상대방 예금주 | `payeeName` | `depositorName` |
| 내 계좌 예금주 | `ownerName` | — |
| 내 통장 표시내용 | `myPassbookMemo` | `memoOut`, `withdrawalMemo`, `senderPrintContent` |
| 받는 분 통장 표시내용 | `recipientPassbookMemo` | `memoIn`, `depositMemo`, `receiverPrintContent` |
| 조회 기준 일시 | `asOf` | — |
| 거래 후 잔액 | `balanceAfter` | 이체 상세는 `balanceAfter`, 즉시이체 실행은 `withdrawalBalanceAfter` |
| 출금계좌 표시명 | `withdrawalAccountName` | 이체결과 조회에서 사용 중, 마스터 미등록 |
| 약관 식별자 타입 | `termsId` — Long / String 확정 | 회원가입 String, 상품 약관 Long |

> `startDate`·`endDate`는 자동이체·예약이체의 이체 기간을 뜻합니다. 조회 조건에는 `fromDate`·`toDate`를 씁니다.

### 6-5. 페이징 응답 표준

```json
{
  "code": "0000",
  "message": "정상 처리되었습니다.",
  "data": {
    "page": 0,
    "size": 10,
    "totalCount": 37,
    "totalPages": 4,
    "items": []
  }
}
```

기본 10건, 선택 5·10·20·30·50 (REQ-CMN-019 · POL-022). 0건이면 `items: []` + HTTP `200`.

### 6-6. 집계(summary) 표준

```json
{
  "summary": {
    "successCount": 5,
    "successAmount": 500000,
    "failureCount": 2,
    "failureAmount": 200000,
    "canceledCount": 1,
    "canceledAmount": 100000
  }
}
```

집계는 페이징과 무관하게 조회 조건 전체 합계입니다.

---

## 7. 멱등키 (REQ-CMN-014)

### 7-1. 속성값

| 값 | 의미 |
| --- | --- |
| `Y` | 사용 확정. `Idempotency-Key` 헤더 필수 |
| `TBD` | 논의 필요 |
| `N` | 미사용 확정 |

공란 금지. `TDM`이 아니라 `TBD`입니다.

### 7-2. 처리 규칙

| 단계 | 동작 |
| --- | --- |
| 1 | `Idempotency-Key` 헤더 수신 및 UUID v4 형식 검증 |
| 2 | 키 없음 → `CMN0002` (400) |
| 3 | 신규 키 → 키·요청 해시·처리 상태 저장 후 비즈니스 로직 실행 |
| 4 | 동일 키 + 동일 요청 해시 + 처리 완료 → 저장된 기존 응답을 HTTP `200`으로 반환 |
| 5 | 동일 키 + 동일 요청 해시 + 처리 중 → `CMN0301` (409) |
| 6 | 동일 키 + 다른 요청 해시 → `CMN0302` (409) |
| 7 | 24시간 경과 → 배치 삭제 |

> **해시 검증 예외 규칙**: 단계 2와 5에서 요청 본문(Body)의 해시를 생성 및 비교할 때, `otpAuthToken`, `accountPasswordAuthToken` 등 `*AuthToken`으로 끝나는 일회성 인증 토큰 필드는 해시 계산 대상에서 제외합니다. (네트워크 지연 재요청 시 토큰 갱신으로 인한 오탐지 방지)

### 7-3. `Y` 적용 대상

프로젝트 정책상 아래에 명시된 API에 `Idempotency-Key`를 적용합니다.

회원가입 완료 · 고객정보 변경 · 로그인 비밀번호 변경 · 계좌비밀번호 변경 · 출금계좌 등록·삭제 · 계좌별명 등록·수정·삭제 · 계좌 표시순서 저장·초기화 · 자주 쓰는 계좌 등록·별칭 수정·삭제 · 상품가입 실행 · 즉시이체 실행 · 이체한도 변경 · 예약이체 등록·취소 · 자동이체 등록·변경·해지 · 알림 읽음 처리 · 비밀번호 재설정

**`N` 대상**: 모든 조회 API, OTP 발급·검증, 계좌비밀번호 검증, 로그인·로그아웃·세션 연장

---

## 8. 인증·인가 전제

### 8-1. 세션

| 항목 | 내용 | 근거 |
| --- | --- | --- |
| 세션 | `JSESSIONID` 쿠키, HttpOnly·Secure·SameSite=Lax | REQ-NFR-006 |
| 타임아웃 | 10분 (무조작 기준) | POL-001 |
| 로그인 시 세션ID 재발급 | 세션 고정 공격 방지 | REQ-NFR-006 |
| 소유 검증 | 자원 접근 시 세션 고객의 소유 여부를 서버에서 검증. 클라이언트가 보낸 고객ID를 신뢰하지 않음 | REQ-NFR-007 |
| 감사 로그 | 원장 변경 거래는 고객ID·요청일시·IP·거래유형·거래번호·결과 기록 | REQ-NFR-010 |
| 로그 마스킹 | 비밀번호·OTP·전체 계좌번호·생년월일 평문 기록 금지 | REQ-NFR-009 |

### 8-2. 2단계 인증 흐름

```
① 계좌비밀번호 검증  → accountPasswordAuthToken 발급
② OTP 발급          → otpRequestId + otpCode (거래 내용을 함께 전송)
③ OTP 검증          → otpAuthToken 발급 (거래 내용이 토큰에 묶임)
④ 최종 거래 API 호출 → 두 토큰 전달. 서버가 토큰의 거래 내용과 요청 본문을 대조
```

④ 단계에서 불일치가 확인되면 `OTP0102`를 반환합니다. 인증 통과 후 금액·입금계좌를 바꿔치기하는 것을 막기 위한 것입니다.

### 8-3. 계좌 대상 API의 소유권 오류

계좌를 다루는 API는 타인 계좌와 미존재 계좌를 외부 응답에서 구분하지 않습니다. 계좌 존재 여부가 노출되면 계좌번호 스캐닝이 가능해집니다.

**적용 대상**: 계좌 상세 · 거래내역 · 거래내역 CSV · 계좌별명 관리 · 출금계좌 관리 · 계좌비밀번호 검증·변경 · 자동이체 조회·변경·해지 · 예약이체 조회·취소

> 2026-08-19 추가: `result(최종본).md`의 예약이체 취소 API Status 표는 403(본인 등록 건 아님)과 404(건 못 찾음)를 분리해 두었으나, `scheduledTransferId`도 순차 증가 PK라 계좌번호와 동일한 스캐닝 위험이 있어 이 규칙 적용 대상에 포함하기로 함(팀장 리뷰, PR #211). 개별 명세서보다 이 문서를 우선한다는 원칙에 따름.

**제외**: 이체한도 조회·변경. 이체한도는 계좌 단위가 아니라 고객 단위이므로(`transfer_limit` PK가 `customer_id`) 계좌 ID를 받지 않고, 계좌번호 스캐닝 위험이 성립하지 않습니다.

**예외**: 예금주 조회(이체 도메인). 정상적인 송금 절차를 위해 수취 계좌의 유효성 검증이 필수이므로, 예금주 조회 API에 한하여 계좌 미존재(`TRF0201`)와 상태 위반(`TRF0301`)을 명확히 구분하여 응답합니다. 돈을 보내기 위해 상대방의 계좌번호를 입력했을 때, 그것이 진짜 없는 계좌인지(`TRF0201`) 아니면 거래가 정지된 계좌인지(`TRF0301`) 명확히 구분해서 응답해야 하기 때문입니다.

```java
// ❌ 존재 여부가 노출된다
if (account == null)              throw new BusinessException(AccountErrorCode.NOT_FOUND);   // 404
if (!account.isOwnedBy(customer)) throw new BusinessException(CommonErrorCode.FORBIDDEN);    // 403

// ✅ 동일 응답 (적용 대상 API 기본 규칙)
if (account == null || !account.isOwnedBy(customer)) {
    throw new BusinessException(AccountErrorCode.ACCOUNT_NOT_ACCESSIBLE);  // ACC0201, 404
}
```

```java
// ✅ 예외 허용 (예금주 조회 API에 한함)
if (targetAccount == null) {
    throw new BusinessException(TransferErrorCode.PAYEE_NOT_FOUND);        // TRF0201, 404
}
if (!targetAccount.isActive()) {
    throw new BusinessException(TransferErrorCode.PAYEE_ACCOUNT_SUSPENDED); // TRF0301, 409
}
```

---

## 9. 결정 대기 항목

| # | 항목 | 주체 |
| --- | --- | --- |
| 1 | 계좌목록·이체내역 CSV API 유지 여부 (1차 피드백 취소선 ↔ 현행 존재) | 전원 |
| 2 | 거래번호 채널코드 값 `WB`/`BT` 확정 (REQ-TRSF-028) | P4 |
| 3 | `TermsType` 약관구분 세부 값 | P6 |
| 4 | REQ-CMN-006(인증가드)·REQ-CMN-025(접속이력) 구현 주체 | P5·P6 |
