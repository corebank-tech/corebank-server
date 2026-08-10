package com.shinhan.corebank.autotransfer.application.port.out;

import com.shinhan.corebank.autotransfer.domain.AutoTransfer;
import com.shinhan.corebank.autotransfer.domain.AutoTransferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AutoTransferQueryPort {
    Page<AutoTransfer> search(Long customerId, Long withdrawalAccountId, AutoTransferStatus status, Pageable pageable);
}
