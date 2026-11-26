package com.shinhan.corebank.transfer.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.transfer.api.TransferHoldReleaseMode;
import com.shinhan.corebank.transfer.application.port.in.TransferHistorySort;
import com.shinhan.corebank.transfer.application.port.in.TransferHistoryStatus;
import com.shinhan.corebank.transfer.application.port.out.TransferHistoryAggregate;
import com.shinhan.corebank.transfer.application.port.out.TransferHistoryRecord;
import com.shinhan.corebank.transfer.domain.Transfer;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferHoldStatus;
import com.shinhan.corebank.transfer.domain.TransferSourceType;
import com.shinhan.corebank.transfer.domain.TransferType;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class TransferHistoryQueryPersistenceAdapterTest extends IntegrationTestSupport {

    private static final LocalDate TRADE_DATE = LocalDate.of(2026, 8, 12);

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
        seedTransfer("20260818IT0000000003", 30_000L, LocalDateTime.of(2026, 8, 18, 9, 0), ProcessResultStatus.TIMEOUT);
        // 다른 계좌(202) 소유 이체 — withdrawalAccountId 필터 검증용. 202는 픽스처상 입금 전용이지만
        // 여기서는 단순히 "다른 출금계좌"를 흉내내는 용도로 withdrawalAccountId만 다르게 저장한다.
        seedTransferForAccount(
                202L,
                "20260816IT0000000004",
                40_000L,
                LocalDateTime.of(2026, 8, 16, 9, 0),
                ProcessResultStatus.SUCCESS);

        entityManager.flush();
        entityManager.clear();
    }

    private void seedTransfer(
            String transactionNumber, long amount, LocalDateTime transferredAt, ProcessResultStatus status) {
        seedTransferForAccount(WITHDRAWAL_ACCOUNT_ID, transactionNumber, amount, transferredAt, status);
    }

    private void seedTransferForAccount(
            Long withdrawalAccountId,
            String transactionNumber,
            long amount,
            LocalDateTime transferredAt,
            ProcessResultStatus status) {
        // withdrawalAccountId=202(픽스처상 입금 전용 계좌)로 "다른 계좌" 이체를 흉내낼 때
        // depositAccountId를 202로 고정하면 출금=입금 계좌가 같아져 Transfer.create()의
        // requireDifferentAccounts 검증(SAME_ACCOUNT_TRANSFER)에 걸린다. 두 계좌뿐인
        // 픽스처에서 "출금계좌가 아닌 쪽"으로 입금 대상을 맞바꿔 피한다.
        boolean primaryWithdrawal = withdrawalAccountId.equals(WITHDRAWAL_ACCOUNT_ID);
        Long depositAccountId = primaryWithdrawal ? 202L : WITHDRAWAL_ACCOUNT_ID;
        String depositAccountNumber = primaryWithdrawal ? "110222222222" : "110111111111";
        Transfer transfer = Transfer.create(
                transactionNumber,
                withdrawalAccountId,
                depositAccountId,
                depositAccountNumber,
                "성춘향",
                amount,
                0L,
                TransferType.IMMEDIATE,
                TransferChannel.BT,
                null,
                null,
                null,
                "출금메모",
                "입금메모",
                transferredAt,
                TRADE_DATE);
        if (status == ProcessResultStatus.SUCCESS) {
            transfer.complete(90_000L, transferredAt);
        } else if (status == ProcessResultStatus.ERROR) {
            transfer.fail("TRF9999", "테스트 실패");
        } else if (status == ProcessResultStatus.TIMEOUT) {
            transfer.timeout();
        }
        saveAdapter.save(transfer);
    }

    @Test
    @DisplayName("withdrawalAccountId로 필터링해 다른 계좌의 이체는 목록에 나오지 않는다")
    void search_filtersByWithdrawalAccountId() {
        Page<TransferHistoryRecord> result = adapter.search(
                WITHDRAWAL_ACCOUNT_ID,
                null,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                TransferHistorySort.LATEST,
                PageRequest.of(0, 10));

        assertThat(result.getContent())
                .hasSize(3)
                .extracting(TransferHistoryRecord::transactionNumber)
                .doesNotContain("20260816IT0000000004");
    }

    @Test
    @DisplayName("sort=LATEST면 transferredAt 내림차순으로 반환한다")
    void search_sortsLatestFirst() {
        Page<TransferHistoryRecord> result = adapter.search(
                WITHDRAWAL_ACCOUNT_ID,
                null,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                TransferHistorySort.LATEST,
                PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(TransferHistoryRecord::transactionNumber)
                .containsExactly("20260818IT0000000003", "20260815IT0000000002", "20260810IT0000000001");
    }

    @Test
    @DisplayName("sort=OLDEST면 transferredAt 오름차순으로 반환한다")
    void search_sortsOldestFirst() {
        Page<TransferHistoryRecord> result = adapter.search(
                WITHDRAWAL_ACCOUNT_ID,
                null,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                TransferHistorySort.OLDEST,
                PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(TransferHistoryRecord::transactionNumber)
                .containsExactly("20260810IT0000000001", "20260815IT0000000002", "20260818IT0000000003");
    }

    @Test
    @DisplayName("status로 필터링하면 해당 상태의 이체만 반환한다")
    void search_filtersByStatus() {
        Page<TransferHistoryRecord> result = adapter.search(
                WITHDRAWAL_ACCOUNT_ID,
                TransferHistoryStatus.ERROR,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                TransferHistorySort.LATEST,
                PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).transactionNumber()).isEqualTo("20260815IT0000000002");
    }

    @Test
    @DisplayName("fromDate/toDate 범위 밖의 이체는 제외된다")
    void search_excludesOutOfRangeDates() {
        Page<TransferHistoryRecord> result = adapter.search(
                WITHDRAWAL_ACCOUNT_ID,
                null,
                LocalDate.of(2026, 8, 11),
                LocalDate.of(2026, 8, 17),
                TransferHistorySort.LATEST,
                PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).transactionNumber()).isEqualTo("20260815IT0000000002");
    }

    @Test
    @DisplayName("toDate 23:59:59와 fromDate 00:00:00 경계의 이체도 포함된다 (inclusive 경계 검증)")
    void search_includesBoundaryTimestamps() {
        seedTransfer(
                "20260801IT0000000010", 5_000L, LocalDateTime.of(2026, 8, 1, 0, 0, 0), ProcessResultStatus.SUCCESS);
        seedTransfer(
                "20260831IT0000000011", 6_000L, LocalDateTime.of(2026, 8, 31, 23, 59, 59), ProcessResultStatus.SUCCESS);
        entityManager.flush();
        entityManager.clear();

        Page<TransferHistoryRecord> result = adapter.search(
                WITHDRAWAL_ACCOUNT_ID,
                null,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                TransferHistorySort.LATEST,
                PageRequest.of(0, 20));

        assertThat(result.getContent())
                .extracting(TransferHistoryRecord::transactionNumber)
                .contains("20260801IT0000000010", "20260831IT0000000011");
    }

    @Test
    @DisplayName("페이징이 적용된다")
    void search_appliesPaging() {
        Page<TransferHistoryRecord> result = adapter.search(
                WITHDRAWAL_ACCOUNT_ID,
                null,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                TransferHistorySort.LATEST,
                PageRequest.of(1, 2));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getTotalPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("Pageable.unpaged()로 조회하면 조건에 맞는 전체 건을 한 페이지로 반환한다")
    void search_unpaged_returnsAllMatchingRows() {
        Page<TransferHistoryRecord> result = adapter.search(
                WITHDRAWAL_ACCOUNT_ID,
                null,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                TransferHistorySort.LATEST,
                org.springframework.data.domain.Pageable.unpaged());

        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getTotalPages()).isEqualTo(1);
        assertThat(result.getNumber()).isZero();
    }

    @Test
    @DisplayName("집계는 SUCCESS/ERROR 건수·금액만 잡고 TIMEOUT은 제외한다")
    void summarize_excludesTimeout() {
        TransferHistoryAggregate aggregate =
                adapter.summarize(WITHDRAWAL_ACCOUNT_ID, null, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertThat(aggregate.successCount()).isEqualTo(1L);
        assertThat(aggregate.successAmount()).isEqualTo(10_000L);
        assertThat(aggregate.errorCount()).isEqualTo(1L);
        assertThat(aggregate.errorAmount()).isEqualTo(20_000L);
    }

    @Test
    @DisplayName("집계 대상이 없으면 0으로 채워진 집계를 반환한다")
    void summarize_returnsZero_whenNoMatch() {
        TransferHistoryAggregate aggregate =
                adapter.summarize(WITHDRAWAL_ACCOUNT_ID, null, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 31));

        assertThat(aggregate.successCount()).isZero();
        assertThat(aggregate.successAmount()).isZero();
        assertThat(aggregate.errorCount()).isZero();
        assertThat(aggregate.errorAmount()).isZero();
    }

    @Test
    @DisplayName("정책 A·B 보류는 거래번호 없이 HELD 이력으로 함께 조회된다")
    void search_includesPolicyAHeldAndPolicyBHeld() {
        Long policyA = seedHold("A", TransferHoldStatus.HELD, LocalDateTime.of(2026, 8, 19, 9, 0), 500_000L);
        Long policyB = seedHold("B", TransferHoldStatus.HELD, LocalDateTime.of(2026, 8, 19, 9, 1), 1_000_000L);
        entityManager.flush();
        entityManager.clear();

        Page<TransferHistoryRecord> result = adapter.search(
                WITHDRAWAL_ACCOUNT_ID,
                TransferHistoryStatus.HELD,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                TransferHistorySort.LATEST,
                PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(TransferHistoryRecord::historyId)
                .containsExactly("HOLD:" + policyB, "HOLD:" + policyA);
        assertThat(result.getContent()).allSatisfy(item -> {
            assertThat(item.status()).isEqualTo(TransferHistoryStatus.HELD);
            assertThat(item.transactionNumber()).isNull();
            assertThat(item.transferHoldId()).isNotNull();
            assertThat(item.payeeName()).isNull();
        });
    }

    @Test
    @DisplayName("실행 선점 중인 보류는 외부 이체내역에서 HELD로 계속 조회된다")
    void search_mapsExecutingHoldToHeld() {
        Long holdId = seedHold("A", TransferHoldStatus.EXECUTING, LocalDateTime.of(2026, 8, 19, 9, 0), 500_000L);
        entityManager.flush();
        entityManager.clear();

        Page<TransferHistoryRecord> result = adapter.search(
                WITHDRAWAL_ACCOUNT_ID,
                TransferHistoryStatus.HELD,
                LocalDate.of(2026, 8, 19),
                LocalDate.of(2026, 8, 19),
                TransferHistorySort.LATEST,
                PageRequest.of(0, 10));

        assertThat(result.getContent()).singleElement().satisfies(item -> {
            assertThat(item.historyId()).isEqualTo("HOLD:" + holdId);
            assertThat(item.status()).isEqualTo(TransferHistoryStatus.HELD);
        });
    }

    @Test
    @DisplayName("관리자 거절된 보류는 같은 historyId로 REJECTED 조회되고 정상·오류 집계에서 제외된다")
    void search_mapsRejectedHoldWithoutAffectingSummary() {
        Long holdId = seedHold("A", TransferHoldStatus.REJECTED, LocalDateTime.of(2026, 8, 19, 9, 0), 500_000L);
        entityManager.flush();
        entityManager.clear();

        Page<TransferHistoryRecord> result = adapter.search(
                WITHDRAWAL_ACCOUNT_ID,
                TransferHistoryStatus.REJECTED,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                TransferHistorySort.LATEST,
                PageRequest.of(0, 10));
        TransferHistoryAggregate aggregate = adapter.summarize(
                WITHDRAWAL_ACCOUNT_ID,
                TransferHistoryStatus.REJECTED,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31));

        assertThat(result.getContent()).singleElement().satisfies(item -> {
            assertThat(item.historyId()).isEqualTo("HOLD:" + holdId);
            assertThat(item.status()).isEqualTo(TransferHistoryStatus.REJECTED);
            assertThat(item.transactionNumber()).isNull();
        });
        assertThat(aggregate).isEqualTo(TransferHistoryAggregate.empty());
    }

    @Test
    @DisplayName("승인 실행이 이체 행 생성 전에 실패한 보류는 거래번호 없는 ERROR로 조회·집계된다")
    void search_mapsFailedHoldToError() {
        Long holdId = seedHold("B", TransferHoldStatus.FAILED, LocalDateTime.of(2026, 8, 19, 9, 0), 1_000_000L);
        entityManager
                .createNativeQuery("UPDATE transfer_hold SET reason_code = 'TRF0303' WHERE transfer_hold_id = :holdId")
                .setParameter("holdId", holdId)
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        Page<TransferHistoryRecord> result = adapter.search(
                WITHDRAWAL_ACCOUNT_ID,
                TransferHistoryStatus.ERROR,
                LocalDate.of(2026, 8, 19),
                LocalDate.of(2026, 8, 19),
                TransferHistorySort.LATEST,
                PageRequest.of(0, 10));
        TransferHistoryAggregate aggregate = adapter.summarize(
                WITHDRAWAL_ACCOUNT_ID,
                TransferHistoryStatus.ERROR,
                LocalDate.of(2026, 8, 19),
                LocalDate.of(2026, 8, 19));

        assertThat(result.getContent()).singleElement().satisfies(item -> {
            assertThat(item.historyId()).isEqualTo("HOLD:" + holdId);
            assertThat(item.status()).isEqualTo(TransferHistoryStatus.ERROR);
            assertThat(item.transactionNumber()).isNull();
            assertThat(item.errorCode()).isEqualTo("TRF0303");
        });
        assertThat(aggregate.errorCount()).isEqualTo(1);
        assertThat(aggregate.errorAmount()).isEqualTo(1_000_000L);
    }

    @Test
    @DisplayName("승인 실행으로 HELD source transfer가 생기면 hold는 숨기고 실제 SUCCESS 한 행만 표시한다")
    void search_deduplicatesExecutedHoldAgainstLinkedTransfer() {
        LocalDateTime heldAt = LocalDateTime.of(2026, 8, 19, 9, 0);
        Long holdId = seedHold("B", TransferHoldStatus.EXECUTED, heldAt, 1_000_000L);
        Transfer linked = Transfer.create(
                "20260819IT0000000099",
                WITHDRAWAL_ACCOUNT_ID,
                202L,
                "110222222222",
                "성춘향",
                1_000_000L,
                0L,
                TransferType.IMMEDIATE,
                TransferChannel.BT,
                TransferSourceType.HELD,
                holdId,
                heldAt.toLocalDate(),
                null,
                null,
                heldAt,
                heldAt.toLocalDate());
        linked.complete(90_000L, heldAt);
        saveAdapter.save(linked);
        entityManager.flush();
        entityManager.clear();

        Page<TransferHistoryRecord> result = adapter.search(
                WITHDRAWAL_ACCOUNT_ID,
                null,
                LocalDate.of(2026, 8, 19),
                LocalDate.of(2026, 8, 19),
                TransferHistorySort.LATEST,
                PageRequest.of(0, 10));

        assertThat(result.getContent()).singleElement().satisfies(item -> {
            assertThat(item.historyId()).isEqualTo("HOLD:" + holdId);
            assertThat(item.transferHoldId()).isEqualTo(holdId);
            assertThat(item.transactionNumber()).isEqualTo("20260819IT0000000099");
            assertThat(item.status()).isEqualTo(TransferHistoryStatus.SUCCESS);
        });
    }

    @Test
    @DisplayName("transfer와 hold 통합 결과를 기준으로 페이징·totalCount·totalPages를 계산한다")
    void search_pagesUnifiedResults() {
        seedHold("A", TransferHoldStatus.HELD, LocalDateTime.of(2026, 8, 19, 9, 0), 500_000L);
        seedHold("B", TransferHoldStatus.REJECTED, LocalDateTime.of(2026, 8, 20, 9, 0), 1_000_000L);
        entityManager.flush();
        entityManager.clear();

        Page<TransferHistoryRecord> first = adapter.search(
                WITHDRAWAL_ACCOUNT_ID,
                null,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                TransferHistorySort.LATEST,
                PageRequest.of(0, 2));

        assertThat(first.getContent()).hasSize(2);
        assertThat(first.getTotalElements()).isEqualTo(5);
        assertThat(first.getTotalPages()).isEqualTo(3);
        assertThat(first.getContent())
                .extracting(TransferHistoryRecord::status)
                .containsExactly(TransferHistoryStatus.REJECTED, TransferHistoryStatus.HELD);
    }

    private Long seedHold(String policyCode, TransferHoldStatus status, LocalDateTime heldAt, long amount) {
        TransferHoldJpaEntity hold = TransferHoldJpaEntity.builder()
                .customerId(1L)
                .withdrawalAccountId(WITHDRAWAL_ACCOUNT_ID)
                .depositAccountNumber("110222222222")
                .amount(amount)
                .channel(TransferChannel.BT)
                .policyCode(policyCode)
                .releaseMode(TransferHoldReleaseMode.ON_APPROVAL)
                .status(status)
                .authenticatedAt(heldAt)
                .heldAt(heldAt)
                .build();
        entityManager.persist(hold);
        entityManager.flush();
        return hold.getTransferHoldId();
    }
}
