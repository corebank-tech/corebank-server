package com.shinhan.corebank.common.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class Phase2MinimumSeedSpecTest {

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 9, 1, 0, 0);

    @Test
    @DisplayName("PH-60 운영 규격은 요구된 건수와 전용 대역을 고정한다")
    void createsProductionSpec() {
        Phase2MinimumSeedSpec spec = Phase2MinimumSeedSpec.production();

        assertThat(spec.customerCount()).isEqualTo(10_000);
        assertThat(spec.accountCount()).isEqualTo(30_000);
        assertThat(spec.ledgerEntryCount()).isEqualTo(500_000);
        assertThat(spec.autoTransferCount()).isEqualTo(5_000);
        assertThat(spec.nearMaturityAccountCount()).isEqualTo(100);
        assertThat(spec.transferCount()).isEqualTo(235_000);
        assertThat(spec.customerIdStart()).isEqualTo(6_000_001L);
        assertThat(spec.accountIdStart()).isEqualTo(60_000_001L);
        assertThat(spec.accountBankCode()).isEqualTo("860");
        assertThat(spec.baseDateTime()).isEqualTo(BASE_TIME);
    }

    @Test
    @DisplayName("고객당 계좌 세 개 규칙을 위반하면 규격 생성을 거부한다")
    void rejectsInvalidAccountCount() {
        assertThatIllegalArgumentException().isThrownBy(() -> spec(2, 5, 9, 0, 0, "860", BASE_TIME));
    }

    @Test
    @DisplayName("원장 행이 초기 기표와 이체 쌍으로 구성되지 않으면 거부한다")
    void rejectsInvalidLedgerCount() {
        assertThatIllegalArgumentException().isThrownBy(() -> spec(2, 6, 7, 0, 0, "860", BASE_TIME));
    }

    @Test
    @DisplayName("자동이체 건수가 고객 수를 넘으면 거부한다")
    void rejectsInvalidAutoTransferCount() {
        assertThatIllegalArgumentException().isThrownBy(() -> spec(2, 6, 8, 3, 0, "860", BASE_TIME));
    }

    @Test
    @DisplayName("만기 임박 계좌 건수가 고객 수를 넘으면 거부한다")
    void rejectsInvalidNearMaturityCount() {
        assertThatIllegalArgumentException().isThrownBy(() -> spec(2, 6, 8, 0, 3, "860", BASE_TIME));
    }

    @Test
    @DisplayName("은행코드와 기준 시각이 없으면 거부한다")
    void rejectsInvalidIdentityRules() {
        assertThatIllegalArgumentException().isThrownBy(() -> spec(2, 6, 8, 0, 0, "86", BASE_TIME));
        assertThatIllegalArgumentException().isThrownBy(() -> spec(2, 6, 8, 0, 0, null, BASE_TIME));
        assertThatIllegalArgumentException().isThrownBy(() -> spec(2, 6, 8, 0, 0, "860", null));
    }

    private Phase2MinimumSeedSpec spec(
            int customers,
            int accounts,
            int ledgerEntries,
            int autoTransfers,
            int nearMaturityAccounts,
            String bankCode,
            LocalDateTime baseTime) {
        return new Phase2MinimumSeedSpec(
                customers,
                accounts,
                ledgerEntries,
                autoTransfers,
                nearMaturityAccounts,
                8_100_001L,
                81_000_001L,
                81_000_001L,
                81_000_001L,
                81_000_001L,
                8_100_000_001L,
                bankCode,
                baseTime);
    }
}
