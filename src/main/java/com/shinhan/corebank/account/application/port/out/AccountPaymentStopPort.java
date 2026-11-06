package com.shinhan.corebank.account.application.port.out;

import com.shinhan.corebank.account.domain.AccountPaymentStop;
import java.util.List;
import java.util.Optional;

public interface AccountPaymentStopPort {

    Optional<AccountPaymentStop> findByAccountIdAndCaseId(long accountId, long caseId);

    // 계좌의 유효한 지급정지 기록. 호출자가 계좌 행을 잠근 뒤 부른다.
    List<AccountPaymentStop> findActiveByAccountId(long accountId);

    AccountPaymentStop save(AccountPaymentStop paymentStop);

    boolean existsOtherCase(long accountId, long excludingCaseId);
}
