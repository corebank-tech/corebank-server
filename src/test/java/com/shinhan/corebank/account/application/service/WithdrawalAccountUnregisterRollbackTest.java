package com.shinhan.corebank.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.adapter.out.persistence.AccountJpaRepository;
import com.shinhan.corebank.account.adapter.out.persistence.AccountPersistenceAdapter;
import com.shinhan.corebank.account.application.port.in.WithdrawalAccountUnregisterCommand;
import com.shinhan.corebank.account.application.port.in.WithdrawalAccountUnregisterUseCase;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.account.support.CustomerTestFixture;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class WithdrawalAccountUnregisterRollbackTest extends IntegrationTestSupport {

    private static final String PASSWORD_HASH = "$2a$10$34abEWY4uXLwTEnT5hNow.603a5rWofFx7Bnj59agU.PsESK0v/Yq";

    @Autowired
    private WithdrawalAccountUnregisterUseCase withdrawalAccountUnregisterUseCase;

    @Autowired
    private AccountJpaRepository accountJpaRepository;

    @Autowired
    private CustomerTestFixture customerTestFixture;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private AccountPersistencePort accountPersistencePort;

    private AccountPersistenceAdapter realAccountPersistenceAdapter;

    private Long customerId;

    @BeforeEach
    void setUp() {
        realAccountPersistenceAdapter = new AccountPersistenceAdapter(accountJpaRepository);

        customerId = customerTestFixture.createCustomer();
    }

    @Test
    @DisplayName("출금계좌 삭제 저장에 실패하면 DB 상태가 등록 상태로 롤백된다")
    void rollbackWhenSaveFails() {
        // given
        Account account = Account.open(
                "088199900091",
                customerId,
                null,
                AccountType.DEMAND_DEPOSIT,
                PASSWORD_HASH,
                LocalDateTime.of(2026, 8, 1, 10, 0),
                null);

        Account savedAccount = realAccountPersistenceAdapter.save(account);

        savedAccount.registerWithdrawalAccount(LocalDateTime.of(2026, 8, 19, 14, 30));

        savedAccount = realAccountPersistenceAdapter.save(savedAccount);

        Long accountId = savedAccount.getAccountId();

        when(accountPersistencePort.findByAccountIdAndCustomerId(accountId, customerId))
                .thenAnswer(invocation ->
                        realAccountPersistenceAdapter.findByAccountIdAndCustomerId(accountId, customerId));

        when(accountPersistencePort.save(any(Account.class))).thenAnswer(invocation -> {
            Account changedAccount = invocation.getArgument(0);

            /*
             * 실제 UPDATE까지 DB에 보낸다.
             */
            realAccountPersistenceAdapter.save(changedAccount);

            accountJpaRepository.flush();

            /*
             * UPDATE 이후 강제로 실패시킨다.
             */
            throw new DataIntegrityViolationException("forced withdrawal unregister save failure");
        });

        WithdrawalAccountUnregisterCommand command = new WithdrawalAccountUnregisterCommand(customerId, accountId);

        // when
        Throwable thrown = catchThrowable(() -> withdrawalAccountUnregisterUseCase.unregister(command));

        // then
        assertThat(thrown)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("forced withdrawal unregister save failure");

        Boolean withdrawalRegistered = jdbcTemplate.queryForObject(
                """
                                SELECT withdrawal_registered
                                FROM account
                                WHERE account_id = ?
                                """,
                Boolean.class,
                accountId);

        LocalDateTime withdrawalRegisteredAt = jdbcTemplate.queryForObject(
                """
                                SELECT withdrawal_registered_at
                                FROM account
                                WHERE account_id = ?
                                """,
                LocalDateTime.class,
                accountId);

        assertThat(withdrawalRegistered).isTrue();

        assertThat(withdrawalRegisteredAt).isNotNull();
    }
}
