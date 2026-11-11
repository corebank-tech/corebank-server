package com.shinhan.corebank.transfer.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.transfer.api.RecipientHistory;
import com.shinhan.corebank.transfer.api.SucceededTransfer;
import com.shinhan.corebank.transfer.api.SucceededTransferHistoryQuery;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@DisplayName("성공 이체 이력·수취인 요약 조회 계약(SucceededTransferHistoryQuery) 통합 테스트")
class SucceededTransferHistoryQueryIntegrationTest extends IntegrationTestSupport {

    // 시드와 다른 테스트가 쓰지 않는 계좌 ID·거래번호 대역(995xxx, IT995, 2031-08-15)
    private static final long SECOND_ACCOUNT = 995_101L;
    private static final long OTHER_CUSTOMER_ACCOUNT = 995_201L;
    private static final String PAYEE = "110222222222";
    private static final String OTHER_PAYEE = "110995200001";
    private static final LocalDateTime T0 = LocalDateTime.of(2031, 8, 15, 10, 0);

    @Autowired
    private SucceededTransferHistoryQuery query;

    @Autowired
    private EntityManager entityManager;

    private int nextSerial = 1;

    @BeforeEach
    void setUp() {
        TransferTestFixtures.seedCustomerAndAccounts(entityManager);
        TransferTestFixtures.seedAnotherCustomer(entityManager);
        insertAccount(SECOND_ACCOUNT, "110995100001", 1L);
        insertAccount(OTHER_CUSTOMER_ACCOUNT, OTHER_PAYEE, 2L);
    }

    @Test
    @DisplayName("findRecent: [from, to) 안의 고객 당행 SUCCESS만 최신순으로 준다. 실패·무효화·취소정정·타행·다른 고객·기간 밖은 뺀다")
    void findRecent_returnsCustomersSucceededTransfersNewestFirst() {
        LocalDateTime to = T0.plusHours(1);
        long first = insertTransfer(101L, 202L, PAYEE, null, 5_000L, "SUCCESS", T0);
        long otherAccountOtherPayee = insertTransfer(
                SECOND_ACCOUNT, OTHER_CUSTOMER_ACCOUNT, OTHER_PAYEE, null, 6_000L, "SUCCESS", T0.plusMinutes(1));
        long invalidated = insertTransfer(101L, 202L, PAYEE, null, 7_000L, "SUCCESS", T0.plusMinutes(2));
        markInvalidated(invalidated);
        insertCorrection(101L, 202L, PAYEE, 7_000L, T0.plusMinutes(3), invalidated, "REVERSAL");
        insertCorrection(101L, 202L, PAYEE, 1_000L, T0.plusMinutes(4), invalidated, "REPOST");
        insertTransfer(101L, 202L, PAYEE, null, 90_000L, "ERROR", T0.plusMinutes(5));
        insertTransfer(101L, null, PAYEE, "004", 90_000L, "SUCCESS", T0.plusMinutes(6));
        insertTransfer(OTHER_CUSTOMER_ACCOUNT, 202L, PAYEE, null, 90_000L, "SUCCESS", T0.plusMinutes(7));
        insertTransfer(101L, 202L, PAYEE, null, 90_000L, "SUCCESS", T0.minusSeconds(1));
        insertTransfer(101L, 202L, PAYEE, null, 90_000L, "SUCCESS", to);

        List<SucceededTransfer> recent = query.findRecent(1L, T0, to, 10);

        assertThat(recent).hasSize(3);
        assertThat(recent.get(0).amount()).isEqualTo(1_000L);
        assertThat(recent.get(0).transferredAt()).isEqualTo(T0.plusMinutes(4));
        assertThat(recent.subList(1, 3))
                .extracting(SucceededTransfer::refId)
                .containsExactly(otherAccountOtherPayee, first);
        assertThat(recent.get(1).depositAccountNumber()).isEqualTo(OTHER_PAYEE);
        assertThat(recent.get(2).amount()).isEqualTo(5_000L);
    }

    @Test
    @DisplayName("findRecent: 같은 시각이면 나중에 생긴 이체가 먼저이고, limit건에서 끊는다")
    void findRecent_breaksTiesByRefIdAndStopsAtLimit() {
        long older = insertTransfer(101L, 202L, PAYEE, null, 1_000L, "SUCCESS", T0);
        long sameTimeFirst = insertTransfer(101L, 202L, PAYEE, null, 2_000L, "SUCCESS", T0.plusMinutes(1));
        long sameTimeSecond = insertTransfer(SECOND_ACCOUNT, 202L, PAYEE, null, 3_000L, "SUCCESS", T0.plusMinutes(1));

        assertThat(query.findRecent(1L, T0, T0.plusHours(1), 2))
                .extracting(SucceededTransfer::refId)
                .containsExactly(sameTimeSecond, sameTimeFirst);
        assertThat(query.findRecent(1L, T0, T0.plusHours(1), 3))
                .extracting(SucceededTransfer::refId)
                .containsExactly(sameTimeSecond, sameTimeFirst, older);
    }

    @Test
    @DisplayName("findRecent: 기간 안에 성공 이체가 없거나 계좌가 없는 고객이면 빈 목록이다")
    void findRecent_withoutRows_returnsEmpty() {
        insertTransfer(101L, 202L, PAYEE, null, 1_000L, "ERROR", T0);

        assertThat(query.findRecent(1L, T0, T0.plusHours(1), 10)).isEmpty();
        assertThat(query.findRecent(999_995L, T0, T0.plusHours(1), 10)).isEmpty();
    }

    @Test
    @DisplayName("summarizeRecipient: 최초 성공 시각은 기간 하한 없이 전체 이력에서 찾고, 다른 출금계좌 수는 제외 계좌를 빼고 서로 다른 계좌만 센다")
    void summarizeRecipient_firstSuccessOverWholeHistory_andDistinctOtherAccounts() {
        insertTransfer(101L, 202L, PAYEE, null, 1_000L, "ERROR", T0.minusYears(2));
        long invalidated = insertTransfer(101L, 202L, PAYEE, null, 1_000L, "SUCCESS", T0.minusYears(2));
        markInvalidated(invalidated);
        insertTransfer(101L, null, PAYEE, "004", 1_000L, "SUCCESS", T0.minusYears(2));
        insertTransfer(SECOND_ACCOUNT, 202L, PAYEE, null, 1_000L, "SUCCESS", T0.minusYears(1));
        insertTransfer(SECOND_ACCOUNT, 202L, PAYEE, null, 1_000L, "SUCCESS", T0);
        insertTransfer(101L, 202L, PAYEE, null, 1_000L, "SUCCESS", T0.plusMinutes(1));
        insertTransfer(OTHER_CUSTOMER_ACCOUNT, 202L, PAYEE, null, 1_000L, "SUCCESS", T0.minusYears(3));
        insertTransfer(SECOND_ACCOUNT, OTHER_CUSTOMER_ACCOUNT, OTHER_PAYEE, null, 1_000L, "SUCCESS", T0.minusYears(3));

        RecipientHistory history = query.summarizeRecipient(1L, PAYEE, 101L);

        assertThat(history.firstSucceededAt()).isEqualTo(T0.minusYears(1));
        assertThat(history.otherWithdrawalAccountCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("summarizeRecipient: 제외 계좌로만 보냈으면 시각은 있고 수는 0, 보낸 적 없으면 시각 null·수 0이다")
    void summarizeRecipient_onlyExcludedAccountOrNever() {
        insertTransfer(101L, 202L, PAYEE, null, 1_000L, "SUCCESS", T0);

        assertThat(query.summarizeRecipient(1L, PAYEE, 101L)).isEqualTo(new RecipientHistory(T0, 0));
        assertThat(query.summarizeRecipient(1L, OTHER_PAYEE, 101L)).isEqualTo(new RecipientHistory(null, 0));
        assertThat(query.summarizeRecipient(999_995L, PAYEE, 101L)).isEqualTo(new RecipientHistory(null, 0));
    }

    @Test
    @DisplayName("findRecent: limit이 0 이하이거나 from이 to보다 늦거나 같으면 호출 결함으로 거부한다")
    void findRecent_invalidArguments_areRejected() {
        assertThatThrownBy(() -> query.findRecent(1L, T0, T0.plusHours(1), 0))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(CommonErrorCode.INTERNAL_ERROR));
        assertThatThrownBy(() -> query.findRecent(1L, T0, T0, 10))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(CommonErrorCode.INTERNAL_ERROR));
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
        String transactionNumber = String.format("20310815IT995%07d", nextSerial++);
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
                .setParameter("at", T0)
                .setParameter("id", transferId)
                .executeUpdate();
    }
}
