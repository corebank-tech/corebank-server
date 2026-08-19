package com.shinhan.corebank.scheduledtransfer.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransfer;
import com.shinhan.corebank.scheduledtransfer.domain.exception.ScheduledTransferErrorCode;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ScheduledTransferPersistenceAdapterTest extends IntegrationTestSupport {

    @Autowired
    ScheduledTransferPersistenceAdapter adapter;

    @Autowired
    EntityManager entityManager;

    private static final AtomicLong CUSTOMER_SEQ = new AtomicLong();
    private static final AtomicLong ACCOUNT_SEQ = new AtomicLong();

    @Test
    @DisplayName("사전 existsActiveDuplicate() 확인 없이 동일 조건(WAITING)으로 두 번 save()하면 " +
            "두 번째는 DB unique 제약(uk_sched_active_dup) 위반을 SCD0301로 변환해서 던진다")
    void save_duplicateActiveKey_translatesToBusinessException() {
        Long customerId = insertCustomer();
        Long withdrawalAccountId = insertAccount(customerId);
        LocalDate scheduledDate = LocalDate.now().plusDays(10);

        ScheduledTransfer first = ScheduledTransfer.register(customerId, withdrawalAccountId, "088",
                "110987654321", "홍길동", 10_000L, scheduledDate, "내메모", "받는메모", LocalDateTime.now());
        adapter.save(first);
        entityManager.flush();

        // 사전 existsActiveDuplicate() 확인을 거치지 않고, 서로 다른 요청이 동시에 통과했다고 가정하고
        // 동일한 active_dup_key(customer_id, withdrawal_account_id, payee_account_number, amount, scheduled_date)로
        // 바로 두 번째 저장을 시도한다 — 애플리케이션 레벨 사전 체크를 우회한 레이스 상황을 재현한다.
        ScheduledTransfer second = ScheduledTransfer.register(customerId, withdrawalAccountId, "088",
                "110987654321", "홍길동", 10_000L, scheduledDate, "내메모", "받는메모", LocalDateTime.now());

        assertThatThrownBy(() -> {
            adapter.save(second);
            entityManager.flush();
        })
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ScheduledTransferErrorCode.DUPLICATE_REGISTRATION));
    }

    @Test
    @DisplayName("금액이 다르면(active_dup_key가 달라짐) 같은 조건이어도 정상 저장된다")
    void save_sameConditionsDifferentAmount_savesSuccessfully() {
        Long customerId = insertCustomer();
        Long withdrawalAccountId = insertAccount(customerId);
        LocalDate scheduledDate = LocalDate.now().plusDays(10);

        ScheduledTransfer first = ScheduledTransfer.register(customerId, withdrawalAccountId, "088",
                "110987654321", "홍길동", 10_000L, scheduledDate, "내메모", "받는메모", LocalDateTime.now());
        adapter.save(first);
        entityManager.flush();

        ScheduledTransfer second = ScheduledTransfer.register(customerId, withdrawalAccountId, "088",
                "110987654321", "홍길동", 20_000L, scheduledDate, "내메모", "받는메모", LocalDateTime.now());

        ScheduledTransfer saved = adapter.save(second);
        entityManager.flush();

        assertThat(saved.getScheduledTransferId()).isNotNull();
        assertThat(saved.getAmount()).isEqualTo(20_000L);
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
}
