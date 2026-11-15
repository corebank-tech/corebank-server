package com.shinhan.corebank.paymentstop.application.port.in;

import java.time.LocalDate;
import java.util.List;

public interface RegisterPaymentStopCaseUseCase {

    /** 사건을 등록하고 같은 트랜잭션에서 계좌를 전부 지급정지한다. 사건 ID를 돌려준다. */
    long register(Command command, AdminActor actor);

    record Command(long accountId, List<String> disputedTransactionNumbers, LocalDate objectionDeadline) {}
}
