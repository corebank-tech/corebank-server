package com.shinhan.corebank.transfer.application.service;

import java.util.Map;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.transfer.adapter.out.persistence.TransferTestFixtures;
import com.shinhan.corebank.transfer.application.port.in.TransferCommand;
import com.shinhan.corebank.transfer.application.port.in.TransferResult;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferType;
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
        jdbcTemplate.update("DELETE FROM ledger_entry WHERE account_id IN (101, 202, 501, 502)");
        jdbcTemplate.update("DELETE FROM transfer WHERE withdrawal_account_id IN (101, 999999, 501)");
        jdbcTemplate.update("DELETE FROM account WHERE account_id IN (501, 502)");
        jdbcTemplate.update("UPDATE account SET balance = 100000, status = 'ACTIVE' WHERE account_id IN (101, 202)");
    }

    @Test
    @DisplayName("입금계좌번호가 존재하지 않으면 transfer 행을 만들지 않고 예외를 던진다")
    void execute_withUnknownDepositAccountNumber_throwsWithoutCreatingTransferRow() {
        // given: 출금계좌만 존재, 입금계좌번호는 어떤 계좌에도 매칭되지 않는다.
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                TransferTestFixtures.seedCustomerAndAccounts(entityManager));

        TransferCommand command = TransferCommand.builder()
                .customerId(1L)
                .withdrawalAccountId(101L)
                .depositAccountNumber("999999999999")
                .amount(30000L)
                .transferType(TransferType.IMMEDIATE)
                .channel(TransferChannel.WB)
                .myPassbookMemo("출금메모")
                .recipientPassbookMemo("입금메모")
                .build();

        // when / then: 채번·저장 전에 터지므로 ERROR 결과가 아니라 예외로 전파된다.
        assertThatThrownBy(() -> transferExecutionService.execute(command))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(TransferErrorCode.PAYEE_NOT_FOUND));

        // then: transfer 행이 아예 생기지 않는다 (입금계좌 해석 단계에서 예외가 나 채번·객체 생성에 도달하지 않는다)
        Long transferCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transfer WHERE withdrawal_account_id = 101", Long.class);
        assertThat(transferCount).isZero();
    }

    @Test
    @DisplayName("존재하지 않는 출금계좌로 이체를 시도하면 ERROR 행조차 남기지 못하고 원래 예외가 그대로 전파된다")
    void execute_withUnknownWithdrawalAccount_propagatesOriginalException() {
        // given: 입금계좌(202)만 존재. 출금계좌 999999는 account 테이블에 아예 없다.
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                TransferTestFixtures.seedCustomerAndAccounts(entityManager));

        TransferCommand command = TransferCommand.builder()
                .customerId(1L)
                .withdrawalAccountId(999999L)
                .depositAccountNumber("110222222222")
                .amount(30000L)
                .transferType(TransferType.IMMEDIATE)
                .channel(TransferChannel.WB)
                .myPassbookMemo("출금메모")
                .recipientPassbookMemo("입금메모")
                .build();

        // when / then: lockForTransfer가 계좌 락 획득 단계에서 먼저 "계좌가 없다"고 판단해
        // ACCOUNT_LOCK_TARGET_NOT_FOUND를 던진다(정상 흐름에서는 발생 불가능한 불변식 위반).
        // withdrawal_account_id가 transfer 테이블에 FK(NOT NULL)라 이 계좌로는 ERROR 확정
        // 행조차 남길 수 없으므로(그 INSERT도 같은 FK 위반), failTransfer는 기록 실패를 삼키지
        // 않고 원래 예외를 그대로 던지되, 기록 실패 원인(FK 위반)은 suppressed로 함께 남긴다.
        assertThatThrownBy(() -> transferExecutionService.execute(command))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(TransferErrorCode.ACCOUNT_LOCK_TARGET_NOT_FOUND);
                    assertThat(e.getSuppressed())
                            .as("ERROR 확정 기록 실패(FK 위반) 원인이 유실되지 않고 suppressed로 남아야 한다")
                            .isNotEmpty();
                });

        // then: transfer 행이 아예 남지 않는다
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
    @DisplayName("출금계좌가 정지 상태이면 락 획득 후 최신 상태로 재검증해 ERROR로 기록하고 잔액을 반영하지 않는다")
    void execute_withSuspendedWithdrawalAccount_recordsErrorTransfer_withoutBalanceChange() {
        // given: 조회 시점에는 확인할 수 없고, 락을 획득한 뒤에만 알 수 있는 최신 상태(SUSPENDED)를 재현한다.
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                TransferTestFixtures.seedCustomerAndAccounts(entityManager));
        jdbcTemplate.update("UPDATE account SET status = 'SUSPENDED' WHERE account_id = 101");

        TransferCommand command = TransferCommand.builder()
                .customerId(1L)
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
            INSERT INTO account (account_id, account_number, customer_id, product_id, account_type, balance, status, password_hash, opened_date, created_at, updated_at)
            VALUES (501, '110777777777', 1, NULL, 'DEMAND_DEPOSIT', 100000, 'ACTIVE', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklm', '2026-08-01', NOW(6), NOW(6)),
                   (502, '110888888888', 1, NULL, 'DEMAND_DEPOSIT', ?, 'ACTIVE', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklm', '2026-08-01', NOW(6), NOW(6))
            ON DUPLICATE KEY UPDATE balance = VALUES(balance)
            """, Long.MAX_VALUE - 500);

        TransferCommand command = TransferCommand.builder()
                .customerId(1L)
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

