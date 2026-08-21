package com.shinhan.corebank.transfer.application.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.transfer.adapter.out.persistence.TransferTestFixtures;
import com.shinhan.corebank.transfer.application.port.in.TransferCommand;
import com.shinhan.corebank.transfer.application.port.in.TransferResult;
import com.shinhan.corebank.transfer.application.port.out.AccountLockPort;
import com.shinhan.corebank.transfer.application.port.out.LedgerSavePort;
import com.shinhan.corebank.transfer.application.port.out.TransferAuthTokenVerificationPort;
import com.shinhan.corebank.transfer.application.port.out.TransferLimitPort;
import com.shinhan.corebank.transfer.application.port.out.TransferLookupPort;
import com.shinhan.corebank.transfer.application.port.out.TransferSavePort;
import com.shinhan.corebank.transfer.application.port.out.TransferSequencePort;
import com.shinhan.corebank.transfer.domain.Transfer;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferSourceType;
import com.shinhan.corebank.transfer.domain.TransferType;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 클래스 레벨 @Transactional을 두지 않는다. 픽스처 커밋과 서비스 호출을 테스트 관리
 * 트랜잭션 밖에서 실행해, execute()의 원자성이 테스트 트랜잭션이 아니라
 * TransferExecutionService 자신의 @Transactional에서 나온다는 것을 실제로 검증한다.
 * 결과가 실제로 커밋되므로, 다른 테스트가 같은 픽스처 계좌(101/202)를 오염된 잔액으로
 * 재시드하지 않도록 @AfterEach에서 이 테스트가 만든 데이터를 정리한다.
 */
class TransferExecutionServiceTest extends IntegrationTestSupport {

    @Autowired
    private TransferExecutionService transferExecutionService;

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
    private TransferSequencePort transferSequencePort;

    @Autowired
    private TransferSavePort transferSavePort;

    @Autowired
    private TransferLookupPort transferLookupPort;

    @Autowired
    private LedgerSavePort ledgerSavePort;

    @Autowired
    private Clock clock;

    @AfterEach
    void cleanUpCommittedData() {
        jdbcTemplate.update("DELETE FROM ledger_entry WHERE account_id IN (101, 202)");
        jdbcTemplate.update("DELETE FROM transfer WHERE withdrawal_account_id = 101 AND deposit_account_id = 202");
        jdbcTemplate.update("UPDATE account SET balance = 100000, status = 'ACTIVE' WHERE account_id IN (101, 202)");
    }

    @Test
    @DisplayName("정상 이체는 원장 2행을 기표하고 양쪽 계좌 잔액을 반영한 SUCCESS 결과를 반환한다")
    void execute_completesTransfer_withLedgerPairAndBalanceUpdate() {
        // given: 픽스처는 별도 트랜잭션에서 커밋한다.
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                TransferTestFixtures.seedCustomerAndAccounts(entityManager));

        TransferCommand command = TransferCommand.builder()
                .customerId(1L)
                .authToken("dummy-auth-token")
                .withdrawalAccountId(101L)
                .depositAccountNumber("110222222222")
                .amount(30000L)
                .transferType(TransferType.IMMEDIATE)
                .channel(TransferChannel.WB)
                .myPassbookMemo("출금메모")
                .recipientPassbookMemo("입금메모")
                .build();

        // when: 테스트 관리 트랜잭션 밖에서 실행한다.
        TransferResult result = transferExecutionService.execute(command);

        // then: 응답
        assertThat(result.status()).isEqualTo(ProcessResultStatus.SUCCESS);
        assertThat(result.transactionNumber()).matches("^[0-9]{8}WB[0-9]{10}$");
        assertThat(result.withdrawalBalanceAfter()).isEqualTo(70000L);

        // then: 계좌 잔액 반영 (커밋된 값을 새 커넥션으로 조회)
        Long withdrawalBalance = jdbcTemplate.queryForObject(
                "SELECT balance FROM account WHERE account_id = 101", Long.class);
        Long depositBalance = jdbcTemplate.queryForObject(
                "SELECT balance FROM account WHERE account_id = 202", Long.class);
        assertThat(withdrawalBalance).isEqualTo(70000L);
        assertThat(depositBalance).isEqualTo(130000L);

        // then: transfer 1행
        Map<String, Object> transferRow = jdbcTemplate.queryForMap(
                "SELECT status, payee_name, withdrawal_account_id, deposit_account_id, amount, fee "
                        + "FROM transfer WHERE transaction_number = ?",
                result.transactionNumber());
        assertThat(transferRow.get("status")).isEqualTo("SUCCESS");
        assertThat(transferRow.get("payee_name")).isEqualTo("테스터");
        assertThat(((Number) transferRow.get("withdrawal_account_id")).longValue()).isEqualTo(101L);
        assertThat(((Number) transferRow.get("deposit_account_id")).longValue()).isEqualTo(202L);
        assertThat(((Number) transferRow.get("amount")).longValue()).isEqualTo(30000L);
        assertThat(((Number) transferRow.get("fee")).longValue()).isEqualTo(0L);

        // then: ledger_entry 원장 2행 (출금/입금)
        List<Map<String, Object>> ledgerRows = jdbcTemplate.queryForList(
                "SELECT direction, account_id, amount, balance_after "
                        + "FROM ledger_entry WHERE transaction_number = ? ORDER BY direction",
                result.transactionNumber());

        assertThat(ledgerRows).hasSize(2);

        Map<String, Object> depositEntry = ledgerRows.get(0);
        assertThat(depositEntry.get("direction")).isEqualTo("DEPOSIT");
        assertThat(((Number) depositEntry.get("account_id")).longValue()).isEqualTo(202L);
        assertThat(((Number) depositEntry.get("amount")).longValue()).isEqualTo(30000L);
        assertThat(((Number) depositEntry.get("balance_after")).longValue()).isEqualTo(130000L);

        Map<String, Object> withdrawalEntry = ledgerRows.get(1);
        assertThat(withdrawalEntry.get("direction")).isEqualTo("WITHDRAWAL");
        assertThat(((Number) withdrawalEntry.get("account_id")).longValue()).isEqualTo(101L);
        assertThat(((Number) withdrawalEntry.get("amount")).longValue()).isEqualTo(30000L);
        assertThat(((Number) withdrawalEntry.get("balance_after")).longValue()).isEqualTo(70000L);
    }

    @Test
    @DisplayName("동일 sourceId+executionDate로 execute()를 두 번 호출해도 실제 이체는 1회만 발생하고 동일 결과가 반환된다")
    void execute_sameSourceAndExecutionDate_isIdempotent() {
        // given
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                TransferTestFixtures.seedCustomerAndAccounts(entityManager));

        LocalDate executionDate = LocalDate.of(2026, 8, 20);
        TransferCommand command = TransferCommand.builder()
                .customerId(1L)
                .withdrawalAccountId(101L)
                .depositAccountNumber("110222222222")
                .amount(30000L)
                .transferType(TransferType.AUTO)
                .channel(TransferChannel.BT)
                .myPassbookMemo("출금메모")
                .recipientPassbookMemo("입금메모")
                .sourceId(555L)
                .executionDate(executionDate)
                .build();

        // when: 같은 회차를 두 번 실행한다 (당일 안전장치 미작동 후 익일 재시도 시나리오와 동일한 모양)
        TransferResult first = transferExecutionService.execute(command);
        TransferResult second = transferExecutionService.execute(command);

        // then: 두 번째 호출은 재처리 없이 첫 번째와 동일한 결과를 반환한다
        assertThat(first.status()).isEqualTo(ProcessResultStatus.SUCCESS);
        assertThat(second.status()).isEqualTo(ProcessResultStatus.SUCCESS);
        assertThat(second.transactionNumber()).isEqualTo(first.transactionNumber());
        assertThat(second.withdrawalBalanceAfter()).isEqualTo(first.withdrawalBalanceAfter());

        // then: 잔액은 1회만 차감되고, transfer 행도 1건만 존재한다
        Long withdrawalBalance = jdbcTemplate.queryForObject(
                "SELECT balance FROM account WHERE account_id = 101", Long.class);
        assertThat(withdrawalBalance).isEqualTo(70000L);

        Integer transferRowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transfer WHERE source_type = 'AUTO' AND source_id = 555", Integer.class);
        assertThat(transferRowCount).isEqualTo(1);
    }

    @Test
    @DisplayName("동일 sourceId+executionDate로 execute()를 동시에 두 번 호출해도 실제 이체는 1회만 발생하고 동일 결과가 반환된다")
    void execute_concurrentSameSourceAndExecutionDate_appliesTransferOnlyOnce() throws Exception {
        // given
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                TransferTestFixtures.seedCustomerAndAccounts(entityManager));

        LocalDate executionDate = LocalDate.of(2026, 8, 20);
        TransferCommand command = TransferCommand.builder()
                .customerId(1L)
                .withdrawalAccountId(101L)
                .depositAccountNumber("110222222222")
                .amount(30000L)
                .transferType(TransferType.AUTO)
                .channel(TransferChannel.BT)
                .myPassbookMemo("출금메모")
                .recipientPassbookMemo("입금메모")
                .sourceId(556L)
                .executionDate(executionDate)
                .build();

        // when: 두 스레드가 사전조회를 모두 통과한 뒤 동시에 INSERT를 시도하도록 CountDownLatch로 출발선을 맞춘다.
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        TransferResult first;
        TransferResult second;
        try {
            Future<TransferResult> firstCall = executor.submit(() -> callAfterLatches(ready, start, command));
            Future<TransferResult> secondCall = executor.submit(() -> callAfterLatches(ready, start, command));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            first = firstCall.get(10, TimeUnit.SECONDS);
            second = secondCall.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        // then: 한쪽은 정상 처리되고, 다른 쪽은 uk_transfer_source_execution_date 충돌 후 재조회로 같은 결과를 받는다
        assertThat(first.status()).isEqualTo(ProcessResultStatus.SUCCESS);
        assertThat(second.status()).isEqualTo(ProcessResultStatus.SUCCESS);
        assertThat(second.transactionNumber()).isEqualTo(first.transactionNumber());
        assertThat(second.withdrawalBalanceAfter()).isEqualTo(first.withdrawalBalanceAfter());

        // then: 잔액은 1회만 차감되고, transfer/원장 행도 1건씩만 존재한다
        Long withdrawalBalance = jdbcTemplate.queryForObject(
                "SELECT balance FROM account WHERE account_id = 101", Long.class);
        assertThat(withdrawalBalance).isEqualTo(70000L);

        Integer transferRowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transfer WHERE source_type = 'AUTO' AND source_id = 556", Integer.class);
        assertThat(transferRowCount).isEqualTo(1);

        Long ledgerRowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger_entry WHERE transaction_number = ?",
                Long.class, first.transactionNumber());
        assertThat(ledgerRowCount).isEqualTo(2L);
    }

    private TransferResult callAfterLatches(CountDownLatch ready, CountDownLatch start, TransferCommand command)
            throws InterruptedException {
        ready.countDown();
        start.await();
        return transferExecutionService.execute(command);
    }

    @Test
    @DisplayName("동일 sourceId+executionDate로 둘 다 실패하는 execute()를 동시에 호출해도 예외 없이 동일 ERROR 결과가 반환된다")
    void execute_concurrentSameSourceAndExecutionDateBothFail_returnsSameErrorResultWithoutThrowing() throws Exception {
        // given: 잔액(100,000)보다 큰 금액이라 두 호출 모두 BusinessException(INSUFFICIENT_BALANCE)으로 실패한다.
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                TransferTestFixtures.seedCustomerAndAccounts(entityManager));

        LocalDate executionDate = LocalDate.of(2026, 8, 20);
        TransferCommand command = TransferCommand.builder()
                .customerId(1L)
                .withdrawalAccountId(101L)
                .depositAccountNumber("110222222222")
                .amount(150000L)
                .transferType(TransferType.AUTO)
                .channel(TransferChannel.BT)
                .myPassbookMemo("출금메모")
                .recipientPassbookMemo("입금메모")
                .sourceId(557L)
                .executionDate(executionDate)
                .build();

        // when: ready/start 래치는 스레드 "시작" 지점만 맞출 뿐, 그 뒤로는 스케줄링에 따라 한쪽이
        // 실패 확정(failTransfer)까지 통째로 끝내버릴 수 있다 — 그러면 다른 쪽은 맨 앞 사전조회
        // (findBySourceAndExecutionDate)에서 그 결과를 그냥 돌려받고 끝나, failTransfer()의
        // DataIntegrityViolationException 재조회 경로를 타지 않고도 테스트가 통과해버린다. 두
        // 스레드의 "첫 번째" 사전조회 호출이 둘 다 빈 결과로 끝날 때까지 대기시키는 래핑 포트로
        // 그 경로를 실제로 강제한다.
        CountDownLatch bothPreChecksEmpty = new CountDownLatch(2);
        TransferExecutionService serviceUnderTest = new TransferExecutionService(
                accountLockPort,
                transferLimitPort,
                transferAuthTokenVerificationPort,
                transferSequencePort,
                transferSavePort,
                new PreCheckBarrierTransferLookupPort(transferLookupPort, bothPreChecksEmpty),
                ledgerSavePort,
                clock,
                transactionManager
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        TransferResult first;
        TransferResult second;
        try {
            Future<TransferResult> firstCall = executor.submit(() -> serviceUnderTest.execute(command));
            Future<TransferResult> secondCall = executor.submit(() -> serviceUnderTest.execute(command));

            first = firstCall.get(10, TimeUnit.SECONDS);
            second = secondCall.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        // then: 어느 쪽도 예외 없이 동일한 ERROR 결과를 반환한다 (실패를 먼저 확정한 쪽의 결과를 재조회로 공유)
        assertThat(first.status()).isEqualTo(ProcessResultStatus.ERROR);
        assertThat(second.status()).isEqualTo(ProcessResultStatus.ERROR);
        assertThat(first.errorCode()).isEqualTo(TransferErrorCode.INSUFFICIENT_BALANCE.getCode());
        assertThat(second.errorCode()).isEqualTo(TransferErrorCode.INSUFFICIENT_BALANCE.getCode());
        assertThat(second.transactionNumber()).isEqualTo(first.transactionNumber());

        // then: transfer 행은 1건만 ERROR로 커밋된다
        Integer transferRowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transfer WHERE source_type = 'AUTO' AND source_id = 557", Integer.class);
        assertThat(transferRowCount).isEqualTo(1);
    }

    // execute() 안에서 이 메서드는 스레드당 최소 1번(맨 앞 사전조회), 많으면 2번(DataIntegrityViolationException
    // 이후 재조회) 호출된다. 스레드별 "첫 번째" 호출에서만 장벽을 걸어야 한다 — 두 번째(재조회) 호출까지 걸면
    // 둘 중 하나만 그 경로를 타는 상황에서 나머지 하나가 영원히 대기하게 된다.
    private static class PreCheckBarrierTransferLookupPort implements TransferLookupPort {
        private final TransferLookupPort delegate;
        private final CountDownLatch bothPreChecksEmpty;
        private final ThreadLocal<Boolean> preCheckSeen = ThreadLocal.withInitial(() -> false);

        PreCheckBarrierTransferLookupPort(TransferLookupPort delegate, CountDownLatch bothPreChecksEmpty) {
            this.delegate = delegate;
            this.bothPreChecksEmpty = bothPreChecksEmpty;
        }

        @Override
        public Optional<TransferResult> findBySourceAndExecutionDate(
                TransferSourceType sourceType, Long sourceId, LocalDate executionDate) {
            boolean isPreCheck = !preCheckSeen.get();
            preCheckSeen.set(true);

            Optional<TransferResult> result = delegate.findBySourceAndExecutionDate(sourceType, sourceId, executionDate);
            if (isPreCheck) {
                bothPreChecksEmpty.countDown();
                try {
                    bothPreChecksEmpty.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return result;
        }

        @Override
        public Optional<Transfer> findByTransactionNumber(String transactionNumber) {
            return delegate.findByTransactionNumber(transactionNumber);
        }
    }

    @Test
    @DisplayName("출금계좌 잔액보다 큰 금액을 이체하면 transfer는 ERROR로 커밋되고 원장은 0행, 계좌 잔액은 그대로다")
    void execute_withInsufficientBalance_recordsErrorTransfer_withoutLedgerRows() {
        // given: 픽스처는 별도 트랜잭션에서 커밋한다. (출금계좌 101 잔액 100,000)
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                TransferTestFixtures.seedCustomerAndAccounts(entityManager));

        TransferCommand command = TransferCommand.builder()
                .customerId(1L)
                .authToken("dummy-auth-token")
                .withdrawalAccountId(101L)
                .depositAccountNumber("110222222222")
                .amount(150000L) // 잔액(100,000)을 초과하는 금액
                .transferType(TransferType.IMMEDIATE)
                .channel(TransferChannel.WB)
                .myPassbookMemo("출금메모")
                .recipientPassbookMemo("입금메모")
                .build();

        // when: 테스트 관리 트랜잭션 밖에서 실행한다.
        TransferResult result = transferExecutionService.execute(command);

        // then: 예외를 던지지 않고 ERROR 결과를 정상 반환한다
        assertThat(result.status()).isEqualTo(ProcessResultStatus.ERROR);
        assertThat(result.errorCode()).isEqualTo(TransferErrorCode.INSUFFICIENT_BALANCE.getCode());

        // then: transfer 행이 ERROR로 커밋된다 (커밋된 값을 새 커넥션으로 조회)
        Map<String, Object> transferRow = jdbcTemplate.queryForMap(
                "SELECT status, error_code FROM transfer WHERE transaction_number = ?",
                result.transactionNumber());
        assertThat(transferRow.get("status")).isEqualTo("ERROR");
        assertThat(transferRow.get("error_code")).isEqualTo(TransferErrorCode.INSUFFICIENT_BALANCE.getCode());

        // then: 원장 0행
        Long ledgerCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger_entry WHERE transaction_number = ?",
                Long.class, result.transactionNumber());
        assertThat(ledgerCount).isZero();

        // then: 계좌 잔액은 그대로다
        Long withdrawalBalance = jdbcTemplate.queryForObject(
                "SELECT balance FROM account WHERE account_id = 101", Long.class);
        assertThat(withdrawalBalance).isEqualTo(100000L);

        Long depositBalance = jdbcTemplate.queryForObject(
                "SELECT balance FROM account WHERE account_id = 202", Long.class);
        assertThat(depositBalance).isEqualTo(100000L);
    }
}
