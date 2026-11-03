package com.shinhan.corebank.account.api;

/**
 * 계좌 지급정지 공개 계약(이의제기 AI 심사 지원, D-36).
 *
 * <p>계좌는 사건별 지급정지 기록을 두고 상태와 보류액을 그 기록으로 계산한다. 전부 지급정지가 하나라도 있으면 SUSPENDED,
 * 일부 지급정지만 있으면 정지 전 상태에 보류액만 묶는다. 마지막 기록이 해제되면 정지 전 상태로 돌아간다.
 *
 * <p>계좌가 이미 다른 경로로 ACTIVE나 SUSPENDED 밖의 상태가 됐으면 상태를 덮어쓰지 않고 ACC0301로 거부한다.
 *
 * <p>상태를 바꾸는 메서드는 호출자 트랜잭션 안에서만 실행한다(없으면 예외). 계좌 행을 잠그고 기록을 갱신한 뒤 상태와
 * 보류액을 다시 계산한다. 같은 (계좌, 사건)으로 같은 요청을 다시 보내면 결과가 바뀌지 않는다.
 */
public interface PaymentStopUseCase {

    /**
     * 사건의 전부 지급정지를 건다. 입출금계좌가 아니면 ACC0004, 계좌가 ACTIVE나 SUSPENDED가 아니면 ACC0301, 이미 해제했거나 일부로 바꾼 사건이면
     * ACC0309이다.
     */
    void applyFullStop(long accountId, long caseId);

    /**
     * 사건의 전부 지급정지를 일부 지급정지로 바꾼다. 남은 잔액이 요청액보다 적으면 남은 잔액까지만 묶는다.
     * 그래서 적용액은 요청액보다 작거나 0일 수 있고, 그 경우를 어떻게 처리할지는 호출자가 정한다. 기록이 없으면
     * ACC0202, 이미 다른 요청액으로 바꿨거나 해제한 사건이면 ACC0309이다.
     */
    PartialStopResult convertToPartial(long accountId, long caseId, long requestedAmount);

    /** 사건의 지급정지를 해제한다. 기록이 없으면 ACC0202이고, 이미 해제했으면 아무것도 하지 않는다. */
    void release(long accountId, long caseId);

    /** 이 사건 말고 다른 사건의 지급정지 기록(해제한 것 포함)이 있는지 본다. 읽기만 한다. */
    boolean hasOtherStopHistory(long accountId, long excludingCaseId);
}
