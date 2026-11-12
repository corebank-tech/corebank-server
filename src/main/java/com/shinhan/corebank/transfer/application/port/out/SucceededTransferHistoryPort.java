package com.shinhan.corebank.transfer.application.port.out;

import com.shinhan.corebank.transfer.api.RecipientHistory;
import com.shinhan.corebank.transfer.api.SucceededTransfer;
import java.time.LocalDateTime;
import java.util.List;

/** 고객의 성공 이체를 목록과 수취인 요약으로 읽는다(#995). 셈하는 규칙은 SucceededTransferHistoryQuery 계약을 따른다. */
public interface SucceededTransferHistoryPort {

    List<SucceededTransfer> findRecentSucceeded(
            long customerId, LocalDateTime fromInclusive, LocalDateTime toExclusive, int limit);

    RecipientHistory summarizeRecipient(long customerId, String depositAccountNumber, long excludeWithdrawalAccountId);
}
