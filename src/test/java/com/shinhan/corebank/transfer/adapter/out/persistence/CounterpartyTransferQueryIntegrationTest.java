package com.shinhan.corebank.transfer.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.transfer.api.CounterpartyTransfer;
import com.shinhan.corebank.transfer.api.CounterpartyTransfer.CounterpartyKind;
import com.shinhan.corebank.transfer.api.CounterpartyTransfer.Direction;
import com.shinhan.corebank.transfer.api.CounterpartyTransferQuery;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@DisplayName("계좌 기준 이체 이력 조회 계약(CounterpartyTransferQuery) 통합 테스트")
class CounterpartyTransferQueryIntegrationTest extends IntegrationTestSupport {

    // 시드와 다른 테스트가 쓰지 않는 계좌 ID, 원장 ID, 거래번호 대역(893xxx, 2031-07).
    private static final long TARGET = 893_101L;
    private static final long SENDER = 101L;
    private static final long PAYEE = 202L;
    private static final LocalDateTime T0 = LocalDateTime.of(2031, 7, 1, 10, 0);
    private static final LocalDateTime FROM = T0;
    private static final LocalDateTime TO = T0.plusDays(1);

    @Autowired
    private CounterpartyTransferQuery query;

    @Autowired
    private EntityManager entityManager;

    private int nextSerial = 1;
    private long nextLedgerId = 893_100_001L;

    @BeforeEach
    void setUp() {
        TransferTestFixtures.seedCustomerAndAccounts(entityManager);
        insertAccount(TARGET, "110893100001");
    }

    @Test
    @DisplayName("당행 입금, 당행 출금, 타행 출금을 상대 계좌와 함께 원장 시각 순으로 돌려준다")
    void returnsTransfersWithCounterpartyInTimeOrder() {
        String deposit = internal(SENDER, TARGET, 30_000L, T0.plusHours(1));
        String withdrawal = internal(TARGET, PAYEE, 10_000L, T0.plusHours(2));
        String interbank = interbank(TARGET, 5_000L, "SUCCESS", T0.plusHours(3));

        assertThat(query.findByAccount(TARGET, FROM, TO))
                .extracting(
                        CounterpartyTransfer::transactionNumber,
                        CounterpartyTransfer::direction,
                        CounterpartyTransfer::counterpartyKind,
                        CounterpartyTransfer::counterpartyAccountId,
                        CounterpartyTransfer::amount,
                        CounterpartyTransfer::occurredAt)
                .containsExactly(
                        tuple(deposit, Direction.IN, CounterpartyKind.INTERNAL, SENDER, 30_000L, T0.plusHours(1)),
                        tuple(withdrawal, Direction.OUT, CounterpartyKind.INTERNAL, PAYEE, 10_000L, T0.plusHours(2)),
                        tuple(interbank, Direction.OUT, CounterpartyKind.INTERBANK, null, 5_000L, T0.plusHours(3)));

        // 같은 거래를 송금인 계좌에서 보면 출금이고 상대는 조회 계좌다
        assertThat(query.findByAccount(SENDER, FROM, TO))
                .extracting(
                        CounterpartyTransfer::transactionNumber,
                        CounterpartyTransfer::direction,
                        CounterpartyTransfer::counterpartyAccountId)
                .containsExactly(tuple(deposit, Direction.OUT, TARGET));
    }

    @Test
    @DisplayName("처리 불명, 무효화된 원거래, 취소정정, 이체가 아닌 입금은 빼고 정상거래(REPOST)는 담는다")
    void excludesUnknownCorrectedAndNonTransferEntries() {
        interbank(TARGET, 7_000L, "TIMEOUT", T0.plusHours(1));

        long original = insertTransfer(SENDER, TARGET, null, 20_000L, "SUCCESS", T0.plusHours(2), null, null);
        String originalNumber = transactionNumberOf(original);
        insertLedger(SENDER, original, originalNumber, "WITHDRAWAL", 20_000L, T0.plusHours(2));
        insertLedger(TARGET, original, originalNumber, "DEPOSIT", 20_000L, T0.plusHours(2));
        markInvalidated(original, T0.plusHours(3));

        long reversal = insertTransfer(TARGET, SENDER, null, 20_000L, "SUCCESS", T0.plusHours(3), original, "REVERSAL");
        insertLedger(TARGET, reversal, transactionNumberOf(reversal), "WITHDRAWAL", 20_000L, T0.plusHours(3));

        long repost = insertTransfer(SENDER, TARGET, null, 2_000L, "SUCCESS", T0.plusHours(4), original, "REPOST");
        String repostNumber = transactionNumberOf(repost);
        insertLedger(TARGET, repost, repostNumber, "DEPOSIT", 2_000L, T0.plusHours(4));

        insertLedger(TARGET, null, "20310701BT8930000099", "DEPOSIT", 70L, T0.plusHours(5));

        assertThat(query.findByAccount(TARGET, FROM, TO))
                .extracting(CounterpartyTransfer::transactionNumber, CounterpartyTransfer::amount)
                .containsExactly(tuple(repostNumber, 2_000L));
    }

    @Test
    @DisplayName("기간은 시작 시각을 포함하고 끝 시각을 포함하지 않는다")
    void periodIncludesFromAndExcludesTo() {
        internal(SENDER, TARGET, 1_000L, FROM.minusNanos(1_000));
        String atFrom = internal(SENDER, TARGET, 2_000L, FROM);
        String beforeTo = internal(SENDER, TARGET, 3_000L, TO.minusNanos(1_000));
        internal(SENDER, TARGET, 4_000L, TO);

        assertThat(query.findByAccount(TARGET, FROM, TO))
                .extracting(CounterpartyTransfer::transactionNumber)
                .containsExactly(atFrom, beforeTo);
    }

    @Test
    @DisplayName("기간이 비었거나 빠졌으면 거부한다")
    void rejectsInvalidPeriod() {
        assertThatThrownBy(() -> query.findByAccount(TARGET, FROM, FROM))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.INVALID_INPUT);
        assertThatThrownBy(() -> query.findByAccount(TARGET, null, TO))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING);
    }

    // 당행 이체 1건과 원장 2행(출금, 입금)
    private String internal(long from, long to, long amount, LocalDateTime at) {
        long transferId = insertTransfer(from, to, null, amount, "SUCCESS", at, null, null);
        String transactionNumber = transactionNumberOf(transferId);
        insertLedger(from, transferId, transactionNumber, "WITHDRAWAL", amount, at);
        insertLedger(to, transferId, transactionNumber, "DEPOSIT", amount, at);
        return transactionNumber;
    }

    // 타행 출금 1건과 원장 1행. 처리 불명이어도 출금 행은 남는다
    private String interbank(long from, long amount, String status, LocalDateTime at) {
        long transferId = insertTransfer(from, null, "088", amount, status, at, null, null);
        String transactionNumber = transactionNumberOf(transferId);
        insertLedger(from, transferId, transactionNumber, "WITHDRAWAL", amount, at);
        return transactionNumber;
    }

    private void insertAccount(long accountId, String accountNumber) {
        entityManager
                .createNativeQuery(
                        """
                        INSERT INTO account (account_id, account_number, customer_id, product_id, account_type, balance, status,
                                             password_hash, withdrawal_registered, withdrawal_registered_at, opened_date,
                                             created_at, updated_at)
                        VALUES (:id, :number, 1, NULL, 'DEMAND_DEPOSIT', 100000, 'ACTIVE',
                                '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklm', TRUE, NOW(6), '2026-08-01',
                                NOW(6), NOW(6))
                        """)
                .setParameter("id", accountId)
                .setParameter("number", accountNumber)
                .executeUpdate();
    }

    private long insertTransfer(
            long withdrawalAccountId,
            Long depositAccountId,
            String payeeBankCode,
            long amount,
            String status,
            LocalDateTime transferredAt,
            Long refTransferId,
            String correctionType) {
        String transactionNumber = String.format("20310701WB893%07d", nextSerial++);
        entityManager
                .createNativeQuery(
                        """
                        INSERT INTO transfer (transaction_number, withdrawal_account_id, deposit_account_id,
                                              deposit_account_number, payee_bank_code, payee_name, amount, fee,
                                              transfer_type, channel, status, transferred_at, created_at,
                                              ref_transfer_id, correction_type)
                        VALUES (:txNo, :from, :to, '110893999999', :bankCode, '홍길동', :amount, 0,
                                'IMMEDIATE', 'WB', :status, :at, NOW(6), :ref, :correction)
                        """)
                .setParameter("txNo", transactionNumber)
                .setParameter("from", withdrawalAccountId)
                .setParameter("to", depositAccountId)
                .setParameter("bankCode", payeeBankCode)
                .setParameter("amount", amount)
                .setParameter("status", status)
                .setParameter("at", transferredAt)
                .setParameter("ref", refTransferId)
                .setParameter("correction", correctionType)
                .executeUpdate();
        return ((Number) entityManager
                        .createNativeQuery("SELECT transfer_id FROM transfer WHERE transaction_number = :txNo")
                        .setParameter("txNo", transactionNumber)
                        .getSingleResult())
                .longValue();
    }

    private String transactionNumberOf(long transferId) {
        return (String) entityManager
                .createNativeQuery("SELECT transaction_number FROM transfer WHERE transfer_id = :id")
                .setParameter("id", transferId)
                .getSingleResult();
    }

    private void insertLedger(
            long accountId,
            Long transferId,
            String transactionNumber,
            String direction,
            long amount,
            LocalDateTime occurredAt) {
        entityManager
                .createNativeQuery(
                        """
                        INSERT INTO ledger_entry (ledger_entry_id, account_id, transfer_id, transaction_number, direction,
                                                  amount, balance_after, transaction_type, transaction_content, channel,
                                                  reversed, reversal_id, occurred_at, trade_date)
                        VALUES (:id, :accountId, :transferId, :txNo, :direction, :amount, 0, :type, 'PA02', 'WB',
                                FALSE, NULL, :at, :tradeDate)
                        """)
                .setParameter("id", nextLedgerId++)
                .setParameter("accountId", accountId)
                .setParameter("transferId", transferId)
                .setParameter("txNo", transactionNumber)
                .setParameter("direction", direction)
                .setParameter("amount", amount)
                .setParameter("type", transferId == null ? "INTEREST" : "IMMEDIATE_TRANSFER")
                .setParameter("at", occurredAt)
                .setParameter("tradeDate", occurredAt.toLocalDate())
                .executeUpdate();
    }

    private void markInvalidated(long transferId, LocalDateTime at) {
        entityManager
                .createNativeQuery("UPDATE transfer SET invalidated_at = :at WHERE transfer_id = :id")
                .setParameter("at", at)
                .setParameter("id", transferId)
                .executeUpdate();
    }
}
