# OTP 인터페이스 사용 가이드

## 1. 목적

OTP 인증이 필요한 업무 모듈은 OTP의 Redis 저장소, JPA Entity 및 내부 Service를 직접 참조하지 않습니다.

각 업무 모듈의 OTP Adapter에서 `otp.api`가 공개한 다음 타입만 사용합니다.
```java
import com.shinhan.corebank.otp.api.OtpAuthTokenVerification;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerifier;
import com.shinhan.corebank.otp.api.OtpTransactionType;
```

다음 OTP 내부 구현은 다른 모듈에서 참조하면 안 됩니다.

```text
otp.domain.model.OtpAuthTokenPayload
otp.application.port.out.OtpAuthTokenStorePort
otp.adapter.out.redis.OtpAuthTokenRedisAdapter
otp.adapter.out.persistence.*
```

---

## 2. OTP 모듈이 제공하는 공개 API

### OtpAuthTokenVerifier

OTP 인증 토큰을 검증하고 성공한 토큰을 일회성으로 소비합니다.

```java
public interface OtpAuthTokenVerifier {

    void verifyAndConsume(OtpAuthTokenVerification verification);
}
```

### OtpAuthTokenVerification

최종 업무 요청이 OTP 인증 당시의 고객·거래 유형·거래 내용과 일치하는지 검증하는 요청입니다.

```java
public record OtpAuthTokenVerification(
        String otpAuthToken,
        Long customerId,
        OtpTransactionType transactionType,
        Map<String, Object> transactionData
) {
}
```

---

## 3. Phase 1 거래 유형

Phase 1에서는 아래 7개 유형만 사용합니다.

```java
public enum OtpTransactionType {

    IMMEDIATE_TRANSFER,
    SCHEDULED_TRANSFER,
    AUTO_TRANSFER,
    PRODUCT_SUBSCRIPTION,
    TRANSFER_LIMIT_CHANGE,
    ACCOUNT_PASSWORD_CHANGE,
    WITHDRAWAL_ACCOUNT_REGISTER
}
```

등록·변경·취소·해지를 구분하는 세부 Enum은 추가하지 않습니다.

따라서 다음과 같은 값은 사용하지 않습니다.

```text
AUTO_TRANSFER_REGISTER
AUTO_TRANSFER_CHANGE
AUTO_TRANSFER_CANCEL

SCHEDULED_TRANSFER_REGISTER
SCHEDULED_TRANSFER_CANCEL
```

자동이체 관련 OTP 인증에는 `AUTO_TRANSFER`, 예약이체 관련 OTP 인증에는 `SCHEDULED_TRANSFER`를 사용합니다.

기존 Mock 인증 포트의 `purpose` 문자열을 다음처럼 직접 변환하면 안 됩니다.

```java
// 해당 Enum이 없으므로 사용 금지
OtpTransactionType.valueOf(purpose);
```

필요하면 업무 Adapter가 명시적으로 확정 Enum에 매핑합니다.

---

## 4. 각 업무 모듈의 호출 방법

각 업무 모듈은 다음과 같이 공개 verifier를 호출합니다.

```java
otpAuthTokenVerifier.verifyAndConsume(
        new OtpAuthTokenVerification(
                otpAuthToken,
                customerId,
                transactionType,
                transactionData
        )
);
```

여기에서 전달하는 값은 다음과 같습니다.

- `otpAuthToken`: OTP 검증 성공 시 발급된 일회용 토큰
- `customerId`: 요청 JSON이 아니라 로그인 세션에서 가져온 고객 PK
- `transactionType`: 업무에 해당하는 확정 `OtpTransactionType`
- `transactionData`: 최종 업무 요청에서 구성한 거래 정보

---

## 5. 권장 모듈 구조

업무 Application Service가 OTP 공개 API를 직접 참조하기보다, 기존 업무 모듈의 outbound port와 Adapter를 통해 호출하는 방식을 권장합니다.

```text
업무 Application Service
→ 업무 모듈의 인증 Port
→ 업무 모듈의 OTP Adapter
→ otp.api.OtpAuthTokenVerifier
```

업무 모듈은 이미 합의된 자체 포트가 있다면 그대로 유지합니다. OTP 연동을 위해 같은 역할의 포트를 새로 만들거나 메서드명을 반드시 `verifyAndConsume`으로 변경할 필요는 없습니다.

예를 들어 업무 포트의 메서드가 다음과 같아도 됩니다.

```java
void verify(
        String authToken,
        Long customerId,
        String purpose
);
```

업무 포트의 `verify()`가 내부 Adapter에서 OTP 공개 API의 `verifyAndConsume()`을 호출할 수 있습니다.

```java
@Override
public void verify(
        String authToken,
        Long customerId,
        String purpose
) {
    otpAuthTokenVerifier.verifyAndConsume(
            new OtpAuthTokenVerification(
                    authToken,
                    customerId,
                    resolveTransactionType(purpose),
                    transactionData
            )
    );
}
```

업무 포트의 이름과 OTP 공개 API의 이름은 같을 필요가 없습니다.

다만 업무 포트의 `verify()`도 성공 시 OTP 토큰이 소비된다는 점은 주석이나 테스트로 명시해야 합니다.

---



# 6. 각 모듈별 해야 할 작업

## 이체한도 모듈 적용 규칙

이체한도 모듈은 기존에 P6과 합의한 다음 포트를 유지합니다.

```java
void verify(
        String authToken,
        Long customerId,
        String purpose
);
```

다음 포트를 별도로 만들지 않습니다.

```java
// 신규 생성하지 않음
LimitOtpVerificationPort
```

기존 메서드도 임의로 `verifyAndConsume()`으로 변경하지 않습니다.

이체한도 OTP Adapter에서는 `purpose`를 `OtpTransactionType.valueOf()`로 변환하지 않고 다음 거래 유형으로 명시적으로 매핑합니다.

```java
OtpTransactionType.TRANSFER_LIMIT_CHANGE
```
`transactionData`의 구체적인 구성은 이체한도 모듈에서 합의한 최종 계약을 따르며, OTP 공통 가이드에서 `oneTimeLimit`, `dailyLimit` 등의 필드를 임의로 추가하지 않습니다.
---

## transfer — 즉시이체

사용 Enum:

```java
OtpTransactionType.IMMEDIATE_TRANSFER
```

권장 핵심 거래정보:

```json
{
  "withdrawalAccountId": 101,
  "depositAccountNumber": "110660000103",
  "amount": 100000
}
```

추가할 포트 예시:

```java
public interface TransferOtpVerificationPort {

    void verifyAndConsume(
            String otpAuthToken,
            Long customerId,
            Long withdrawalAccountId,
            String depositAccountNumber,
            long amount
    );
}
```

Adapter에서는 다음처럼 변환합니다.

```java
otpAuthTokenVerifier.verifyAndConsume(
        new OtpAuthTokenVerification(
                otpAuthToken,
                customerId,
                OtpTransactionType.IMMEDIATE_TRANSFER,
                Map.of(
                        "withdrawalAccountId", withdrawalAccountId,
                        "depositAccountNumber", depositAccountNumber,
                        "amount", amount
                )
        )
);
```

현재 `TransferCommand`에는 계좌비밀번호 토큰인 `authToken`만 있습니다. 즉시이체에 OTP가 필수라면 다음 작업이 필요합니다.

- `TransferController`에서 `Otp-Auth-Token` 헤더 수신
- `TransferRequest` 또는 command 변환 시 OTP 토큰 전달
- `TransferCommand`에 `otpAuthToken` 추가
- `TransferOtpVerificationPort` 추가
- 실제 OTP adapter 추가
- 기존 `TransferAuthTokenVerificationPort`는 계좌비밀번호 검증용으로 유지
- 즉시이체에만 OTP 검증
- 예약·자동이체 배치 실행에서는 OTP 재검증 금지

---

## scheduledtransfer — 예약이체

사용 Enum:

```java
OtpTransactionType.SCHEDULED_TRANSFER
```

등록 시 권장 거래정보:

```json
{
  "withdrawalAccountId": 101,
  "depositAccountNumber": "110660000103",
  "amount": 100000,
  "scheduledDate": "2026-08-30"
}
```

취소에도 OTP가 필요하다면 권장 거래정보:

```json
{
  "scheduledTransferId": 15
}
```

현재 예약이체는 이미 다음을 받고 있습니다.

```text
Account-Password-Auth-Token
Otp-Auth-Token
```

하지만 두 토큰 모두 기존 `AuthTokenVerificationPort`의 Mock 검증으로 전달됩니다.

따라서 다음처럼 분리해야 합니다.

```text
AuthTokenVerificationPort
→ accountPasswordAuthToken 전용

ScheduledTransferOtpVerificationPort
→ otpAuthToken 전용
```

추가할 포트 예시:

```java
public interface ScheduledTransferOtpVerificationPort {

    void verifyRegisterAndConsume(
            String otpAuthToken,
            Long customerId,
            Long withdrawalAccountId,
            String depositAccountNumber,
            Long amount,
            LocalDate scheduledDate
    );

    void verifyCancelAndConsume(
            String otpAuthToken,
            Long customerId,
            Long scheduledTransferId
    );
}
```

날짜는 반드시 문자열로 변환합니다.

```java
"scheduledDate", scheduledDate.toString()
```

배치 실행 시에는 OTP를 다시 검증하면 안 됩니다. 예약이체 등록 시 받은 인증으로 실제 실행 인증을 갈음합니다.

---

## autotransfer — 자동이체

사용 Enum:

```java
OtpTransactionType.AUTO_TRANSFER
```

등록 시 권장 거래정보:

```json
{
  "withdrawalAccountId": 101,
  "depositAccountNumber": "110660000103",
  "amount": 100000,
  "cycleMonths": 1,
  "transferDay": 25,
  "startDate": "2026-09-01",
  "endDate": "2027-08-31"
}
```

현재 자동이체 command에는 `accountPasswordAuthToken`만 있고 `otpAuthToken`은 없습니다.

OTP가 필요한 사용자 동작에는 다음 작업이 필요합니다.

- Controller에서 `Otp-Auth-Token` 수신
- Request·Command에 `otpAuthToken` 추가
- `AutoTransferOtpVerificationPort` 추가
- 실제 OTP adapter 추가
- 기존 `AuthTokenVerificationPort`는 계좌비밀번호 전용으로 유지
- 등록·변경·해지 중 상세명세상 OTP가 필요한 동작에서 호출

변경·해지의 권장 최소 데이터:

```json
{
  "autoTransferId": 20
}
```

변경 금액까지 인증 대상이면 다음처럼 포함합니다.

```json
{
  "autoTransferId": 20,
  "amount": 200000,
  "endDate": "2027-12-31"
}
```

null인 선택 필드는 넣으면 안 됩니다.

배치 회차 실행에서는 OTP를 다시 검증하지 않습니다.

---

## subscription — 상품가입

사용 Enum:

```java
OtpTransactionType.PRODUCT_SUBSCRIPTION
```

권장 핵심 거래정보:

```json
{
  "productId": 10,
  "subscriptionAmount": 1000000,
  "termMonths": 12,
  "withdrawalAccountId": 101
}
```

추가할 포트 예시:

```java
public interface ProductSubscriptionOtpVerificationPort {

    void verifyAndConsume(
            String otpAuthToken,
            Long customerId,
            Long productId,
            Long subscriptionAmount,
            Integer termMonths,
            Long withdrawalAccountId
    );
}
```

현재 `ProductSubscriptionValidationCommand`에는 OTP 토큰이 없습니다. 실제 상품가입 실행 API를 구현할 때:

- 실행 Request·Command에 `otpAuthToken` 추가
- 실행 service에 OTP port 주입
- 약관·상품·계좌 검증 이후 실제 가입 상태 변경 직전에 OTP 검증
- `PRODUCT_SUBSCRIPTION`으로 호출

약관 ID 목록이나 우대조건처럼 OTP 변조 방지에 꼭 필요하지 않은 값은 제외하는 것이 좋습니다.

---

## account — 계좌비밀번호 변경

사용 Enum:

```java
OtpTransactionType.ACCOUNT_PASSWORD_CHANGE
```

권장 핵심 거래정보:

```json
{
  "accountId": 101
}
```

새 비밀번호는 절대 `transactionData`에 넣으면 안 됩니다.

현재 validator도 다음 필드를 차단합니다.

```text
password가 포함된 필드명
otpCode
*AuthToken
```

추가할 포트 예시:

```java
public interface AccountPasswordChangeOtpVerificationPort {

    void verifyAndConsume(
            String otpAuthToken,
            Long customerId,
            Long accountId
    );
}
```

계좌비밀번호 변경 service에서:

- 세션 고객의 계좌 소유권 확인
- 기존 계좌비밀번호 등 필요한 사전 검증
- OTP 검증·소비
- 신규 비밀번호 BCrypt 저장

---

## account — 출금계좌 등록

사용 Enum:

```java
OtpTransactionType.WITHDRAWAL_ACCOUNT_REGISTER
```

거래정보:

```json
{
  "accountId": 101
}
```

현재 `WithdrawalAccountRegisterCommand`에는 이미 두 토큰이 있습니다.

```java
String accountPasswordAuthToken
String otpAuthToken
```

또한 기존 Mock adapter에는 TODO가 있습니다.

```java
// TODO P6 OtpAuthTokenVerifier 연동
```

권장 구조는 기존 혼합 포트를 분리하는 것입니다.

```text
WithdrawalAccountPasswordVerificationPort
WithdrawalAccountOtpVerificationPort
```

OTP adapter 예시:

```java
@Component
@RequiredArgsConstructor
public class WithdrawalAccountOtpVerificationAdapter
        implements WithdrawalAccountOtpVerificationPort {

    private final OtpAuthTokenVerifier otpAuthTokenVerifier;

    @Override
    public void verifyAndConsume(
            String otpAuthToken,
            Long customerId,
            Long accountId
    ) {
        otpAuthTokenVerifier.verifyAndConsume(
                new OtpAuthTokenVerification(
                        otpAuthToken,
                        customerId,
                        OtpTransactionType.WITHDRAWAL_ACCOUNT_REGISTER,
                        Map.of("accountId", accountId)
                )
        );
    }
}
```

---

## 7. customerId와 accountId 처리

Redis의 OTP 토큰 payload에는 다음 정보가 저장됩니다.

```java
public record OtpAuthTokenPayload(
        String otpRequestId,
        Long customerId
) {
}
```

이 payload는 OTP 내부 저장 모델이므로 다른 모듈에서 직접 조회하지 않습니다.

업무 모듈은 세션의 `customerId`를 verifier에 전달합니다. OTP 모듈은 내부적으로 다음 값이 모두 일치하는지 확인합니다.

```text
호출자가 전달한 세션 customerId
= Redis payload.customerId
= verification_request.customer_id
```

계좌 단위 거래에서 필요한 `accountId`는 Redis payload에 추가하지 않고 `transactionData`에 포함합니다.

```java
Map.of(
        "accountId", accountId
)
```

업무 모듈은 OTP를 소비하기 전에 해당 계좌가 로그인 고객 소유인지 먼저 검증해야 합니다.

---

## 8. transactionData 규칙
`transactionData`는 OTP 발급·OTP 번호 검증·최종 업무 실행에서 동일하게 구성해야 합니다.
```text
OTP 발급 transactionData
= OTP 검증 transactionData
= 최종 업무 실행 transactionData
```

적용 규칙은 다음과 같습니다.

- 빈 객체를 전달하지 않습니다.
- `null` 값을 포함하지 않습니다.
- 금액은 문자열이나 `Integer`가 아니라 `Long`을 사용합니다.
- 날짜는 `YYYY-MM-DD` 형식으로 전달합니다.
- 비밀번호, OTP 번호 및 인증 토큰을 포함하지 않습니다.
- Phase 1에서는 별도의 `operation` 필드를 추가하지 않습니다.
- JSON 키 순서는 달라도 무관하며 canonical JSON으로 비교됩니다.
- 업무별 세부 필드 계약은 각 업무 모듈에서 확정합니다.

예시:

```java
Map<String, Object> transactionData = Map.of(
        "accountId", accountId,
        "amount", amount,
        "transferDate", transferDate.toString()
);
```

---

## 9. 호출 순서

최종 업무 API에서는 다음 순서를 권장합니다.

```text
1. 세션 customerId 확인
2. 요청 형식 및 업무 규칙 검증
3. 계좌 등 업무 대상의 소유권 검증
4. 멱등 완료 응답이면 기존 응답 재생
5. OTP 토큰 검증 및 소비
6. 실제 상태 변경
```

OTP 토큰은 성공 시 즉시 소비되므로, OTP 검증 이후에 일반 입력 오류가 발생하지 않도록 선행 검증을 먼저 수행해야 합니다.

멱등 요청의 replay에서는 이미 소비한 OTP 토큰을 다시 검증하면 안 됩니다.

---

## 10. OTP 검증 결과
`verifyAndConsume()`은 성공 값을 반환하지 않습니다.

```java
void verifyAndConsume(OtpAuthTokenVerification verification);
```

정상적으로 반환되면 OTP 검증과 토큰 소비가 모두 완료된 것입니다.

실패할 경우 OTP 모듈의 공통 예외가 전달됩니다.

- `OTP0101`: 토큰 없음, 만료, 사용 완료 또는 고객 불일치
- `OTP0102`: 거래 유형 또는 거래 내용 불일치

업무 모듈은 OTP 예외를 자체 오류코드로 임의 변환하지 않고 공통 예외 처리를 그대로 사용합니다.

---

## 11. 업무 모듈 테스트 항목

각 업무 모듈은 최소한 다음을 검증해야 합니다.

- 올바른 `OtpTransactionType`을 전달하는지
- 세션의 `customerId`를 전달하는지
- 최종 요청과 동일한 `transactionData`를 전달하는지
- OTP 검증 실패 시 업무 상태가 변경되지 않는지
- 계좌 대상 업무에서 소유권 검증 후 OTP를 소비하는지
- 멱등 replay에서 OTP verifier를 다시 호출하지 않는지
- 예약이체·자동이체 실행 배치에서 OTP를 다시 검증하지 않는지