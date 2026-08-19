package com.shinhan.corebank.scheduledtransfer.application.port.out;

import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransfer;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface ScheduledTransferQueryPort {
    Page<ScheduledTransfer> search(Long customerId, ScheduledTransferStatus status, Long withdrawalAccountId,
                                    LocalDate fromDate, LocalDate toDate, Pageable pageable);
}
