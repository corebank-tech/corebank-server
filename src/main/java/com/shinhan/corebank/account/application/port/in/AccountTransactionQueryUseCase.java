package com.shinhan.corebank.account.application.port.in;

import com.shinhan.corebank.transfer.application.port.in.LedgerHistoryDirection;
import com.shinhan.corebank.transfer.application.port.in.LedgerHistoryResult;
import com.shinhan.corebank.transfer.application.port.in.LedgerHistorySort;
import java.time.LocalDate;

public interface AccountTransactionQueryUseCase {

    LedgerHistoryResult getTransactions(
            Long customerId,
            Long accountId,
            LocalDate fromDate,
            LocalDate toDate,
            LedgerHistoryDirection direction,
            String keyword,
            LedgerHistorySort sort,
            int page,
            int size,
            boolean all);
}
