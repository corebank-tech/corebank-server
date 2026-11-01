package com.shinhan.corebank.transfer.application.service;

import static java.time.format.DateTimeFormatter.BASIC_ISO_DATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.batch.api.CobStepHaltException;
import com.shinhan.corebank.transfer.adapter.out.persistence.LedgerEntryIdGenerator;
import com.shinhan.corebank.transfer.adapter.out.persistence.LedgerEntryJpaEntity;
import com.shinhan.corebank.transfer.adapter.out.persistence.LedgerEntryJpaRepository;
import com.shinhan.corebank.transfer.adapter.out.persistence.TransferTestFixtures;
import com.shinhan.corebank.transfer.application.port.in.LedgerReconciliationBatchUseCase;
import com.shinhan.corebank.transfer.application.port.in.LedgerReconciliationMismatchQueryUseCase.LedgerReconciliationMismatchView;
import com.shinhan.corebank.transfer.application.port.out.LedgerReconciliationMismatchPort;
import com.shinhan.corebank.transfer.domain.LedgerDirection;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

// 마감을 멈춘 날짜로 대사가 다시 돌아도 그날 목록이 남는지 본다(#749). 불일치 2건으로 멈추도록 임계를 1로 낮춘다.
@Transactional
@TestPropertySource(properties = "app.ledger-reconciliation.cob-hard-threshold=1")
class LedgerReconciliationCobResumeIntegrationTest extends IntegrationTestSupport {

    private static final LocalDate COB_DATE = LocalDate.of(2026, 10, 22);
    private static final LocalDateTime POSTED_AT = LocalDateTime.of(2026, 10, 1, 10, 0, 0);

    @Autowired
    private LedgerReconciliationBatchUseCase ledgerReconciliationBatchUseCase;

    @Autowired
    private LedgerReconciliationMismatchPort ledgerReconciliationMismatchPort;

    @Autowired
    private LedgerEntryJpaRepository ledgerEntryJpaRepository;

    @Autowired
    private LedgerEntryIdGenerator ledgerEntryIdGenerator;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("hard로 마감을 멈춘 날짜로 대사가 다시 돌아도 그날 처음 저장한 불일치 목록이 남는다(#749)")
    void runInCob_rerunForHaltedDate_keepsFirstMismatchList() {
        // given — 계좌 101·202의 잔액만 원장보다 7,000원 많다
        TransferTestFixtures.seedCustomerAndAccounts(entityManager);
        saveEntry(101L);
        saveEntry(202L);
        entityManager.flush();
        updateBalance("balance + 7000", "101, 202");

        // 10/22 COB 대사가 2건을 찾아 마감을 멈춘다
        assertThatThrownBy(() -> ledgerReconciliationBatchUseCase.runInCob(COB_DATE))
                .isInstanceOf(CobStepHaltException.class);
        entityManager.flush();
        entityManager.clear();

        // 운영자가 101을 손으로 맞춘다
        updateBalance("100000", "101");

        // when — 멈춘 COB를 재개하면 같은 cobDate로 대사가 다시 돈다. 다른 시드 계좌가 섞이면 또 멈출 수 있어 결과는 보지 않는다
        catchThrowable(() -> ledgerReconciliationBatchUseCase.runInCob(COB_DATE));
        entityManager.flush();
        entityManager.clear();

        // then — 마감을 멈춘 그 목록(101·202)이 그대로다
        var saved = ledgerReconciliationMismatchPort
                .findByBusinessDate(COB_DATE, PageRequest.of(0, 100))
                .getContent();
        assertThat(saved)
                .extracting(
                        LedgerReconciliationMismatchView::accountId,
                        LedgerReconciliationMismatchView::ledgerBalance,
                        LedgerReconciliationMismatchView::accountBalance)
                .contains(tuple(101L, 100000L, 107000L), tuple(202L, 100000L, 107000L));
    }

    private void updateBalance(String balanceExpression, String accountIds) {
        entityManager
                .createNativeQuery("UPDATE account SET balance = " + balanceExpression + " WHERE account_id IN ("
                        + accountIds + ")")
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }

    private void saveEntry(Long accountId) {
        Long ledgerEntryId = ledgerEntryIdGenerator.nextId();
        ledgerEntryJpaRepository.save(LedgerEntryJpaEntity.builder()
                .ledgerEntryId(ledgerEntryId)
                .accountId(accountId)
                .transactionNumber(
                        String.format("%sWB%010d", POSTED_AT.toLocalDate().format(BASIC_ISO_DATE), ledgerEntryId))
                .direction(LedgerDirection.DEPOSIT)
                .amount(100000L)
                .balanceAfter(100000L)
                .transactionType("IMMEDIATE_TRANSFER")
                .channel(TransferChannel.WB)
                .reversed(false)
                .occurredAt(POSTED_AT)
                .build());
    }
}
