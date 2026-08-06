package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.domain.AccountType;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("계좌번호 채번 동시성 통합 테스트")
class AccountNumberIssuanceConcurrencyTest
    extends IntegrationTestSupport {

    private static final int REQUEST_COUNT = 100;
    private static final int THREAD_COUNT = 10;

    @Autowired
    private AccountNumberIssuanceService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        deleteDemandDepositSequence();
        insertDemandDepositSequence(0L);
    }

    @Test
    @DisplayName("동시에 100건을 요청해도 중복 없는 계좌번호 100개를 발급한다")
    void issuesUniqueAccountNumbersUnderConcurrency()
        throws Exception {

        ExecutorService executor =
            Executors.newFixedThreadPool(THREAD_COUNT);

        CountDownLatch startLatch =
            new CountDownLatch(1);

        List<Future<String>> futures =
            new ArrayList<>();

        try {
            // given
            for (int i = 0; i < REQUEST_COUNT; i++) {
                futures.add(
                    executor.submit(() -> {
                        startLatch.await();

                        return service.issue(
                            AccountType.DEMAND_DEPOSIT,
                            null
                        );
                    })
                );
            }

            // when
            startLatch.countDown();

            List<String> accountNumbers =
                new ArrayList<>();

            for (Future<String> future : futures) {
                accountNumbers.add(
                    future.get(60, TimeUnit.SECONDS)
                );
            }

            // then
            assertThat(accountNumbers)
                .hasSize(REQUEST_COUNT)
                .doesNotHaveDuplicates();

            assertThat(accountNumbers)
                .allMatch(number ->
                    number.matches("^[0-9]{12}$")
                );

            assertThat(accountNumbers)
                .contains(
                    "088100000001",
                    "088100000100"
                );

            assertThat(findLastSequence())
                .isEqualTo(100L);

        } finally {
            executor.shutdownNow();
            executor.awaitTermination(
                10,
                TimeUnit.SECONDS
            );
        }
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
}