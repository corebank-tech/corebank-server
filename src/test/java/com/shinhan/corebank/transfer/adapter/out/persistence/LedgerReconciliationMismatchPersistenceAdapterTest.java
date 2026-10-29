package com.shinhan.corebank.transfer.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.transfer.application.port.in.LedgerReconciliationMismatch;
import com.shinhan.corebank.transfer.application.port.in.LedgerReconciliationMismatchQueryUseCase.LedgerReconciliationMismatchView;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class LedgerReconciliationMismatchPersistenceAdapterTest extends IntegrationTestSupport {

    // 시드·다른 테스트와 겹치지 않는 전용 대역
    private static final Long ACCOUNT_A = 8_813_001L;
    private static final Long ACCOUNT_B = 8_813_002L;
    private static final Long ACCOUNT_C = 8_813_003L;
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2099, 1, 5);
    private static final LocalDate OTHER_BUSINESS_DATE = LocalDate.of(2099, 1, 4);
    private static final LocalDateTime FIRST_RUN_AT = LocalDateTime.of(2099, 1, 5, 23, 31);
    private static final LocalDateTime SECOND_RUN_AT = LocalDateTime.of(2099, 1, 6, 9, 0);

    @Autowired
    private LedgerReconciliationMismatchPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("영업일의 불일치를 계좌별 1행으로 저장한다")
    void saveIfAbsent_savesOneRowPerAccount() {
        adapter.saveIfAbsent(
                BUSINESS_DATE,
                List.of(
                        new LedgerReconciliationMismatch(ACCOUNT_A, 1_000L, 900L),
                        new LedgerReconciliationMismatch(ACCOUNT_B, 0L, 50L)),
                FIRST_RUN_AT);

        assertThat(rowsOf(BUSINESS_DATE))
                .extracting(
                        LedgerReconciliationMismatchJpaEntity::getAccountId,
                        LedgerReconciliationMismatchJpaEntity::getLedgerBalance,
                        LedgerReconciliationMismatchJpaEntity::getAccountBalance,
                        LedgerReconciliationMismatchJpaEntity::getDetectedAt)
                .containsExactly(tuple(ACCOUNT_A, 1_000L, 900L, FIRST_RUN_AT), tuple(ACCOUNT_B, 0L, 50L, FIRST_RUN_AT));
    }

    @Test
    @DisplayName("같은 영업일에 행이 이미 있으면 다시 저장해도 처음 목록이 그대로 남는다(#749)")
    void saveIfAbsent_sameDate_keepsFirstRun() {
        boolean first = adapter.saveIfAbsent(
                BUSINESS_DATE,
                List.of(
                        new LedgerReconciliationMismatch(ACCOUNT_A, 1_000L, 900L),
                        new LedgerReconciliationMismatch(ACCOUNT_B, 0L, 50L)),
                FIRST_RUN_AT);
        flushAndClear();

        // 재개 대사: A는 값이 바뀌었고 B는 해소, C는 새로 생겼다
        boolean second = adapter.saveIfAbsent(
                BUSINESS_DATE,
                List.of(
                        new LedgerReconciliationMismatch(ACCOUNT_A, 1_000L, 800L),
                        new LedgerReconciliationMismatch(ACCOUNT_C, 300L, 0L)),
                SECOND_RUN_AT);

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(rowsOf(BUSINESS_DATE))
                .extracting(
                        LedgerReconciliationMismatchJpaEntity::getAccountId,
                        LedgerReconciliationMismatchJpaEntity::getAccountBalance,
                        LedgerReconciliationMismatchJpaEntity::getDetectedAt)
                .containsExactly(tuple(ACCOUNT_A, 900L, FIRST_RUN_AT), tuple(ACCOUNT_B, 50L, FIRST_RUN_AT));
    }

    @Test
    @DisplayName("불일치 0건으로 다시 저장해도 그날 처음 목록은 지워지지 않는다")
    void saveIfAbsent_emptyListAfterFirstRun_keepsFirstRun() {
        adapter.saveIfAbsent(
                BUSINESS_DATE, List.of(new LedgerReconciliationMismatch(ACCOUNT_A, 1_000L, 900L)), FIRST_RUN_AT);
        flushAndClear();

        adapter.saveIfAbsent(BUSINESS_DATE, List.of(), SECOND_RUN_AT);

        assertThat(rowsOf(BUSINESS_DATE))
                .extracting(LedgerReconciliationMismatchJpaEntity::getAccountId)
                .containsExactly(ACCOUNT_A);
    }

    @Test
    @DisplayName("처음 실행이 0건이라 행이 없던 영업일은 다시 돈 대사 결과를 저장한다")
    void saveIfAbsent_noRowsYet_savesRerun() {
        adapter.saveIfAbsent(BUSINESS_DATE, List.of(), FIRST_RUN_AT);
        flushAndClear();

        boolean saved = adapter.saveIfAbsent(
                BUSINESS_DATE, List.of(new LedgerReconciliationMismatch(ACCOUNT_A, 1_000L, 900L)), SECOND_RUN_AT);

        assertThat(saved).isTrue();
        assertThat(rowsOf(BUSINESS_DATE))
                .extracting(
                        LedgerReconciliationMismatchJpaEntity::getAccountId,
                        LedgerReconciliationMismatchJpaEntity::getDetectedAt)
                .containsExactly(tuple(ACCOUNT_A, SECOND_RUN_AT));
    }

    @Test
    @DisplayName("다른 영업일에 행이 있어도 이 영업일은 저장하고, 다른 영업일 행은 건드리지 않는다")
    void saveIfAbsent_otherDateHasRows_savesThisDate() {
        adapter.saveIfAbsent(
                OTHER_BUSINESS_DATE,
                List.of(new LedgerReconciliationMismatch(ACCOUNT_A, 1_000L, 900L)),
                FIRST_RUN_AT.minusDays(1));
        flushAndClear();

        boolean saved = adapter.saveIfAbsent(
                BUSINESS_DATE, List.of(new LedgerReconciliationMismatch(ACCOUNT_B, 0L, 50L)), FIRST_RUN_AT);

        assertThat(saved).isTrue();
        assertThat(rowsOf(BUSINESS_DATE))
                .extracting(LedgerReconciliationMismatchJpaEntity::getAccountId)
                .containsExactly(ACCOUNT_B);
        assertThat(rowsOf(OTHER_BUSINESS_DATE))
                .extracting(LedgerReconciliationMismatchJpaEntity::getAccountId)
                .containsExactly(ACCOUNT_A);
    }

    @Test
    @DisplayName("가장 최근 영업일을 찾는다")
    void findLatestBusinessDate_returnsMaxDate() {
        adapter.saveIfAbsent(
                OTHER_BUSINESS_DATE,
                List.of(new LedgerReconciliationMismatch(ACCOUNT_A, 1_000L, 900L)),
                FIRST_RUN_AT.minusDays(1));
        adapter.saveIfAbsent(
                BUSINESS_DATE, List.of(new LedgerReconciliationMismatch(ACCOUNT_B, 0L, 50L)), FIRST_RUN_AT);
        flushAndClear();

        assertThat(adapter.findLatestBusinessDate()).contains(BUSINESS_DATE);
    }

    @Test
    @DisplayName("영업일 목록을 계좌 ID 오름차순 페이지로 돌려주고 건수를 센다")
    void findByBusinessDate_pagesByAccountId() {
        adapter.saveIfAbsent(
                BUSINESS_DATE,
                List.of(
                        new LedgerReconciliationMismatch(ACCOUNT_C, 300L, 0L),
                        new LedgerReconciliationMismatch(ACCOUNT_A, 1_000L, 900L),
                        new LedgerReconciliationMismatch(ACCOUNT_B, 0L, 50L)),
                FIRST_RUN_AT);
        flushAndClear();

        Page<LedgerReconciliationMismatchView> firstPage =
                adapter.findByBusinessDate(BUSINESS_DATE, PageRequest.of(0, 2));

        assertThat(firstPage.getContent())
                .extracting(LedgerReconciliationMismatchView::accountId)
                .containsExactly(ACCOUNT_A, ACCOUNT_B);
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(adapter.countByBusinessDate(BUSINESS_DATE)).isEqualTo(3);
        assertThat(adapter.countByBusinessDate(OTHER_BUSINESS_DATE)).isZero();
    }

    @Test
    @DisplayName("Pageable.unpaged()로 조회하면 그 영업일 전체를 계좌 ID 오름차순 한 페이지로 돌려준다")
    void findByBusinessDate_unpaged_returnsAllRowsInOnePage() {
        adapter.saveIfAbsent(
                BUSINESS_DATE,
                List.of(
                        new LedgerReconciliationMismatch(ACCOUNT_C, 300L, 0L),
                        new LedgerReconciliationMismatch(ACCOUNT_A, 1_000L, 900L),
                        new LedgerReconciliationMismatch(ACCOUNT_B, 0L, 50L)),
                FIRST_RUN_AT);
        adapter.saveIfAbsent(
                OTHER_BUSINESS_DATE,
                List.of(new LedgerReconciliationMismatch(ACCOUNT_A, 1_000L, 900L)),
                FIRST_RUN_AT.minusDays(1));
        flushAndClear();

        Page<LedgerReconciliationMismatchView> all = adapter.findByBusinessDate(BUSINESS_DATE, Pageable.unpaged());

        assertThat(all.getContent())
                .extracting(LedgerReconciliationMismatchView::accountId)
                .containsExactly(ACCOUNT_A, ACCOUNT_B, ACCOUNT_C);
        assertThat(all.getTotalElements()).isEqualTo(3);
        assertThat(all.getTotalPages()).isEqualTo(1);
        assertThat(all.getNumber()).isZero();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    // 영속성 컨텍스트 캐시가 아니라 DB 값을 본다
    private List<LedgerReconciliationMismatchJpaEntity> rowsOf(LocalDate businessDate) {
        entityManager.flush();
        entityManager.clear();
        return entityManager
                .createQuery(
                        "SELECT m FROM LedgerReconciliationMismatchJpaEntity m"
                                + " WHERE m.businessDate = :businessDate ORDER BY m.accountId",
                        LedgerReconciliationMismatchJpaEntity.class)
                .setParameter("businessDate", businessDate)
                .getResultList();
    }
}
