package com.shinhan.corebank.autotransfer.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.autotransfer.application.port.out.TransferLookupResult;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class TransferLookupAdapterTest extends IntegrationTestSupport {

    @Autowired
    TransferLookupAdapter adapter;

    @Autowired
    EntityManager entityManager;

    private static final AtomicLong CUSTOMER_SEQ = new AtomicLong();
    private static final AtomicLong ACCOUNT_SEQ = new AtomicLong();

    @Test
    @DisplayName("성공으로 확정된 transfer 행이 있으면 그 status·거래번호로 반환한다")
    void findBySourceAndDate_success_returnsSuccessResult() {
        Long customerId = insertCustomer();
        Long withdrawalAccountId = insertAccount(customerId);
        Long depositAccountId = insertAccount(customerId);
        Long autoTransferId = 5001L;
        insertTransfer(withdrawalAccountId, depositAccountId, "20260315BT0000000001",
                ProcessResultStatus.SUCCESS, "AUTO", autoTransferId, null,
                LocalDateTime.of(2026, 3, 15, 0, 10, 0));

        Optional<TransferLookupResult> result = adapter.findBySourceAndDate(autoTransferId, LocalDate.of(2026, 3, 15));

        assertThat(result).isPresent();
        assertThat(result.get().transactionNumber()).isEqualTo("20260315BT0000000001");
        assertThat(result.get().status()).isEqualTo(ProcessResultStatus.SUCCESS);
        assertThat(result.get().errorMessage()).isNull();
    }

    @Test
    @DisplayName("ERROR로 확정된 transfer 행이 있으면 그 실패사유를 그대로 반환한다")
    void findBySourceAndDate_error_returnsErrorResult() {
        Long customerId = insertCustomer();
        Long withdrawalAccountId = insertAccount(customerId);
        Long depositAccountId = insertAccount(customerId);
        Long autoTransferId = 5002L;
        insertTransfer(withdrawalAccountId, depositAccountId, "20260315BT0000000002",
                ProcessResultStatus.ERROR, "AUTO", autoTransferId, "잔액 부족",
                LocalDateTime.of(2026, 3, 15, 0, 10, 0));

        Optional<TransferLookupResult> result = adapter.findBySourceAndDate(autoTransferId, LocalDate.of(2026, 3, 15));

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(ProcessResultStatus.ERROR);
        assertThat(result.get().errorMessage()).isEqualTo("잔액 부족");
    }

    @Test
    @DisplayName("해당 자동이체·날짜로 확정된 거래가 없으면 빈 Optional을 반환한다")
    void findBySourceAndDate_noMatch_returnsEmpty() {
        Optional<TransferLookupResult> result = adapter.findBySourceAndDate(9999L, LocalDate.of(2026, 3, 15));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("실행일자 범위(하루) 밖의 거래는 매칭되지 않는다")
    void findBySourceAndDate_outsideDateRange_returnsEmpty() {
        Long customerId = insertCustomer();
        Long withdrawalAccountId = insertAccount(customerId);
        Long depositAccountId = insertAccount(customerId);
        Long autoTransferId = 5003L;
        // 3/16 00:00:00 정각 - 3/15 범위(< 3/16 00:00:00)에 안 들어감
        insertTransfer(withdrawalAccountId, depositAccountId, "20260316BT0000000003",
                ProcessResultStatus.SUCCESS, "AUTO", autoTransferId, null,
                LocalDateTime.of(2026, 3, 16, 0, 0, 0));

        Optional<TransferLookupResult> result = adapter.findBySourceAndDate(autoTransferId, LocalDate.of(2026, 3, 15));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("같은 autoTransferId라도 source_type이 AUTO가 아니면 매칭되지 않는다")
    void findBySourceAndDate_differentSourceType_returnsEmpty() {
        Long customerId = insertCustomer();
        Long withdrawalAccountId = insertAccount(customerId);
        Long depositAccountId = insertAccount(customerId);
        Long sourceId = 5004L;
        insertTransfer(withdrawalAccountId, depositAccountId, "20260315BT0000000004",
                ProcessResultStatus.SUCCESS, "SCHEDULED", sourceId, null,
                LocalDateTime.of(2026, 3, 15, 0, 10, 0));

        Optional<TransferLookupResult> result = adapter.findBySourceAndDate(sourceId, LocalDate.of(2026, 3, 15));

        assertThat(result).isEmpty();
    }

    private Long insertCustomer() {
        long seq = CUSTOMER_SEQ.incrementAndGet();
        entityManager.createNativeQuery(
                        "INSERT INTO customer (user_id, password_hash, user_name, birth_date, email, phone_number, joined_at, created_at, updated_at) "
                                + "VALUES (:userId, 'x', '홍길동', '1990-01-01', :email, '01012345678', NOW(), NOW(), NOW())")
                .setParameter("userId", "u" + seq)
                .setParameter("email", "test" + seq + "@test.com")
                .executeUpdate();
        return ((Number) entityManager.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
    }

    private Long insertAccount(Long customerId) {
        String accountNumber = String.format("%012d", ACCOUNT_SEQ.incrementAndGet());
        entityManager.createNativeQuery(
                        "INSERT INTO account (account_number, customer_id, account_type, status, password_hash, opened_date, created_at, updated_at) "
                                + "VALUES (:accountNumber, :customerId, 'DEMAND_DEPOSIT', 'ACTIVE', 'x', NOW(), NOW(), NOW())")
                .setParameter("accountNumber", accountNumber)
                .setParameter("customerId", customerId)
                .executeUpdate();
        return ((Number) entityManager.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
    }

    private void insertTransfer(Long withdrawalAccountId, Long depositAccountId, String transactionNumber,
            ProcessResultStatus status, String sourceType, Long sourceId, String errorMessage, LocalDateTime transferredAt) {
        entityManager.createNativeQuery(
                        "INSERT INTO transfer (transaction_number, withdrawal_account_id, deposit_account_id, "
                                + "deposit_account_number, payee_name, amount, fee, transfer_type, channel, status, "
                                + "source_type, source_id, error_message, transferred_at, created_at) "
                                + "VALUES (:transactionNumber, :withdrawalAccountId, :depositAccountId, "
                                + "'110000000099', '홍길동', 10000, 0, 'AUTO', 'BT', :status, "
                                + ":sourceType, :sourceId, :errorMessage, :transferredAt, NOW())")
                .setParameter("transactionNumber", transactionNumber)
                .setParameter("withdrawalAccountId", withdrawalAccountId)
                .setParameter("depositAccountId", depositAccountId)
                .setParameter("status", status.name())
                .setParameter("sourceType", sourceType)
                .setParameter("sourceId", sourceId)
                .setParameter("errorMessage", errorMessage)
                .setParameter("transferredAt", transferredAt)
                .executeUpdate();
    }
}
