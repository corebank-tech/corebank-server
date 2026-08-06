package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.domain.AccountType;
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

    @Autowired
    private AccountNumberIssuanceService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        deleteDemandDepositSequence();
        insertDemandDepositSequence(INITIAL_SEQUENCE);
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
                    .isEqualTo("088100000101");

                throw new ForcedRollbackException();
            })
        ).isInstanceOf(ForcedRollbackException.class);

        // 트랜잭션 종료 후 DB를 새로 조회한다.
        assertThat(findLastSequence())
            .isEqualTo(INITIAL_SEQUENCE);
    }

    private void insertDemandDepositSequence(
        long lastSequence
    ) {
        jdbcTemplate.update("""
            INSERT INTO account_number_sequence (
                bank_code,
                account_type,
                product_id,
                product_prefix,
                last_sequence,
                created_at,
                updated_at
            )
            VALUES (
                '088',
                'DEMAND_DEPOSIT',
                NULL,
                '10',
                ?,
                CURRENT_TIMESTAMP(6),
                CURRENT_TIMESTAMP(6)
            )
            """,
            lastSequence
        );
    }

    private void deleteDemandDepositSequence() {
        jdbcTemplate.update("""
            DELETE FROM account_number_sequence
             WHERE bank_code = '088'
               AND account_type = 'DEMAND_DEPOSIT'
               AND product_id IS NULL
            """);
    }

    private Long findLastSequence() {
        return jdbcTemplate.queryForObject("""
            SELECT last_sequence
              FROM account_number_sequence
             WHERE bank_code = '088'
               AND account_type = 'DEMAND_DEPOSIT'
               AND product_id IS NULL
            """,
            Long.class
        );
    }

    private static class ForcedRollbackException
        extends RuntimeException {
    }
}