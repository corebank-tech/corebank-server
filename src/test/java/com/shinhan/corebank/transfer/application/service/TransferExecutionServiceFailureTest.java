package com.shinhan.corebank.transfer.application.service;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.transfer.adapter.out.persistence.TransferTestFixtures;
import com.shinhan.corebank.transfer.application.port.in.TransferCommand;
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
        jdbcTemplate.update("DELETE FROM ledger_entry WHERE account_id IN (101, 202)");
        jdbcTemplate.update("DELETE FROM transfer WHERE withdrawal_account_id IN (101, 999999)");
        jdbcTemplate.update("UPDATE account SET balance = 100000 WHERE account_id IN (101, 202)");
    }

    @Test
    @DisplayName("입금계좌번호가 존재하지 않으면 transfer 행을 만들지 않고 예외를 던진다")
    void execute_withUnknownDepositAccountNumber_throwsWithoutCreatingTransferRow() {
        // given: 출금계좌만 존재, 입금계좌번호는 어떤 계좌에도 매칭되지 않는다.
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                TransferTestFixtures.seedCustomerAndAccounts(entityManager));

        TransferCommand command = TransferCommand.builder()
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

        // then: transfer 행이 아예 생기지 않는다 (deposit_account_id가 없어 애초에 저장 불가능)
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
        // 않고 원래 예외를 그대로 던진다.
        assertThatThrownBy(() -> transferExecutionService.execute(command))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(TransferErrorCode.ACCOUNT_LOCK_TARGET_NOT_FOUND));

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
}
