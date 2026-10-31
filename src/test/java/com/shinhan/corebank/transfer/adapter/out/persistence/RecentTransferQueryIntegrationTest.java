package com.shinhan.corebank.transfer.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.transfer.api.RecentTransferAggregate;
import com.shinhan.corebank.transfer.api.RecentTransferQuery;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@DisplayName("수취계좌별 성공 이체 조회 계약(RecentTransferQuery) 통합 테스트")
class RecentTransferQueryIntegrationTest extends IntegrationTestSupport {

    // 시드와 다른 테스트가 쓰지 않는 계좌 ID·거래번호 대역(7711xx, 2031-06).
    private static final long SECOND_ACCOUNT = 771_101L;
    private static final long OTHER_CUSTOMER_ACCOUNT = 771_201L;
    private static final String PAYEE = "110222222222";
    private static final String OTHER_PAYEE = "110771200001";
    private static final LocalDateTime SINCE = LocalDateTime.of(2031, 6, 1, 10, 0);

    @Autowired
    private RecentTransferQuery query;

    @Autowired
    private EntityManager entityManager;

    private int nextSerial = 1;

    @BeforeEach
    void setUp() {
        TransferTestFixtures.seedCustomerAndAccounts(entityManager);
        TransferTestFixtures.seedAnotherCustomer(entityManager);
        insertAccount(SECOND_ACCOUNT, "110771100001", 1L);
        insertAccount(OTHER_CUSTOMER_ACCOUNT, OTHER_PAYEE, 2L);
    }

    @Test
    @DisplayName("고객의 모든 출금계좌에서 since 이후(포함) 보낸 당행 SUCCESS만 세고, 실패·무효화·타행·취소정정·다른 수취인·다른 고객은 뺀다")
    void aggregateSucceeded_countsOnlyCustomersSuccessfulTransfersToPayee() {
        insertTransfer(101L, 202L, PAYEE, null, 5_000L, "SUCCESS", SINCE);
        insertTransfer(101L, 202L, PAYEE, null, 10_000L, "SUCCESS", SINCE.plusMinutes(1));
        insertTransfer(SECOND_ACCOUNT, 202L, PAYEE, null, 20_000L, "SUCCESS", SINCE.plusMinutes(2));
        long invalidated = insertTransfer(101L, 202L, PAYEE, null, 7_000L, "SUCCESS", SINCE.plusMinutes(3));
        markInvalidated(invalidated);
        insertCorrection(101L, 202L, PAYEE, 7_000L, SINCE.plusMinutes(4), invalidated, "REVERSAL");
        insertCorrection(101L, 202L, PAYEE, 1_000L, SINCE.plusMinutes(5), invalidated, "REPOST");

        insertTransfer(101L, 202L, PAYEE, null, 90_000L, "ERROR", SINCE.plusMinutes(6));
        insertTransfer(101L, null, PAYEE, "088", 90_000L, "SUCCESS", SINCE.plusMinutes(7));
        insertTransfer(101L, OTHER_CUSTOMER_ACCOUNT, OTHER_PAYEE, null, 90_000L, "SUCCESS", SINCE.plusMinutes(8));
        insertTransfer(OTHER_CUSTOMER_ACCOUNT, 202L, PAYEE, null, 90_000L, "SUCCESS", SINCE.plusMinutes(9));
        insertTransfer(101L, 202L, PAYEE, null, 90_000L, "SUCCESS", SINCE.minusSeconds(1));

        RecentTransferAggregate aggregate = query.aggregateSucceeded(1L, PAYEE, SINCE);

        assertThat(aggregate.count()).isEqualTo(4);
        assertThat(aggregate.totalAmount()).isEqualTo(36_000L);
        assertThat(aggregate.firstTransferredAt()).isEqualTo(SINCE);
    }

    @Test
    @DisplayName("성공 이체가 없거나 계좌가 없는 고객이면 건수 0·합계 0·첫 시각 null이다")
    void aggregateSucceeded_withoutRows_returnsEmpty() {
        insertTransfer(101L, 202L, PAYEE, null, 90_000L, "ERROR", SINCE.plusMinutes(1));

        assertThat(query.aggregateSucceeded(1L, PAYEE, SINCE)).isEqualTo(RecentTransferAggregate.empty());
        assertThat(query.aggregateSucceeded(999_771L, PAYEE, SINCE)).isEqualTo(RecentTransferAggregate.empty());
    }

    @Test
    @DisplayName("before 이전(미포함)에 성공 이체가 있어야 true다. 실패 이체만 있으면 false다")
    void hasSucceededBefore_isExclusiveAndSuccessOnly() {
        insertTransfer(101L, 202L, PAYEE, null, 10_000L, "ERROR", SINCE.minusDays(1));
        insertTransfer(SECOND_ACCOUNT, 202L, PAYEE, null, 10_000L, "SUCCESS", SINCE);

        assertThat(query.hasSucceededBefore(1L, PAYEE, SINCE)).isFalse();
        assertThat(query.hasSucceededBefore(1L, PAYEE, SINCE.plusNanos(1_000))).isTrue();
        assertThat(query.hasSucceededBefore(2L, PAYEE, SINCE.plusDays(1))).isFalse();
    }

    private void insertAccount(long accountId, String accountNumber, long customerId) {
        entityManager
                .createNativeQuery(
                        """
                        INSERT INTO account (account_id, account_number, customer_id, product_id, account_type, balance, status,
                                             password_hash, withdrawal_registered, withdrawal_registered_at, opened_date,
                                             created_at, updated_at)
                        VALUES (:id, :number, :customerId, NULL, 'DEMAND_DEPOSIT', 100000, 'ACTIVE',
                                '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklm', TRUE, NOW(6), '2026-08-01',
                                NOW(6), NOW(6))
                        """)
                .setParameter("id", accountId)
                .setParameter("number", accountNumber)
                .setParameter("customerId", customerId)
                .executeUpdate();
    }

    private long insertTransfer(
            long withdrawalAccountId,
            Long depositAccountId,
            String depositAccountNumber,
            String payeeBankCode,
            long amount,
            String status,
            LocalDateTime transferredAt) {
        return insert(
                withdrawalAccountId,
                depositAccountId,
                depositAccountNumber,
                payeeBankCode,
                amount,
                status,
                transferredAt,
                null,
                null);
    }

    private void insertCorrection(
            long withdrawalAccountId,
            long depositAccountId,
            String depositAccountNumber,
            long amount,
            LocalDateTime transferredAt,
            long refTransferId,
            String correctionType) {
        insert(
                withdrawalAccountId,
                depositAccountId,
                depositAccountNumber,
                null,
                amount,
                "SUCCESS",
                transferredAt,
                refTransferId,
                correctionType);
    }

    private long insert(
            long withdrawalAccountId,
            Long depositAccountId,
            String depositAccountNumber,
            String payeeBankCode,
            long amount,
            String status,
            LocalDateTime transferredAt,
            Long refTransferId,
            String correctionType) {
        String transactionNumber = String.format("20310601IT771%07d", nextSerial++);
        entityManager
                .createNativeQuery(
                        """
                        INSERT INTO transfer (transaction_number, withdrawal_account_id, deposit_account_id,
                                              deposit_account_number, payee_bank_code, payee_name, amount, fee,
                                              transfer_type, channel, status, transferred_at, created_at,
                                              ref_transfer_id, correction_type)
                        VALUES (:txNo, :from, :to, :toNumber, :bankCode, '홍길동', :amount, 0,
                                'IMMEDIATE', 'WB', :status, :at, NOW(6), :ref, :correction)
                        """)
                .setParameter("txNo", transactionNumber)
                .setParameter("from", withdrawalAccountId)
                .setParameter("to", depositAccountId)
                .setParameter("toNumber", depositAccountNumber)
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

    private void markInvalidated(long transferId) {
        entityManager
                .createNativeQuery("UPDATE transfer SET invalidated_at = :at WHERE transfer_id = :id")
                .setParameter("at", SINCE.plusMinutes(4))
                .setParameter("id", transferId)
                .executeUpdate();
    }
}
