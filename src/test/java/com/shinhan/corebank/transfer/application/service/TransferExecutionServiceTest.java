package com.shinhan.corebank.transfer.application.service;

import java.util.List;
import java.util.Map;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.transfer.adapter.out.persistence.TransferTestFixtures;
import com.shinhan.corebank.transfer.application.port.in.TransferCommand;
import com.shinhan.corebank.transfer.application.port.in.TransferResult;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferType;

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

    @AfterEach
    void cleanUpCommittedData() {
        jdbcTemplate.update("DELETE FROM ledger_entry WHERE account_id IN (101, 202)");
        jdbcTemplate.update("DELETE FROM transfer WHERE withdrawal_account_id = 101 AND deposit_account_id = 202");
        jdbcTemplate.update("UPDATE account SET balance = 100000 WHERE account_id IN (101, 202)");
    }

    @Test
    @DisplayName("정상 이체는 원장 2행을 기표하고 양쪽 계좌 잔액을 반영한 SUCCESS 결과를 반환한다")
    void execute_completesTransfer_withLedgerPairAndBalanceUpdate() {
        // given: 픽스처는 별도 트랜잭션에서 커밋한다.
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                TransferTestFixtures.seedCustomerAndAccounts(entityManager));

        TransferCommand command = TransferCommand.builder()
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
}
