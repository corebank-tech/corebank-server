package com.shinhan.corebank.autotransfer.application.port.in;

import com.shinhan.corebank.autotransfer.domain.AutoTransfer;
import com.shinhan.corebank.autotransfer.domain.AutoTransferStatus;
import org.springframework.data.domain.Page;

public interface AutoTransferQueryUseCase {
    Page<AutoTransfer> search(Long withdrawalAccountId, AutoTransferStatus status, int page, int size);
}
