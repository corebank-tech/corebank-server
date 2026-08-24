package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.domain.AccountNumberPolicy;
import com.shinhan.corebank.account.domain.AccountType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.shinhan.corebank.account.support.AccountNumberSequenceTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static com.shinhan.corebank.account.support.AccountNumberSequenceTestFixture.DEMAND_DEPOSIT_PREFIX;
import static com.shinhan.corebank.common.util.AccountNumberPolicy.ACCOUNT_NUMBER_PATTERN;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("계좌번호 채번 동시성 통합 테스트")
class AccountNumberIssuanceConcurrencyTest
        extends IntegrationTestSupport {

    private static final int REQUEST_COUNT = 100;
    private static final int THREAD_COUNT = 10;

    private AccountNumberSequenceTestFixture fixture;
    private ExecutorService executor;

    @Autowired
    private AccountNumberIssuanceService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        fixture =
                new AccountNumberSequenceTestFixture(
                        jdbcTemplate
                );

        fixture.resetDemandDepositSequence(0L);

        executor =
                Executors.newFixedThreadPool(
                        THREAD_COUNT
                );
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        boolean terminated = terminateExecutor();

        if (terminated && fixture != null) {
            fixture.deleteDemandDepositSequence();
        }

        assertThat(terminated)
                .as("동시성 테스트 작업 스레드가 정상적으로 종료되어야 한다.")
                .isTrue();
    }

    @Test
    @DisplayName("동시에 100건을 요청해도 중복 없는 계좌번호 100개를 발급한다")
    void issuesUniqueAccountNumbersUnderConcurrency()
            throws Exception {

        CountDownLatch startLatch =
                new CountDownLatch(1);

        List<Future<String>> futures =
                new ArrayList<>();

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
                        ACCOUNT_NUMBER_PATTERN.matcher(number).matches()
                );
        assertThat(accountNumbers)
                .allMatch(number ->
                        number.startsWith(
                                AccountNumberPolicy.BANK_CODE
                                        + DEMAND_DEPOSIT_PREFIX
                        )
                );

        assertThat(accountNumbers)
                .contains(
                        AccountNumberSequenceTestFixture.accountNumberOf(
                                DEMAND_DEPOSIT_PREFIX, 1L),
                        AccountNumberSequenceTestFixture.accountNumberOf(
                                DEMAND_DEPOSIT_PREFIX, REQUEST_COUNT)
                );

        assertThat(
                fixture.findDemandDepositLastSequence()
        ).isEqualTo(100L);
    }

    private boolean terminateExecutor()
            throws InterruptedException {

        if (executor == null) {
            return true;
        }

        executor.shutdownNow();

        return executor.awaitTermination(
                30,
                TimeUnit.SECONDS
        );
    }
}