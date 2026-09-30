package com.shinhan.corebank.common.init;

import java.time.LocalDateTime;

// PH-60 최소 시드의 고정 규모와 전용 식별자 대역을 정의한다.
public record Phase2MinimumSeedSpec(
        int customerCount,
        int accountCount,
        int ledgerEntryCount,
        int autoTransferCount,
        int nearMaturityAccountCount,
        long customerIdStart,
        long accountIdStart,
        long transferIdStart,
        long ledgerEntryIdStart,
        long autoTransferIdStart,
        long transactionSequenceStart,
        String accountBankCode,
        LocalDateTime baseDateTime) {

    public static Phase2MinimumSeedSpec production() {
        return new Phase2MinimumSeedSpec(
                10_000,
                30_000,
                500_000,
                5_000,
                100,
                6_000_001L,
                60_000_001L,
                60_000_001L,
                60_000_001L,
                60_000_001L,
                6_000_000_001L,
                "860",
                LocalDateTime.of(2026, 9, 1, 0, 0));
    }

    public Phase2MinimumSeedSpec {
        if (customerCount <= 1 || accountCount != customerCount * 3) {
            throw new IllegalArgumentException("PH-60 계좌 수는 고객당 3개여야 합니다.");
        }
        if (ledgerEntryCount < accountCount || (ledgerEntryCount - accountCount) % 2 != 0) {
            throw new IllegalArgumentException("원장 수는 초기 기표와 이체 쌍으로 구성되어야 합니다.");
        }
        if (autoTransferCount < 0 || autoTransferCount > customerCount) {
            throw new IllegalArgumentException("자동이체 수는 고객 수 범위여야 합니다.");
        }
        if (nearMaturityAccountCount < 0 || nearMaturityAccountCount > customerCount) {
            throw new IllegalArgumentException("만기 임박 계좌 수는 고객 수 범위여야 합니다.");
        }
        if (accountBankCode == null || !accountBankCode.matches("[0-9]{3}")) {
            throw new IllegalArgumentException("PH-60 은행코드는 숫자 3자리여야 합니다.");
        }
        if (baseDateTime == null) {
            throw new IllegalArgumentException("PH-60 기준 시각은 필수입니다.");
        }
    }

    public int transferCount() {
        return (ledgerEntryCount - accountCount) / 2;
    }
}
