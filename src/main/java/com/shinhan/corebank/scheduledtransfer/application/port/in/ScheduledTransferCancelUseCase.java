package com.shinhan.corebank.scheduledtransfer.application.port.in;

import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransfer;

public interface ScheduledTransferCancelUseCase {
    ScheduledTransfer cancel(Long scheduledTransferId, ScheduledTransferCancelCommand command);
}
