package com.shinhan.corebank.transfer.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.transfer.application.port.out.RecentDepositAccount;
import com.shinhan.corebank.transfer.application.port.out.RecentTransferAccountQueryPort;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@DisplayName("최근 이체계좌 조회(RecentTransferAccountQueryPort) 통합 테스트")
class RecentTransferAccountQueryIntegrationTest extends IntegrationTestSupport {

    // 시드와 다른 테스트가 쓰지 않는 계좌 ID·계좌번호·거래번호 대역(1970xx·1971xx, 2031-07).
    private static final long MY_SECOND_ACCOUNT = 197_001L;
    private static final long PAYEE_A = 197_101L;
    private static final long PAYEE_B = 197_102L;
    private static final String PAYEE_A_NUMBER = "110197100001";
    private static final String PAYEE_B_NUMBER = "110197100002";
    private static final LocalDateTime SINCE = LocalDateTime.of(2031, 7, 1, 10, 0);

    @Autowired
    private RecentTransferAccountQueryPort port;

    @Autowired
    private EntityManager entityManager;

    private int nextSerial = 1;

    @BeforeEach
    void setUp() {
        TransferTestFixtures.seedCustomerAndAccounts(entityManager);
        TransferTestFixtures.seedAnotherCustomer(entityManager);
        insertAccount(MY_SECOND_ACCOUNT, "110197000001", 1L);
        insertAccount(PAYEE_A, PAYEE_A_NUMBER, 2L);
        insertAccount(PAYEE_B, PAYEE_B_NUMBER, 2L);
    }

    @Test
    @DisplayName("고객의 모든 계좌에서 보낸 성공 이체를 입금계좌별 하나로 묶고, 마지막 이체 시각이 최신인 순으로 준다")
    void findRecentDepositAccounts_groupsByDepositAccountLatestFirst() {
        insertTransfer(101L, PAYEE_A, PAYEE_A_NUMBER, null, "SUCCESS", SINCE);
        insertTransfer(101L, PAYEE_B, PAYEE_B_NUMBER, null, "SUCCESS", SINCE.plusMinutes(1));
        insertTransfer(MY_SECOND_ACCOUNT, PAYEE_A, PAYEE_A_NUMBER, null, "SUCCESS", SINCE.plusMinutes(2));

        List<RecentDepositAccount> result = port.findRecentDepositAccounts(1L, SINCE, 5);

        assertThat(result)
                .containsExactly(
                        new RecentDepositAccount(PAYEE_A_NUMBER, SINCE.plusMinutes(2)),
                        new RecentDepositAccount(PAYEE_B_NUMBER, SINCE.plusMinutes(1)));
    }

    @Test
    @DisplayName("내 계좌로 보낸 이체·실패·무효화·취소정정·타행·since 이전·다른 고객의 이체는 뺀다")
    void findRecentDepositAccounts_excludesNonTargetTransfers() {
        insertTransfer(101L, 202L, "110222222222", null, "SUCCESS", SINCE.plusMinutes(1));
        insertTransfer(101L, PAYEE_A, PAYEE_A_NUMBER, null, "ERROR", SINCE.plusMinutes(2));
        long invalidated = insertTransfer(101L, PAYEE_A, PAYEE_A_NUMBER, null, "SUCCESS", SINCE.plusMinutes(3));
        markInvalidated(invalidated);
        insertCorrection(101L, PAYEE_A, PAYEE_A_NUMBER, SINCE.plusMinutes(4), invalidated, "REVERSAL");
        insertTransfer(101L, null, "3333010000001", "088", "SUCCESS", SINCE.plusMinutes(5));
        insertTransfer(101L, PAYEE_A, PAYEE_A_NUMBER, null, "SUCCESS", SINCE.minusSeconds(1));
        insertTransfer(PAYEE_B, PAYEE_A, PAYEE_A_NUMBER, null, "SUCCESS", SINCE.plusMinutes(6));

        assertThat(port.findRecentDepositAccounts(1L, SINCE, 5)).isEmpty();
    }

    @Test
    @DisplayName("limit보다 많으면 최신 limit건만 준다")
    void findRecentDepositAccounts_appliesLimit() {
        insertTransfer(101L, PAYEE_A, PAYEE_A_NUMBER, null, "SUCCESS", SINCE);
        insertTransfer(101L, PAYEE_B, PAYEE_B_NUMBER, null, "SUCCESS", SINCE.plusMinutes(1));

        assertThat(port.findRecentDepositAccounts(1L, SINCE, 1))
                .containsExactly(new RecentDepositAccount(PAYEE_B_NUMBER, SINCE.plusMinutes(1)));
    }

    @Test
    @DisplayName("계좌가 없는 고객이면 빈 목록이다")
    void findRecentDepositAccounts_withoutAccounts_returnsEmpty() {
        assertThat(port.findRecentDepositAccounts(999_197L, SINCE, 5)).isEmpty();
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
            String status,
            LocalDateTime transferredAt) {
        return insert(
                withdrawalAccountId,
                depositAccountId,
                depositAccountNumber,
                payeeBankCode,
                status,
                transferredAt,
                null,
                null);
    }

    private void insertCorrection(
            long withdrawalAccountId,
            long depositAccountId,
            String depositAccountNumber,
            LocalDateTime transferredAt,
            long refTransferId,
            String correctionType) {
        insert(
                withdrawalAccountId,
                depositAccountId,
                depositAccountNumber,
                null,
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
            String status,
            LocalDateTime transferredAt,
            Long refTransferId,
            String correctionType) {
        String transactionNumber = String.format("20310701IT197%07d", nextSerial++);
        entityManager
                .createNativeQuery(
                        """
                        INSERT INTO transfer (transaction_number, withdrawal_account_id, deposit_account_id,
                                              deposit_account_number, payee_bank_code, payee_name, amount, fee,
                                              transfer_type, channel, status, transferred_at, created_at,
                                              ref_transfer_id, correction_type)
                        VALUES (:txNo, :from, :to, :toNumber, :bankCode, '홍길동', 10000, 0,
                                'IMMEDIATE', 'WB', :status, :at, NOW(6), :ref, :correction)
                        """)
                .setParameter("txNo", transactionNumber)
                .setParameter("from", withdrawalAccountId)
                .setParameter("to", depositAccountId)
                .setParameter("toNumber", depositAccountNumber)
                .setParameter("bankCode", payeeBankCode)
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
