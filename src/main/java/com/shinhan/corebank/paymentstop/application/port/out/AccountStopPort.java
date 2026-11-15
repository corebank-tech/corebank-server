package com.shinhan.corebank.paymentstop.application.port.out;

// 계좌 지급정지. account.api 로 부른다. 변경 메서드는 호출자 트랜잭션 안에서만 부른다.
public interface AccountStopPort {

    void applyFullStop(long accountId, long caseId);

    /** 일부 지급정지로 바꾸고 실제로 묶은 금액을 돌려준다. */
    long convertToPartial(long accountId, long caseId, long requestedAmount);

    void release(long accountId, long caseId);

    boolean hasOtherStopHistory(long accountId, long excludingCaseId);
}
