package com.shinhan.corebank.autotransfer.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.autotransfer.application.port.out.AutoTransferExecutionHistoryAggregate;
import com.shinhan.corebank.autotransfer.application.port.out.AutoTransferExecutionHistoryRow;
import com.shinhan.corebank.autotransfer.domain.AutoTransfer;
import com.shinhan.corebank.autotransfer.domain.AutoTransferStatus;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AutoTransferPersistenceAdapterTest extends IntegrationTestSupport {

    @Autowired
    AutoTransferJpaRepository repository;

    @Autowired
    AutoTransferPersistenceAdapter adapter;

    @Autowired
    AutoTransferExecutionJpaRepository executionRepository;

    @Autowired
    EntityManager entityManager;

    private static final AtomicLong CUSTOMER_SEQ = new AtomicLong();
    private static final AtomicLong ACCOUNT_SEQ = new AtomicLong();

    private Long customerId;
    private Long accountA;
    private Long accountB;

    // autotransfer는 customer_id/withdrawal_account_id에 FK가 걸려있는데,
    // 이 프로젝트엔 아직 Customer/Account용 JPA 엔티티가 없어서(다른 팀원 담당, 미구현)
    // 네이티브 SQL로 FK를 만족하는 최소한의 행만 직접 심는다.
    @BeforeEach
    void seedCustomerAndAccounts() {
        customerId = insertCustomer();
        accountA = insertAccount(customerId);
        accountB = insertAccount(customerId);
    }

    @Test
    @DisplayName("신규 도메인 객체를 저장하면 ID가 채번되어 반환된다")
    void save_newAutoTransfer_assignsId() {
        AutoTransfer newAutoTransfer = AutoTransfer.register(
                customerId,
                accountA,
                "110987654321",
                "홍길동",
                10000L,
                1,
                15,
                LocalDate.now().plusDays(1),
                LocalDate.now().plusYears(1),
                "내메모",
                "받는메모",
                LocalDateTime.now());

        AutoTransfer saved = adapter.save(newAutoTransfer);

        assertThat(saved.getAutoTransferId()).isNotNull();
        assertThat(repository.findById(saved.getAutoTransferId())).isPresent();
    }

    @Test
    @DisplayName("조회 후 변경한 도메인 객체를 저장하면 변경 내용이 DB에 반영된다")
    void save_changedAutoTransfer_updatesRow() {
        AutoTransferJpaEntity entity =
                repository.save(autoTransfer(accountA, "110000000001", AutoTransferStatus.NORMAL, 15));
        entityManager.flush();
        entityManager.clear();

        AutoTransfer domain = adapter.findById(entity.getAutoTransferId()).orElseThrow();
        domain.change(20000L, 3, domain.getEndDate(), "새메모", "새받는메모");
        adapter.save(domain);
        entityManager.flush();
        entityManager.clear();

        AutoTransferJpaEntity updated =
                repository.findById(entity.getAutoTransferId()).orElseThrow();
        assertThat(updated.getAmount()).isEqualTo(20000L);
        assertThat(updated.getCycleMonths()).isEqualTo(3);
        assertThat(updated.getMyPassbookMemo()).isEqualTo("새메모");
    }

    @Test
    @DisplayName("존재하지 않는 ID로 조회하면 빈 Optional을 반환한다")
    void findById_notFound_returnsEmpty() {
        assertThat(adapter.findById(999_999L)).isEmpty();
    }

    @Test
    @DisplayName("동일 출금계좌·입금계좌·이체지정일 조합이 정상 상태로 존재하면 true를 반환한다")
    void existsActiveDuplicate_sameCombinationNormalStatus_returnsTrue() {
        repository.save(autoTransfer(accountA, "110000000002", AutoTransferStatus.NORMAL, 20));
        entityManager.flush();
        entityManager.clear();

        boolean result = adapter.existsActiveDuplicate(accountA, "110000000002", 20);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("같은 조합이어도 해지 상태면 중복으로 보지 않는다")
    void existsActiveDuplicate_terminatedStatus_returnsFalse() {
        repository.save(autoTransfer(accountA, "110000000003", AutoTransferStatus.TERMINATED, 20));
        entityManager.flush();
        entityManager.clear();

        boolean result = adapter.existsActiveDuplicate(accountA, "110000000003", 20);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("출금계좌ID로 필터링하고, status가 null이면 전체 상태를 조회한다")
    void search_filterByAccountAndAllStatus() {
        repository.save(autoTransfer(accountA, "110000000004", AutoTransferStatus.NORMAL, 10));
        repository.save(autoTransfer(accountA, "110000000005", AutoTransferStatus.TERMINATED, 11));
        repository.save(autoTransfer(accountB, "110000000006", AutoTransferStatus.NORMAL, 12));
        entityManager.flush();
        entityManager.clear();

        Page<AutoTransfer> result = adapter.search(customerId, accountA, null, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent())
                .extracting(AutoTransfer::getWithdrawalAccountId)
                .containsOnly(accountA);
    }

    @Test
    @DisplayName("registeredAt이 동률이면 autoTransferId 내림차순으로 정렬돼 페이지가 뒤섞이지 않는다")
    void search_tiedRegisteredAt_ordersByAutoTransferIdDescForDeterministicPaging() {
        // autoTransfer() 픽스처는 registeredAt이 전부 고정값이라 동률 상황을 자연히 재현한다
        AutoTransferJpaEntity e1 =
                repository.save(autoTransfer(accountA, "110000000020", AutoTransferStatus.NORMAL, 10));
        AutoTransferJpaEntity e2 =
                repository.save(autoTransfer(accountA, "110000000021", AutoTransferStatus.NORMAL, 11));
        AutoTransferJpaEntity e3 =
                repository.save(autoTransfer(accountA, "110000000022", AutoTransferStatus.NORMAL, 12));
        entityManager.flush();
        entityManager.clear();

        Page<AutoTransfer> page0 = adapter.search(customerId, accountA, null, PageRequest.of(0, 2));
        Page<AutoTransfer> page1 = adapter.search(customerId, accountA, null, PageRequest.of(1, 2));

        assertThat(page0.getContent())
                .extracting(AutoTransfer::getAutoTransferId)
                .containsExactly(e3.getAutoTransferId(), e2.getAutoTransferId());
        assertThat(page1.getContent())
                .extracting(AutoTransfer::getAutoTransferId)
                .containsExactly(e1.getAutoTransferId());
    }

    @Test
    @DisplayName("Pageable.unpaged()로 조회하면 offset/limit 없이 조건에 맞는 전체 건을 한 번에 반환한다 (#297)")
    void search_unpaged_returnsAllMatchingRowsInOnePage() {
        repository.save(autoTransfer(accountA, "110000000010", AutoTransferStatus.NORMAL, 10));
        repository.save(autoTransfer(accountA, "110000000011", AutoTransferStatus.NORMAL, 11));
        repository.save(autoTransfer(accountA, "110000000012", AutoTransferStatus.NORMAL, 12));
        entityManager.flush();
        entityManager.clear();

        Page<AutoTransfer> result =
                adapter.search(customerId, accountA, null, org.springframework.data.domain.Pageable.unpaged());

        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getTotalPages()).isEqualTo(1);
        // PageResponse.from()이 이 값들을 그대로 응답에 싣는다 - #297 요청 형식(size=totalCount, page=0) 검증
        assertThat(result.getNumber()).isEqualTo(0);
        assertThat(result.getSize()).isEqualTo(3);
    }

    @Test
    @DisplayName("status를 지정하면 해당 상태만 조회된다")
    void search_filterByStatus() {
        repository.save(autoTransfer(accountA, "110000000007", AutoTransferStatus.NORMAL, 10));
        repository.save(autoTransfer(accountA, "110000000008", AutoTransferStatus.TERMINATED, 11));
        entityManager.flush();
        entityManager.clear();

        Page<AutoTransfer> result =
                adapter.search(customerId, accountA, AutoTransferStatus.NORMAL, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getStatus()).isEqualTo(AutoTransferStatus.NORMAL);
    }

    @Test
    @DisplayName("customerId가 실제 소유자와 다르면 withdrawalAccountId가 맞아도 조회되지 않는다 (타 고객 접근 차단)")
    void search_customerIdMismatch_returnsEmpty() {
        repository.save(autoTransfer(accountA, "110000000009", AutoTransferStatus.NORMAL, 10));
        entityManager.flush();
        entityManager.clear();

        Long otherCustomerId = insertCustomer();

        Page<AutoTransfer> result = adapter.search(otherCustomerId, accountA, null, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("정상 상태이고 다음 실행일이 오늘인 자동이체만 조회된다 (미래 실행일은 제외)")
    void findDueForExecution_filtersByStatusAndNextExecutionDate() {
        LocalDate today = LocalDate.of(2026, 3, 15);
        repository.save(autoTransferDue(accountA, "110000000030", AutoTransferStatus.NORMAL, today));
        repository.save(autoTransferDue(accountA, "110000000031", AutoTransferStatus.TERMINATED, today));
        repository.save(autoTransferDue(accountA, "110000000032", AutoTransferStatus.NORMAL, today.plusDays(1)));
        entityManager.flush();
        entityManager.clear();

        var result = adapter.findDueForExecution(today);

        assertThat(result).extracting(AutoTransfer::getDepositAccountNumber).containsExactly("110000000030");
    }

    @Test
    @DisplayName("다음 실행일이 과거(밀린 회차)여도 조회된다 - 실패로 멈춘 건이 다음 배치에서 자동 재시도되게 함")
    void findDueForExecution_includesPastNextExecutionDate() {
        LocalDate today = LocalDate.of(2026, 3, 15);
        repository.save(autoTransferDue(accountA, "110000000035", AutoTransferStatus.NORMAL, today.minusDays(3)));
        entityManager.flush();
        entityManager.clear();

        var result = adapter.findDueForExecution(today);

        assertThat(result).extracting(AutoTransfer::getDepositAccountNumber).containsExactly("110000000035");
    }

    @Test
    @DisplayName("조회 결과는 registeredAt 오름차순으로 정렬된다 (POL-037)")
    void findDueForExecution_ordersByRegisteredAtAscending() {
        LocalDate today = LocalDate.of(2026, 3, 15);
        AutoTransferJpaEntity later = repository.save(
                autoTransferDueWithRegisteredAt(accountA, "110000000033", today, LocalDateTime.of(2026, 1, 2, 0, 0)));
        AutoTransferJpaEntity earlier = repository.save(
                autoTransferDueWithRegisteredAt(accountA, "110000000034", today, LocalDateTime.of(2026, 1, 1, 0, 0)));
        entityManager.flush();
        entityManager.clear();

        var result = adapter.findDueForExecution(today);

        assertThat(result)
                .extracting(AutoTransfer::getAutoTransferId)
                .containsExactly(earlier.getAutoTransferId(), later.getAutoTransferId());
    }

    @Test
    @DisplayName("조회기간 내 정상/오류 회차만 조회되고, 기간 밖·PROCESSING 회차는 제외된다")
    void search_executionHistory_filtersByPeriodAndExcludesProcessing() {
        AutoTransferJpaEntity autoTransfer =
                repository.save(autoTransfer(accountA, "110000000040", AutoTransferStatus.NORMAL, 10));
        entityManager.flush();

        executionRepository.save(execution(
                autoTransfer, LocalDate.of(2026, 3, 10), ProcessResultStatus.SUCCESS, 10000L, "TXN0001", null));
        executionRepository.save(
                execution(autoTransfer, LocalDate.of(2026, 3, 20), ProcessResultStatus.ERROR, 5000L, null, "잔액부족"));
        executionRepository.save(execution(
                autoTransfer, LocalDate.of(2026, 2, 1), ProcessResultStatus.SUCCESS, 10000L, "TXN0002", null)); // 기간 밖
        executionRepository.save(execution(
                autoTransfer,
                LocalDate.of(2026, 3, 15),
                ProcessResultStatus.PROCESSING,
                10000L,
                null,
                null)); // 아직 확정 안 됨
        entityManager.flush();
        entityManager.clear();

        Page<AutoTransferExecutionHistoryRow> result = adapter.search(
                customerId, accountA, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent())
                .extracting(AutoTransferExecutionHistoryRow::status)
                .containsExactlyInAnyOrder(ProcessResultStatus.SUCCESS, ProcessResultStatus.ERROR);
        assertThat(result.getContent())
                .filteredOn(row -> row.status() == ProcessResultStatus.ERROR)
                .extracting(AutoTransferExecutionHistoryRow::failureReason)
                .containsExactly("잔액부족");
    }

    @Test
    @DisplayName("처리결과 조회도 Pageable.unpaged()면 offset/limit 없이 조건에 맞는 전체 건을 반환한다 (#297)")
    void search_executionHistory_unpaged_returnsAllMatchingRows() {
        AutoTransferJpaEntity autoTransfer =
                repository.save(autoTransfer(accountA, "110000000046", AutoTransferStatus.NORMAL, 10));
        entityManager.flush();
        executionRepository.save(execution(
                autoTransfer, LocalDate.of(2026, 3, 5), ProcessResultStatus.SUCCESS, 10000L, "TXN0010", null));
        executionRepository.save(execution(
                autoTransfer, LocalDate.of(2026, 3, 10), ProcessResultStatus.SUCCESS, 10000L, "TXN0011", null));
        entityManager.flush();
        entityManager.clear();

        Page<AutoTransferExecutionHistoryRow> result = adapter.search(
                customerId,
                accountA,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31),
                org.springframework.data.domain.Pageable.unpaged());

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalPages()).isEqualTo(1);
    }

    @Test
    @DisplayName("customerId가 다르면 withdrawalAccountId가 같아도 조회되지 않는다 (타 고객 접근 차단)")
    void search_executionHistory_customerIdMismatch_returnsEmpty() {
        AutoTransferJpaEntity autoTransfer =
                repository.save(autoTransfer(accountA, "110000000041", AutoTransferStatus.NORMAL, 10));
        entityManager.flush();
        executionRepository.save(execution(
                autoTransfer, LocalDate.of(2026, 3, 10), ProcessResultStatus.SUCCESS, 10000L, "TXN0003", null));
        entityManager.flush();
        entityManager.clear();

        Long otherCustomerId = insertCustomer();

        Page<AutoTransferExecutionHistoryRow> result = adapter.search(
                otherCustomerId, accountA, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("정렬은 실행일자(executionDate) 내림차순이 우선이다 — executedAt이 같아도 executionDate가 늦은 쪽이 먼저 나온다")
    void search_executionHistory_ordersByExecutionDateDescFirst() {
        AutoTransferJpaEntity autoTransfer =
                repository.save(autoTransfer(accountA, "110000000043", AutoTransferStatus.NORMAL, 10));
        entityManager.flush();

        // uk_ate_dup(auto_transfer_id, execution_date) 제약 때문에 executionDate는 다르게 하고,
        // executedAt은 같은 값으로 맞춰서 "executionDate만 다른" 상황을 재현한다.
        LocalDateTime tiedExecutedAt = LocalDateTime.of(2026, 3, 10, 9, 0);
        AutoTransferExecutionJpaEntity e1 = executionRepository.save(executionWithExecutedAt(
                autoTransfer,
                LocalDate.of(2026, 3, 10),
                tiedExecutedAt,
                ProcessResultStatus.SUCCESS,
                10000L,
                "TXN0006",
                null));
        AutoTransferExecutionJpaEntity e2 = executionRepository.save(executionWithExecutedAt(
                autoTransfer,
                LocalDate.of(2026, 3, 11),
                tiedExecutedAt,
                ProcessResultStatus.SUCCESS,
                10000L,
                "TXN0007",
                null));
        entityManager.flush();
        entityManager.clear();

        Page<AutoTransferExecutionHistoryRow> result = adapter.search(
                customerId, accountA, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(AutoTransferExecutionHistoryRow::executionId)
                .containsExactly(e2.getExecutionId(), e1.getExecutionId());
    }

    @Test
    @DisplayName("executionDate가 같으면 executedAt 내림차순으로, 그것도 같으면 executionId 내림차순으로 정렬된다")
    void search_executionHistory_sameExecutionDate_ordersByExecutedAtThenExecutionId() {
        // 같은 출금계좌 아래 서로 다른 자동이체 두 건이 같은 날 실행됐지만 실행시각이 다른 상황
        AutoTransferJpaEntity autoTransferA =
                repository.save(autoTransfer(accountA, "110000000044", AutoTransferStatus.NORMAL, 12));
        AutoTransferJpaEntity autoTransferB =
                repository.save(autoTransfer(accountA, "110000000045", AutoTransferStatus.NORMAL, 13));
        entityManager.flush();

        LocalDate sameDate = LocalDate.of(2026, 3, 12);
        AutoTransferExecutionJpaEntity earlier = executionRepository.save(executionWithExecutedAt(
                autoTransferA, sameDate, sameDate.atTime(9, 0), ProcessResultStatus.SUCCESS, 10000L, "TXN0008", null));
        AutoTransferExecutionJpaEntity later = executionRepository.save(executionWithExecutedAt(
                autoTransferB, sameDate, sameDate.atTime(15, 0), ProcessResultStatus.SUCCESS, 10000L, "TXN0009", null));
        entityManager.flush();
        entityManager.clear();

        Page<AutoTransferExecutionHistoryRow> result = adapter.search(
                customerId, accountA, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(AutoTransferExecutionHistoryRow::executionId)
                .containsExactly(later.getExecutionId(), earlier.getExecutionId());
    }

    @Test
    @DisplayName("정상/오류 처리 건수·금액을 정확히 집계하고 PROCESSING은 집계에서 제외한다")
    void summarize_aggregatesSuccessAndErrorExcludingProcessing() {
        AutoTransferJpaEntity autoTransfer =
                repository.save(autoTransfer(accountA, "110000000042", AutoTransferStatus.NORMAL, 10));
        entityManager.flush();

        executionRepository.save(execution(
                autoTransfer, LocalDate.of(2026, 3, 5), ProcessResultStatus.SUCCESS, 10000L, "TXN0004", null));
        executionRepository.save(execution(
                autoTransfer, LocalDate.of(2026, 3, 10), ProcessResultStatus.SUCCESS, 20000L, "TXN0005", null));
        executionRepository.save(
                execution(autoTransfer, LocalDate.of(2026, 3, 15), ProcessResultStatus.ERROR, 5000L, null, "잔액부족"));
        executionRepository.save(
                execution(autoTransfer, LocalDate.of(2026, 3, 20), ProcessResultStatus.PROCESSING, 7000L, null, null));
        entityManager.flush();
        entityManager.clear();

        AutoTransferExecutionHistoryAggregate result =
                adapter.summarize(customerId, accountA, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.successAmount()).isEqualTo(30000L);
        assertThat(result.errorCount()).isEqualTo(1);
        assertThat(result.errorAmount()).isEqualTo(5000L);
    }

    @Test
    @DisplayName("일치하는 회차가 없으면 집계는 전부 0이다")
    void summarize_noMatches_returnsZeros() {
        AutoTransferExecutionHistoryAggregate result =
                adapter.summarize(customerId, accountA, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

        assertThat(result.successCount()).isZero();
        assertThat(result.successAmount()).isZero();
        assertThat(result.errorCount()).isZero();
        assertThat(result.errorAmount()).isZero();
    }

    private AutoTransferExecutionJpaEntity execution(
            AutoTransferJpaEntity autoTransfer,
            LocalDate executionDate,
            ProcessResultStatus status,
            Long amount,
            String transactionNumber,
            String failureReason) {
        return executionWithExecutedAt(
                autoTransfer,
                executionDate,
                executionDate.atStartOfDay(),
                status,
                amount,
                transactionNumber,
                failureReason);
    }

    private AutoTransferExecutionJpaEntity executionWithExecutedAt(
            AutoTransferJpaEntity autoTransfer,
            LocalDate executionDate,
            LocalDateTime executedAt,
            ProcessResultStatus status,
            Long amount,
            String transactionNumber,
            String failureReason) {
        return AutoTransferExecutionJpaEntity.builder()
                .autoTransfer(autoTransfer)
                .executionDate(executionDate)
                .amount(amount)
                .status(status)
                .transactionNumber(transactionNumber)
                .failureReason(failureReason)
                .executedAt(executedAt)
                .build();
    }

    private AutoTransferJpaEntity autoTransferDue(
            Long withdrawalAccountId,
            String depositAccountNumber,
            AutoTransferStatus status,
            LocalDate nextExecutionDate) {
        return autoTransferDueWithRegisteredAt(
                withdrawalAccountId,
                depositAccountNumber,
                status,
                nextExecutionDate,
                LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    private AutoTransferJpaEntity autoTransferDueWithRegisteredAt(
            Long withdrawalAccountId,
            String depositAccountNumber,
            LocalDate nextExecutionDate,
            LocalDateTime registeredAt) {
        return autoTransferDueWithRegisteredAt(
                withdrawalAccountId, depositAccountNumber, AutoTransferStatus.NORMAL, nextExecutionDate, registeredAt);
    }

    private AutoTransferJpaEntity autoTransferDueWithRegisteredAt(
            Long withdrawalAccountId,
            String depositAccountNumber,
            AutoTransferStatus status,
            LocalDate nextExecutionDate,
            LocalDateTime registeredAt) {
        return AutoTransferJpaEntity.builder()
                .customerId(customerId)
                .withdrawalAccountId(withdrawalAccountId)
                .depositAccountNumber(depositAccountNumber)
                .payeeName("홍길동")
                .amount(10000L)
                .cycleMonths(1)
                .transferDay(nextExecutionDate.getDayOfMonth())
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2027, 1, 1))
                .nextExecutionDate(nextExecutionDate)
                .myPassbookMemo("메모")
                .recipientPassbookMemo("받는메모")
                .status(status)
                .registeredAt(registeredAt)
                .updatedAt(registeredAt)
                .build();
    }

    private AutoTransferJpaEntity autoTransfer(
            Long withdrawalAccountId, String depositAccountNumber, AutoTransferStatus status, int transferDay) {
        return AutoTransferJpaEntity.builder()
                .customerId(customerId)
                .withdrawalAccountId(withdrawalAccountId)
                .depositAccountNumber(depositAccountNumber)
                .payeeName("홍길동")
                .amount(10000L)
                .cycleMonths(1)
                .transferDay(transferDay)
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2027, 1, 1))
                .nextExecutionDate(LocalDate.of(2026, 1, transferDay))
                .myPassbookMemo("메모")
                .recipientPassbookMemo("받는메모")
                .status(status)
                .registeredAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                // auto_transfer.updated_at도 DB DEFAULT가 제거됐고 JPA Auditing도 안 붙어있어(아래 버그 리포트 참고),
                // 이 시드 헬퍼(findById/existsActiveDuplicate/search 준비용)에서는 직접 채워서 그 버그를 우회한다.
                .updatedAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .build();
    }

    private Long insertCustomer() {
        // customer.created_at/updated_at은 V202608041310 마이그레이션에서 DEFAULT CURRENT_TIMESTAMP가
        // 제거됐고(JPA Auditing 정책 전환), Customer용 JPA 엔티티가 아직 없어 Auditing도 안 붙어있다.
        // 그래서 네이티브 INSERT에서 두 값을 직접 채워야 한다.
        // 한 테스트 안에서 두 번째 고객을 만들 때 System.nanoTime()이 짧은 간격에선 값이 겹칠 수 있어 카운터로 유일성을 보장한다.
        // (user_id는 VARCHAR(20), email은 UNIQUE)
        long seq = CUSTOMER_SEQ.incrementAndGet();
        String userId = "u" + seq;
        String email = "test" + seq + "@test.com";
        entityManager
                .createNativeQuery(
                        "INSERT INTO customer (user_id, password_hash, user_name, birth_date, email, phone_number, joined_at, created_at, updated_at) "
                                + "VALUES (:userId, 'x', '홍길동', '1990-01-01', :email, '01012345678', NOW(), NOW(), NOW())")
                .setParameter("userId", userId)
                .setParameter("email", email)
                .executeUpdate();
        return ((Number) entityManager
                        .createNativeQuery("SELECT LAST_INSERT_ID()")
                        .getSingleResult())
                .longValue();
    }

    private Long insertAccount(Long customerId) {
        // account.created_at/updated_at도 V202608031651 마이그레이션에서 DEFAULT가 제거됐다.
        // System.nanoTime() 기반 생성은 짧은 간격의 연속 호출에서 겹칠 수 있어 카운터로 유일성을 보장한다(uk_account_number)
        String accountNumber = String.format("%012d", ACCOUNT_SEQ.incrementAndGet());
        entityManager
                .createNativeQuery(
                        "INSERT INTO account (account_number, customer_id, account_type, status, password_hash, opened_date, created_at, updated_at) "
                                + "VALUES (:accountNumber, :customerId, 'DEMAND_DEPOSIT', 'ACTIVE', 'x', NOW(), NOW(), NOW())")
                .setParameter("accountNumber", accountNumber)
                .setParameter("customerId", customerId)
                .executeUpdate();
        return ((Number) entityManager
                        .createNativeQuery("SELECT LAST_INSERT_ID()")
                        .getSingleResult())
                .longValue();
    }
}
