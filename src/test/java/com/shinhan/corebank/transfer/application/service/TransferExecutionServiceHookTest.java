package com.shinhan.corebank.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerifier;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerifier;
import com.shinhan.corebank.transfer.adapter.out.persistence.TransferTestFixtures;
import com.shinhan.corebank.transfer.api.LedgerPostingContext;
import com.shinhan.corebank.transfer.api.LedgerPostingHook;
import com.shinhan.corebank.transfer.api.TransferPreCheck;
import com.shinhan.corebank.transfer.api.TransferPreCheckContext;
import com.shinhan.corebank.transfer.application.port.in.TransferCommand;
import com.shinhan.corebank.transfer.application.port.in.TransferResult;
import com.shinhan.corebank.transfer.application.port.out.AccountLockPort;
import com.shinhan.corebank.transfer.application.port.out.LedgerSavePort;
import com.shinhan.corebank.transfer.application.port.out.TransferAuthTokenVerificationPort;
import com.shinhan.corebank.transfer.application.port.out.TransferLimitPort;
import com.shinhan.corebank.transfer.application.port.out.TransferLookupPort;
import com.shinhan.corebank.transfer.application.port.out.TransferOtpVerificationPort;
import com.shinhan.corebank.transfer.application.port.out.TransferSavePort;
import com.shinhan.corebank.transfer.application.port.out.TransferSequencePort;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferType;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 이체 확장점(PH-99) 훅이 execute()의 약속한 자리에서, 약속한 트랜잭션 규칙대로 호출되는지 검증한다.
 *
 * 테스트용 훅을 빈으로 등록하면 같은 컨텍스트를 쓰는 다른 이체 테스트까지 영향을 받으므로, 실제 포트를 주입받아 서비스를 직접 조립한다.
 * MockitoBean 구성은 TransferExecutionServiceTest와 같게 맞춰 컨텍스트 캐시를 공유한다.
 */
class TransferExecutionServiceHookTest extends IntegrationTestSupport {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private AccountLockPort accountLockPort;

    @Autowired
    private TransferLimitPort transferLimitPort;

    @Autowired
    private TransferAuthTokenVerificationPort transferAuthTokenVerificationPort;

    @Autowired
    private TransferOtpVerificationPort transferOtpVerificationPort;

    @Autowired
    private TransferSequencePort transferSequencePort;

    @Autowired
    private TransferSavePort transferSavePort;

    @Autowired
    private TransferLookupPort transferLookupPort;

    @Autowired
    private LedgerSavePort ledgerSavePort;

    @Autowired
    private Clock clock;

    @MockitoBean
    private OtpAuthTokenVerifier otpAuthTokenVerifier;

    @MockitoBean
    private AccountPasswordAuthTokenVerifier accountPasswordAuthTokenVerifier;

    @BeforeEach
    void seed() {
        // 다른 테스트 클래스가 남긴 고객 1의 한도 사용액 행이 롤백 검증을 오염시키지 않게 먼저 비운다.
        cleanUpCommittedData();
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> TransferTestFixtures.seedCustomerAndAccounts(entityManager));
    }

    @AfterEach
    void cleanUpCommittedData() {
        jdbcTemplate.update("DELETE FROM ledger_entry WHERE account_id IN (101, 202)");
        jdbcTemplate.update("DELETE FROM transfer WHERE withdrawal_account_id = 101 AND deposit_account_id = 202");
        jdbcTemplate.update("UPDATE account SET balance = 100000, status = 'ACTIVE' WHERE account_id IN (101, 202)");
        jdbcTemplate.update("DELETE FROM transfer_limit_daily_usage WHERE customer_id = 1");
    }

    @Test
    @DisplayName("[훅 A] PreCheck가 거부하면 ERROR로 확정하고 OTP·한도·잔액을 건드리지 않으며 뒤 순번은 호출하지 않는다")
    void preCheck_rejects_failsTransferBeforeOtpAndLimit() {
        List<Integer> called = new ArrayList<>();
        TransferExecutionService service = serviceWith(
                List.of(recordingPreCheck(1, called, true), recordingPreCheck(2, called, false)), List.of());

        TransferResult result = service.execute(immediateCommand(30000L));

        assertThat(result.status()).isEqualTo(ProcessResultStatus.ERROR);
        assertThat(result.errorCode()).isEqualTo(TransferErrorCode.INSUFFICIENT_BALANCE.getCode());
        assertThat(called).containsExactly(1);

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM transfer WHERE transaction_number = ?", String.class, result.transactionNumber());
        assertThat(status).isEqualTo("ERROR");
        assertThat(balanceOf(101)).isEqualTo(100000L);
        verifyNoInteractions(otpAuthTokenVerifier);
        Integer usageRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transfer_limit_daily_usage WHERE customer_id = 1", Integer.class);
        assertThat(usageRows).isZero();
    }

    @Test
    @DisplayName("[훅 A] PreCheck는 등록 순서와 무관하게 order 오름차순으로 호출되고 이체 입력값을 받는다")
    void preChecks_runInOrder_withTransferInput() {
        List<Integer> called = new ArrayList<>();
        List<TransferPreCheckContext> received = new ArrayList<>();
        TransferPreCheck capturing = new TransferPreCheck() {
            @Override
            public int order() {
                return 20;
            }

            @Override
            public void check(TransferPreCheckContext context) {
                called.add(20);
                received.add(context);
            }
        };
        TransferExecutionService service =
                serviceWith(List.of(capturing, recordingPreCheck(10, called, false)), List.of());

        TransferResult result = service.execute(immediateCommand(30000L));

        assertThat(result.status()).isEqualTo(ProcessResultStatus.SUCCESS);
        assertThat(called).containsExactly(10, 20);
        assertThat(received).containsExactly(new TransferPreCheckContext(1L, 101L, 30000L));
    }

    @Test
    @DisplayName("[훅 B] 훅이 BusinessException을 던지면 잔액·원장·한도 적립이 롤백되고 이체는 ERROR로 확정된다")
    void ledgerPostingHook_businessException_rollsBackLedgerAndFails() {
        // 오류코드 값은 무관하다. GL 기표 실패를 대신하는 대역이다.
        LedgerPostingHook failing = context -> {
            throw new BusinessException(TransferErrorCode.INSUFFICIENT_BALANCE);
        };
        TransferExecutionService service = serviceWith(List.of(), List.of(failing));

        TransferResult result = service.execute(immediateCommand(30000L));

        assertThat(result.status()).isEqualTo(ProcessResultStatus.ERROR);
        assertThat(result.errorCode()).isEqualTo(TransferErrorCode.INSUFFICIENT_BALANCE.getCode());
        assertRolledBack();
        assertThat(transferStatuses()).containsExactly("ERROR");
    }

    @Test
    @DisplayName("[훅 B] 훅이 예상 밖 예외를 던지면 삼키지 않고 호출자에 전파하며 기표를 롤백한다")
    void ledgerPostingHook_unexpectedException_propagatesAndRollsBack() {
        IllegalStateException boom = new IllegalStateException("gl down");
        LedgerPostingHook failing = context -> {
            throw boom;
        };
        TransferExecutionService service = serviceWith(List.of(), List.of(failing));

        assertThatThrownBy(() -> service.execute(immediateCommand(30000L))).isSameAs(boom);

        assertRolledBack();
        String errorCode = jdbcTemplate.queryForObject(
                "SELECT error_code FROM transfer WHERE withdrawal_account_id = 101 AND status = 'ERROR'", String.class);
        assertThat(errorCode).isEqualTo(CommonErrorCode.INTERNAL_ERROR.getCode());
    }

    @Test
    @DisplayName("[훅 B] 훅은 활성 트랜잭션 안에서 거래번호·유형·금액·계좌·거래일을 받는다")
    void ledgerPostingHook_receivesContext_insideTransaction() {
        List<LedgerPostingContext> received = new ArrayList<>();
        List<Boolean> transactionActive = new ArrayList<>();
        LedgerPostingHook capturing = context -> {
            received.add(context);
            transactionActive.add(TransactionSynchronizationManager.isActualTransactionActive());
        };
        TransferExecutionService service = serviceWith(List.of(), List.of(capturing));
        LocalDate before = LocalDate.now(clock);

        TransferResult result = service.execute(immediateCommand(30000L));

        assertThat(result.status()).isEqualTo(ProcessResultStatus.SUCCESS);
        assertThat(transactionActive).containsExactly(true);
        assertThat(received).hasSize(1);
        LedgerPostingContext context = received.get(0);
        assertThat(context.transactionNumber()).isEqualTo(result.transactionNumber());
        assertThat(context.txType()).isEqualTo("IMMEDIATE_TRANSFER");
        assertThat(context.amount()).isEqualTo(30000L);
        assertThat(context.fromAccountId()).isEqualTo(101L);
        assertThat(context.toAccountId()).isEqualTo(202L);
        assertThat(context.tradeDate()).isBetween(before, LocalDate.now(clock));
    }

    private TransferExecutionService serviceWith(
            List<TransferPreCheck> preChecks, List<LedgerPostingHook> ledgerPostingHooks) {
        return new TransferExecutionService(
                accountLockPort,
                transferLimitPort,
                transferAuthTokenVerificationPort,
                transferOtpVerificationPort,
                transferSequencePort,
                transferSavePort,
                transferLookupPort,
                ledgerSavePort,
                preChecks,
                ledgerPostingHooks,
                clock,
                transactionManager);
    }

    private static TransferPreCheck recordingPreCheck(int order, List<Integer> called, boolean reject) {
        return new TransferPreCheck() {
            @Override
            public int order() {
                return order;
            }

            @Override
            public void check(TransferPreCheckContext context) {
                called.add(order);
                if (reject) {
                    throw new BusinessException(TransferErrorCode.INSUFFICIENT_BALANCE);
                }
            }
        };
    }

    private static TransferCommand immediateCommand(long amount) {
        return TransferCommand.builder()
                .customerId(1L)
                .authToken("dummy-auth-token")
                .otpAuthToken("dummy-otp-token")
                .withdrawalAccountId(101L)
                .depositAccountNumber("110222222222")
                .amount(amount)
                .transferType(TransferType.IMMEDIATE)
                .channel(TransferChannel.WB)
                .build();
    }

    private void assertRolledBack() {
        assertThat(balanceOf(101)).isEqualTo(100000L);
        assertThat(balanceOf(202)).isEqualTo(100000L);
        Integer ledgerRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger_entry WHERE account_id IN (101, 202)", Integer.class);
        assertThat(ledgerRows).isZero();
        Integer usageRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transfer_limit_daily_usage WHERE customer_id = 1", Integer.class);
        assertThat(usageRows).isZero();
    }

    private List<String> transferStatuses() {
        return jdbcTemplate.queryForList(
                "SELECT status FROM transfer WHERE withdrawal_account_id = 101 AND deposit_account_id = 202",
                String.class);
    }

    private long balanceOf(long accountId) {
        return jdbcTemplate.queryForObject("SELECT balance FROM account WHERE account_id = ?", Long.class, accountId);
    }
}
