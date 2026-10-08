package com.shinhan.corebank.common.init;

import java.time.LocalDate;

// PH-60b 대량 시드의 규모와 전용 식별자 대역을 한곳에서 관리한다.
public record Phase2BulkSeedSpec(
        int customerCount,
        int accountCount,
        int transferCount,
        int subscriptionCount,
        int autoTransferCount,
        int scheduledTransferCount,
        int maturedCount,
        int nearMaturityCount,
        int dormantCandidateCount,
        int transactionChunkSize,
        int jdbcBatchSize,
        long customerIdStart,
        long accountIdStart,
        long transferIdStart,
        long subscriptionIdStart,
        long ledgerEntryIdStart,
        long autoTransferIdStart,
        long scheduledTransferIdStart,
        long journalEntryIdStart,
        LocalDate periodStart,
        LocalDate periodEnd) {

    public static Phase2BulkSeedSpec production() {
        return new Phase2BulkSeedSpec(
                50_000,
                150_000,
                2_900_000,
                100_000,
                10_000,
                10_000,
                1_000,
                1_000,
                1_000,
                50_000,
                2_000,
                10_000_001L,
                100_000_001L,
                100_000_001L,
                100_000_001L,
                100_000_001L,
                100_000_001L,
                100_000_001L,
                100_000_001L,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 10, 5));
    }

    public Phase2BulkSeedSpec {
        if (customerCount <= 0 || accountCount != customerCount * 3) {
            throw new IllegalArgumentException("PH-60b 계좌 수는 고객당 3개여야 합니다.");
        }
        if (transferCount <= customerCount || subscriptionCount != customerCount * 2) {
            throw new IllegalArgumentException("PH-60b 거래 구성은 고객별 자금 공급과 예적금 가입을 포함해야 합니다.");
        }
        if (maturedCount < 0 || nearMaturityCount < 0 || maturedCount + nearMaturityCount > customerCount) {
            throw new IllegalArgumentException("PH-60b 단기예금 상태 건수가 고객 수 범위를 벗어났습니다.");
        }
        if (dormantCandidateCount < 0 || dormantCandidateCount >= customerCount) {
            throw new IllegalArgumentException("PH-60b 휴면 후보 수가 고객 수 범위를 벗어났습니다.");
        }
        if (autoTransferCount < 0
                || scheduledTransferCount < 0
                || autoTransferCount > customerCount
                || scheduledTransferCount > customerCount) {
            throw new IllegalArgumentException("PH-60b 자동·예약이체 수가 고객 수 범위를 벗어났습니다.");
        }
        if (transactionChunkSize <= 0 || jdbcBatchSize <= 0 || transactionChunkSize % jdbcBatchSize != 0) {
            throw new IllegalArgumentException("PH-60b 트랜잭션 크기는 JDBC 배치 크기의 양의 배수여야 합니다.");
        }
        if (periodStart == null || periodEnd == null || periodEnd.isBefore(periodStart)) {
            throw new IllegalArgumentException("PH-60b 거래 기간이 올바르지 않습니다.");
        }
    }

    public int transactionCount() {
        return transferCount + subscriptionCount;
    }

    public int ledgerEntryCount() {
        return transactionCount() * 2;
    }

    public int voucherCount() {
        return transactionCount();
    }

    public int journalEntryCount() {
        return transactionCount() * 2;
    }
}
