# 자동이체·예약이체 회차 상태 사용 현황 — ProcessResultStatus

> PH-80(P4)이 `ProcessResultStatus`에 `TIMEOUT`을 추가하기 전에, 자동이체·예약이체가
> 이 값을 어떻게 쓰는지와 "배치 회차는 TIMEOUT을 쓰지 않는다"는 결론을 전달한다
> ([tasks.md PH-80](tasks.md#ph-80-timeout--정정-체인--자동-재시도-금지), "P5가 10/6에
> 주는 문서를 PR 본문 근거로 단다"). PH-80 PR 본문의 근거로 이 문서를 인용한다.
>
> **범위: 이 문서는 `TIMEOUT`만 다룬다.** PH-80에 같이 들어가는 `INVALID`(정정 체인)는
> 검토하지 않았다 — `INVALID`는 당행 거래에도 붙을 수 있어서, 아래 1번 근거(당행 전용
> 등록)로 막히지 않는다. `INVALID`를 어디에 저장할지는 PH-80에서 정할 일이라 이 문서가
> 답하지 않는다.

## 0. 요약 — 어디서 쓰고, TIMEOUT이 들어올 수 있는지

| 어디서 쓰나 | 타입 | TIMEOUT이 들어올 수 있나 |
|---|---|---|
| `AutoTransferExecution.status` (자동이체 회차) | `ProcessResultStatus` | 아니오 |
| `ScheduledTransfer.status` (예약이체) | `ScheduledTransferStatus`(별개 enum) | 담을 수 없음(타입 자체가 다름) |
| `AutoTransferExecutionSettled`·`ScheduledTransferSettled` 이벤트 | `ProcessResultStatus` | 아니오 |
| `TransferLookupJpaEntity`·`ScheduledTransferLookupJpaEntity`(transfer 원장 읽기) | `ProcessResultStatus` | 원장에 TIMEOUT 행이 생기면 읽힌다(재확정 배치의 else 분기로 감 — 3번 참고) |
| `account.AutoTransferUsagePersistenceAdapter`(계좌 해지 시 진행중 회차 검사) | `PROCESSING`만 "진행 중"으로 봄 | 아니오. 단 TIMEOUT 회차가 생기면 "진행 중"으로 안 잡혀 계좌 해지가 통과된다 |
| 응답 DTO(`AutoTransferExecutionHistoryItemResponse` 등) | `ProcessResultStatus` | 아니오. 단 Swagger 스키마의 값 목록엔 TIMEOUT이 추가된다(FE 영향) |

자동이체는 `ProcessResultStatus`를 엔티티 자신의 영구 상태로 그대로 쓰고, 예약이체는
별도 enum(`ScheduledTransferStatus`)을 쓰지만 정산 이벤트 필드에서는 같은 타입을 쓴다.
아래 1~3번은 "그래도 왜 TIMEOUT이 실제로 안 들어오는지"를 설명한다.

## 1. 진짜 이유 — 등록할 때부터 당행 계좌만 받는다

자동이체(`AutoTransferCommandService.findDepositAccountInfo()`)와 예약이체
(`ScheduledTransferCommandService.findAccountTypeByNumber()`)는 입금계좌 조회에
실패하면 `ACCOUNT_NOT_ACCESSIBLE`로 등록 자체를 거부한다. 예약이체는 한 걸음 더 나가서
**은행코드를 `PAYEE_BANK_CODE = "088"`(당행)로 고정해서 저장**한다 — 등록 시점부터
"이 거래는 당행"이라고 못박아 두는 셈이다.

타행이체는 FE B-8([tasks.md FE B-8](tasks.md#fe-b-8-타행이체-입력결과))로 **은행 선택이
있는 완전히 새로운 화면**으로 만들어지고 있고, 기존 자동이체·예약이체 등록 화면·검증을
열어주는 계획은 지금 `tasks.md`에 없다. 그래서 이체 엔진에 타행 송신 능력(PH-33-②)이
추가돼도, 자동이체·예약이체가 등록하는 거래는 항상 당행이라 그 경로를 탈 일이 없다.

**이건 "지금 계획에 없다"는 데 근거한 추론이라 확정은 아니다.** 팀이 나중에 자동이체·
예약이체에도 타행 등록을 열어주기로 결정하면 이 문서의 결론을 재검토해야 한다.

## 2. 보조 근거 — 지금은 원장도 TIMEOUT을 막아주지만, PH-80이 지나면 약해진다

서버 크래시·응답 유실 등으로 `PROCESSING`에 멈춘 회차는 `reconcileStuckExecution()`이
**원장(`transfer` 테이블)을 직접 조회**해서 실제 결과를 확정한다 — `transfer` 행이 있고
`SUCCESS`/`ERROR`면 그대로, 없으면 `ERROR`로 확정한다.

지금까지는 이게 "구조적으로 보장"됐다. `transfer/domain/Transfer.java` 클래스 주석
(#377)이 "PROCESSING은 커밋되지 않는다 — 커밋된 transfer 행은 항상 SUCCESS 아니면
ERROR다"라고 못박아 두기 때문이다. **그런데 PH-80이 정확히 이 전제를 깬다** — 타행
이체가 TIMEOUT으로 끝나면, 그 상태로 커밋된 transfer 행이 생긴다([tasks.md PH-33
송신 흐름](tasks.md#ph-33-대외계-①-모의-서버-s1--②-타행-이체-s3): "송신: 당행 출금 →
전문 송신 → 응답 → 완료/TIMEOUT"). PH-80이 머지되는 순간 이 근거는 낡은 전제가 된다.

**그래도 결론이 안 바뀌는 건 1번(당행 전용 등록) 덕분이다.** 자동이체·예약이체가
만드는 transfer 행은 항상 당행 거래라 TIMEOUT이 될 일이 없다.

**재검토할 때 볼 위험 지점**: `reconcileStuckExecution()`의 "조회된 상태가 SUCCESS/
ERROR가 아니면" 분기(else)는 지금은 방어 코드일 뿐이지만, 1번 전제가 깨지는 순간(예:
나중에 자동이체도 타행으로 나갈 수 있게 되면) 위험해진다. 그 분기로 들어가면 회차를
**ERROR로 확정**하고 거래번호도 `null`로 남는다 — 실제로는 상대 은행에 돈이 들어갔을
수도 있는데 "결과를 모른다"가 아무 신호 없이 "실패"로 굳어버린다.

## 3. 놓치면 안 되는 두 코드 위치 — `isConfirmed()`와 `completeProcessing()`

`ProcessResultStatus.isConfirmed()`는 지금 이렇게 구현돼 있다.

```java
public boolean isConfirmed() {
    return this != PROCESSING;   // TIMEOUT을 추가만 해도 true가 된다
}
```

그리고 자동이체·예약이체의 `completeProcessing()`은 결과를 "SUCCESS냐, 아니냐" 둘로만
나눈다.

```java
if (result.status() == ProcessResultStatus.SUCCESS) {
    processingExecution.markSuccess(...);
} else {
    processingExecution.markError(...);   // TIMEOUT도 여기로 들어온다
}
```

**지금은 이체 엔진이 TIMEOUT을 만들어내지 않아서 문제가 없다.** 하지만 두 코드를
그대로 두면, 나중에 엔진이 TIMEOUT을 돌려주는 순간 `isConfirmed()`가 예외 없이 통과
시키고, 회차는 조용히 **ERROR로 저장**되며, 더 이상 PROCESSING이 아니니 재확정 배치도
다시 보지 않는다. "결과를 모르는 상태"가 아무 소리 없이 "실패"로 확정되는 경로다.
재검토할 때 이 두 곳(`isConfirmed()`, `completeProcessing()`의 if/else)을 같이 본다.

## 4. PH-80 작업 시 참고

- `ProcessResultStatus`에 `TIMEOUT` 추가는 자동이체·예약이체 코드에 지금은 영향이
  없다(그 값을 만들어내는 경로가 아직 없을 뿐, 컴파일·동작 모두 그대로다). 단, FE가
  Swagger에서 타입을 뽑으면 응답 DTO들의 `status` 값 목록에 `TIMEOUT`이 추가된다.
- `isConfirmed()`를 그대로 두면 `TIMEOUT`은 자동으로 "확정"으로 취급되고, 자동이체·
  예약이체 `completeProcessing()`의 if/else에서 `ERROR`로 처리된다(3번 참고).
- 재검토 트리거: **PH-33-②(송신 어댑터를 `TransferExecutionService`에 연결하는 단계,
  [머지 순서](tasks.md#ph-99-이체-파이프라인-확장점seam) 상 10/23) 착수**, 또는
  **자동이체·예약이체 등록에 타행 계좌를 허용하는 변경** — 둘 중 하나라도 생기면 이
  문서의 1·2번 결론을 재검토해야 한다. PH-33-①(모의 대외기관 서버, PR #552)은
  `TransferExecutionService`를 건드리지 않아 재검토 트리거가 아니다.
