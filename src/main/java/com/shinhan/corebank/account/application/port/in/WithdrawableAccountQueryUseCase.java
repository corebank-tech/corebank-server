package com.shinhan.corebank.account.application.port.in;

import java.util.Optional;

public interface WithdrawableAccountQueryUseCase {

    /**
     * 출금계좌로 쓸 수 있는 상태일 때만 값을 반환한다 — 계좌가 존재하고, customerId 소유이며,
     * 활성(ACTIVE)이고, 출금계좌로 등록(withdrawal_registered)된 경우다.
     * 그 외(미존재·타인 계좌·정지·해지·미등록)는 사유를 구분하지 않고 전부 빈 Optional이며,
     * 어떤 오류로 응답할지는 호출 도메인이 정한다.
     */
    Optional<WithdrawableAccountResult> findWithdrawable(
            Long accountId,
            Long customerId
    );
}
