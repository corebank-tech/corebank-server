package com.shinhan.corebank.account.adapter.out.transferusage;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.application.port.out.AutoTransferUsageQueryPort;
import com.shinhan.corebank.account.application.port.out.ScheduledTransferUsageQueryPort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.account.support.CustomerTestFixture;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class WithdrawalAccountUsagePersistenceAdapterTest extends IntegrationTestSupport {

    private static final String PASSWORD_HASH = "$2a$10$34abEWY4uXLwTEnT5hNow.603a5rWofFx7Bnj59agU.PsESK0v/Yq";

    @Autowired
    private ScheduledTransferUsageQueryPort scheduledTransferUsageQueryPort;

    @Autowired
    private AutoTransferUsageQueryPort autoTransferUsageQueryPort;

    @Autowired
    private AccountPersistencePort accountPersistencePort;

    @Autowired
    private CustomerTestFixture customerTestFixture;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long customerId;
    private Long accountId;

    @BeforeEach
    void setUp() {
        customerId = customerTestFixture.createCustomer();

        Account account = Account.open(
                "088188800001",
                customerId,
                null,
                AccountType.DEMAND_DEPOSIT,
                PASSWORD_HASH,
                LocalDateTime.of(2026, 8, 1, 10, 0),
                null);

        accountId = accountPersistencePort.save(account).getAccountId();
    }

    @ParameterizedTest
    @ValueSource(strings = {"WAITING", "PROCESSING"})
    @DisplayName("WAITING 또는 PROCESSING 예약이체가 있으면 출금계좌 사용 중으로 조회한다")
    void findBlockingScheduledTransfer(String status) {
        // given
        insertScheduledTransfer(status);

        // when
        boolean exists = scheduledTransferUsageQueryPort.existsUsingWithdrawalAccount(accountId);

        // then
        assertThat(exists).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUCCESS", "FAILED", "CANCELED"})
    @DisplayName("완료·실패·취소 예약이체는 출금계좌 삭제를 막지 않는다")
    void ignoreNonBlockingScheduledTransfer(String status) {
        // given
        insertScheduledTransfer(status);

        // when
        boolean exists = scheduledTransferUsageQueryPort.existsUsingWithdrawalAccount(accountId);

        // then
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("NORMAL 자동이체가 있으면 출금계좌 사용 중으로 조회한다")
    void findNormalAutoTransfer() {
        // given
        insertAutoTransfer("NORMAL", "2026-08-01", "2026-12-31");

        // when
        boolean exists = autoTransferUsageQueryPort.existsUsingWithdrawalAccount(accountId);

        // then
        assertThat(exists).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"EXPIRED", "TERMINATED"})
    @DisplayName("NORMAL이 아닌 자동이체는 출금계좌 삭제를 막지 않는다")
    void ignoreNonNormalAutoTransfer(String status) {
        // given
        insertAutoTransfer(status, "2026-08-01", "2026-12-31");

        // when
        boolean exists = autoTransferUsageQueryPort.existsUsingWithdrawalAccount(accountId);

        // then
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("종료일이 지났어도 상태가 NORMAL이면 출금계좌 사용 중으로 조회한다")
    void findNormalAutoTransferEvenWhenEndDatePassed() {
        // given
        insertAutoTransfer("NORMAL", "2026-01-01", "2026-01-31");

        // when
        boolean exists = autoTransferUsageQueryPort.existsUsingWithdrawalAccount(accountId);

        // then
        assertThat(exists).isTrue();
    }

    private void insertScheduledTransfer(String status) {
        jdbcTemplate.update(
                """
                        INSERT INTO scheduled_transfer (
                            customer_id,
                            withdrawal_account_id,
                            payee_bank_code,
                            payee_account_number,
                            payee_name,
                            amount,
                            scheduled_date,
                            status,
                            registered_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                customerId,
                accountId,
                "088",
                "088299900001",
                "테스트",
                10_000L,
                "2026-08-21",
                status,
                "2026-08-20 10:00:00");
    }

    private void insertAutoTransfer(String status, String startDate, String endDate) {
        jdbcTemplate.update(
                """
                        INSERT INTO auto_transfer (
                            customer_id,
                            withdrawal_account_id,
                            deposit_account_number,
                            payee_name,
                            amount,
                            cycle_months,
                            transfer_day,
                            start_date,
                            end_date,
                            next_execution_date,
                            status,
                            registered_at,
                            updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                customerId,
                accountId,
                "088299900002",
                "테스트",
                10_000L,
                1,
                20,
                startDate,
                endDate,
                endDate,
                status,
                "2026-08-20 10:00:00",
                "2026-08-20 10:00:00");
    }
}
