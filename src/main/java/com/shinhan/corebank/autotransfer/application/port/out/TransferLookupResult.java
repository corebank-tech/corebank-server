package com.shinhan.corebank.autotransfer.application.port.out;

import com.shinhan.corebank.common.domain.ProcessResultStatus;

public record TransferLookupResult(String transactionNumber, ProcessResultStatus status, String errorMessage) {
}
