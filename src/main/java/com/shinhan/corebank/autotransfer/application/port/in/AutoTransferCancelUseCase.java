package com.shinhan.corebank.autotransfer.application.port.in;

public interface AutoTransferCancelUseCase {
    void cancel(Long autoTransferId, AutoTransferCancelCommand command);
}
