package com.shinhan.corebank.autotransfer.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.autotransfer.application.port.out.StuckExecution;
import com.shinhan.corebank.autotransfer.domain.AutoTransferExecution;
import com.shinhan.corebank.autotransfer.domain.AutoTransferStatus;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AutoTransferExecutionPersistenceAdapterTest extends IntegrationTestSupport {

    @Autowired
    AutoTransferExecutionPersistenceAdapter adapter;

    @Autowired
    AutoTransferExecutionJpaRepository executionRepository;

    @Autowired
    AutoTransferJpaRepository autoTransferJpaRepository;

    @Autowired
    EntityManager entityManager;

    private static final AtomicLong CUSTOMER_SEQ = new AtomicLong();
    private static final AtomicLong ACCOUNT_SEQ = new AtomicLong();

    private Long autoTransferId;

    @BeforeEach
    void seedAutoTransfer() {
        Long customerId = insertCustomer();
        Long accountId = insertAccount(customerId);
        AutoTransferJpaEntity entity = autoTransferJpaRepository.save(AutoTransferJpaEntity.builder()
                .customerId(customerId)
                .withdrawalAccountId(accountId)
                .depositAccountNumber("110987654321")
                .payeeName("홍길동")
                .amount(10000L)
                .cycleMonths(1)
                .transferDay(15)
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2027, 1, 1))
                .nextExecutionDate(LocalDate.of(2026, 3, 15))
                .myPassbookMemo("메모")
                .recipientPassbookMemo("받는메모")
                .status(AutoTransferStatus.NORMAL)
                .registeredAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .updatedAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .build());
        autoTransferId = entity.getAutoTransferId();
    }

    @Test
    @DisplayName("회차를 저장하면 executionId가 채번되어 반환되고 DB에도 즉시 반영된다")
    void save_newExecution_assignsIdAndPersistsImmediately() {
        AutoTransferExecution execution = AutoTransferExecution.processing(
                LocalDate.of(2026, 3, 15), 10000L, LocalDateTime.of(2026, 3, 15, 9, 0));

        AutoTransferExecution saved = adapter.save(execution, autoTransferId);

        assertThat(saved.getExecutionId()).isNotNull();
        // entityManager.flush() 없이도 리포지토리로 바로 조회되면, saveAndFlush가 즉시 INSERT했다는 뜻
        assertThat(executionRepository.findById(saved.getExecutionId())).isPresent();
    }

    @Test
    @DisplayName("같은 자동이체·같은 실행일로 두 번 저장하면 두 번째는 유니크 제약 위반으로 즉시 실패한다")
    void save_duplicateExecutionDate_throwsImmediately() {
        LocalDate executionDate = LocalDate.of(2026, 3, 15);
        adapter.save(AutoTransferExecution.processing(executionDate, 10000L, LocalDateTime.of(2026, 3, 15, 9, 0)), autoTransferId);

        // saveAndFlush가 아니라 지연 flush였다면 이 두 번째 save() 호출 시점엔 아직 예외가 안 나고
        // 트랜잭션 커밋 시점에야 터졌을 것 — 여기서 바로 예외가 나는 것 자체가 즉시 flush 증거
        assertThatThrownBy(() -> adapter.save(
                AutoTransferExecution.processing(executionDate, 10000L, LocalDateTime.of(2026, 3, 15, 9, 1)), autoTransferId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("제약 위반을 같은 트랜잭션 안에서 catch해도, 그 트랜잭션에서의 다음 저장 시도는 여전히 실패한다 (R2: catch해도 트랜잭션은 안 살아남)")
    void save_afterConstraintViolationCaughtInSameTransaction_nextSaveStillFails() {
        LocalDate executionDate = LocalDate.of(2026, 3, 15);
        adapter.save(AutoTransferExecution.processing(executionDate, 10000L, LocalDateTime.of(2026, 3, 15, 9, 0)), autoTransferId);

        // 실제로 발생 가능한 시나리오: "이미 처리됨이니 건너뛰자"고 여기서 catch하고 넘어간다고 가정
        try {
            adapter.save(AutoTransferExecution.processing(executionDate, 10000L, LocalDateTime.of(2026, 3, 15, 9, 1)), autoTransferId);
        } catch (DataIntegrityViolationException expected) {
            // catch했으니 트랜잭션이 괜찮아졌다고 착각하기 쉽지만 아니다 - 아래에서 증명
        }

        // 같은 트랜잭션 안에서의 그다음 저장(전혀 다른 실행일, 제약과 무관)도 실패해야 한다 -
        // 이게 실패한다는 것 자체가 "catch해도 트랜잭션은 이미 오염됐다"는 증거.
        // 실제로 Hibernate가 무슨 예외를 던질지는 버전에 따라 달라질 수 있어 타입은 느슨하게 확인한다.
        assertThatThrownBy(() -> adapter.save(
                AutoTransferExecution.processing(LocalDate.of(2026, 3, 16), 10000L, LocalDateTime.of(2026, 3, 16, 9, 0)), autoTransferId))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("PROCESSING 상태 회차만 반환하고 SUCCESS/ERROR는 제외한다")
    void findAllProcessing_returnsOnlyProcessingExecutions() {
        adapter.save(AutoTransferExecution.processing(
                LocalDate.of(2026, 3, 15), 10000L, LocalDateTime.of(2026, 3, 15, 9, 0)), autoTransferId);

        AutoTransferExecution success = AutoTransferExecution.processing(
                LocalDate.of(2026, 3, 16), 10000L, LocalDateTime.of(2026, 3, 16, 9, 0));
        success.markSuccess("20260316BT0000000001");
        adapter.save(success, autoTransferId);

        AutoTransferExecution error = AutoTransferExecution.processing(
                LocalDate.of(2026, 3, 17), 10000L, LocalDateTime.of(2026, 3, 17, 9, 0));
        error.markError("잔액 부족");
        adapter.save(error, autoTransferId);

        List<StuckExecution> result = adapter.findAllProcessing();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).execution().getStatus()).isEqualTo(ProcessResultStatus.PROCESSING);
        assertThat(result.get(0).execution().getExecutionDate()).isEqualTo(LocalDate.of(2026, 3, 15));
        assertThat(result.get(0).autoTransfer().getAutoTransferId()).isEqualTo(autoTransferId);
    }

    @Test
    @DisplayName("PROCESSING 상태 회차가 없으면 빈 리스트를 반환한다")
    void findAllProcessing_noProcessingExecutions_returnsEmpty() {
        AutoTransferExecution success = AutoTransferExecution.processing(
                LocalDate.of(2026, 3, 15), 10000L, LocalDateTime.of(2026, 3, 15, 9, 0));
        success.markSuccess("20260315BT0000000001");
        adapter.save(success, autoTransferId);

        List<StuckExecution> result = adapter.findAllProcessing();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("서로 다른 자동이체의 PROCESSING 회차가 모두 각자의 AutoTransfer와 함께 반환된다")
    void findAllProcessing_multipleAutoTransfers_allIncludedWithOwnParent() {
        Long otherCustomerId = insertCustomer();
        Long otherAccountId = insertAccount(otherCustomerId);
        AutoTransferJpaEntity otherEntity = autoTransferJpaRepository.save(AutoTransferJpaEntity.builder()
                .customerId(otherCustomerId)
                .withdrawalAccountId(otherAccountId)
                .depositAccountNumber("110987654322")
                .payeeName("김철수")
                .amount(20000L)
                .cycleMonths(1)
                .transferDay(15)
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2027, 1, 1))
                .nextExecutionDate(LocalDate.of(2026, 3, 15))
                .myPassbookMemo("메모2")
                .recipientPassbookMemo("받는메모2")
                .status(AutoTransferStatus.NORMAL)
                .registeredAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .updatedAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .build());
        Long otherAutoTransferId = otherEntity.getAutoTransferId();

        adapter.save(AutoTransferExecution.processing(
                LocalDate.of(2026, 3, 15), 10000L, LocalDateTime.of(2026, 3, 15, 9, 0)), autoTransferId);
        adapter.save(AutoTransferExecution.processing(
                LocalDate.of(2026, 3, 15), 20000L, LocalDateTime.of(2026, 3, 15, 9, 0)), otherAutoTransferId);

        List<StuckExecution> result = adapter.findAllProcessing();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(r -> r.autoTransfer().getAutoTransferId())
                .containsExactlyInAnyOrder(autoTransferId, otherAutoTransferId);
    }

    private Long insertCustomer() {
        long seq = CUSTOMER_SEQ.incrementAndGet();
        String userId = "u" + seq;
        String email = "test" + seq + "@test.com";
        entityManager.createNativeQuery(
                        "INSERT INTO customer (user_id, password_hash, user_name, birth_date, email, phone_number, joined_at, created_at, updated_at) "
                                + "VALUES (:userId, 'x', '홍길동', '1990-01-01', :email, '01012345678', NOW(), NOW(), NOW())")
                .setParameter("userId", userId)
                .setParameter("email", email)
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
