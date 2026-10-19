package com.shinhan.corebank.transfer.application.port.out;

import com.shinhan.corebank.transfer.domain.Transfer;
import java.util.Optional;

/** 처리 불명 타행 건을 확정할 때 쓰는 transfer 조회. */
public interface InterbankTransferPort {

    // SELECT FOR UPDATE. 송신 직후 확정과 조회거래가 동시에 와도 한쪽만 TIMEOUT을 본다.
    Optional<Transfer> findByTransactionNumberForUpdate(String transactionNumber);
}
