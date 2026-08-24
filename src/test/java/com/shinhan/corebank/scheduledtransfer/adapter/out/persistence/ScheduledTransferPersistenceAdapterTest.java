package com.shinhan.corebank.scheduledtransfer.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferExecutionResultSort;
import com.shinhan.corebank.scheduledtransfer.application.port.out.ScheduledTransferExecutionResultAggregate;
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
import org.springframework.dao.OptimisticLockingFailureException;
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
    @DisplayName("searchExecutionResults()는 WAITING을 제외하고 SUCCESS/FAILED/CANCELED만 처리결과 기준일(executedAt/canceledAt) 내림차순으로 조회한다")
    void searchExecutionResults_excludesWaiting_ordersByEffectiveDateDesc() {
        Long customerId = insertCustomer();
        Long withdrawalAccountId = insertAccount(customerId);

        adapter.save(ScheduledTransfer.register(customerId, withdrawalAccountId, "088", "110111111111", "홍길동",
                10_000L, LocalDate.now().plusDays(10), "메모", "메모", LocalDateTime.now()));
        ScheduledTransfer success = saveTerminal(customerId, withdrawalAccountId, ScheduledTransferStatus.SUCCESS,
                LocalDate.now().minusDays(5), 10_000L, "TXN0001", null);
        ScheduledTransfer failed = saveTerminal(customerId, withdrawalAccountId, ScheduledTransferStatus.FAILED,
                LocalDate.now().minusDays(3), 20_000L, null, "잔액 부족");
        ScheduledTransfer canceled = saveTerminal(customerId, withdrawalAccountId, ScheduledTransferStatus.CANCELED,
                LocalDate.now().minusDays(1), 30_000L, null, null);
        entityManager.flush();

        Page<ScheduledTransfer> result = adapter.searchExecutionResults(customerId, null,
                LocalDate.now().minusDays(10), LocalDate.now(), ScheduledTransferExecutionResultSort.LATEST, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(ScheduledTransfer::getScheduledTransferId)
                .containsExactly(canceled.getScheduledTransferId(), failed.getScheduledTransferId(), success.getScheduledTransferId());
    }

    @Test
    @DisplayName("조회기간은 예정일(scheduledDate)이 아니라 실제 처리일(executedAt)을 기준으로 필터링한다 " +
            "(PR #223 리뷰) - 예정일이 조회기간 밖이어도 실제 처리일이 안이면 포함되고, 그 반대는 제외된다")
    void searchExecutionResults_filtersByExecutedAtNotScheduledDate() {
        Long customerId = insertCustomer();
        Long withdrawalAccountId = insertAccount(customerId);
        LocalDate scheduledOutsideRange = LocalDate.now().minusDays(20);
        LocalDate executedInsideRange = LocalDate.now().minusDays(2);

        // 예정일은 조회기간(최근 10일) 밖이지만, 실제 처리는 조회기간 안에 됨 - 포함돼야 함
        ScheduledTransfer executedLate = adapter.save(ScheduledTransfer.reconstitute(
                null, customerId, withdrawalAccountId, "088", "110987654321", "홍길동", 10_000L,
                scheduledOutsideRange, "메모", "메모", ScheduledTransferStatus.SUCCESS, "TXN0010",
                LocalDateTime.now().minusDays(30), executedInsideRange.atTime(9, 0), null, null, null));
        // 예정일은 조회기간 안이지만, 실제 처리(executedAt)는 조회기간 밖 - 제외돼야 함
        ScheduledTransfer executedTooEarly = adapter.save(ScheduledTransfer.reconstitute(
                null, customerId, withdrawalAccountId, "088", "110987654321", "홍길동", 20_000L,
                LocalDate.now().minusDays(3), "메모", "메모", ScheduledTransferStatus.SUCCESS, "TXN0011",
                LocalDateTime.now().minusDays(30), LocalDate.now().minusDays(20).atTime(9, 0), null, null, null));
        entityManager.flush();

        Page<ScheduledTransfer> result = adapter.searchExecutionResults(customerId, null,
                LocalDate.now().minusDays(10), LocalDate.now(), ScheduledTransferExecutionResultSort.LATEST, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(ScheduledTransfer::getScheduledTransferId)
                .containsExactly(executedLate.getScheduledTransferId());
        assertThat(result.getContent())
                .extracting(ScheduledTransfer::getScheduledTransferId)
                .doesNotContain(executedTooEarly.getScheduledTransferId());
    }

    @Test
    @DisplayName("summarizeExecutionResults()는 정상/오류/취소 각각의 건수·금액을 실제 합계와 일치하게 집계한다")
    void summarizeExecutionResults_aggregatesCorrectly() {
        Long customerId = insertCustomer();
        Long withdrawalAccountId = insertAccount(customerId);

        // 집계 대상 아님 - 아직 결과가 아닌 WAITING
        adapter.save(ScheduledTransfer.register(customerId, withdrawalAccountId, "088", "110111111111", "홍길동",
                999_999L, LocalDate.now().plusDays(10), "메모", "메모", LocalDateTime.now()));
        saveTerminal(customerId, withdrawalAccountId, ScheduledTransferStatus.SUCCESS, LocalDate.now().minusDays(5), 10_000L, "TXN0001", null);
        saveTerminal(customerId, withdrawalAccountId, ScheduledTransferStatus.SUCCESS, LocalDate.now().minusDays(4), 15_000L, "TXN0002", null);
        saveTerminal(customerId, withdrawalAccountId, ScheduledTransferStatus.FAILED, LocalDate.now().minusDays(3), 20_000L, null, "잔액 부족");
        saveTerminal(customerId, withdrawalAccountId, ScheduledTransferStatus.CANCELED, LocalDate.now().minusDays(1), 30_000L, null, null);
        entityManager.flush();

        ScheduledTransferExecutionResultAggregate aggregate = adapter.summarizeExecutionResults(customerId, null,
                LocalDate.now().minusDays(10), LocalDate.now());

        assertThat(aggregate.successCount()).isEqualTo(2L);
        assertThat(aggregate.successAmount()).isEqualTo(25_000L);
        assertThat(aggregate.failedCount()).isEqualTo(1L);
        assertThat(aggregate.failedAmount()).isEqualTo(20_000L);
        assertThat(aggregate.canceledCount()).isEqualTo(1L);
        assertThat(aggregate.canceledAmount()).isEqualTo(30_000L);
    }

    // effectiveDate가 처리결과 조회의 조회기간/정렬 기준(executedAt 또는 canceledAt)이 되도록,
    // SUCCESS/FAILED는 executedAt에, CANCELED는 canceledAt에 채운다(scheduledDate는 별도 의미 없이 동일값 사용)
    private ScheduledTransfer saveTerminal(Long customerId, Long withdrawalAccountId, ScheduledTransferStatus status,
                                           LocalDate effectiveDate, Long amount, String transactionNumber, String failureReason) {
        LocalDateTime effectiveAt = effectiveDate.atTime(9, 0);
        LocalDateTime executedAt = status == ScheduledTransferStatus.CANCELED ? null : effectiveAt;
        LocalDateTime canceledAt = status == ScheduledTransferStatus.CANCELED ? effectiveAt : null;
        ScheduledTransfer domain = ScheduledTransfer.reconstitute(
                null, customerId, withdrawalAccountId, "088", "110987654321", "홍길동", amount, effectiveDate,
                "메모", "메모", status, transactionNumber, LocalDateTime.now().minusDays(30), executedAt,
                canceledAt, failureReason, null);
        return adapter.save(domain);
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

    @Test
    @DisplayName("saveIfStillProcessing()은 PROCESSING 건이면 최종 상태로 확정하고 true를 반환한다")
    void saveIfStillProcessing_processingRow_confirmsAndReturnsTrue() {
        Long customerId = insertCustomer();
        Long withdrawalAccountId = insertAccount(customerId);
        ScheduledTransfer saved = adapter.save(ScheduledTransfer.register(customerId, withdrawalAccountId,
                "088", "110111111111", "홍길동", 10_000L, LocalDate.now().plusDays(10), "메모", "메모", LocalDateTime.now()));
        entityManager.flush();
        adapter.claimForProcessing(saved.getScheduledTransferId());
        entityManager.clear();

        ScheduledTransfer toConfirm = ScheduledTransfer.reconstitute(
                saved.getScheduledTransferId(), customerId, withdrawalAccountId, "088", "110111111111", "홍길동",
                10_000L, LocalDate.now().plusDays(10), "메모", "메모", ScheduledTransferStatus.SUCCESS,
                "20260315BT0000000010", saved.getRegisteredAt(), LocalDateTime.now(), null, null, null);

        boolean confirmed = adapter.saveIfStillProcessing(toConfirm);
        entityManager.clear();

        assertThat(confirmed).isTrue();
        ScheduledTransfer after = adapter.findById(saved.getScheduledTransferId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(ScheduledTransferStatus.SUCCESS);
        assertThat(after.getTransactionNumber()).isEqualTo("20260315BT0000000010");
    }

    @Test
    @DisplayName("saveIfStillProcessing()은 이미 최종 확정된(PROCESSING 아닌) 건에는 false를 반환하고 값을 바꾸지 않는다 (동시 재확정 방어)")
    void saveIfStillProcessing_alreadyConfirmedRow_returnsFalseAndDoesNotOverwrite() {
        Long customerId = insertCustomer();
        Long withdrawalAccountId = insertAccount(customerId);
        ScheduledTransfer saved = adapter.save(ScheduledTransfer.register(customerId, withdrawalAccountId,
                "088", "110111111111", "홍길동", 10_000L, LocalDate.now().plusDays(10), "메모", "메모", LocalDateTime.now()));
        entityManager.flush();
        adapter.claimForProcessing(saved.getScheduledTransferId());
        entityManager.clear();

        ScheduledTransfer firstConfirm = ScheduledTransfer.reconstitute(
                saved.getScheduledTransferId(), customerId, withdrawalAccountId, "088", "110111111111", "홍길동",
                10_000L, LocalDate.now().plusDays(10), "메모", "메모", ScheduledTransferStatus.SUCCESS,
                "20260315BT0000000011", saved.getRegisteredAt(), LocalDateTime.now(), null, null, null);
        adapter.saveIfStillProcessing(firstConfirm);
        entityManager.clear();

        // 동시에 두 번째 재확정 실행이 같은 건을 다른 결과(FAILED)로 확정하려는 상황을 재현
        ScheduledTransfer secondConfirm = ScheduledTransfer.reconstitute(
                saved.getScheduledTransferId(), customerId, withdrawalAccountId, "088", "110111111111", "홍길동",
                10_000L, LocalDate.now().plusDays(10), "메모", "메모", ScheduledTransferStatus.FAILED,
                null, saved.getRegisteredAt(), LocalDateTime.now(), null, "잔액 부족", null);

        boolean confirmed = adapter.saveIfStillProcessing(secondConfirm);
        entityManager.clear();

        assertThat(confirmed).isFalse();
        ScheduledTransfer after = adapter.findById(saved.getScheduledTransferId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(ScheduledTransferStatus.SUCCESS);
        assertThat(after.getTransactionNumber()).isEqualTo("20260315BT0000000011");
    }

    @Test
    @DisplayName("배치가 선점(claim)한 뒤 선점 이전에 읽어둔 건을 저장하면 낙관적 락 충돌이 난다 " +
            "- 취소가 PROCESSING을 CANCELED로 조용히 덮어쓰던 문제 (PR #335 리뷰 R2)")
    void save_staleVersionAfterClaim_throwsOptimisticLockingFailure() {
        Long customerId = insertCustomer();
        Long withdrawalAccountId = insertAccount(customerId);
        ScheduledTransfer saved = adapter.save(ScheduledTransfer.register(customerId, withdrawalAccountId,
                "088", "110111111111", "홍길동", 10_000L, LocalDate.now().plusDays(10), "메모", "메모", LocalDateTime.now()));
        entityManager.flush();
        entityManager.clear();

        // 취소 요청이 WAITING 상태로 읽어둔 시점
        ScheduledTransfer readByCancel = adapter.findById(saved.getScheduledTransferId()).orElseThrow();
        // 그 사이 배치가 선점해 version이 올라간다
        adapter.claimForProcessing(saved.getScheduledTransferId());
        entityManager.clear();

        readByCancel.cancel(LocalDateTime.now());

        assertThatThrownBy(() -> {
            adapter.save(readByCancel);
            entityManager.flush();
        }).isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("선점이 없었으면 취소 저장은 낙관적 락에 걸리지 않고 그대로 반영된다")
    void save_noConcurrentClaim_cancelSucceeds() {
        Long customerId = insertCustomer();
        Long withdrawalAccountId = insertAccount(customerId);
        ScheduledTransfer saved = adapter.save(ScheduledTransfer.register(customerId, withdrawalAccountId,
                "088", "110111111111", "홍길동", 10_000L, LocalDate.now().plusDays(10), "메모", "메모", LocalDateTime.now()));
        entityManager.flush();
        entityManager.clear();

        ScheduledTransfer readByCancel = adapter.findById(saved.getScheduledTransferId()).orElseThrow();
        readByCancel.cancel(LocalDateTime.now());
        adapter.save(readByCancel);
        entityManager.flush();
        entityManager.clear();

        assertThat(adapter.findById(saved.getScheduledTransferId()).orElseThrow().getStatus())
                .isEqualTo(ScheduledTransferStatus.CANCELED);
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
