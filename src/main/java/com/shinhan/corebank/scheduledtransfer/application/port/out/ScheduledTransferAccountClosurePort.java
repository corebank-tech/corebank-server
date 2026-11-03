package com.shinhan.corebank.scheduledtransfer.application.port.out;

import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransfer;
import java.util.List;

public interface ScheduledTransferAccountClosurePort {

    List<ScheduledTransfer> findPendingReferencingAccountForUpdate(Long accountId, String accountNumber);
}
