package com.shinhan.corebank.transfer.application.port.in;

import java.util.List;

public interface RecentTransferAccountQueryUseCase {
    List<RecentTransferAccountResult> queryRecent(Long customerId);
}
