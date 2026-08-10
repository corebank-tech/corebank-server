package com.shinhan.corebank.autotransfer.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.autotransfer.domain.AutoTransfer;
import com.shinhan.corebank.autotransfer.domain.AutoTransferStatus;
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
    EntityManager entityManager;

    private static final AtomicLong CUSTOMER_SEQ = new AtomicLong();

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
                customerId, accountA, "110987654321", "홍길동",
                10000L, 1, 15,
                LocalDate.now().plusDays(1), LocalDate.now().plusYears(1),
                "내메모", "받는메모", LocalDateTime.now());

        AutoTransfer saved = adapter.save(newAutoTransfer);

        assertThat(saved.getAutoTransferId()).isNotNull();
        assertThat(repository.findById(saved.getAutoTransferId())).isPresent();
    }

    @Test
    @DisplayName("조회 후 변경한 도메인 객체를 저장하면 변경 내용이 DB에 반영된다")
    void save_changedAutoTransfer_updatesRow() {
        AutoTransferJpaEntity entity = repository.save(autoTransfer(accountA, "110000000001", AutoTransferStatus.NORMAL, 15));
        entityManager.flush();
        entityManager.clear();

        AutoTransfer domain = adapter.findById(entity.getAutoTransferId()).orElseThrow();
        domain.change(20000L, 3, domain.getEndDate(), "새메모", "새받는메모");
        adapter.save(domain);
        entityManager.flush();
        entityManager.clear();

        AutoTransferJpaEntity updated = repository.findById(entity.getAutoTransferId()).orElseThrow();
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
    @DisplayName("status를 지정하면 해당 상태만 조회된다")
    void search_filterByStatus() {
        repository.save(autoTransfer(accountA, "110000000007", AutoTransferStatus.NORMAL, 10));
        repository.save(autoTransfer(accountA, "110000000008", AutoTransferStatus.TERMINATED, 11));
        entityManager.flush();
        entityManager.clear();

        Page<AutoTransfer> result = adapter.search(customerId, accountA, AutoTransferStatus.NORMAL, PageRequest.of(0, 10));

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

    private AutoTransferJpaEntity autoTransfer(Long withdrawalAccountId, String depositAccountNumber, AutoTransferStatus status, int transferDay) {
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
        // 한 테스트 안에서 두 번째 고객을 만들 때 System.nanoTime()이 짧은 간격에선 값이 겹칠 수 있어 카운터로 유일성을 보장한다(user_id는 VARCHAR(20), email은 UNIQUE)
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
        // account.created_at/updated_at도 V202608031651 마이그레이션에서 DEFAULT가 제거됐다.
        String accountNumber = String.format("%012d", System.nanoTime() % 1_000_000_000_000L);
        entityManager.createNativeQuery(
                        "INSERT INTO account (account_number, customer_id, account_type, status, password_hash, opened_date, created_at, updated_at) "
                                + "VALUES (:accountNumber, :customerId, 'DEMAND_DEPOSIT', 'ACTIVE', 'x', NOW(), NOW(), NOW())")
                .setParameter("accountNumber", accountNumber)
                .setParameter("customerId", customerId)
                .executeUpdate();
        return ((Number) entityManager.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
    }
}
