package com.shinhan.corebank.autotransfer.application.port.in;

import com.shinhan.corebank.autotransfer.domain.AutoTransfer;

public interface AutoTransferChangeUseCase {
    AutoTransfer change(Long autoTransferId, AutoTransferChangeCommand command);
}
