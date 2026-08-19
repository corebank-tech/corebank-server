package com.shinhan.corebank.scheduledtransfer.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransfer;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;
import com.shinhan.corebank.scheduledtransfer.domain.exception.ScheduledTransferErrorCode;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

    @Test
    @DisplayName("search()는 scheduledDate 오름차순, 동일 날짜면 scheduledTransferId 오름차순으로 정렬한다")
    void search_ordersByScheduledDateThenId() {
        Long customerId = insertCustomer();
        Long withdrawalAccountId = insertAccount(customerId);
        LocalDate earlierDate = LocalDate.now().plusDays(5);
        LocalDate laterDate = LocalDate.now().plusDays(10);

        // laterDate에 먼저 저장해서, 정렬이 등록 순서가 아니라 scheduledDate/id 기준임을 검증한다
        ScheduledTransfer firstOnLaterDate = adapter.save(ScheduledTransfer.register(customerId, withdrawalAccountId,
                "088", "110111111111", "홍길동", 10_000L, laterDate, "메모", "메모", LocalDateTime.now()));
        entityManager.flush();
        ScheduledTransfer secondOnLaterDate = adapter.save(ScheduledTransfer.register(customerId, withdrawalAccountId,
                "088", "110222222222", "홍길동", 20_000L, laterDate, "메모", "메모", LocalDateTime.now()));
        entityManager.flush();
        ScheduledTransfer onEarlierDate = adapter.save(ScheduledTransfer.register(customerId, withdrawalAccountId,
                "088", "110333333333", "홍길동", 30_000L, earlierDate, "메모", "메모", LocalDateTime.now()));
        entityManager.flush();

        Page<ScheduledTransfer> result = adapter.search(customerId, null, null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(ScheduledTransfer::getScheduledTransferId)
                .containsExactly(
                        onEarlierDate.getScheduledTransferId(),
                        firstOnLaterDate.getScheduledTransferId(),
                        secondOnLaterDate.getScheduledTransferId());
    }

    @Test
    @DisplayName("findDueForExecution()은 status=WAITING, scheduledDate 일치 건을 registeredAt 오름차순으로 조회한다")
    void findDueForExecution_returnsWaitingDueRowsOrderedByRegisteredAt() {
        Long customerId = insertCustomer();
        Long withdrawalAccountId = insertAccount(customerId);
        LocalDate dueDate = LocalDate.now().plusDays(10);

        ScheduledTransfer later = adapter.save(ScheduledTransfer.register(customerId, withdrawalAccountId,
                "088", "110111111111", "홍길동", 10_000L, dueDate, "메모", "메모", LocalDateTime.now()));
        entityManager.flush();
        ScheduledTransfer earlier = adapter.save(ScheduledTransfer.register(customerId, withdrawalAccountId,
                "088", "110222222222", "홍길동", 20_000L, dueDate, "메모", "메모", LocalDateTime.now().minusHours(1)));
        entityManager.flush();
        // 다른 날짜 - 대상 아님
        adapter.save(ScheduledTransfer.register(customerId, withdrawalAccountId,
                "088", "110333333333", "홍길동", 30_000L, dueDate.plusDays(1), "메모", "메모", LocalDateTime.now()));
        entityManager.flush();

        List<ScheduledTransfer> due = adapter.findDueForExecution(dueDate);

        assertThat(due).extracting(ScheduledTransfer::getScheduledTransferId)
                .containsExactly(earlier.getScheduledTransferId(), later.getScheduledTransferId());
    }

    @Test
    @DisplayName("claimForProcessing()은 WAITING 건을 PROCESSING으로 바꾸고 true를 반환한다")
    void claimForProcessing_waitingRow_returnsTrueAndUpdatesStatus() {
        Long customerId = insertCustomer();
        Long withdrawalAccountId = insertAccount(customerId);
        ScheduledTransfer saved = adapter.save(ScheduledTransfer.register(customerId, withdrawalAccountId,
                "088", "110111111111", "홍길동", 10_000L, LocalDate.now().plusDays(10), "메모", "메모", LocalDateTime.now()));
        entityManager.flush();

        boolean claimed = adapter.claimForProcessing(saved.getScheduledTransferId());
        entityManager.clear();

        assertThat(claimed).isTrue();
        assertThat(adapter.findById(saved.getScheduledTransferId()).orElseThrow().getStatus())
                .isEqualTo(ScheduledTransferStatus.PROCESSING);
    }

    @Test
    @DisplayName("claimForProcessing()은 이미 WAITING이 아닌 건에는 false를 반환한다 (중복 선점 방어)")
    void claimForProcessing_nonWaitingRow_returnsFalse() {
        Long customerId = insertCustomer();
        Long withdrawalAccountId = insertAccount(customerId);
        ScheduledTransfer saved = adapter.save(ScheduledTransfer.register(customerId, withdrawalAccountId,
                "088", "110111111111", "홍길동", 10_000L, LocalDate.now().plusDays(10), "메모", "메모", LocalDateTime.now()));
        entityManager.flush();
        adapter.claimForProcessing(saved.getScheduledTransferId());
        entityManager.clear();

        boolean secondClaim = adapter.claimForProcessing(saved.getScheduledTransferId());

        assertThat(secondClaim).isFalse();
    }

    @Test
    @DisplayName("findAllProcessing()은 status=PROCESSING 건만 조회한다")
    void findAllProcessing_returnsOnlyProcessingRows() {
        Long customerId = insertCustomer();
        Long withdrawalAccountId = insertAccount(customerId);
        ScheduledTransfer waiting = adapter.save(ScheduledTransfer.register(customerId, withdrawalAccountId,
                "088", "110111111111", "홍길동", 10_000L, LocalDate.now().plusDays(10), "메모", "메모", LocalDateTime.now()));
        ScheduledTransfer toBeProcessing = adapter.save(ScheduledTransfer.register(customerId, withdrawalAccountId,
                "088", "110222222222", "홍길동", 20_000L, LocalDate.now().plusDays(11), "메모", "메모", LocalDateTime.now()));
        entityManager.flush();
        adapter.claimForProcessing(toBeProcessing.getScheduledTransferId());
        entityManager.clear();

        List<ScheduledTransfer> stuck = adapter.findAllProcessing();

        assertThat(stuck).extracting(ScheduledTransfer::getScheduledTransferId)
                .containsExactly(toBeProcessing.getScheduledTransferId());
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
