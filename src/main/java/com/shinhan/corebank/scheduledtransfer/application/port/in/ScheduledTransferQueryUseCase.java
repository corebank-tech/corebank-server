package com.shinhan.corebank.scheduledtransfer.application.port.in;

import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;
import org.springframework.data.domain.Page;

import java.time.LocalDate;

public interface ScheduledTransferQueryUseCase {
    Page<ScheduledTransferListItem> search(Long customerId, ScheduledTransferStatus status, Long withdrawalAccountId,
                                    LocalDate fromDate, LocalDate toDate, int page, int size);
}
