package com.shinhan.infoetl.adapter.out.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.infoetl.InfoEtlTestDatabase;
import com.shinhan.infoetl.ReplicaFixtures;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JdbcReplicaReconciliationAdapterTest {

    private static final LocalDate D = LocalDate.of(2026, 10, 20);

    private final JdbcReplicaReconciliationAdapter adapter =
            new JdbcReplicaReconciliationAdapter(InfoEtlTestDatabase::replica);

    @BeforeEach
    void resetReplica() throws SQLException {
        InfoEtlTestDatabase.resetReplica();
        ReplicaFixtures.insertAccount(1L, 11L, null, "DEMAND_DEPOSIT", "ACTIVE");
        ReplicaFixtures.insertAccount(2L, 12L, null, "DEMAND_DEPOSIT", "ACTIVE");
    }

    @Test
    @DisplayName("원장 합계는 입금 합계에서 출금 합계를 뺀 값이다")
    void ledgerTotalIsDepositsMinusWithdrawals() throws SQLException {
        ReplicaFixtures.insertLedger(1L, 1L, 1_000L, LocalDateTime.of(2026, 10, 19, 9, 0), D.minusDays(1));
        ReplicaFixtures.insertLedger(2L, 1L, 400L, LocalDateTime.of(2026, 10, 20, 9, 0), D); // 600 출금
        ReplicaFixtures.insertLedger(3L, 2L, 250L, LocalDateTime.of(2026, 10, 20, 10, 0), D);

        assertThat(adapter.readLedgerTotal(D)).isEqualTo(650L);
    }

    @Test
    @DisplayName("거래일이 기준일 뒤인 기표는 원장 합계에 넣지 않는다")
    void ledgerTotalIgnoresEntriesAfterBaseDate() throws SQLException {
        ReplicaFixtures.insertLedger(1L, 1L, 1_000L, LocalDateTime.of(2026, 10, 20, 9, 0), D);
        ReplicaFixtures.insertLedger(2L, 1L, 1_800L, LocalDateTime.of(2026, 10, 21, 9, 0), D.plusDays(1));

        assertThat(adapter.readLedgerTotal(D)).isEqualTo(1_000L);
        assertThat(adapter.readLedgerTotal(D.plusDays(1))).isEqualTo(1_800L);
    }

    @Test
    @DisplayName("거래일이 비어 있으면 주말 발생 기표를 다음 영업일로 올려 합계에 넣는다")
    void ledgerTotalRollsNullTradeDate() throws SQLException {
        ReplicaFixtures.insertLedger(1L, 1L, 500L, LocalDateTime.of(2026, 10, 17, 10, 0), null); // 토

        assertThat(adapter.readLedgerTotal(LocalDate.of(2026, 10, 16))).isZero();
        assertThat(adapter.readLedgerTotal(LocalDate.of(2026, 10, 19))).isEqualTo(500L);
    }

    @Test
    @DisplayName("원장이 없으면 합계는 0이다")
    void ledgerTotalIsZeroWithoutEntries() {
        assertThat(adapter.readLedgerTotal(D)).isZero();
    }

    @Test
    @DisplayName("GL 증감은 20100 계정의 대변에서 차변을 뺀 값이고 구간은 시작일 초과 종료일 이하다")
    void glMovementIsCreditMinusDebitWithinRange() throws SQLException {
        ReplicaFixtures.insertGlEntry("20100", "CREDIT", 900L, D.minusDays(1)); // 구간 밖(시작일)
        ReplicaFixtures.insertGlEntry("20100", "CREDIT", 800L, D);
        ReplicaFixtures.insertGlEntry("20100", "DEBIT", 50L, D);
        ReplicaFixtures.insertGlEntry("20100", "CREDIT", 70L, D.plusDays(1)); // 구간 밖(종료일 뒤)
        ReplicaFixtures.insertGlEntry("10100", "DEBIT", 99_999L, D); // 다른 계정

        assertThat(adapter.readGlDepositMovement(D.minusDays(1), D)).isEqualTo(750L);
    }

    @Test
    @DisplayName("구간에 분개가 없으면 GL 증감은 0이다")
    void glMovementIsZeroWithoutEntries() {
        assertThat(adapter.readGlDepositMovement(D.minusDays(1), D)).isZero();
    }
}
