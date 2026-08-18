package com.shinhan.corebank.transfer.application.service;

import java.util.Map;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.transfer.adapter.out.persistence.TransferTestFixtures;
import com.shinhan.corebank.transfer.application.port.in.TransferCommand;
import com.shinhan.corebank.transfer.application.port.in.TransferResult;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferType;
import com.shinhan.corebank.transfer.domain.exception.LimitErrorCode;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * "존재하지 않는 계좌" 계열 실패 경로. 잔액 부족(같은 트랜잭션 안에서 검증 가능)과 달리,
 * 이 두 경로는 실패 시점에 따라 transfer 행 자체가 생기지 않거나(TOCTOU 이전) DB에 남는다.
 */
class TransferExecutionServiceFailureTest extends IntegrationTestSupport {

    @Autowired
    private TransferExecutionService transferExecutionService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUpCommittedData() {
        jdbcTemplate.update("DELETE FROM ledger_entry WHERE account_id IN (101, 202, 501, 502, 601)");
        jdbcTemplate.update("DELETE FROM transfer WHERE withdrawal_account_id IN (101, 202, 999999, 501)");
        jdbcTemplate.update("DELETE FROM account WHERE account_id IN (501, 502, 601)");
        jdbcTemplate.update("DELETE FROM product WHERE product_id = 701");
        jdbcTemplate.update("UPDATE account SET balance = 100000, status = 'ACTIVE' WHERE account_id IN (101, 202)");
    }

    @Test
    @DisplayName("입금계좌번호가 존재하지 않으면 transfer 행을 만들지 않고 ERROR 결과를 반환한다")
    void execute_withUnknownDepositAccountNumber_returnsErrorResultWithoutCreatingTransferRow() {
        // given: 출금계좌만 존재, 입금계좌번호는 어떤 계좌에도 매칭되지 않는다.
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                TransferTestFixtures.seedCustomerAndAccounts(entityManager));

        TransferCommand command = TransferCommand.builder()
                .customerId(1L)
                .authToken("dummy-auth-token")
                .withdrawalAccountId(101L)
                .depositAccountNumber("999999999999")
                .amount(30000L)
                .transferType(TransferType.IMMEDIATE)
                .channel(TransferChannel.WB)
                .myPassbookMemo("출금메모")
                .recipientPassbookMemo("입금메모")
                .build();

        // when: 채번·저장 전에 실패하므로 남길 transfer 행이 없어 예외 대신 ERROR 결과로 돌아온다.
        TransferResult result = transferExecutionService.execute(command);

        assertThat(result.status()).isEqualTo(ProcessResultStatus.ERROR);
        assertThat(result.errorCode()).isEqualTo(TransferErrorCode.PAYEE_NOT_FOUND.getCode());

        // then: transfer 행이 아예 생기지 않는다 (입금계좌 해석 단계에서 실패해 채번·객체 생성에 도달하지 않는다)
        Long transferCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transfer WHERE withdrawal_account_id = 101", Long.class);
        assertThat(transferCount).isZero();
    }

    @Test
    @DisplayName("존재하지 않는 출금계좌로 이체를 시도하면 소유권 1차 검증에서 TRF0001로 거부되고 transfer 행을 만들지 않는다")
    void execute_withUnknownWithdrawalAccount_returnsErrorResultWithoutCreatingTransferRow() {
        // given: 입금계좌(202)만 존재. 출금계좌 999999는 account 테이블에 아예 없다.
        // findWithdrawalAccountDetail은 존재하지 않는 계좌를 "내 소유가 아님"과 구분하지 않으므로
        // 락 획득(lockForTransfer)까지 가지 않고 1차 검증에서 먼저 거부된다.
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                TransferTestFixtures.seedCustomerAndAccounts(entityManager));

        TransferCommand command = TransferCommand.builder()
                .customerId(1L)
                .authToken("dummy-auth-token")
                .withdrawalAccountId(999999L)
                .depositAccountNumber("110222222222")
                .amount(30000L)
                .transferType(TransferType.IMMEDIATE)
                .channel(TransferChannel.WB)
                .myPassbookMemo("출금메모")
                .recipientPassbookMemo("입금메모")
                .build();

        TransferResult result = transferExecutionService.execute(command);

        assertThat(result.status()).isEqualTo(ProcessResultStatus.ERROR);
        assertThat(result.errorCode()).isEqualTo(TransferErrorCode.WITHDRAWAL_ACCOUNT_NOT_REGISTERED.getCode());

        // then: transfer 행이 아예 생기지 않는다
        Long transferCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transfer WHERE withdrawal_account_id = 999999", Long.class);
        assertThat(transferCount).isZero();

        // then: 원장도 남지 않는다
        Long ledgerCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger_entry WHERE account_id = 202", Long.class);
        assertThat(ledgerCount).isZero();

        // then: 입금계좌 잔액은 그대로다
        Long depositBalance = jdbcTemplate.queryForObject(
                "SELECT balance FROM account WHERE account_id = 202", Long.class);
        assertThat(depositBalance).isEqualTo(100000L);
    }

    @Test
    @DisplayName("요청자 소유가 아닌 출금계좌로 이체를 시도하면 TRF0001로 거부되고 transfer 행을 만들지 않는다")
    void execute_withNotOwnedWithdrawalAccount_returnsErrorResultWithoutCreatingTransferRow() {
        // given: 출금계좌(101)의 실제 소유자는 customer_id=1이지만, 요청은 customerId=2로 온다.
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                TransferTestFixtures.seedCustomerAndAccounts(entityManager));

        TransferCommand command = TransferCommand.builder()
                .customerId(2L)
                .authToken("dummy-auth-token")
                .withdrawalAccountId(101L)
                .depositAccountNumber("110222222222")
                .amount(30000L)
                .transferType(TransferType.IMMEDIATE)
                .channel(TransferChannel.WB)
                .myPassbookMemo("출금메모")
                .recipientPassbookMemo("입금메모")
                .build();

        TransferResult result = transferExecutionService.execute(command);

        assertThat(result.status()).isEqualTo(ProcessResultStatus.ERROR);
        assertThat(result.errorCode()).isEqualTo(TransferErrorCode.WITHDRAWAL_ACCOUNT_NOT_REGISTERED.getCode());

        Long transferCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transfer WHERE withdrawal_account_id = 101", Long.class);
        assertThat(transferCount).isZero();
    }

    @Test
    @DisplayName("출금계좌로 등록되지 않은 계좌로 이체를 시도하면 TRF0001로 거부되고 transfer 행을 만들지 않는다")
    void execute_withUnregisteredWithdrawalAccount_returnsErrorResultWithoutCreatingTransferRow() {
        // given: 202는 픽스처에서 withdrawal_registered=FALSE로 시드된다. 101로 입금해
        // 별도 계좌 시드 없이 재사용한다.
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                TransferTestFixtures.seedCustomerAndAccounts(entityManager));

        TransferCommand command = TransferCommand.builder()
                .customerId(1L)
                .authToken("dummy-auth-token")
                .withdrawalAccountId(202L)
                .depositAccountNumber("110111111111")
                .amount(30000L)
                .transferType(TransferType.IMMEDIATE)
                .channel(TransferChannel.WB)
                .myPassbookMemo("출금메모")
                .recipientPassbookMemo("입금메모")
                .build();

        TransferResult result = transferExecutionService.execute(command);

        assertThat(result.status()).isEqualTo(ProcessResultStatus.ERROR);
        assertThat(result.errorCode()).isEqualTo(TransferErrorCode.WITHDRAWAL_ACCOUNT_NOT_REGISTERED.getCode());

        Long transferCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transfer WHERE withdrawal_account_id = 202", Long.class);
        assertThat(transferCount).isZero();
    }

    @Test
    @DisplayName("출금계좌와 입금계좌가 같으면 한도를 소모하기 전에 TRF0002로 거부되고 transfer 행을 만들지 않는다")
    void execute_withSameWithdrawalAndDepositAccount_returnsErrorResultWithoutReservingLimit() {
        // given: 101을 출금·입금 양쪽에 그대로 쓴다. 금액을 1회 한도(1,000만원)보다 크게 잡아,
        // 한도 검증이 동일계좌 검증보다 먼저 돌면 LMT0002가 나올 걸 TRF0002로 구분해낸다.
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                TransferTestFixtures.seedCustomerAndAccounts(entityManager));

        TransferCommand command = TransferCommand.builder()
                .customerId(1L)
                .authToken("dummy-auth-token")
                .withdrawalAccountId(101L)
                .depositAccountNumber("110111111111")
                .amount(10_000_001L)
                .transferType(TransferType.IMMEDIATE)
                .channel(TransferChannel.WB)
                .myPassbookMemo("출금메모")
                .recipientPassbookMemo("입금메모")
                .build();

        TransferResult result = transferExecutionService.execute(command);

        assertThat(result.status()).isEqualTo(ProcessResultStatus.ERROR);
        assertThat(result.errorCode()).isEqualTo(TransferErrorCode.SAME_ACCOUNT_TRANSFER.getCode());

        Long transferCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transfer WHERE withdrawal_account_id = 101", Long.class);
        assertThat(transferCount).isZero();
    }

    @Test
    @DisplayName("정기예금 계좌로는 입금할 수 없어 TRF0004로 거부되고 transfer 행을 만들지 않는다")
    void execute_withTimeDepositAsPayeeAccount_returnsErrorResultWithoutCreatingTransferRow() {
        // given: 601은 정기예금(TIME_DEPOSIT) 계좌다. REQ-TRSF-030에 따라 정기적금은 허용되지만
        // 정기예금은 입금계좌로 지정할 수 없다. product_id는 NOT NULL FK라 최소 상품 행(701)도
        // 함께 시드한다.
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            TransferTestFixtures.seedCustomerAndAccounts(entityManager);
            entityManager.createNativeQuery("""
                INSERT INTO product (product_id, product_code, product_name, product_group, deposit_type,
                    base_rate, max_rate, min_amount, max_amount, amount_unit, min_term_months, max_term_months,
                    interest_pay_type, sale_status, created_at, updated_at)
                VALUES (701, 'TRF-TEST-001', '테스트 정기예금', 'DEPOSIT', 'LUMP_SUM',
                    2.50, 3.00, 100000, 100000000, 10000, 6, 36,
                    'SIMPLE', 'ON_SALE', NOW(6), NOW(6))
                ON DUPLICATE KEY UPDATE product_id = product_id
                """).executeUpdate();
            entityManager.createNativeQuery("""
                INSERT INTO account (account_id, account_number, customer_id, product_id, account_type, balance, status, password_hash, opened_date, maturity_date, created_at, updated_at)
                VALUES (601, '110666666666', 1, 701, 'TIME_DEPOSIT', 0, 'ACTIVE', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklm', '2026-08-01', '2027-08-01', NOW(6), NOW(6))
                ON DUPLICATE KEY UPDATE account_type = VALUES(account_type)
                """).executeUpdate();
        });

        TransferCommand command = TransferCommand.builder()
                .customerId(1L)
                .authToken("dummy-auth-token")
                .withdrawalAccountId(101L)
                .depositAccountNumber("110666666666")
                .amount(30000L)
                .transferType(TransferType.IMMEDIATE)
                .channel(TransferChannel.WB)
                .myPassbookMemo("출금메모")
                .recipientPassbookMemo("입금메모")
                .build();

        TransferResult result = transferExecutionService.execute(command);

        assertThat(result.status()).isEqualTo(ProcessResultStatus.ERROR);
        assertThat(result.errorCode()).isEqualTo(TransferErrorCode.UNSUPPORTED_ACCOUNT_TYPE.getCode());

        Long transferCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transfer WHERE withdrawal_account_id = 101", Long.class);
        assertThat(transferCount).isZero();
    }

    @Test
    @DisplayName("출금계좌가 정지 상태이면 락 획득 후 최신 상태로 재검증해 ERROR로 기록하고 잔액을 반영하지 않는다")
    void execute_withSuspendedWithdrawalAccount_recordsErrorTransfer_withoutBalanceChange() {
        // given: 조회 시점에는 확인할 수 없고, 락을 획득한 뒤에만 알 수 있는 최신 상태(SUSPENDED)를 재현한다.
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                TransferTestFixtures.seedCustomerAndAccounts(entityManager));
        jdbcTemplate.update("UPDATE account SET status = 'SUSPENDED' WHERE account_id = 101");

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

        TransferResult result = transferExecutionService.execute(command);

        assertThat(result.status()).isEqualTo(ProcessResultStatus.ERROR);
        assertThat(result.errorCode()).isEqualTo(TransferErrorCode.WITHDRAWAL_ACCOUNT_SUSPENDED.getCode());

        Map<String, Object> transferRow = jdbcTemplate.queryForMap(
                "SELECT status, error_code FROM transfer WHERE transaction_number = ?",
                result.transactionNumber());
        assertThat(transferRow.get("status")).isEqualTo("ERROR");
        assertThat(transferRow.get("error_code")).isEqualTo(TransferErrorCode.WITHDRAWAL_ACCOUNT_SUSPENDED.getCode());

        Long ledgerCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger_entry WHERE transaction_number = ?",
                Long.class, result.transactionNumber());
        assertThat(ledgerCount).isZero();

        Long withdrawalBalance = jdbcTemplate.queryForObject(
                "SELECT balance FROM account WHERE account_id = 101", Long.class);
        assertThat(withdrawalBalance).isEqualTo(100000L);

        Long depositBalance = jdbcTemplate.queryForObject(
                "SELECT balance FROM account WHERE account_id = 202", Long.class);
        assertThat(depositBalance).isEqualTo(100000L);
    }

    @Test
    @DisplayName("입금계좌가 정지 상태이면 락 획득 후 최신 상태로 재검증해 ERROR로 기록하고 잔액을 반영하지 않는다")
    void execute_withSuspendedDepositAccount_recordsErrorTransfer_withoutBalanceChange() {
        // given: 계좌번호 사전 조회 이후, 락 획득 시점 사이에 입금계좌가 정지됐다고 가정한다.
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                TransferTestFixtures.seedCustomerAndAccounts(entityManager));
        jdbcTemplate.update("UPDATE account SET status = 'SUSPENDED' WHERE account_id = 202");

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

        TransferResult result = transferExecutionService.execute(command);

        assertThat(result.status()).isEqualTo(ProcessResultStatus.ERROR);
        assertThat(result.errorCode()).isEqualTo(TransferErrorCode.PAYEE_ACCOUNT_SUSPENDED.getCode());

        Map<String, Object> transferRow = jdbcTemplate.queryForMap(
                "SELECT status, error_code FROM transfer WHERE transaction_number = ?",
                result.transactionNumber());
        assertThat(transferRow.get("status")).isEqualTo("ERROR");
        assertThat(transferRow.get("error_code")).isEqualTo(TransferErrorCode.PAYEE_ACCOUNT_SUSPENDED.getCode());

        Long ledgerCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger_entry WHERE transaction_number = ?",
                Long.class, result.transactionNumber());
        assertThat(ledgerCount).isZero();

        Long withdrawalBalance = jdbcTemplate.queryForObject(
                "SELECT balance FROM account WHERE account_id = 101", Long.class);
        assertThat(withdrawalBalance).isEqualTo(100000L);

        Long depositBalance = jdbcTemplate.queryForObject(
                "SELECT balance FROM account WHERE account_id = 202", Long.class);
        assertThat(depositBalance).isEqualTo(100000L);
    }

    @Test
    @DisplayName("1회 이체한도(Mock 고정 상한)를 초과하면 락 획득 후 ERROR로 기록하고 잔액을 반영하지 않는다")
    void execute_withAmountExceedingOneTimeLimit_recordsErrorTransfer_withoutBalanceChange() {
        // given: MockTransferLimitPort의 고정 상한(1,000만원)을 넘는 금액으로 요청한다.
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                TransferTestFixtures.seedCustomerAndAccounts(entityManager));
        jdbcTemplate.update("UPDATE account SET balance = 20000000 WHERE account_id = 101");

        TransferCommand command = TransferCommand.builder()
                .customerId(1L)
                .authToken("dummy-auth-token")
                .withdrawalAccountId(101L)
                .depositAccountNumber("110222222222")
                .amount(10_000_001L)
                .transferType(TransferType.IMMEDIATE)
                .channel(TransferChannel.WB)
                .myPassbookMemo("출금메모")
                .recipientPassbookMemo("입금메모")
                .build();

        TransferResult result = transferExecutionService.execute(command);

        assertThat(result.status()).isEqualTo(ProcessResultStatus.ERROR);
        assertThat(result.errorCode()).isEqualTo(LimitErrorCode.ONE_TIME_LIMIT_EXCEEDED.getCode());

        Map<String, Object> transferRow = jdbcTemplate.queryForMap(
                "SELECT status, error_code FROM transfer WHERE transaction_number = ?",
                result.transactionNumber());
        assertThat(transferRow.get("status")).isEqualTo("ERROR");
        assertThat(transferRow.get("error_code")).isEqualTo(LimitErrorCode.ONE_TIME_LIMIT_EXCEEDED.getCode());

        Long ledgerCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger_entry WHERE transaction_number = ?",
                Long.class, result.transactionNumber());
        assertThat(ledgerCount).isZero();

        Long withdrawalBalance = jdbcTemplate.queryForObject(
                "SELECT balance FROM account WHERE account_id = 101", Long.class);
        assertThat(withdrawalBalance).isEqualTo(20000000L);
    }

    @Test
    @DisplayName("예기치 못한 RuntimeException이 나면, execute()를 호출자의 트랜잭션 안에서 실행했고 그 트랜잭션이 나중에 롤백되더라도 ERROR 확정 행은 살아남는다")
    void execute_withUnexpectedRuntimeException_errorRowSurvivesCallerTransactionRollback() {
        // given: 입금계좌 잔액을 Long.MAX_VALUE 근처로 세팅해 credit()의 Math.addExact가 오버플로우하게 만든다.
        // (잔액검증은 출금계좌만 보므로 이 시나리오는 BusinessException이 아니라 ArithmeticException 경로를 탄다.)
        jdbcTemplate.update("""
            INSERT INTO customer (customer_id, user_id, password_hash, user_name, birth_date, email, phone_number, joined_at, created_at, updated_at)
            VALUES (1, 'user1', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklm', '테스터', '1990-01-01', 'test@test.com', '01012345678', NOW(6), NOW(6), NOW(6))
            ON DUPLICATE KEY UPDATE customer_id = customer_id
            """);
        jdbcTemplate.update("""
            INSERT INTO account (account_id, account_number, customer_id, product_id, account_type, balance, status, password_hash, withdrawal_registered, withdrawal_registered_at, opened_date, created_at, updated_at)
            VALUES (501, '110777777777', 1, NULL, 'DEMAND_DEPOSIT', 100000, 'ACTIVE', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklm', TRUE, NOW(6), '2026-08-01', NOW(6), NOW(6)),
                   (502, '110888888888', 1, NULL, 'DEMAND_DEPOSIT', ?, 'ACTIVE', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklm', FALSE, NULL, '2026-08-01', NOW(6), NOW(6))
            ON DUPLICATE KEY UPDATE balance = VALUES(balance)
            """, Long.MAX_VALUE - 500);

        TransferCommand command = TransferCommand.builder()
                .customerId(1L)
                .authToken("dummy-auth-token")
                .withdrawalAccountId(501L)
                .depositAccountNumber("110888888888")
                .amount(1000L)
                .transferType(TransferType.IMMEDIATE)
                .channel(TransferChannel.WB)
                .myPassbookMemo("오버플로우")
                .recipientPassbookMemo("오버플로우")
                .build();

        // execute()를 실제 프로덕션 호출자(AutoTransferBatchItemProcessor.completeProcessing() 등)처럼
        // 그 자신도 REQUIRES_NEW 트랜잭션 안에서 호출한다고 가정한다.
        TransactionTemplate callerTransaction = new TransactionTemplate(transactionManager);
        callerTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // when: execute()는 ArithmeticException을 다시 던지고, 이게 호출자 트랜잭션 밖으로
        // 전파되어 호출자 트랜잭션 전체가 롤백된다.
        assertThatThrownBy(() -> callerTransaction.executeWithoutResult(status ->
                transferExecutionService.execute(command)))
                .isInstanceOf(ArithmeticException.class);

        // then: ERROR 확정 행은 호출자 트랜잭션 롤백과 무관하게 살아남아야 한다.
        Map<String, Object> transferRow = jdbcTemplate.queryForMap(
                "SELECT status, error_code FROM transfer WHERE withdrawal_account_id = 501");
        assertThat(transferRow.get("status")).isEqualTo("ERROR");
        assertThat(transferRow.get("error_code")).isEqualTo(CommonErrorCode.INTERNAL_ERROR.getCode());

        // then: 원장 0행
        Long ledgerCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger_entry WHERE account_id IN (501, 502)", Long.class);
        assertThat(ledgerCount).isZero();

        // then: 오버플로우로 롤백됐으므로 양쪽 계좌 잔액 모두 시딩 당시 값 그대로다
        Long withdrawalBalance = jdbcTemplate.queryForObject(
                "SELECT balance FROM account WHERE account_id = 501", Long.class);
        assertThat(withdrawalBalance).isEqualTo(100000L);

        Long depositBalance = jdbcTemplate.queryForObject(
                "SELECT balance FROM account WHERE account_id = 502", Long.class);
        assertThat(depositBalance).isEqualTo(Long.MAX_VALUE - 500);
    }
}

