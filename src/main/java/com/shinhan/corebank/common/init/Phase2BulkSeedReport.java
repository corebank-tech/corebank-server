package com.shinhan.corebank.common.init;

import java.time.Duration;

// PH-60b 완료 로그에 필요한 실제 건수와 소요 시간을 전달한다.
public record Phase2BulkSeedReport(
        int customers,
        int accounts,
        int transactions,
        int ledgerEntries,
        int vouchers,
        int journalEntries,
        int autoTransfers,
        int scheduledTransfers,
        Duration elapsed) {}
