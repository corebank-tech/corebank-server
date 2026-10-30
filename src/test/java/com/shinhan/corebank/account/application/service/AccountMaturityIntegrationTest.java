package com.shinhan.corebank.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.adapter.out.persistence.AccountStatusHistoryPersistenceAdapter;
import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.account.support.CustomerTestFixture;
import com.shinhan.corebank.business.api.BusinessDateProvider;
import com.shinhan.corebank.product.adapter.out.persistence.ProductJpaRepository;
import com.shinhan.corebank.product.adapter.out.persistence.ProductTestFixtures;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class AccountMaturityIntegrationTest extends IntegrationTestSupport {

    private static final LocalDate COB_DATE = LocalDate.of(2000, 1, 7);

    private static final LocalDate BASE_DATE = LocalDate.of(2000, 1, 10);

    private static final LocalDateTime OPENED_AT = LocalDateTime.of(1999, 12, 1, 10, 0);

    private static final String PASSWORD_HASH = "$2a$10$34abEWY4uXLwTEnT5hNow.603a5rWofFx7Bnj59agU.PsESK0v/Yq";

    @Autowired
    private AccountMaturityService maturityService;

    @Autowired
    private AccountMaturityTransitionService transitionService;

    @Autowired
    private BusinessDateProvider businessDateProvider;

    @Autowired
    private CustomerTestFixture customerTestFixture;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 두 번째 감사 이력 저장 뒤 실패를 주입해 청크 전체가 롤백되는지 본다 — 앞선 저장은 실제로 타도록 스파이로 감싼다.
    @MockitoSpyBean
    private AccountStatusHistoryPersistenceAdapter historyAdapter;

    private Long customerId;
    private Long productId;

    @BeforeEach
    void setUp() {
        customerId = customerTestFixture.createCustomer();

        String productCode = "MT633-" + UUID.randomUUID().toString().substring(0, 8);

        productId = productJpaRepository
                .save(ProductTestFixtures.productWithCode(productCode))
                .getProductId();
    }

    @AfterEach
    void tearDown() {
        if (customerId != null) {
            jdbcTemplate.update(
                    """
                    DELETE h
                    FROM account_status_history h
                    JOIN account a ON a.account_id = h.account_id
                    WHERE a.customer_id = ?
                    """,
                    customerId);

            jdbcTemplate.update("DELETE FROM account WHERE customer_id = ?", customerId);
        }

        if (productId != null) {
            productJpaRepository.deleteById(productId);
        }

        if (customerId != null) {
            customerTestFixture.deleteCustomer(customerId);
        }
    }

    @Test
    @DisplayName("청크 도중 계좌 조회가 실패하면 앞선 상태 변경도 롤백된다")
    void transition_rollsBackEntireChunk() {
        List<Long> ids = insertAccounts(2, BASE_DATE, AccountStatus.ACTIVE);

        Long first = ids.get(0);
        Long second = ids.get(1);

        // 첫 계좌는 정상 처리되지만, 두 번째 조회 대상은 존재하지 않는다.
        assertThatThrownBy(() -> transitionService.transition(List.of(first, Long.MAX_VALUE), BASE_DATE))
                .isInstanceOf(RuntimeException.class);

        // 첫 계좌도 커밋되지 않았어야 한다.
        assertThat(status(first)).isEqualTo("ACTIVE");
        assertThat(status(second)).isEqualTo("ACTIVE");

        // [추가] 실패한 청크에는 감사 이력도 남지 않아야 한다.
        assertThat(historyCount(first)).isZero();
        assertThat(historyCount(second)).isZero();

        // 재실행하면 남아 있는 두 계좌가 정상 전이된다.
        assertThat(transitionService.transition(ids, BASE_DATE)).isEqualTo(2);

        assertThat(status(first)).isEqualTo("MATURED");
        assertThat(status(second)).isEqualTo("MATURED");

        // [추가] 정상 전이된 계좌에는 이력이 각각 1건 남는다.
        assertThat(historyCount(first)).isEqualTo(1L);
        assertThat(historyCount(second)).isEqualTo(1L);
    }

    @Test
    @DisplayName("커밋된 청크는 유지되고 실패한 청크는 재실행할 수 있다")
    void transition_commitsPreviousChunkAndRecovers() {
        List<Long> ids = insertAccounts(3, BASE_DATE, AccountStatus.ACTIVE);

        // 첫 청크는 정상 커밋
        assertThat(transitionService.transition(List.of(ids.get(0)), BASE_DATE)).isEqualTo(1);

        // [추가] 정상 커밋된 청크의 이력은 유지된다.
        assertThat(historyCount(ids.get(0))).isEqualTo(1L);

        // 다음 청크는 첫 계좌를 변경한 뒤 실패
        assertThatThrownBy(() -> transitionService.transition(List.of(ids.get(1), Long.MAX_VALUE), BASE_DATE))
                .isInstanceOf(RuntimeException.class);

        assertThat(status(ids.get(0))).isEqualTo("MATURED");
        assertThat(status(ids.get(1))).isEqualTo("ACTIVE");
        assertThat(status(ids.get(2))).isEqualTo("ACTIVE");

        // [추가] 실패한 청크의 이력은 롤백된다.
        assertThat(historyCount(ids.get(0))).isEqualTo(1L);
        assertThat(historyCount(ids.get(1))).isZero();
        assertThat(historyCount(ids.get(2))).isZero();

        assertNoOutsideCandidates();

        // 남은 ACTIVE 계좌만 탐색하여 처리
        maturityService.runInCob(COB_DATE);

        assertThat(status(ids.get(0))).isEqualTo("MATURED");
        assertThat(status(ids.get(1))).isEqualTo("MATURED");
        assertThat(status(ids.get(2))).isEqualTo("MATURED");

        // [추가] 재실행 후 모든 계좌에 이력이 정확히 1건씩 존재한다.
        assertThat(historyCount(ids.get(0))).isEqualTo(1L);
        assertThat(historyCount(ids.get(1))).isEqualTo(1L);
        assertThat(historyCount(ids.get(2))).isEqualTo(1L);
        assertThat(totalHistoryCount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("501개 계좌를 두 청크로 처리하고 재실행해도 중복 전이하지 않는다")
    void runInCob_processesMultiplePagesIdempotently() {
        assertThat(businessDateProvider.nextBusinessDay(COB_DATE)).isEqualTo(BASE_DATE);

        // PAGE_SIZE=500이므로 최소 두 청크가 필요하다.
        List<Long> dueIds = insertAccounts(501, BASE_DATE, AccountStatus.ACTIVE);

        // 미래 만기 계좌와 이미 만기된 계좌는 제외 여부를 확인한다.
        List<Long> futureIds = insertAccounts(1, BASE_DATE.plusDays(1), AccountStatus.ACTIVE);
        List<Long> maturedIds = insertAccounts(1, BASE_DATE, AccountStatus.MATURED);

        Long first = dueIds.get(0);
        Long last = dueIds.get(500);

        long balanceBefore = totalBalance();
        long ledgerBefore = ledgerCount(first, last);

        assertNoOutsideCandidates();
        maturityService.runInCob(COB_DATE);

        assertThat(status(first)).isEqualTo("MATURED");
        assertThat(status(last)).isEqualTo("MATURED");
        assertThat(status(futureIds.get(0))).isEqualTo("ACTIVE");
        assertThat(status(maturedIds.get(0))).isEqualTo("MATURED");

        // 신규 만기 501개 + 기존 MATURED 1개
        assertThat(countByStatus("MATURED")).isEqualTo(502L);

        // [추가] 실제 전이된 501개 계좌에 대해서만 이력이 생성된다.
        assertThat(totalHistoryCount()).isEqualTo(501L);
        assertThat(historyCount(first)).isEqualTo(1L);
        assertThat(historyCount(last)).isEqualTo(1L);
        assertThat(historyCount(futureIds.get(0))).isZero();
        assertThat(historyCount(maturedIds.get(0))).isZero();

        long firstVersion = version(first);
        long lastVersion = version(last);

        // 동일 COB_DATE 재실행
        maturityService.runInCob(COB_DATE);

        assertThat(countByStatus("MATURED")).isEqualTo(502L);
        assertThat(version(first)).isEqualTo(firstVersion);
        assertThat(version(last)).isEqualTo(lastVersion);

        // [추가] 재실행해도 이력 건수가 증가하지 않는다.
        assertThat(totalHistoryCount()).isEqualTo(501L);
        assertThat(historyCount(first)).isEqualTo(1L);
        assertThat(historyCount(last)).isEqualTo(1L);

        // 만기 감지는 상태만 바꾼다. 잔액과 원장은 그대로다.
        assertThat(totalBalance()).isEqualTo(balanceBefore);
        assertThat(ledgerCount(first, last)).isEqualTo(ledgerBefore);
    }

    @Test
    @DisplayName("감사 이력 저장 실패 시 청크 전체의 상태 변경과 이력이 롤백된다")
    void transition_rollsBackWhenHistorySaveFails() {
        List<Long> ids = insertAccounts(2, BASE_DATE, AccountStatus.ACTIVE);

        Long first = ids.get(0);
        Long second = ids.get(1);

        AtomicInteger saveCount = new AtomicInteger();

        // 두 번째 계좌의 감사 이력까지 실제 저장한 후 예외 발생
        doAnswer(invocation -> {
                    Object result = invocation.callRealMethod();

                    if (saveCount.incrementAndGet() == 2) {
                        throw new IllegalStateException("감사 이력 저장 실패 테스트");
                    }

                    return result;
                })
                .when(historyAdapter)
                .recordMaturity(anyLong(), any(), any(), any());

        // 감사 이력 저장 실패
        assertThatThrownBy(() -> transitionService.transition(ids, BASE_DATE)).isInstanceOf(RuntimeException.class);

        // 두 계좌의 상태 변경이 모두 롤백됐는지 검증
        assertThat(status(first)).isEqualTo("ACTIVE");
        assertThat(status(second)).isEqualTo("ACTIVE");

        // 실제 INSERT된 감사 이력도 롤백됐는지 검증
        assertThat(historyCount(first)).isZero();
        assertThat(historyCount(second)).isZero();

        // 실패 주입 제거
        reset(historyAdapter);

        // 재실행 시 정상 처리되는지 검증
        assertThat(transitionService.transition(ids, BASE_DATE)).isEqualTo(2);

        assertThat(status(first)).isEqualTo("MATURED");
        assertThat(status(second)).isEqualTo("MATURED");

        assertThat(historyCount(first)).isEqualTo(1L);
        assertThat(historyCount(second)).isEqualTo(1L);
    }

    private List<Long> insertAccounts(int count, LocalDate maturityDate, AccountStatus status) {

        int startIndex = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM account WHERE customer_id = ?", Integer.class, customerId);

        jdbcTemplate.batchUpdate(
                """
                INSERT INTO account (
                    account_number,
                    customer_id,
                    product_id,
                    account_type,
                    balance,
                    status,
                    password_hash,
                    opened_date,
                    maturity_date,
                    created_at,\s
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int index) throws SQLException {

                        int sequence = startIndex + index;

                        ps.setString(1, accountNumber(sequence));
                        ps.setLong(2, customerId);
                        ps.setLong(3, productId);
                        ps.setString(4, index % 2 == 0 ? "TIME_DEPOSIT" : "INSTALLMENT_SAVINGS");
                        ps.setLong(5, 10_000L + index);
                        ps.setString(6, status.name());
                        ps.setString(7, PASSWORD_HASH);
                        ps.setTimestamp(8, Timestamp.valueOf(OPENED_AT));
                        ps.setDate(9, java.sql.Date.valueOf(maturityDate));
                        ps.setTimestamp(10, Timestamp.valueOf(OPENED_AT));
                        ps.setTimestamp(11, Timestamp.valueOf(OPENED_AT));
                    }

                    @Override
                    public int getBatchSize() {
                        return count;
                    }
                });

        return jdbcTemplate
                .queryForList(
                        """
                SELECT account_id
                FROM account
                WHERE customer_id = ?
                ORDER BY account_id
                """,
                        Long.class,
                        customerId)
                .stream()
                .skip(startIndex)
                .toList();
    }

    private String accountNumber(int index) {
        return String.format("088%09d", customerId * 1000L + index);
    }

    private String status(Long accountId) {
        return jdbcTemplate.queryForObject("SELECT status FROM account WHERE account_id = ?", String.class, accountId);
    }

    private long version(Long accountId) {
        return jdbcTemplate.queryForObject("SELECT version FROM account WHERE account_id = ?", Long.class, accountId);
    }

    private long countByStatus(String status) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM account
                WHERE customer_id = ?
                  AND status = ?
                """,
                Long.class,
                customerId,
                status);
    }

    private long totalBalance() {
        return jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(SUM(balance), 0)
                FROM account
                WHERE customer_id = ?
                """,
                Long.class,
                customerId);
    }

    private long ledgerCount(Long firstId, Long lastId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM ledger_entry
                WHERE account_id IN (?, ?)
                """,
                Long.class,
                firstId,
                lastId);
    }

    private void assertNoOutsideCandidates() {
        Long count = jdbcTemplate.queryForObject(
                """
            SELECT COUNT(*)
            FROM account
            WHERE customer_id <> ?
              AND status = 'ACTIVE'
              AND account_type IN ('TIME_DEPOSIT', 'INSTALLMENT_SAVINGS')
              AND maturity_date <= ?
            """,
                Long.class,
                customerId,
                BASE_DATE);

        assertThat(count).isZero();
    }

    private long historyCount(Long accountId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM account_status_history
                WHERE account_id = ?
                """,
                Long.class,
                accountId);
    }

    private long totalHistoryCount() {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM account_status_history h
                JOIN account a ON a.account_id = h.account_id
                WHERE a.customer_id = ?
                """,
                Long.class,
                customerId);
    }
}
