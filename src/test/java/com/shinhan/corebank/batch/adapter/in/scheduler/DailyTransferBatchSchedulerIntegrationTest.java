package com.shinhan.corebank.batch.adapter.in.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * DailyTransferBatchScheduler를 실제로 돌려서 POL-037(자동이체 -> 예약이체 순서)이 자금 흐름
 * 결과로도 증명되는지 확인한다(#189 DoD). 같은 출금계좌에 자동이체·예약이체가 같은 날 겹치고
 * 잔액이 둘 다 감당 못 하면, 자동이체가 먼저 성공해 잔액을 소진하고 예약이체는 잔액부족으로
 * 실패해야 한다 - 순서가 바뀌면 정반대 결과가 나오므로 이 결과 자체가 순서의 증거다.
 *
 * <p>클래스 레벨 @Transactional을 쓰지 않는다 - 배치 각 건이 REQUIRES_NEW로 독립 커밋되고
 * 배치 락도 REQUIRES_NEW라 진짜 커밋된 최종 상태를 봐야 한다.
 */
class DailyTransferBatchSchedulerIntegrationTest extends IntegrationTestSupport {

    @Autowired
    DailyTransferBatchScheduler dailyTransferBatchScheduler;

    @Autowired
    EntityManager entityManager;

    @Autowired
    PlatformTransactionManager transactionManager;

    private static final AtomicLong CUSTOMER_SEQ = new AtomicLong();
    private static final AtomicLong ACCOUNT_SEQ = new AtomicLong();
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final long WITHDRAWAL_INITIAL_BALANCE = 15_000L;
    private static final long EACH_TRANSFER_AMOUNT = 10_000L;

    private Long withdrawalCustomerId;
    private Long depositCustomerId;
    private Long withdrawalAccountId;
    private Long depositAccountId;
    private Long autoTransferId;
    private Long scheduledTransferId;

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    @BeforeEach
    void setUp() {
        // 이전 테스트 실행이 크래시로 락을 TRUE로 남겼을 가능성에 대비해 매 테스트마다 강제 초기화
        transactionTemplate().executeWithoutResult(status -> entityManager
                .createNativeQuery(
                        "UPDATE batch_execution_lock SET currently_running = FALSE WHERE job_name = 'DAILY_TRANSFER_BATCH'")
                .executeUpdate());

        LocalDate today = LocalDate.now(SEOUL);

        transactionTemplate().executeWithoutResult(status -> {
            withdrawalCustomerId = insertCustomer();
            withdrawalAccountId = insertAccount(withdrawalCustomerId, WITHDRAWAL_INITIAL_BALANCE, true);

            depositCustomerId = insertCustomer();
            depositAccountId = insertAccount(depositCustomerId, 0L, false);
            String depositAccountNumber = accountNumberOf(depositAccountId);

            autoTransferId = insertAutoTransfer(withdrawalCustomerId, withdrawalAccountId, depositAccountNumber, today);
            scheduledTransferId =
                    insertScheduledTransfer(withdrawalCustomerId, withdrawalAccountId, depositAccountNumber, today);
        });
    }

    @AfterEach
    void cleanUp() {
        transactionTemplate().executeWithoutResult(status -> {
            entityManager
                    .createNativeQuery("DELETE FROM ledger_entry WHERE account_id IN (:w, :d)")
                    .setParameter("w", withdrawalAccountId)
                    .setParameter("d", depositAccountId)
                    .executeUpdate();
            entityManager
                    .createNativeQuery("DELETE FROM transfer WHERE withdrawal_account_id = :w")
                    .setParameter("w", withdrawalAccountId)
                    .executeUpdate();
            entityManager
                    .createNativeQuery("DELETE FROM auto_transfer_execution WHERE auto_transfer_id = :id")
                    .setParameter("id", autoTransferId)
                    .executeUpdate();
            entityManager
                    .createNativeQuery("DELETE FROM auto_transfer WHERE auto_transfer_id = :id")
                    .setParameter("id", autoTransferId)
                    .executeUpdate();
            entityManager
                    .createNativeQuery("DELETE FROM scheduled_transfer WHERE scheduled_transfer_id = :id")
                    .setParameter("id", scheduledTransferId)
                    .executeUpdate();
            entityManager
                    .createNativeQuery("DELETE FROM account WHERE account_id IN (:w, :d)")
                    .setParameter("w", withdrawalAccountId)
                    .setParameter("d", depositAccountId)
                    .executeUpdate();
            entityManager
                    .createNativeQuery("DELETE FROM audit_log WHERE customer_id IN (:w, :d)")
                    .setParameter("w", withdrawalCustomerId)
                    .setParameter("d", depositCustomerId)
                    .executeUpdate();
            entityManager
                    .createNativeQuery("DELETE FROM customer WHERE customer_id IN (:w, :d)")
                    .setParameter("w", withdrawalCustomerId)
                    .setParameter("d", depositCustomerId)
                    .executeUpdate();
        });
    }

    @Test
    @DisplayName(
            "같은 출금계좌에 자동이체·예약이체가 같은 날 겹치고 잔액이 둘 다 감당 못 하면, " + "자동이체가 먼저 성공해 잔액을 소진하고 예약이체는 잔액부족으로 오류 확정된다 (POL-037)")
    void runDailyBatch_sameAccountBothDue_autoTransferWinsPriority() {
        dailyTransferBatchScheduler.runDailyBatch();

        Object[] execution = (Object[]) entityManager
                .createNativeQuery(
                        "SELECT status, failure_reason FROM auto_transfer_execution WHERE auto_transfer_id = :id")
                .setParameter("id", autoTransferId)
                .getSingleResult();
        assertThat(execution[0]).isEqualTo("SUCCESS");

        Object[] scheduled = (Object[]) entityManager
                .createNativeQuery(
                        "SELECT status, failure_reason FROM scheduled_transfer WHERE scheduled_transfer_id = :id")
                .setParameter("id", scheduledTransferId)
                .getSingleResult();
        assertThat(scheduled[0]).isEqualTo("FAILED");
        assertThat((String) scheduled[1]).contains("잔액이 부족");

        long balanceAfter = ((Number) entityManager
                        .createNativeQuery("SELECT balance FROM account WHERE account_id = :id")
                        .setParameter("id", withdrawalAccountId)
                        .getSingleResult())
                .longValue();
        assertThat(balanceAfter).isEqualTo(WITHDRAWAL_INITIAL_BALANCE - EACH_TRANSFER_AMOUNT);
    }

    private String accountNumberOf(Long accountId) {
        return (String) entityManager
                .createNativeQuery("SELECT account_number FROM account WHERE account_id = :id")
                .setParameter("id", accountId)
                .getSingleResult();
    }

    private Long insertCustomer() {
        long seq = CUSTOMER_SEQ.incrementAndGet();
        entityManager
                .createNativeQuery(
                        "INSERT INTO customer (user_id, password_hash, user_name, birth_date, email, phone_number, joined_at, created_at, updated_at) "
                                + "VALUES (:userId, 'x', '홍길동', '1990-01-01', :email, '01012345678', NOW(), NOW(), NOW())")
                .setParameter("userId", "u" + seq)
                .setParameter("email", "test" + seq + "@test.com")
                .executeUpdate();
        return ((Number) entityManager
                        .createNativeQuery("SELECT LAST_INSERT_ID()")
                        .getSingleResult())
                .longValue();
    }

    private Long insertAccount(Long customerId, long balance, boolean withdrawalRegistered) {
        String accountNumber = String.format("%012d", ACCOUNT_SEQ.incrementAndGet());
        entityManager
                .createNativeQuery(
                        "INSERT INTO account (account_number, customer_id, account_type, status, balance, password_hash, "
                                + "withdrawal_registered, withdrawal_registered_at, opened_date, created_at, updated_at) "
                                + "VALUES (:accountNumber, :customerId, 'DEMAND_DEPOSIT', 'ACTIVE', :balance, 'x', "
                                + ":withdrawalRegistered, CASE WHEN :withdrawalRegistered THEN NOW() ELSE NULL END, NOW(), NOW(), NOW())")
                .setParameter("accountNumber", accountNumber)
                .setParameter("customerId", customerId)
                .setParameter("balance", balance)
                .setParameter("withdrawalRegistered", withdrawalRegistered)
                .executeUpdate();
        return ((Number) entityManager
                        .createNativeQuery("SELECT LAST_INSERT_ID()")
                        .getSingleResult())
                .longValue();
    }

    private Long insertAutoTransfer(
            Long customerId, Long withdrawalAccountId, String depositAccountNumber, LocalDate today) {
        entityManager
                .createNativeQuery(
                        "INSERT INTO auto_transfer (customer_id, withdrawal_account_id, deposit_account_number, payee_name, amount, "
                                + "cycle_months, transfer_day, start_date, end_date, next_execution_date, status, registered_at, updated_at) "
                                + "VALUES (:customerId, :withdrawalAccountId, :depositAccountNumber, '홍길동', :amount, "
                                + "1, 15, :startDate, :endDate, :nextExecutionDate, 'NORMAL', NOW(), NOW())")
                .setParameter("customerId", customerId)
                .setParameter("withdrawalAccountId", withdrawalAccountId)
                .setParameter("depositAccountNumber", depositAccountNumber)
                .setParameter("amount", EACH_TRANSFER_AMOUNT)
                .setParameter("startDate", today.minusMonths(1))
                .setParameter("endDate", today.plusMonths(6))
                .setParameter("nextExecutionDate", today)
                .executeUpdate();
        return ((Number) entityManager
                        .createNativeQuery("SELECT LAST_INSERT_ID()")
                        .getSingleResult())
                .longValue();
    }

    private Long insertScheduledTransfer(
            Long customerId, Long withdrawalAccountId, String payeeAccountNumber, LocalDate today) {
        entityManager
                .createNativeQuery(
                        "INSERT INTO scheduled_transfer (customer_id, withdrawal_account_id, payee_bank_code, payee_account_number, "
                                + "payee_name, amount, scheduled_date, status, registered_at) "
                                + "VALUES (:customerId, :withdrawalAccountId, '088', :payeeAccountNumber, '김철수', :amount, "
                                + ":scheduledDate, 'WAITING', NOW())")
                .setParameter("customerId", customerId)
                .setParameter("withdrawalAccountId", withdrawalAccountId)
                .setParameter("payeeAccountNumber", payeeAccountNumber)
                .setParameter("amount", EACH_TRANSFER_AMOUNT)
                .setParameter("scheduledDate", today)
                .executeUpdate();
        return ((Number) entityManager
                        .createNativeQuery("SELECT LAST_INSERT_ID()")
                        .getSingleResult())
                .longValue();
    }
}
