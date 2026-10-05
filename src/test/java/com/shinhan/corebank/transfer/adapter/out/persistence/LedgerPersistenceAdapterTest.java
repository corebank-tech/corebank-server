package com.shinhan.corebank.transfer.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.transfer.application.port.out.PostedLedgerPair;
import com.shinhan.corebank.transfer.domain.LedgerDirection;
import com.shinhan.corebank.transfer.domain.LedgerPair;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class LedgerPersistenceAdapterTest extends IntegrationTestSupport {

    @Autowired
    private LedgerPersistenceAdapter adapter;

    @Autowired
    private LedgerEntryJpaRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("LedgerPair를 저장하면 출금·입금 2행이 서로 다른 ledgerEntryId로 저장된다")
    void save_persistsBothWithdrawalAndDepositEntries() {
        // given
        LedgerPair pair = LedgerPair.forTransfer(
                500L,
                "20260812WB0000000003",
                101L,
                90000L,
                202L,
                110000L,
                10000L,
                "IMMEDIATE_TRANSFER",
                "이체출금",
                "이체입금",
                TransferChannel.WB,
                LocalDateTime.now());

        // when
        adapter.save(pair);
        entityManager.flush();
        entityManager.clear();

        // then
        List<LedgerEntryJpaEntity> entries = repository.findByTransactionNumber("20260812WB0000000003");
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).getLedgerEntryId())
                .isNotEqualTo(entries.get(1).getLedgerEntryId());

        LedgerEntryJpaEntity withdrawal = entries.stream()
                .filter(e -> e.getDirection() == LedgerDirection.WITHDRAWAL)
                .findFirst()
                .orElseThrow();
        LedgerEntryJpaEntity deposit = entries.stream()
                .filter(e -> e.getDirection() == LedgerDirection.DEPOSIT)
                .findFirst()
                .orElseThrow();

        assertThat(withdrawal.getAccountId()).isEqualTo(101L);
        assertThat(withdrawal.getBalanceAfter()).isEqualTo(90000L);
        assertThat(deposit.getAccountId()).isEqualTo(202L);
        assertThat(deposit.getBalanceAfter()).isEqualTo(110000L);
        assertThat(withdrawal.getTransferId()).isEqualTo(500L);
        assertThat(deposit.getTransferId()).isEqualTo(500L);
    }

    private static final String ORIGINAL_TXNO = "20260812WB0000000548";
    private static final LocalDateTime ORIGINAL_AT = LocalDateTime.of(2026, 8, 12, 10, 0, 0);

    private void saveOriginal() {
        adapter.save(LedgerPair.forTransfer(
                548L,
                ORIGINAL_TXNO,
                101L,
                90000L,
                202L,
                110000L,
                10000L,
                "IMMEDIATE_TRANSFER",
                "이체출금",
                "이체입금",
                TransferChannel.WB,
                ORIGINAL_AT));
        entityManager.flush();
        entityManager.clear();
    }

    private LedgerPair reversalOf(PostedLedgerPair original, String transactionNumber) {
        return LedgerPair.forReversal(
                549L,
                transactionNumber,
                original.withdrawal(),
                original.deposit(),
                100000L,
                100000L,
                TransferChannel.BT,
                ORIGINAL_AT.plusDays(1));
    }

    @Test
    @DisplayName("거래번호로 원장 출금·입금 행을 ID와 함께 한 쌍으로 조회한다")
    void findPostedPair_returnsWithdrawalAndDepositWithIds() {
        // given
        saveOriginal();

        // when
        PostedLedgerPair pair = adapter.findPostedPair(ORIGINAL_TXNO).orElseThrow();

        // then
        assertThat(pair.withdrawal().getDirection()).isEqualTo(LedgerDirection.WITHDRAWAL);
        assertThat(pair.withdrawal().getAccountId()).isEqualTo(101L);
        assertThat(pair.withdrawal().getLedgerEntryId()).isNotNull();
        assertThat(pair.deposit().getDirection()).isEqualTo(LedgerDirection.DEPOSIT);
        assertThat(pair.deposit().getAccountId()).isEqualTo(202L);
        assertThat(pair.deposit().getLedgerEntryId()).isNotNull();
        assertThat(adapter.findPostedPair("20260812WB0000009999")).isEmpty();
    }

    @Test
    @DisplayName("반대기표를 저장하면 반대기표 2행이 생기고 원거래 2행에 reversed가 세워진다 — 삭제 0건")
    void saveReversal_insertsReversalAndMarksOriginalReversed() {
        // given
        saveOriginal();
        PostedLedgerPair original = adapter.findPostedPair(ORIGINAL_TXNO).orElseThrow();

        // when
        adapter.saveReversal(reversalOf(original, "20260813BT0000000548"), original);
        entityManager.flush();
        entityManager.clear();

        // then: 원거래 2행은 남아 있고 reversed만 바뀌었다
        List<LedgerEntryJpaEntity> originals = repository.findByTransactionNumber(ORIGINAL_TXNO);
        assertThat(originals).hasSize(2).allSatisfy(e -> assertThat(e.isReversed())
                .isTrue());
        assertThat(originals)
                .extracting(LedgerEntryJpaEntity::getBalanceAfter)
                .containsExactlyInAnyOrder(90000L, 110000L);

        // then: 반대기표 2행은 각자 같은 계좌의 원거래 행을 가리킨다
        List<LedgerEntryJpaEntity> reversals = repository.findByTransactionNumber("20260813BT0000000548");
        assertThat(reversals).hasSize(2).allSatisfy(e -> assertThat(e.getTransactionType())
                .isEqualTo("REVERSAL"));
        LedgerEntryJpaEntity fromPayee = reversals.stream()
                .filter(e -> e.getDirection() == LedgerDirection.WITHDRAWAL)
                .findFirst()
                .orElseThrow();
        assertThat(fromPayee.getAccountId()).isEqualTo(202L);
        assertThat(fromPayee.getReversalId()).isEqualTo(original.deposit().getLedgerEntryId());
    }

    @Test
    @DisplayName("이미 취소된 원장을 다시 취소하면 TRF0305로 거부된다")
    void saveReversal_twice_throws() {
        // given
        saveOriginal();
        PostedLedgerPair original = adapter.findPostedPair(ORIGINAL_TXNO).orElseThrow();
        adapter.saveReversal(reversalOf(original, "20260813BT0000000548"), original);
        entityManager.flush();
        entityManager.clear();

        // when & then
        assertThatThrownBy(() -> adapter.saveReversal(reversalOf(original, "20260813BT0000000549"), original))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(TransferErrorCode.NOT_CORRECTABLE);
    }
}
