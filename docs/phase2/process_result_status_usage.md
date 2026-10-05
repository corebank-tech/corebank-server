# 자동이체·예약이체 회차 상태 사용 현황 — ProcessResultStatus

> PH-80(P4)이 `ProcessResultStatus`에 `TIMEOUT`을 추가하기 전에, 자동이체·예약이체가
> 이 값을 어떻게 쓰는지와 "배치 회차는 TIMEOUT을 쓰지 않는다"는 결론을 전달한다
> (`docs/phase2/tasks.md` PH-80, 750번째 줄). PH-80 PR 본문의 근거로 이 문서를 인용한다.

## 0. 먼저 — `ProcessResultStatus`를 직접 공유하는 건 자동이체뿐이다

`tasks.md`도 "이 enum은 **자동이체** 실행 기록도 공유한다"로 적어뒀듯, **엔티티
자신의 영구 상태**로 이 타입을 그대로 쓰는 건 자동이체(`AutoTransferExecution.status:
ProcessResultStatus`)뿐이다. 예약이체(`ScheduledTransfer.status`)는 별개의
`ScheduledTransferStatus`(`WAITING·PROCESSING·SUCCESS·FAILED·CANCELED`)를 쓴다 —
값 이름이 비슷해 보이지만 타입 자체가 다르다. **다만 예약이체도 `ScheduledTransferSettled`
이벤트의 `status` 필드에서는 `ProcessResultStatus`를 그대로 쓴다** — "예약이체는
이 타입을 전혀 안 쓴다"는 아니다. 자세한 내용과 그래도 결론이 같은 이유는 4번에 있다.

아래 1~3번은 "자동이체"에 대한 내용이다.

## 1. 현재 값과 쓰임 (자동이체 `AutoTransferExecution`)

| 값 | 의미 | 설정 시점 |
|---|---|---|
| `PROCESSING` | 처리 중(아직 결과 미확정) | `saveProcessing()` — 실제 이체 시도 직전, `REQUIRES_NEW`로 즉시 커밋 |
| `SUCCESS` | 이체 성공 | `completeProcessing()` — 이체 엔진 결과가 `SUCCESS`일 때 |
| `ERROR` | 이체 실패 | `completeProcessing()` — 엔진 결과가 `ERROR`일 때, 또는 재확정 배치가 실제 거래 없음을 확인했을 때 |

## 2. PROCESSING이 "끼인" 상태가 됐을 때 — 재확정 배치

서버 크래시·응답 유실 등으로 `PROCESSING`에 멈춘 회차는
`AutoTransferBatchItemProcessor.reconcileStuckExecution()`이 **원장(`transfer`
테이블)을 직접 조회**해서 실제 결과를 확정한다.

- `transfer` 행이 있고 `SUCCESS` → 회차도 `SUCCESS`로 확정
- `transfer` 행이 있고 `ERROR` → 회차도 `ERROR`로 확정
- `transfer` 행이 없음(엔진이 아예 발행하지 못함) → `ERROR`로 확정

이게 "대체로 그렇다"가 아니라 **구조적으로 보장**된다는 점이 중요하다.
`transfer/domain/Transfer.java` 클래스 주석(#377)이 이렇게 못박아 둔다:

> PROCESSING은 커밋되지 않는다. INSERT부터 complete()/fail()까지가 한 트랜잭션이라
> 커밋된 transfer 행은 항상 SUCCESS 아니면 ERROR다 — 이중 전이를 막는 내부 상태일 뿐이다.

즉 `transfer` 테이블에서 조회되는 행은 **DB 커밋 시점에 이미 SUCCESS/ERROR로 확정된
행뿐**이다. `reconcileStuckExecution()`에 "조회된 상태가 SUCCESS/ERROR가 아니면"
분기하는 방어 코드가 있지만, 위 보장 때문에 실제로는 타지 않는 경로다.

**결과**: "결과를 끝내 모르겠다"로 끝나는 경우가 구조적으로 없다 — 원장이 유일한
진실 소스(source of truth)라서, 재확정은 항상 `SUCCESS` 또는 `ERROR` 둘 중
하나로 확정된다.

## 3. 결론 — 자동이체 회차는 TIMEOUT을 쓰지 않는다

PH-80이 대외계(타행 이체 등) 쪽에 추가하는 `TIMEOUT`은 "외부 응답을 끝내 받지 못해
결과를 모르는 상태"를 표현하는 값으로 보인다. 자동이체 회차는 위 2번처럼 **원장
조회로 항상 확정 가능**해서 이 "모르는 상태" 자체가 구조적으로 생기지 않는다.
그래서 `TIMEOUT`을 자동이체 회차에는 적용하지 않는다 — enum에 값이 추가돼도,
그 값이 실제로 설정되는 경로가 자동이체 쪽에는 없다.

## 4. 예약이체 — 엔티티 상태는 별개 enum, 이벤트 필드는 `ProcessResultStatus`를 그대로 씀

예약이체 엔티티 자신의 상태(`ScheduledTransfer.status`)는 `ScheduledTransferStatus`
(`WAITING·PROCESSING·SUCCESS·FAILED·CANCELED`)라는 별도 enum이라, `TIMEOUT`을
담을 수조차 없다. **다만 `ScheduledTransferSettled` 이벤트의 `status` 필드는
`ProcessResultStatus` 타입을 그대로 쓴다** — `completeProcessing()`이 이체 엔진
결과(`result.status()`)를, `reconcileStuckExecution()`이 `ProcessResultStatus.ERROR`를
직접 넣는다. 그래서 "예약이체는 이 enum을 전혀 참조하지 않는다"는 말은 틀리다 —
타입은 쓰지만, 엔티티 자신의 영구 상태로는 안 쓴다는 게 정확한 표현이다.

**그래도 결론은 같다.** 지금 이체 엔진(`TransferExecutionService`)에는 대외계(타행
이체) 연동이 아직 없다 — `result.status()`는 지금 `SUCCESS`/`ERROR`만 반환할 수
있고, `TIMEOUT`을 만들어내는 코드 경로 자체가 존재하지 않는다.
`reconcileStuckExecution()`도 자동이체와 동일하게 `transfer` 원장을 직접 조회해
확정하므로 "모르는 상태"가 남지 않는다. 그래서 지금 시점엔 예약이체 쪽에서도
`TIMEOUT`이 실제로 설정될 일이 없다.

**이 문서 작성 시점(10/6) 기준 PH-33 진행 상황**: PH-33-①(모의 대외기관 서버·전문
변환기, PR #552)이 **아직 머지 전**인데, 그 PR 본문에도 "`TransferExecutionService`와
이체 API는 고치지 않았다 — 변환기를 부르는 송신 어댑터는 PH-33-②에서 만든다"고
명시돼 있다. 즉 ①이 머지돼도 이 문서의 결론은 안 바뀐다.

**PH-33-②(송신 어댑터를 `TransferExecutionService`에 실제로 연결하는 단계)는
공식 데드라인이 10/23이고, PR도 아직 없다.** `tasks.md` 707번째 줄에 머지 순서가
강제돼 있다 — PH-99 → PH-80 → PH-90 → PH-24 → PH-41 대입 → **PH-33-②(10/23)**
순서이고, **"이 순서를 벗어나 `execute()`를 고치는 PR은 P4가 리뷰에서 막는다"**고
명시돼 있다. 그래서 PH-33-②는 지금 당장 시작될 수 없고, 시작되더라도 리뷰
단계에서 걸러진다.

**PH-33-②가 머지돼도 자동이체·예약이체는 여전히 영향 밖일 가능성이 높다.**
자동이체·예약이체 **등록 시점**에 이미 입금계좌가 당행(우리 은행) 계좌인지 확인하고,
아니면 `ACCOUNT_NOT_ACCESSIBLE`로 등록 자체를 거부한다(`AutoTransferCommandService`·
`ScheduledTransferCommandService`의 `findDepositAccountInfo()` 조회 실패 시 거부).
타행이체는 FE B-8(`tasks.md` 645번째 줄)로 **은행 선택이 있는 완전히 새로운 화면**으로
만들어지고 있어서, 기존 자동이체·예약이체 등록 화면·검증을 열어주는 계획은 지금
`tasks.md`에 없다. 즉 PH-33-②가 이체 엔진에 타행 송신 능력을 추가해도, 자동이체·
예약이체가 그 경로를 탈 방법이 등록 단계에서 막혀 있다.

**다만 이건 "지금 계획에 없다"는 데 근거한 추론이라 확정은 아니다.** 팀이 나중에
자동이체·예약이체에도 타행 등록을 열어주기로 결정하면 이 결론은 다시 봐야 한다.
**PH-33-②가 실제로 머지되는 시점, 그리고 자동이체·예약이체 등록에 타행 계좌를
허용하는 변경이 생기는 시점 — 둘 중 하나라도 발생하면 이 문서의 3·4번 결론을
재검토해야 한다.**

## 5. PH-80 작업 시 참고

- `ProcessResultStatus`에 `TIMEOUT` 추가는 자동이체·예약이체 코드에 지금은 영향이
  없다(그 값을 만들어내는 경로가 아직 없을 뿐, 컴파일·동작 모두 그대로다).
- `isConfirmed()`(`ProcessResultStatus`의 메서드, `PROCESSING`만 `false`)가
  `TIMEOUT`을 `ERROR`·`SUCCESS`와 같이 "확정"으로 볼지는 PH-80에서 결정이
  필요하다 — 자동이체·예약이체는 지금 이 값을 만들어내지 않으므로 그 결정과 무관하다.
- PH-33-②(송신 어댑터를 `TransferExecutionService`에 연결하는 단계) 착수, 또는
  자동이체·예약이체 등록에 타행 계좌를 허용하는 변경 — 둘 중 하나라도 생기면 이
  문서의 3·4번 결론을 재검토해야 한다. PH-33-①(PR #552)은 재검토 트리거가 아니다.
