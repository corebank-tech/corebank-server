package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.account.support.AccountNumberSequenceTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("계좌번호 채번 롤백 통합 테스트")
class AccountNumberIssuanceRollbackTest
    extends IntegrationTestSupport {

    private static final long INITIAL_SEQUENCE = 100L;
    private AccountNumberSequenceTestFixture fixture;

    @Autowired
    private AccountNumberIssuanceService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        fixture =
                new AccountNumberSequenceTestFixture(
                        jdbcTemplate
                );

        fixture.resetDemandDepositSequence(
                INITIAL_SEQUENCE
        );
    }

    @AfterEach
    void tearDown() {
        fixture.deleteDemandDepositSequence();
    }

    @Test
    @DisplayName("채번 후 같은 트랜잭션에서 예외가 발생하면 일련번호 증가가 롤백된다")
    void rollsBackSequenceWhenOuterTransactionFails() {
        // given
        TransactionTemplate transactionTemplate =
            new TransactionTemplate(transactionManager);

        // when & then
        assertThatThrownBy(() ->
            transactionTemplate.executeWithoutResult(status -> {
                String accountNumber = service.issue(
                    AccountType.DEMAND_DEPOSIT,
                    null
                );

                assertThat(accountNumber)
                    .isEqualTo(
                        AccountNumberSequenceTestFixture.accountNumberOf(
                            AccountNumberSequenceTestFixture
                                .DEMAND_DEPOSIT_PREFIX,
                            INITIAL_SEQUENCE + 1
                        )
                    );

                throw new ForcedRollbackException();
            })
        ).isInstanceOf(ForcedRollbackException.class);

        // 트랜잭션 종료 후 DB를 새로 조회한다.
        assertThat(
                fixture.findDemandDepositLastSequence()
        ).isEqualTo(INITIAL_SEQUENCE);
    }

    private static class ForcedRollbackException
        extends RuntimeException {
    }
}