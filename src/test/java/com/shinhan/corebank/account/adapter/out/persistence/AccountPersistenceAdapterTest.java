package com.shinhan.corebank.account.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.entry;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.account.support.CustomerTestFixture;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.product.adapter.out.persistence.ProductJpaRepository;
import com.shinhan.corebank.product.adapter.out.persistence.ProductTestFixtures;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AccountPersistenceAdapterTest extends IntegrationTestSupport {

    private static final String ACCOUNT_NUMBER = "088100000001";

    private static final String PASSWORD_HASH = "$2a$10$34abEWY4uXLwTEnT5hNow.603a5rWofFx7Bnj59agU.PsESK0v/Yq";

    @Autowired
    private AccountPersistencePort accountPersistencePort;

    @Autowired
    private CustomerTestFixture customerTestFixture;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    private Long customerId;

    @BeforeEach
    void setUp() {
        customerId = customerTestFixture.createCustomer();
    }

    @Test
    @DisplayName("입출금계좌를 저장하면 Account가 DB에 정상적으로 저장된다")
    void saveDemandDepositAccount() {
        // given
        LocalDateTime openedDate = LocalDateTime.of(2026, 8, 10, 10, 0);

        Account account = Account.open(
                ACCOUNT_NUMBER, customerId, null, AccountType.DEMAND_DEPOSIT, PASSWORD_HASH, openedDate, null);

        // when
        Account savedAccount = accountPersistencePort.save(account);

        // then
        assertThat(savedAccount.getAccountId()).isNotNull();

        assertThat(savedAccount.getAccountNumber()).isEqualTo(ACCOUNT_NUMBER);

        assertThat(savedAccount.getCustomerId()).isEqualTo(customerId);

        assertThat(savedAccount.getProductId()).isNull();

        assertThat(savedAccount.getAccountType()).isEqualTo(AccountType.DEMAND_DEPOSIT);

        assertThat(savedAccount.getBalance()).isZero();

        assertThat(savedAccount.getStatus()).isEqualTo(AccountStatus.ACTIVE);

        assertThat(savedAccount.getPasswordHash()).isEqualTo(PASSWORD_HASH);

        assertThat(savedAccount.getPasswordFailureCount()).isZero();
        assertThat(savedAccount.isPasswordLocked()).isFalse();

        assertThat(savedAccount.getAlias()).isNull();
        assertThat(savedAccount.getDisplayOrder()).isNull();

        assertThat(savedAccount.isWithdrawalRegistered()).isFalse();
        assertThat(savedAccount.getWithdrawalRegisteredAt()).isNull();

        assertThat(savedAccount.getOpenedDate()).isEqualTo(openedDate);

        assertThat(savedAccount.getMaturityDate()).isNull();
        assertThat(savedAccount.getClosedDate()).isNull();
        assertThat(savedAccount.getLastTransactionAt()).isNull();

        assertThat(savedAccount.getVersion()).isNotNull();

        assertThat(savedAccount.getCreatedAt()).isNotNull();
        assertThat(savedAccount.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("저장된 입출금계좌의 값이 account 테이블에 동일하게 저장된다")
    void saveDemandDepositAccountToDatabase() {
        // given
        LocalDateTime openedDate = LocalDateTime.of(2026, 8, 10, 10, 0);

        Account account = Account.open(
                ACCOUNT_NUMBER, customerId, null, AccountType.DEMAND_DEPOSIT, PASSWORD_HASH, openedDate, null);

        // when
        Account savedAccount = accountPersistencePort.save(account);

        // then
        AccountRow row = jdbcTemplate.queryForObject(
                """
                        SELECT
                            account_id,
                            account_number,
                            customer_id,
                            product_id,
                            account_type,
                            balance,
                            status,
                            password_hash,
                            password_failure_count,
                            password_locked,
                            withdrawal_registered
                        FROM account
                        WHERE account_id = ?
                        """,
                (rs, rowNum) -> new AccountRow(
                        rs.getLong("account_id"),
                        rs.getString("account_number"),
                        rs.getLong("customer_id"),
                        (Long) rs.getObject("product_id"),
                        rs.getString("account_type"),
                        rs.getLong("balance"),
                        rs.getString("status"),
                        rs.getString("password_hash"),
                        rs.getInt("password_failure_count"),
                        rs.getBoolean("password_locked"),
                        rs.getBoolean("withdrawal_registered")),
                savedAccount.getAccountId());

        assertThat(row).isNotNull();

        assertThat(row.accountId()).isEqualTo(savedAccount.getAccountId());

        assertThat(row.accountNumber()).isEqualTo(ACCOUNT_NUMBER);

        assertThat(row.customerId()).isEqualTo(customerId);

        assertThat(row.productId()).isNull();

        assertThat(row.accountType()).isEqualTo(AccountType.DEMAND_DEPOSIT.name());

        assertThat(row.balance()).isZero();

        assertThat(row.status()).isEqualTo(AccountStatus.ACTIVE.name());

        assertThat(row.passwordHash()).isEqualTo(PASSWORD_HASH);

        assertThat(row.passwordFailureCount()).isZero();
        assertThat(row.passwordLocked()).isFalse();
        assertThat(row.withdrawalRegistered()).isFalse();
    }

    private record AccountRow(
            Long accountId,
            String accountNumber,
            Long customerId,
            Long productId,
            String accountType,
            long balance,
            String status,
            String passwordHash,
            int passwordFailureCount,
            boolean passwordLocked,
            boolean withdrawalRegistered) {}

    @Test
    @DisplayName("기존 계좌를 다시 저장해도 감사 시각이 유지된다")
    void preserveAuditTimestampsWhenSavingExistingAccount() {
        // given
        LocalDateTime openedDate = LocalDateTime.of(2026, 8, 10, 10, 0);

        Account account = Account.open(
                ACCOUNT_NUMBER, customerId, null, AccountType.DEMAND_DEPOSIT, PASSWORD_HASH, openedDate, null);

        Account savedAccount = accountPersistencePort.save(account);

        LocalDateTime createdAt = savedAccount.getCreatedAt();

        assertThat(createdAt).isNotNull();
        assertThat(savedAccount.getUpdatedAt()).isNotNull();

        // when
        Account resavedAccount = accountPersistencePort.save(savedAccount);

        // then
        assertThat(resavedAccount.getAccountId()).isEqualTo(savedAccount.getAccountId());

        assertThat(resavedAccount.getCreatedAt()).isEqualTo(createdAt);

        assertThat(resavedAccount.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("기존 계좌의 version이 DB version과 다르면 동시 수정 예외가 발생한다")
    void throwExceptionWhenAccountVersionIsStale() {
        // given
        LocalDateTime openedDate = LocalDateTime.of(2026, 8, 10, 10, 0);

        Account account = Account.open(
                ACCOUNT_NUMBER, customerId, null, AccountType.DEMAND_DEPOSIT, PASSWORD_HASH, openedDate, null);

        Account savedAccount = accountPersistencePort.save(account);

        Long originalVersion = savedAccount.getVersion();

        entityManager.flush();

        jdbcTemplate.update(
                """
                        UPDATE account
                        SET version = version + 1
                        WHERE account_id = ?
                        """,
                savedAccount.getAccountId());

        entityManager.clear();

        // when
        Throwable thrown = catchThrowable(() -> accountPersistencePort.save(savedAccount));

        // then
        assertThat(thrown).isInstanceOf(BusinessException.class).satisfies(exception -> {
            BusinessException businessException = (BusinessException) exception;

            assertThat(businessException.getErrorCode()).isEqualTo(CommonErrorCode.CONCURRENT_MODIFICATION);
        });

        Long currentVersion = jdbcTemplate.queryForObject(
                """
                        SELECT version
                        FROM account
                        WHERE account_id = ?
                        """,
                Long.class,
                savedAccount.getAccountId());

        assertThat(currentVersion).isEqualTo(originalVersion + 1);
    }

    @Test
    @DisplayName("고객 ID로 계좌를 조회하면 해당 고객의 계좌만 반환한다")
    void findAllByCustomerIdReturnsOnlyOwnedAccounts() {
        // given
        Long otherCustomerId = customerTestFixture.createCustomer();

        Account firstAccount = Account.open(
                "088100000002",
                customerId,
                null,
                AccountType.DEMAND_DEPOSIT,
                PASSWORD_HASH,
                LocalDateTime.of(2026, 8, 10, 10, 0),
                null);

        Account secondAccount = Account.open(
                "088100000003",
                customerId,
                null,
                AccountType.DEMAND_DEPOSIT,
                PASSWORD_HASH,
                LocalDateTime.of(2026, 8, 10, 11, 0),
                null);

        Account otherCustomerAccount = Account.open(
                "088100000004",
                otherCustomerId,
                null,
                AccountType.DEMAND_DEPOSIT,
                PASSWORD_HASH,
                LocalDateTime.of(2026, 8, 10, 12, 0),
                null);

        accountPersistencePort.save(firstAccount);
        accountPersistencePort.save(secondAccount);
        accountPersistencePort.save(otherCustomerAccount);

        // when
        List<Account> result = accountPersistencePort.findAllByCustomerId(customerId);

        // then
        assertThat(result).hasSize(2);

        assertThat(result)
                .extracting(Account::getAccountNumber)
                .containsExactlyInAnyOrder("088100000002", "088100000003");

        assertThat(result).allMatch(account -> account.getCustomerId().equals(customerId));
    }

    @Test
    @DisplayName("계좌 ID와 고객 ID로 본인 계좌를 조회한다")
    void findByAccountIdAndCustomerId() {
        // given
        Account savedAccount = accountPersistencePort.save(Account.open(
                "088100000021",
                customerId,
                null,
                AccountType.DEMAND_DEPOSIT,
                PASSWORD_HASH,
                LocalDateTime.of(2026, 8, 10, 10, 0),
                null));

        // when
        Optional<Account> result =
                accountPersistencePort.findByAccountIdAndCustomerId(savedAccount.getAccountId(), customerId);

        // then
        assertThat(result).isPresent();

        assertThat(result.get().getAccountId()).isEqualTo(savedAccount.getAccountId());

        assertThat(result.get().getCustomerId()).isEqualTo(customerId);
    }

    @Test
    @DisplayName("다른 고객의 계좌는 계좌 ID로 조회해도 반환하지 않는다")
    void findByAccountIdAndDifferentCustomerIdReturnsEmpty() {
        // given
        Long otherCustomerId = customerTestFixture.createCustomer();

        Account savedAccount = accountPersistencePort.save(Account.open(
                "088100000022",
                customerId,
                null,
                AccountType.DEMAND_DEPOSIT,
                PASSWORD_HASH,
                LocalDateTime.of(2026, 8, 10, 10, 0),
                null));

        // when
        Optional<Account> result =
                accountPersistencePort.findByAccountIdAndCustomerId(savedAccount.getAccountId(), otherCustomerId);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 계좌 ID는 빈 결과를 반환한다")
    void findByNonExistingAccountIdReturnsEmpty() {
        // when
        Optional<Account> result = accountPersistencePort.findByAccountIdAndCustomerId(Long.MAX_VALUE, customerId);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("계좌 ID 목록으로 잔액을 락 없이 일괄 조회한다 — 다른 도메인의 대사 배치(#378)가 쓰는 경로")
    void findBalancesByAccountIds() {
        // given
        Account first = accountPersistencePort.save(Account.importExisting(
                "088100000023",
                customerId,
                null,
                AccountType.DEMAND_DEPOSIT,
                100000L,
                AccountStatus.ACTIVE,
                PASSWORD_HASH,
                LocalDateTime.of(2026, 8, 10, 10, 0),
                null));
        Account second = accountPersistencePort.save(Account.importExisting(
                "088100000024",
                customerId,
                null,
                AccountType.DEMAND_DEPOSIT,
                50000L,
                AccountStatus.ACTIVE,
                PASSWORD_HASH,
                LocalDateTime.of(2026, 8, 10, 10, 0),
                null));

        // when
        var balances =
                accountPersistencePort.findBalancesByAccountIds(List.of(first.getAccountId(), second.getAccountId()));

        // then
        assertThat(balances).containsEntry(first.getAccountId(), 100000L).containsEntry(second.getAccountId(), 50000L);
    }

    @Test
    @DisplayName("커서(afterAccountId) 다음 계좌부터 account_id 오름차순으로 limit개만 잔액을 조회한다 — 전수 대사(#468)의 keyset 페이지")
    void findBalancesAfter() {
        // given — 같은 트랜잭션에서 연달아 저장해 ID가 이어지므로, 커서를 첫 계좌 바로 앞에 두면 시드 계좌가 섞이지 않는다
        Account first = saveDemandDeposit("088100000025", 100000L);
        Account second = saveDemandDeposit("088100000026", 50000L);
        long cursor = first.getAccountId() - 1;

        // when
        var firstPage = accountPersistencePort.findBalancesAfter(cursor, 1);
        var nextPage = accountPersistencePort.findBalancesAfter(firstPage.lastKey(), 1);

        // then
        assertThat(firstPage).containsExactly(entry(first.getAccountId(), 100000L));
        assertThat(nextPage).containsExactly(entry(second.getAccountId(), 50000L));
    }

    @Test
    @DisplayName("커서 뒤에 계좌가 없으면 빈 맵을 돌려준다 — 전수 대사의 종료 조건")
    void findBalancesAfterReturnsEmptyAtEnd() {
        // when
        var page = accountPersistencePort.findBalancesAfter(Long.MAX_VALUE, 1000);

        // then
        assertThat(page).isEmpty();
    }

    @Test
    @DisplayName("DB 보류액이 도메인 객체로 정확하게 복원된다")
    void restoresHoldAmountFromDatabase() {
        // given
        LocalDateTime openedAt = LocalDateTime.of(2026, 8, 10, 10, 0);

        Account account = Account.open(
                ACCOUNT_NUMBER, customerId, null, AccountType.DEMAND_DEPOSIT, PASSWORD_HASH, openedAt, null);

        Account saved = accountPersistencePort.save(account);
        entityManager.flush();

        jdbcTemplate.update(
                """
                UPDATE account
                SET balance = ?, hold_amount = ?
                WHERE account_id = ?
                """,
                100_000L,
                30_000L,
                saved.getAccountId());

        entityManager.clear();

        // when
        Account restored = accountPersistencePort
                .findByAccountIdAndCustomerId(saved.getAccountId(), customerId)
                .orElseThrow();

        // then
        assertThat(restored.getBalance()).isEqualTo(100_000L);
        assertThat(restored.getHoldAmount()).isEqualTo(30_000L);
        assertThat(restored.getAvailableBalance()).isEqualTo(70_000L);
    }

    @Test
    @DisplayName("만기 후보 조회: ACTIVE 예금 중 기준일까지 만기된 계좌만 ID 순으로 조회한다")
    void findMaturityCandidateIdsAfter_filtersAndPaginates() {
        Long productId = productJpaRepository
                .save(ProductTestFixtures.productWithCode("MAT-633-01"))
                .getProductId();

        Account first =
                saveMaturityAccount("088200000091", productId, AccountStatus.ACTIVE, LocalDate.of(2026, 10, 24));

        Account second =
                saveMaturityAccount("088200000092", productId, AccountStatus.ACTIVE, LocalDate.of(2026, 10, 26));

        saveMaturityAccount("088200000093", productId, AccountStatus.MATURED, LocalDate.of(2026, 10, 25));

        saveMaturityAccount("088200000094", productId, AccountStatus.ACTIVE, LocalDate.of(2026, 10, 27));

        saveDemandDeposit("088100000095", 100_000L);

        entityManager.flush();
        entityManager.clear();

        LocalDate maturityBaseDate = LocalDate.of(2026, 10, 26);

        List<Long> firstPage =
                accountPersistencePort.findMaturityCandidateIdsAfter(first.getAccountId() - 1, maturityBaseDate, 1);

        List<Long> secondPage =
                accountPersistencePort.findMaturityCandidateIdsAfter(first.getAccountId(), maturityBaseDate, 1);

        List<Long> lastPage =
                accountPersistencePort.findMaturityCandidateIdsAfter(second.getAccountId(), maturityBaseDate, 1);

        assertThat(firstPage).containsExactly(first.getAccountId());
        assertThat(secondPage).containsExactly(second.getAccountId());
        assertThat(lastPage).isEmpty();
    }

    @Test
    @DisplayName("잠금 조회한 계좌의 만기 상태 전이가 DB에 저장된다")
    void findByAccountIdForUpdate_savesMaturityTransition() {
        Long productId = productJpaRepository
                .save(ProductTestFixtures.productWithCode("MAT-633-02"))
                .getProductId();

        Account saved =
                saveMaturityAccount("088200000096", productId, AccountStatus.ACTIVE, LocalDate.of(2026, 10, 26));

        entityManager.flush();
        entityManager.clear();

        Account locked = accountPersistencePort
                .findByAccountIdForUpdate(saved.getAccountId())
                .orElseThrow();

        assertThat(locked.markMatured(LocalDate.of(2026, 10, 26))).isTrue();

        accountPersistencePort.save(locked);

        entityManager.flush();
        entityManager.clear();

        Account restored = accountPersistencePort
                .findByAccountIdAndCustomerId(saved.getAccountId(), customerId)
                .orElseThrow();

        assertThat(restored.getStatus()).isEqualTo(AccountStatus.MATURED);
        assertThat(restored.getBalance()).isZero();
        assertThat(restored.getClosedDate()).isNull();
    }

    private Account saveMaturityAccount(
            String accountNumber, Long productId, AccountStatus status, LocalDate maturityDate) {

        Account account = Account.importExisting(
                accountNumber,
                customerId,
                productId,
                AccountType.TIME_DEPOSIT,
                0L,
                status,
                PASSWORD_HASH,
                LocalDateTime.of(2026, 9, 1, 10, 0),
                maturityDate);

        return accountPersistencePort.save(account);
    }

    private Account saveDemandDeposit(String accountNumber, long balance) {
        return accountPersistencePort.save(Account.importExisting(
                accountNumber,
                customerId,
                null,
                AccountType.DEMAND_DEPOSIT,
                balance,
                AccountStatus.ACTIVE,
                PASSWORD_HASH,
                LocalDateTime.of(2026, 8, 10, 10, 0),
                null));
    }

    @Test
    @DisplayName("만기 후보 조회 시 기준일이 없으면 거부한다")
    void rejectNullMaturityBaseDate() {
        assertThatThrownBy(() -> accountPersistencePort.findMaturityCandidateIdsAfter(0L, null, 500))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING);
    }

    @Test
    @DisplayName("만기 후보 조회 시 음수 커서를 거부한다")
    void rejectNegativeMaturityCursor() {
        assertThatThrownBy(() ->
                        accountPersistencePort.findMaturityCandidateIdsAfter(-1L, LocalDate.of(2026, 10, 26), 500))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("만기 후보 조회 시 0 이하 페이지 크기를 거부한다")
    void rejectInvalidMaturityPageSize() {
        assertThatThrownBy(
                        () -> accountPersistencePort.findMaturityCandidateIdsAfter(0L, LocalDate.of(2026, 10, 26), 0))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_PAGE_SIZE);
    }

    @Test
    @DisplayName("잠금 조회 시 계좌 ID가 없으면 거부한다")
    void rejectNullAccountIdForUpdate() {
        assertThatThrownBy(() -> accountPersistencePort.findByAccountIdForUpdate(null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.REQUIRED_FIELD_MISSING);
    }
}
