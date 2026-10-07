package com.shinhan.corebank.transfer.application.service;

import static java.time.format.DateTimeFormatter.BASIC_ISO_DATE;
import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.transfer.adapter.out.persistence.LedgerEntryIdGenerator;
import com.shinhan.corebank.transfer.adapter.out.persistence.LedgerEntryJpaEntity;
import com.shinhan.corebank.transfer.adapter.out.persistence.LedgerEntryJpaRepository;
import com.shinhan.corebank.transfer.adapter.out.persistence.TransferTestFixtures;
import com.shinhan.corebank.transfer.application.port.in.LedgerFullReconciliationUseCase;
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
    private static final LocalDateTime DORMANT_POSTED_AT = LocalDateTime.of(2026, 8, 1, 10, 0, 0);

    @Autowired
    private LedgerReconciliationUseCase ledgerReconciliationUseCase;

    @Autowired
    private LedgerFullReconciliationUseCase ledgerFullReconciliationUseCase;

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

    @Test
    @DisplayName("원장 기표 없이 잔액만 틀어진 휴면 계좌는 증분 대사가 놓치고 전수 대사가 잡는다(#468)")
    void reconcileAll_detectsDormantDriftMissedByIncremental() {
        // given — 8/1 기표로 원장과 잔액이 맞던 계좌 101·202가, 그 뒤 원장을 거치지 않고 잔액만 7,000원 늘었다
        TransferTestFixtures.seedCustomerAndAccounts(entityManager);
        saveEntry(101L, LedgerDirection.DEPOSIT, 100000L, DORMANT_POSTED_AT);
        saveEntry(202L, LedgerDirection.DEPOSIT, 100000L, DORMANT_POSTED_AT);
        entityManager.flush();
        entityManager
                .createNativeQuery("UPDATE account SET balance = balance + 7000 WHERE account_id IN (101, 202)")
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        // when
        var incremental = ledgerReconciliationUseCase.reconcile(DATE);
        var full = ledgerFullReconciliationUseCase.reconcileAll();

        // then — 다른 시드 계좌가 섞일 수 있어 정확 일치 대신 포함 여부로 본다
        assertThat(incremental).isEmpty();
        assertThat(full)
                .contains(
                        new LedgerReconciliationMismatch(101L, 100000L, 107000L),
                        new LedgerReconciliationMismatch(202L, 100000L, 107000L));
    }

    private void saveEntry(Long accountId, LedgerDirection direction, long amount) {
        saveEntry(accountId, direction, amount, LocalDateTime.of(2026, 8, 9, 10, 0, 0));
    }

    private void saveEntry(Long accountId, LedgerDirection direction, long amount, LocalDateTime occurredAt) {
        Long ledgerEntryId = ledgerEntryIdGenerator.nextId();
        ledgerEntryJpaRepository.save(LedgerEntryJpaEntity.builder()
                .ledgerEntryId(ledgerEntryId)
                .accountId(accountId)
                .transactionNumber(
                        String.format("%sWB%010d", occurredAt.toLocalDate().format(BASIC_ISO_DATE), ledgerEntryId))
                .direction(direction)
                .amount(amount)
                .balanceAfter(amount)
                .transactionType("IMMEDIATE_TRANSFER")
                .channel(TransferChannel.WB)
                .reversed(false)
                .occurredAt(occurredAt)
                .build());
    }
}
