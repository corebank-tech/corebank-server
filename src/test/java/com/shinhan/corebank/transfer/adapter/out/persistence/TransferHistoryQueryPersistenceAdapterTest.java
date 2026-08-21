package com.shinhan.corebank.transfer.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.transfer.application.port.in.TransferHistorySort;
import com.shinhan.corebank.transfer.application.port.out.TransferHistoryAggregate;
import com.shinhan.corebank.transfer.domain.Transfer;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferType;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class TransferHistoryQueryPersistenceAdapterTest extends IntegrationTestSupport {

    @Autowired
    private TransferHistoryQueryPersistenceAdapter adapter;

    @Autowired
    private TransferPersistenceAdapter saveAdapter;

    @Autowired
    private EntityManager entityManager;

    private static final Long WITHDRAWAL_ACCOUNT_ID = 101L;

    @BeforeEach
    void setUp() {
        TransferTestFixtures.seedCustomerAndAccounts(entityManager);

        seedTransfer("20260810IT0000000001", 10_000L, LocalDateTime.of(2026, 8, 10, 9, 0), ProcessResultStatus.SUCCESS);
        seedTransfer("20260815IT0000000002", 20_000L, LocalDateTime.of(2026, 8, 15, 9, 0), ProcessResultStatus.ERROR);
        seedTransfer("20260818IT0000000003", 30_000L, LocalDateTime.of(2026, 8, 18, 9, 0), ProcessResultStatus.PROCESSING);
        // 다른 계좌(202) 소유 이체 — withdrawalAccountId 필터 검증용. 202는 픽스처상 입금 전용이지만
        // 여기서는 단순히 "다른 출금계좌"를 흉내내는 용도로 withdrawalAccountId만 다르게 저장한다.
        seedTransferForAccount(202L, "20260816IT0000000004", 40_000L, LocalDateTime.of(2026, 8, 16, 9, 0), ProcessResultStatus.SUCCESS);

        entityManager.flush();
        entityManager.clear();
    }

    private void seedTransfer(String transactionNumber, long amount, LocalDateTime transferredAt, ProcessResultStatus status) {
        seedTransferForAccount(WITHDRAWAL_ACCOUNT_ID, transactionNumber, amount, transferredAt, status);
    }

    private void seedTransferForAccount(Long withdrawalAccountId, String transactionNumber, long amount,
                                         LocalDateTime transferredAt, ProcessResultStatus status) {
        // withdrawalAccountId=202(픽스처상 입금 전용 계좌)로 "다른 계좌" 이체를 흉내낼 때
        // depositAccountId를 202로 고정하면 출금=입금 계좌가 같아져 Transfer.create()의
        // requireDifferentAccounts 검증(SAME_ACCOUNT_TRANSFER)에 걸린다. 두 계좌뿐인
        // 픽스처에서 "출금계좌가 아닌 쪽"으로 입금 대상을 맞바꿔 피한다.
        boolean primaryWithdrawal = withdrawalAccountId.equals(WITHDRAWAL_ACCOUNT_ID);
        Long depositAccountId = primaryWithdrawal ? 202L : WITHDRAWAL_ACCOUNT_ID;
        String depositAccountNumber = primaryWithdrawal ? "110222222222" : "110111111111";
        Transfer transfer = Transfer.create(
                transactionNumber,
                withdrawalAccountId, depositAccountId, depositAccountNumber, "성춘향",
                amount, 0L,
                TransferType.IMMEDIATE, TransferChannel.BT,
                null, null, null,
                "출금메모", "입금메모",
                transferredAt
        );
        if (status == ProcessResultStatus.SUCCESS) {
            transfer.complete(90_000L, transferredAt);
        } else if (status == ProcessResultStatus.ERROR) {
            transfer.fail("TRF9999", "테스트 실패");
        }
        saveAdapter.save(transfer);
    }

    @Test
    @DisplayName("withdrawalAccountId로 필터링해 다른 계좌의 이체는 목록에 나오지 않는다")
    void search_filtersByWithdrawalAccountId() {
        Page<Transfer> result = adapter.search(WITHDRAWAL_ACCOUNT_ID, null,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), TransferHistorySort.LATEST, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(3)
                .extracting(Transfer::getTransactionNumber)
                .doesNotContain("20260816IT0000000004");
    }

    @Test
    @DisplayName("sort=LATEST면 transferredAt 내림차순으로 반환한다")
    void search_sortsLatestFirst() {
        Page<Transfer> result = adapter.search(WITHDRAWAL_ACCOUNT_ID, null,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), TransferHistorySort.LATEST, PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(Transfer::getTransactionNumber)
                .containsExactly("20260818IT0000000003", "20260815IT0000000002", "20260810IT0000000001");
    }

    @Test
    @DisplayName("sort=OLDEST면 transferredAt 오름차순으로 반환한다")
    void search_sortsOldestFirst() {
        Page<Transfer> result = adapter.search(WITHDRAWAL_ACCOUNT_ID, null,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), TransferHistorySort.OLDEST, PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(Transfer::getTransactionNumber)
                .containsExactly("20260810IT0000000001", "20260815IT0000000002", "20260818IT0000000003");
    }

    @Test
    @DisplayName("status로 필터링하면 해당 상태의 이체만 반환한다")
    void search_filtersByStatus() {
        Page<Transfer> result = adapter.search(WITHDRAWAL_ACCOUNT_ID, ProcessResultStatus.ERROR,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), TransferHistorySort.LATEST, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTransactionNumber()).isEqualTo("20260815IT0000000002");
    }

    @Test
    @DisplayName("fromDate/toDate 범위 밖의 이체는 제외된다")
    void search_excludesOutOfRangeDates() {
        Page<Transfer> result = adapter.search(WITHDRAWAL_ACCOUNT_ID, null,
                LocalDate.of(2026, 8, 11), LocalDate.of(2026, 8, 17), TransferHistorySort.LATEST, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTransactionNumber()).isEqualTo("20260815IT0000000002");
    }

    @Test
    @DisplayName("toDate 23:59:59와 fromDate 00:00:00 경계의 이체도 포함된다 (inclusive 경계 검증)")
    void search_includesBoundaryTimestamps() {
        seedTransfer("20260801IT0000000010", 5_000L, LocalDateTime.of(2026, 8, 1, 0, 0, 0), ProcessResultStatus.SUCCESS);
        seedTransfer("20260831IT0000000011", 6_000L, LocalDateTime.of(2026, 8, 31, 23, 59, 59), ProcessResultStatus.SUCCESS);
        entityManager.flush();
        entityManager.clear();

        Page<Transfer> result = adapter.search(WITHDRAWAL_ACCOUNT_ID, null,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), TransferHistorySort.LATEST, PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(Transfer::getTransactionNumber)
                .contains("20260801IT0000000010", "20260831IT0000000011");
    }

    @Test
    @DisplayName("페이징이 적용된다")
    void search_appliesPaging() {
        Page<Transfer> result = adapter.search(WITHDRAWAL_ACCOUNT_ID, null,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), TransferHistorySort.LATEST, PageRequest.of(1, 2));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getTotalPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("집계는 SUCCESS/ERROR 건수·금액만 잡고 PROCESSING은 제외한다")
    void summarize_excludesProcessing() {
        TransferHistoryAggregate aggregate = adapter.summarize(WITHDRAWAL_ACCOUNT_ID, null,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertThat(aggregate.successCount()).isEqualTo(1L);
        assertThat(aggregate.successAmount()).isEqualTo(10_000L);
        assertThat(aggregate.errorCount()).isEqualTo(1L);
        assertThat(aggregate.errorAmount()).isEqualTo(20_000L);
    }

    @Test
    @DisplayName("집계 대상이 없으면 0으로 채워진 집계를 반환한다")
    void summarize_returnsZero_whenNoMatch() {
        TransferHistoryAggregate aggregate = adapter.summarize(WITHDRAWAL_ACCOUNT_ID, null,
                LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 31));

        assertThat(aggregate.successCount()).isZero();
        assertThat(aggregate.successAmount()).isZero();
        assertThat(aggregate.errorCount()).isZero();
        assertThat(aggregate.errorAmount()).isZero();
    }
}
