package com.shinhan.corebank.transfer.application.port.in;

import java.time.LocalDateTime;

public record RecentTransferAccountResult(
        String depositAccountNumber, String payeeName, LocalDateTime lastTransferredAt, boolean transferable) {}
