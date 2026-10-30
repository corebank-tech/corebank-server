package com.shinhan.corebank.transfer.application.port.out;

import com.shinhan.corebank.transfer.domain.LedgerEntry;
import java.util.Optional;

public interface LedgerLookupPort {

    /** 거래번호로 기표된 원장 출금·입금 한 쌍을 조회한다. 한 쌍이 아니면 빈 값을 돌려준다. */
    Optional<PostedLedgerPair> findPostedPair(String transactionNumber);

    /** 타행이체 거래번호의 출금 1행. 되돌림 입금은 제외한다. */
    Optional<LedgerEntry> findInterbankDebit(String transactionNumber);
}
