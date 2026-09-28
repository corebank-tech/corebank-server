package com.shinhan.corebank.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.transfer.adapter.out.persistence.LedgerEntryIdGenerator;
import com.shinhan.corebank.transfer.adapter.out.persistence.LedgerEntryJpaEntity;
import com.shinhan.corebank.transfer.adapter.out.persistence.LedgerEntryJpaRepository;
import com.shinhan.corebank.transfer.adapter.out.persistence.TransferTestFixtures;
import com.shinhan.corebank.transfer.application.port.in.LedgerReconciliationMismatch;
import com.shinhan.corebank.transfer.application.port.in.LedgerReconciliationUseCase;
import com.shinhan.corebank.transfer.domain.LedgerDirection;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

// LedgerReconciliationService -> LedgerReconciliationPort/AccountBalanceSnapshotPort -> 실제 DB까지
// 전체 체인을 엮어, 원장 합계와 계좌 잔액이 어긋난 계좌를 실제로 탐지하는지 검증한다(#378).
@Transactional
class LedgerReconciliationIntegrationTest extends IntegrationTestSupport {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 9);

    @Autowired
    private LedgerReconciliationUseCase ledgerReconciliationUseCase;

    @Autowired
    private LedgerEntryJpaRepository ledgerEntryJpaRepository;

    @Autowired
    private LedgerEntryIdGenerator ledgerEntryIdGenerator;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("원장 합계와 계좌 잔액이 어긋난 계좌만 불일치로 탐지하고, 일치하는 계좌는 조용히 넘어간다")
    void reconcile_detectsOnlyMismatchedAccount() {
        // given
        // 계좌 101, 202 둘 다 balance=100000으로 시드된다.
        TransferTestFixtures.seedCustomerAndAccounts(entityManager);

        // 101: 원장 합계가 95000으로, 화면상 잔액(100000)과 5000원 어긋나게 만든다 (탐지 대상)
        saveEntry(101L, LedgerDirection.DEPOSIT, 95000L);
        // 202: 원장 합계가 100000으로, 화면상 잔액과 정확히 일치시킨다 (탐지되면 안 됨)
        saveEntry(202L, LedgerDirection.DEPOSIT, 100000L);
        entityManager.flush();
        entityManager.clear();

        // when
        var mismatches = ledgerReconciliationUseCase.reconcile(DATE);

        // then
        assertThat(mismatches).containsExactly(new LedgerReconciliationMismatch(101L, 95000L, 100000L));
    }

    private void saveEntry(Long accountId, LedgerDirection direction, long amount) {
        Long ledgerEntryId = ledgerEntryIdGenerator.nextId();
        ledgerEntryJpaRepository.save(LedgerEntryJpaEntity.builder()
                .ledgerEntryId(ledgerEntryId)
                .accountId(accountId)
                .transactionNumber(String.format("20260809WB%010d", ledgerEntryId))
                .direction(direction)
                .amount(amount)
                .balanceAfter(amount)
                .transactionType("IMMEDIATE_TRANSFER")
                .channel(TransferChannel.WB)
                .reversed(false)
                .occurredAt(LocalDateTime.of(2026, 8, 9, 10, 0, 0))
                .build());
    }
}
