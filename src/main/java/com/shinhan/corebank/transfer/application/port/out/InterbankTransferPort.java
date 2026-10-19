package com.shinhan.corebank.transfer.application.port.out;

import com.shinhan.corebank.transfer.domain.Transfer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 처리 불명 타행 건을 확정할 때 쓰는 transfer 조회. */
public interface InterbankTransferPort {

    // SELECT FOR UPDATE. 송신 직후 확정과 조회거래가 동시에 와도 한쪽만 TIMEOUT을 본다.
    Optional<Transfer> findByTransactionNumberForUpdate(String transactionNumber);

    /** createdBefore보다 먼저 만들어진 처리 불명 타행 건을 오래된 순으로 limit건. 락은 잡지 않는다. */
    List<Transfer> findUnsettled(LocalDateTime createdBefore, int limit);
}
