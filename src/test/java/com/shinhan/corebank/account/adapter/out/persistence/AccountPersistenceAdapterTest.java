package com.shinhan.corebank.account.adapter.out.persistence;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.account.support.CustomerTestFixture;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@Transactional
class AccountPersistenceAdapterTest extends IntegrationTestSupport {

    private static final String ACCOUNT_NUMBER = "088100000001";

    private static final String PASSWORD_HASH =
            "$2a$10$34abEWY4uXLwTEnT5hNow.603a5rWofFx7Bnj59agU.PsESK0v/Yq";

    @Autowired
    private AccountPersistencePort accountPersistencePort;

    @Autowired
    private CustomerTestFixture customerTestFixture;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private Long customerId;

    @BeforeEach
    void setUp() {
        customerId = customerTestFixture.createCustomer();
    }

    @Test
    @DisplayName("입출금계좌를 저장하면 Account가 DB에 정상적으로 저장된다")
    void saveDemandDepositAccount() {
        // given
        LocalDateTime openedDate =
                LocalDateTime.of(2026, 8, 10, 10, 0);

        Account account = Account.open(
                ACCOUNT_NUMBER,
                customerId,
                null,
                AccountType.DEMAND_DEPOSIT,
                PASSWORD_HASH,
                openedDate,
                null
        );

        // when
        Account savedAccount = accountPersistencePort.save(account);

        // then
        assertThat(savedAccount.getAccountId()).isNotNull();

        assertThat(savedAccount.getAccountNumber())
                .isEqualTo(ACCOUNT_NUMBER);

        assertThat(savedAccount.getCustomerId())
                .isEqualTo(customerId);

        assertThat(savedAccount.getProductId()).isNull();

        assertThat(savedAccount.getAccountType())
                .isEqualTo(AccountType.DEMAND_DEPOSIT);

        assertThat(savedAccount.getBalance()).isZero();

        assertThat(savedAccount.getStatus())
                .isEqualTo(AccountStatus.ACTIVE);

        assertThat(savedAccount.getPasswordHash())
                .isEqualTo(PASSWORD_HASH);

        assertThat(savedAccount.getPasswordFailureCount()).isZero();
        assertThat(savedAccount.isPasswordLocked()).isFalse();

        assertThat(savedAccount.getAlias()).isNull();
        assertThat(savedAccount.getDisplayOrder()).isNull();

        assertThat(savedAccount.isWithdrawalRegistered()).isFalse();
        assertThat(savedAccount.getWithdrawalRegisteredAt()).isNull();

        assertThat(savedAccount.getOpenedDate())
                .isEqualTo(openedDate);

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
        LocalDateTime openedDate =
                LocalDateTime.of(2026, 8, 10, 10, 0);

        Account account = Account.open(
                ACCOUNT_NUMBER,
                customerId,
                null,
                AccountType.DEMAND_DEPOSIT,
                PASSWORD_HASH,
                openedDate,
                null
        );

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
                        rs.getBoolean("withdrawal_registered")
                ),
                savedAccount.getAccountId()
        );

        assertThat(row).isNotNull();

        assertThat(row.accountId())
                .isEqualTo(savedAccount.getAccountId());

        assertThat(row.accountNumber())
                .isEqualTo(ACCOUNT_NUMBER);

        assertThat(row.customerId())
                .isEqualTo(customerId);

        assertThat(row.productId()).isNull();

        assertThat(row.accountType())
                .isEqualTo(AccountType.DEMAND_DEPOSIT.name());

        assertThat(row.balance()).isZero();

        assertThat(row.status())
                .isEqualTo(AccountStatus.ACTIVE.name());

        assertThat(row.passwordHash())
                .isEqualTo(PASSWORD_HASH);

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
            boolean withdrawalRegistered
    ) {
    }

    @Test
    @DisplayName("기존 계좌를 다시 저장해도 감사 시각이 유지된다")
    void preserveAuditTimestampsWhenSavingExistingAccount() {
        // given
        LocalDateTime openedDate =
                LocalDateTime.of(2026, 8, 10, 10, 0);

        Account account = Account.open(
                ACCOUNT_NUMBER,
                customerId,
                null,
                AccountType.DEMAND_DEPOSIT,
                PASSWORD_HASH,
                openedDate,
                null
        );

        Account savedAccount =
                accountPersistencePort.save(account);

        LocalDateTime createdAt = savedAccount.getCreatedAt();

        assertThat(createdAt).isNotNull();
        assertThat(savedAccount.getUpdatedAt()).isNotNull();

        // when
        Account resavedAccount =
                accountPersistencePort.save(savedAccount);

        // then
        assertThat(resavedAccount.getAccountId())
                .isEqualTo(savedAccount.getAccountId());

        assertThat(resavedAccount.getCreatedAt())
                .isEqualTo(createdAt);

        assertThat(resavedAccount.getUpdatedAt())
                .isNotNull();
    }

    @Test
    @DisplayName("기존 계좌의 version이 DB version과 다르면 동시 수정 예외가 발생한다")
    void throwExceptionWhenAccountVersionIsStale() {
        // given
        LocalDateTime openedDate =
                LocalDateTime.of(2026, 8, 10, 10, 0);

        Account account = Account.open(
                ACCOUNT_NUMBER,
                customerId,
                null,
                AccountType.DEMAND_DEPOSIT,
                PASSWORD_HASH,
                openedDate,
                null
        );

        Account savedAccount =
                accountPersistencePort.save(account);

        Long originalVersion = savedAccount.getVersion();

        entityManager.flush();

        jdbcTemplate.update(
                """
                        UPDATE account
                        SET version = version + 1
                        WHERE account_id = ?
                        """,
                savedAccount.getAccountId()
        );

        entityManager.clear();

        // when
        Throwable thrown = catchThrowable(
                () -> accountPersistencePort.save(savedAccount)
        );

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException =
                            (BusinessException) exception;

                    assertThat(businessException.getErrorCode())
                            .isEqualTo(
                                    CommonErrorCode.CONCURRENT_MODIFICATION
                            );
                });

        Long currentVersion = jdbcTemplate.queryForObject(
                """
                        SELECT version
                        FROM account
                        WHERE account_id = ?
                        """,
                Long.class,
                savedAccount.getAccountId()
        );

        assertThat(currentVersion)
                .isEqualTo(originalVersion + 1);
    }

    @Test
    @DisplayName("고객 ID로 계좌를 조회하면 해당 고객의 계좌만 반환한다")
    void findAllByCustomerIdReturnsOnlyOwnedAccounts() {
        // given
        Long otherCustomerId =
                customerTestFixture.createCustomer();

        Account firstAccount = Account.open(
                "088100000002",
                customerId,
                null,
                AccountType.DEMAND_DEPOSIT,
                PASSWORD_HASH,
                LocalDateTime.of(2026, 8, 10, 10, 0),
                null
        );

        Account secondAccount = Account.open(
                "088100000003",
                customerId,
                null,
                AccountType.DEMAND_DEPOSIT,
                PASSWORD_HASH,
                LocalDateTime.of(2026, 8, 10, 11, 0),
                null
        );

        Account otherCustomerAccount = Account.open(
                "088100000004",
                otherCustomerId,
                null,
                AccountType.DEMAND_DEPOSIT,
                PASSWORD_HASH,
                LocalDateTime.of(2026, 8, 10, 12, 0),
                null
        );

        accountPersistencePort.save(firstAccount);
        accountPersistencePort.save(secondAccount);
        accountPersistencePort.save(otherCustomerAccount);

        // when
        List<Account> result =
                accountPersistencePort.findAllByCustomerId(customerId);

        // then
        assertThat(result).hasSize(2);

        assertThat(result)
                .extracting(Account::getAccountNumber)
                .containsExactlyInAnyOrder(
                        "088100000002",
                        "088100000003"
                );

        assertThat(result)
                .allMatch(account ->
                        account.getCustomerId().equals(customerId)
                );
    }
}