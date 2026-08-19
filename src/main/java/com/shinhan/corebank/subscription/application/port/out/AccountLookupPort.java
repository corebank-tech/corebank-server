package com.shinhan.corebank.subscription.application.port.out;

import java.util.Optional;

public interface AccountLookupPort {
    /**
     * accountId가 존재하고, customerId 소유이며, 출금계좌로 등록(withdrawal_registered=TRUE)된
     * 활성(ACTIVE) 계좌일 때만 값을 반환한다. 그 외(미존재/타인 계좌/미등록/정지·해지)는 전부
     * 빈 Optional — 이 API는 사유를 구분하지 않고 동일하게 ACC0201로 응답한다.
     */
    Optional<WithdrawableAccount> findWithdrawable(Long accountId, Long customerId);
}
