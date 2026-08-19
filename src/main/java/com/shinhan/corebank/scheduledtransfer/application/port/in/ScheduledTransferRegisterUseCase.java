package com.shinhan.corebank.scheduledtransfer.application.port.in;

import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransfer;

public interface ScheduledTransferRegisterUseCase {
    ScheduledTransfer register(ScheduledTransferRegisterCommand command);
}
