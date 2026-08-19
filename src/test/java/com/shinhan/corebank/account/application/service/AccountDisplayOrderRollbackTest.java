package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.adapter.out.persistence.AccountJpaRepository;
import com.shinhan.corebank.account.adapter.out.persistence.AccountPersistenceAdapter;
import com.shinhan.corebank.account.application.port.in.AccountDisplayOrderCommand;
import com.shinhan.corebank.account.application.port.in.AccountDisplayOrderUseCase;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.account.support.CustomerTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class AccountDisplayOrderRollbackTest
        extends IntegrationTestSupport {

    private static final String PASSWORD_HASH =
            "$2a$10$34abEWY4uXLwTEnT5hNow.603a5rWofFx7Bnj59agU.PsESK0v/Yq";

    @Autowired
    private AccountDisplayOrderUseCase
            accountDisplayOrderUseCase;

    @Autowired
    private AccountJpaRepository
            accountJpaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CustomerTestFixture
            customerTestFixture;

    @MockitoBean
    private AccountPersistencePort
            accountPersistencePort;

    private AccountPersistenceAdapter
            realAccountPersistenceAdapter;

    private Long customerId;

    @BeforeEach
    void setUp() {
        realAccountPersistenceAdapter =
                new AccountPersistenceAdapter(
                        accountJpaRepository
                );

        customerId =
                customerTestFixture.createCustomer();
    }

    @AfterEach
    void tearDown() {
        if (customerId != null) {
            jdbcTemplate.update(
                    """
                            DELETE FROM account
                            WHERE customer_id = ?
                            """,
                    customerId
            );

            customerTestFixture
                    .deleteCustomer(customerId);
        }
    }

    @Test
    @DisplayName("여러 계좌 표시순서 저장 중 하나가 실패하면 앞선 변경도 롤백된다")
    void rollbackAllDisplayOrdersWhenOneSaveFails() {
        // given
        Account account1 =
                createAccount(
                        "088100000081"
                );

        Account account2 =
                createAccount(
                        "088100000082"
                );

        /*
         * 서비스는 mock port를 사용하지만,
         * 조회는 실제 persistence adapter로 위임한다.
         */
        when(
                accountPersistencePort
                        .findAllByCustomerId(customerId)
        ).thenAnswer(
                invocation ->
                        realAccountPersistenceAdapter
                                .findAllByCustomerId(
                                        customerId
                                )
        );

        AtomicInteger saveCount =
                new AtomicInteger();

        when(
                accountPersistencePort
                        .save(any(Account.class))
        ).thenAnswer(invocation -> {
            Account account =
                    invocation.getArgument(0);

            int currentSaveCount =
                    saveCount.incrementAndGet();

            /*
             * 첫 번째 계좌는 실제 DB에 UPDATE한다.
             * flush까지 실행해 실제 SQL이 DB로 전달됐음을 보장한다.
             */
            if (currentSaveCount == 1) {
                Account saved =
                        realAccountPersistenceAdapter
                                .save(account);

                accountJpaRepository.flush();

                return saved;
            }

            /*
             * 두 번째 저장에서 강제로 실패시킨다.
             */
            throw new DataIntegrityViolationException(
                    "forced display order save failure"
            );
        });

        AccountDisplayOrderCommand command =
                new AccountDisplayOrderCommand(
                        customerId,
                        List.of(
                                account1.getAccountId(),
                                account2.getAccountId()
                        )
                );

        // when
        Throwable thrown =
                catchThrowable(
                        () ->
                                accountDisplayOrderUseCase
                                        .saveDisplayOrder(
                                                command
                                        )
                );

        // then
        assertThat(thrown)
                .isInstanceOf(
                        DataIntegrityViolationException.class
                )
                .hasMessageContaining(
                        "forced display order save failure"
                );

        /*
         * 첫 번째 계좌는 changeDisplayOrder(1) 후
         * 실제 UPDATE + flush까지 수행됐다.
         *
         * 그런데 두 번째 저장 실패로 트랜잭션 전체가 rollback되었으므로
         * DB의 display_order는 다시 null이어야 한다.
         */
        Integer account1DisplayOrder =
                findDisplayOrder(
                        account1.getAccountId()
                );

        Integer account2DisplayOrder =
                findDisplayOrder(
                        account2.getAccountId()
                );

        assertThat(account1DisplayOrder)
                .isNull();

        assertThat(account2DisplayOrder)
                .isNull();
    }

    private Account createAccount(
            String accountNumber
    ) {
        Account account =
                Account.open(
                        accountNumber,
                        customerId,
                        null,
                        AccountType.DEMAND_DEPOSIT,
                        PASSWORD_HASH,
                        LocalDateTime.of(
                                2026,
                                8,
                                1,
                                10,
                                0
                        ),
                        null
                );

        return realAccountPersistenceAdapter
                .save(account);
    }

    private Integer findDisplayOrder(
            Long accountId
    ) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT display_order
                        FROM account
                        WHERE account_id = ?
                        """,
                Integer.class,
                accountId
        );
    }
}