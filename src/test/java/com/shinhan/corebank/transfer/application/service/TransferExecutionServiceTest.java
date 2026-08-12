package com.shinhan.corebank.transfer.application.service;

import java.util.List;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.transfer.adapter.out.persistence.TransferTestFixtures;
import com.shinhan.corebank.transfer.application.port.in.TransferCommand;
import com.shinhan.corebank.transfer.application.port.in.TransferResult;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferType;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class TransferExecutionServiceTest extends IntegrationTestSupport {

    @Autowired
    private TransferExecutionService transferExecutionService;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("정상 이체는 원장 2행을 기표하고 양쪽 계좌 잔액을 반영한 SUCCESS 결과를 반환한다")
    void execute_completesTransfer_withLedgerPairAndBalanceUpdate() {
        // given
        TransferTestFixtures.seedCustomerAndAccounts(entityManager);
        entityManager.flush();
        entityManager.clear();

        TransferCommand command = TransferCommand.builder()
                .withdrawalAccountId(101L)
                .depositAccountNumber("110222222222")
                .amount(30000L)
                .transferType(TransferType.IMMEDIATE)
                .channel(TransferChannel.WB)
                .myPassbookMemo("출금메모")
                .recipientPassbookMemo("입금메모")
                .build();

        // when
        TransferResult result = transferExecutionService.execute(command);
        entityManager.flush();
        entityManager.clear();

        // then: 응답
        assertThat(result.status()).isEqualTo(ProcessResultStatus.SUCCESS);
        assertThat(result.transactionNumber()).matches("^[0-9]{8}WB[0-9]{10}$");
        assertThat(result.withdrawalBalanceAfter()).isEqualTo(70000L);

        // then: 계좌 잔액 반영
        long withdrawalBalance = ((Number) entityManager
                .createNativeQuery("SELECT balance FROM account WHERE account_id = 101")
                .getSingleResult())
                .longValue();
        long depositBalance = ((Number) entityManager
                .createNativeQuery("SELECT balance FROM account WHERE account_id = 202")
                .getSingleResult())
                .longValue();
        assertThat(withdrawalBalance).isEqualTo(70000L);
        assertThat(depositBalance).isEqualTo(130000L);

        // then: transfer 1행
        Tuple transferRow = (Tuple) entityManager
                .createNativeQuery("""
                    SELECT status, payee_name, withdrawal_account_id, deposit_account_id, amount, fee
                    FROM transfer WHERE transaction_number = :txn
                    """, Tuple.class)
                .setParameter("txn", result.transactionNumber())
                .getSingleResult();
        assertThat(transferRow.get("status")).isEqualTo("SUCCESS");
        assertThat(transferRow.get("payee_name")).isEqualTo("테스터");
        assertThat(((Number) transferRow.get("withdrawal_account_id")).longValue()).isEqualTo(101L);
        assertThat(((Number) transferRow.get("deposit_account_id")).longValue()).isEqualTo(202L);
        assertThat(((Number) transferRow.get("amount")).longValue()).isEqualTo(30000L);
        assertThat(((Number) transferRow.get("fee")).longValue()).isEqualTo(0L);

        // then: ledger_entry 원장 2행 (출금/입금)
        @SuppressWarnings("unchecked")
        List<Tuple> ledgerRows = entityManager
                .createNativeQuery("""
                    SELECT direction, account_id, amount, balance_after
                    FROM ledger_entry WHERE transaction_number = :txn ORDER BY direction
                    """, Tuple.class)
                .setParameter("txn", result.transactionNumber())
                .getResultList();

        assertThat(ledgerRows).hasSize(2);

        Tuple depositEntry = ledgerRows.get(0);
        assertThat(depositEntry.get("direction")).isEqualTo("DEPOSIT");
        assertThat(((Number) depositEntry.get("account_id")).longValue()).isEqualTo(202L);
        assertThat(((Number) depositEntry.get("amount")).longValue()).isEqualTo(30000L);
        assertThat(((Number) depositEntry.get("balance_after")).longValue()).isEqualTo(130000L);

        Tuple withdrawalEntry = ledgerRows.get(1);
        assertThat(withdrawalEntry.get("direction")).isEqualTo("WITHDRAWAL");
        assertThat(((Number) withdrawalEntry.get("account_id")).longValue()).isEqualTo(101L);
        assertThat(((Number) withdrawalEntry.get("amount")).longValue()).isEqualTo(30000L);
        assertThat(((Number) withdrawalEntry.get("balance_after")).longValue()).isEqualTo(70000L);
    }
}
