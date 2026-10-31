# PH-92. MFA · Step-up 매트릭스

> **결론:** 현재 이체는 로그인 세션, 계좌비밀번호 검증, 거래내용에 묶인 Mock OTP 검증을 거친다. 검증 단계는 여러 개지만, 로그인 비밀번호와 계좌비밀번호는 모두 **지식**이고 OTP 번호는 같은 API 응답에서 확인할 수 있으므로 **독립적인 2요소 인증을 충족하지 않는다**. 이 문서는 [#642](https://github.com/corebank-tech/corebank-server/issues/642)의 현황·미달 구간을 정의한다. MFA·Step-up 구현은 범위 밖(B-10)이다([tasks.md](tasks.md#ph-92-mfa--step-up-매트릭스), [README.md](README.md)).

## 1. 판정 규칙

| 용어 | 이 문서에서의 기준 |
|---|---|
| 인증 단계 | 비밀번호 입력, OTP 번호 검증, 토큰 제출처럼 사용자가 거치는 절차. 단계 수와 인증요소 수는 다르다. |
| 인증요소 | 지식(알고 있는 비밀), 소유(독립적으로 관리하는 기기·매체), 생체(본인의 신체 특징) 중 하나. 서로 다른 범주에서 검증된 요소만 별도로 센다. |
| 인증 결과 토큰 | 앞선 검증에 성공했다는 짧은 수명의 증명. 그 자체를 새 지식·소유·생체 요소로 세지 않는다. 유효기간·1회 소비·거래정보 결합은 재사용과 다른 거래 전용을 막는 통제다. |
| 2요소 인증(MFA) | 동일 업무의 승인 근거에 **독립적인 두 범주**가 확인될 때만 `충족`. `지식 + 지식`은 비밀번호가 달라도 미충족이다. |
| Step-up | 로그인 세션을 가진 고객·관리자가 민감한 동작을 하기 전, 추가 인증을 요구하는 정책. `Step-up 대상`과 `이미 구현됨`, `MFA 충족`은 각각 다른 판정이다. |

로그인 세션은 로그인 결과를 유지하고, CSRF 토큰은 요청 위조를 막는다. 둘 다 로그인 비밀번호와 별개인 소유 요소로 계산하지 않는다. `Otp-Auth-Token`을 제출했다는 사실도 **OTP 번호를 누가 어떤 채널에서 받았는지**를 증명하지 않는다.

## 2. 인증수단과 증명 관계

| 수단 | 범주 | 현재 코드에서 확인한 사실 | 요소 판정 |
|---|---|---|---|
| 로그인 비밀번호 | 지식 | `POST /auth/login`이 아이디·비밀번호를 검증하고 세션을 생성한다. [LoginController](../../src/main/java/com/shinhan/corebank/auth/adapter/in/web/LoginController.java) | 지식 1종 |
| 계좌비밀번호 | 지식 | 로그인 고객의 계좌에 대해 숫자 4자리를 검증한다. 성공하면 고객·계좌에 묶인 `accountPasswordAuthToken`을 발급한다. [AccountPasswordController](../../src/main/java/com/shinhan/corebank/account/adapter/in/web/AccountPasswordController.java), [AccountPasswordVerificationService](../../src/main/java/com/shinhan/corebank/account/application/service/AccountPasswordVerificationService.java) | 로그인 비밀번호와 **같은 범주**. 두 번째 요소 아님 |
| 계좌비밀번호 토큰 | 독립 요소 아님 | 계좌별 사용처에서는 고객·계좌를 대조해 1회 소비한다. #443 고객 기준 사용처에서는 계좌를 다시 대조하지 않고 토큰의 고객 소유권을 확인한다. 검증 범위가 달라도 원천은 같은 계좌비밀번호다. [AccountPasswordAuthTokenService](../../src/main/java/com/shinhan/corebank/account/application/service/AccountPasswordAuthTokenService.java) | 지식 검증의 증명. 소유 요소 아님 |
| OTP 번호 | 소유 요소를 목표로 하는 절차이나 **현재는 독립 소유 증명 불가** | 거래 유형·거래정보와 함께 번호를 발급한다. 실제 소유 기기로 전송하지 않는 Mock이고, `app.otp.expose-code: true`라 발급 응답에 번호가 포함된다. [OtpIssueProcessor](../../src/main/java/com/shinhan/corebank/otp/application/service/OtpIssueProcessor.java), [OtpController](../../src/main/java/com/shinhan/corebank/otp/adapter/in/web/OtpController.java), [application.yml](../../src/main/resources/application.yml) | 번호 검증은 실행되지만 독립 소유 요소로 **계산하지 않음** |
| OTP 검증 토큰 | 독립 요소 아님 | 번호 검증 뒤 발급된다. 사용 시 고객·거래 유형·거래정보를 대조하고 1회 소비한다. [OtpAuthTokenVerificationService](../../src/main/java/com/shinhan/corebank/otp/application/service/OtpAuthTokenVerificationService.java) | OTP 절차의 증명. 추가 요소 아님 |
| 생체 인증 | 생체 | 현재 검토한 로그인·이체 인증 경로에서 생체 검증은 없다. | 미구현 |

**예시:** 로그인 비밀번호 → 계좌비밀번호 → Mock OTP → 즉시이체는 인증 절차가 세 번이어도, 확인된 독립 요소는 지식 범주뿐이다. 거래내용 결합과 일회용 소비 덕분에 OTP 토큰을 다른 거래에 쓰기 어려운 것과, 소유 요소가 입증된 것은 다른 문제다. `expose-code`를 `false`로 바꾸는 것만으로는 충분하지 않다. 그 뒤 **고객이 통제하는 독립 채널로 발송·검증하는 경로**가 실제로 있어야 소유 요소를 재판정할 수 있다.

## 3. 업무별 MFA·Step-up 매트릭스

`미충족`은 현재 구현을 고장으로 판정한다는 뜻이 아니라, **독립된 2요소가 입증되지 않았음**을 뜻한다. `해당 없음`은 시스템 실행 건에 사용자 재인증을 새로 요구하지 않는다는 뜻이다.

| 업무·판정 시점 | 현재 요구하는 검증 | 독립 2요소 | Step-up 정책 | 현재 미달 사유 | 근거 |
|---|---|---|---|---|---|
| 고객 로그인 | 로그인 비밀번호 → 고객 세션 | **미충족:** 지식 1종 | 로그인 자체의 MFA 적용 여부는 별도 결정 | 독립 소유·생체 요소 없음 | [LoginController](../../src/main/java/com/shinhan/corebank/auth/adapter/in/web/LoginController.java) |
| 즉시이체 실행 | 고객 세션 + 계좌비밀번호 토큰 + 거래내용에 묶인 OTP 토큰 | **미충족:** 지식 + Mock OTP | 실행 직전 Step-up 대상 | 두 토큰을 검증·소비하지만 독립 소유 요소 없음 | [TransferController](../../src/main/java/com/shinhan/corebank/transfer/adapter/in/web/TransferController.java), [TransferCommand](../../src/main/java/com/shinhan/corebank/transfer/application/port/in/TransferCommand.java), [TransferExecutionService](../../src/main/java/com/shinhan/corebank/transfer/application/service/TransferExecutionService.java) |
| 예약·자동이체 등록 | 고객 세션 + 계좌비밀번호 토큰 + 등록 내용에 묶인 OTP 토큰 | **미충족:** 지식 + Mock OTP | 장래 출금 지시 등록 시 Step-up 대상 | 추가 검증 절차는 있지만 독립 소유 요소 없음 | [ScheduledTransferCommandService](../../src/main/java/com/shinhan/corebank/scheduledtransfer/application/service/ScheduledTransferCommandService.java), [AutoTransferCommandService](../../src/main/java/com/shinhan/corebank/autotransfer/application/service/AutoTransferCommandService.java) |
| 예약이체 취소·자동이체 변경/해지 | 고객 세션 + 계좌비밀번호 토큰 + 변경·취소 내용에 묶인 OTP 토큰 | **미충족:** 지식 + Mock OTP | 지시 취소·조건 변경·해지 시 Step-up 대상 | 독립 소유 요소 없음 | [ScheduledTransferCommandService](../../src/main/java/com/shinhan/corebank/scheduledtransfer/application/service/ScheduledTransferCommandService.java), [AutoTransferCommandService](../../src/main/java/com/shinhan/corebank/autotransfer/application/service/AutoTransferCommandService.java) |
| 상품가입·이체한도 변경·계좌비밀번호 변경·출금계좌 등록 | 고객 세션 + 계좌비밀번호 토큰 + 업무 내용에 묶인 OTP 토큰 | **미충족:** 지식 + Mock OTP | 초입금 출금·한도 변경·비밀번호 변경·출금계좌 등록 시 Step-up 대상 | 네 업무 모두 토큰을 소비하지만 Mock OTP가 독립 소유를 증명하지 못함. 특히 한도 변경의 고객 기준 계좌비밀번호 토큰도 별도 요소가 아님 | [ProductSubscriptionController](../../src/main/java/com/shinhan/corebank/subscription/adapter/in/web/ProductSubscriptionController.java), [TransferLimitCommandService](../../src/main/java/com/shinhan/corebank/limit/application/service/TransferLimitCommandService.java), [AccountPasswordChangeService](../../src/main/java/com/shinhan/corebank/account/application/service/AccountPasswordChangeService.java), [WithdrawalAccountRegisterService](../../src/main/java/com/shinhan/corebank/account/application/service/WithdrawalAccountRegisterService.java), [OtpTransactionType](../../src/main/java/com/shinhan/corebank/otp/api/OtpTransactionType.java) |
| 예약·자동이체 배치 실행 | 등록된 지시에 따른 시스템 실행. 고객 세션·인증 토큰 없음 | **해당 없음:** 새 고객 승인 행위 아님 | 배치 회차마다 고객 Step-up을 요구하지 않음 | 등록 시점의 인증을 근거로 실행 | [TransferCommand](../../src/main/java/com/shinhan/corebank/transfer/application/port/in/TransferCommand.java) |
| 관리자 조회 API | 관리자 로그인 세션 + 경로별 `CUSTOMER_READ`·`GL_READ`·`AUDIT_READ` 권한 | **미충족:** 로그인 비밀번호 1종 | 조회마다 추가 Step-up을 요구하지 않음 | `GET /admin/customers`, `GET /admin/trial-balance`, `GET /admin/ledger-reconciliation/mismatches`가 구현됨. 행위 직전 추가 인증 없음 | [SecurityConfig](../../src/main/java/com/shinhan/corebank/auth/adapter/in/security/SecurityConfig.java), [AdminCustomerController](../../src/main/java/com/shinhan/corebank/customer/adapter/in/web/admin/AdminCustomerController.java), [AdminTrialBalanceController](../../src/main/java/com/shinhan/corebank/gl/adapter/in/web/admin/AdminTrialBalanceController.java), [AdminLedgerReconciliationController](../../src/main/java/com/shinhan/corebank/transfer/adapter/in/web/admin/AdminLedgerReconciliationController.java) |
| 관리자 고객 변경 API | 관리자 로그인 세션 + `CUSTOMER_WRITE` 권한 | **미충족:** 로그인 비밀번호 1종 | 고객 상태 변경 전 Step-up 요구 구간 | `POST /admin/customers/{customerId}/unlock`, `/status`, `/password-reset`이 구현됐으나 실행 직전 재인증 없음 | [AdminCustomerController](../../src/main/java/com/shinhan/corebank/customer/adapter/in/web/admin/AdminCustomerController.java), [SecurityConfig](../../src/main/java/com/shinhan/corebank/auth/adapter/in/security/SecurityConfig.java) |
| 관리자 권한 부여/회수 API | 관리자 로그인 세션 + `CUSTOMER_WRITE` 권한 | **미충족:** 로그인 비밀번호 1종 | 권한 변경 전 Step-up 요구 구간 | `POST·DELETE /admin/customers/{customerId}/permissions`가 구현됐으나 실행 직전 재인증 없음 | [AdminCustomerController](../../src/main/java/com/shinhan/corebank/customer/adapter/in/web/admin/AdminCustomerController.java), [admin_auth.md](admin_auth.md) |
| 관리자 GL 변경 API | 해당 변경 API 미구현 | **판정 보류:** 실행 경로 없음 | `GL_WRITE` 변경 전 Step-up 요구 구간 | 권한 Enum·인가 계약은 있지만 변경 API와 Step-up 검증 경로는 아직 없음 | [admin_auth.md](admin_auth.md) |

예약이체의 등록·취소와 자동이체의 등록·변경·해지는 해당 동작의 거래정보로 OTP를 검증한다. 반면 배치 실행은 고객이 그때 다시 요청한 이체가 아니다. 이 차이를 무시하고 배치 실행을 `2요소 충족` 또는 `OTP 누락`으로 표시하지 않는다.

## 4. PH-49b에 넘길 표기 계약

PH-49b는 이 문서의 관리자 행을 받아 **관리자 변경 API가 Step-up 요구 대상임을 표시**한다. 표시는 `Step-up 필요`라는 정책 요구를 나타내며 `Step-up 검증 완료`나 `MFA 적용 완료`를 뜻하지 않는다. 현재의 경로별 권한 검사와 향후 추가될 `GL_WRITE`·권한 부여/회수 API의 인가는 **접근 권한** 판정이며, 행위 직전 재인증의 대체물이 아니다.

| 관리 행위 | 관련 권한·계약 | PH-49b 표기 |
|---|---|---|
| `POST /admin/customers/{customerId}/unlock` · `POST /admin/customers/{customerId}/status` · `POST /admin/customers/{customerId}/password-reset` | `CUSTOMER_WRITE` ([AdminCustomerController](../../src/main/java/com/shinhan/corebank/customer/adapter/in/web/admin/AdminCustomerController.java)) | `Step-up 필요` |
| GL·전표·분개 변경 | `GL_WRITE` ([admin_auth.md](admin_auth.md)) | `Step-up 필요` |
| 관리자 권한 부여·회수 | `POST·DELETE /admin/customers/{customerId}/permissions` — `CUSTOMER_WRITE` ([AdminCustomerController](../../src/main/java/com/shinhan/corebank/customer/adapter/in/web/admin/AdminCustomerController.java)) | `Step-up 필요` |
| 고객·GL·감사 조회 | `CUSTOMER_READ`·`GL_READ`·`AUDIT_READ` | 이 매트릭스만으로 추가 Step-up을 요구하지 않음 |

고객 변경 API와 권한 부여/회수 API의 위 경로·메서드는 현재 구현 기준이고, 이 API들은 Swagger operation에 `x-step-up-required`와 설명 한 문장으로 표기했다. GL 변경 API의 실제 경로·메서드는 담당 구현과 함께 확정한다. 기존 코드에 없는 Step-up 완료 상태나 새 응답 필드를 이 문서가 정의하지 않는다. 권한 검사, 감사로그, maker-checker는 Step-up과 서로 다른 통제다.

## 5. 후속 구현 때 다시 판정할 조건

1. OTP를 독립 소유 요소로 쓰려면 API 응답 노출을 중단하는 것에 더해, 고객이 통제하는 별도 기기·채널로 발송하고 해당 채널 접근을 검증해야 한다. 로그인 세션만 탈취한 사용자가 OTP 번호까지 같은 경로에서 얻는다면 2요소라고 표시할 수 없다.
2. 로그인 후 재인증의 유효시간, 어떤 거래정보·관리 행위에 묶이는지, 재사용 가능 여부, 실패·만료 시 거부 방식은 구현 작업에서 정한다. 이 문서는 임의의 금액 기준이나 유효시간을 새로 정하지 않는다.
3. 관리자 변경 API에는 권한 확인과 별도로, Step-up을 실제로 검증하는 경로가 들어간 뒤에만 `적용 완료`를 표시한다. PH-49b의 정책 표기만으로 차단 기능이 구현된 것은 아니다.
4. 구현 후에는 같은 세션에서 두 비밀번호만 입력한 경우, Mock OTP 번호를 같은 응답에서 읽은 경우, 다른 거래용 OTP 토큰을 제출한 경우, 만료·재사용 토큰을 제출한 경우를 구분해 재검증한다.

이 문서의 사실 판정은 `dev` 커밋 `83948e97`(2026-10-22)을 합친 코드와 설정 기준이다. PH-49b 권한 부여/회수 API는 `dev` 커밋 `0c6674ab`(2026-10-29) 위에서 반영해 관리자 행을 다시 대조했다.
