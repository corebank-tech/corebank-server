package com.shinhan.corebank.autotransfer.application.port.out;

import com.shinhan.corebank.autotransfer.domain.AutoTransfer;
import com.shinhan.corebank.autotransfer.domain.AutoTransferExecution;

public record StuckExecution(AutoTransfer autoTransfer, AutoTransferExecution execution) {
}
