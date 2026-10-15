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
    void replace_savesOneRowPerAccount() {
        adapter.replaceForBusinessDate(
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
    @DisplayName("같은 영업일을 다시 저장하면 그날 행은 마지막 결과로 바뀐다")
    void replace_sameDate_keepsOnlyLatestRun() {
        adapter.replaceForBusinessDate(
                BUSINESS_DATE,
                List.of(
                        new LedgerReconciliationMismatch(ACCOUNT_A, 1_000L, 900L),
                        new LedgerReconciliationMismatch(ACCOUNT_B, 0L, 50L)),
                FIRST_RUN_AT);

        // A는 다시 나오고 값이 바뀌었다(유니크 키 충돌 없이 교체돼야 한다), B는 해소, C는 새로 생겼다
        adapter.replaceForBusinessDate(
                BUSINESS_DATE,
                List.of(
                        new LedgerReconciliationMismatch(ACCOUNT_A, 1_000L, 800L),
                        new LedgerReconciliationMismatch(ACCOUNT_C, 300L, 0L)),
                SECOND_RUN_AT);

        assertThat(rowsOf(BUSINESS_DATE))
                .extracting(
                        LedgerReconciliationMismatchJpaEntity::getAccountId,
                        LedgerReconciliationMismatchJpaEntity::getAccountBalance,
                        LedgerReconciliationMismatchJpaEntity::getDetectedAt)
                .containsExactly(tuple(ACCOUNT_A, 800L, SECOND_RUN_AT), tuple(ACCOUNT_C, 0L, SECOND_RUN_AT));
    }

    @Test
    @DisplayName("불일치 0건으로 다시 저장하면 그날 행이 없어진다")
    void replace_emptyList_clearsThatDate() {
        adapter.replaceForBusinessDate(
                BUSINESS_DATE, List.of(new LedgerReconciliationMismatch(ACCOUNT_A, 1_000L, 900L)), FIRST_RUN_AT);

        adapter.replaceForBusinessDate(BUSINESS_DATE, List.of(), SECOND_RUN_AT);

        assertThat(rowsOf(BUSINESS_DATE)).isEmpty();
    }

    @Test
    @DisplayName("다른 영업일의 행은 건드리지 않는다")
    void replace_doesNotTouchOtherDates() {
        adapter.replaceForBusinessDate(
                OTHER_BUSINESS_DATE,
                List.of(new LedgerReconciliationMismatch(ACCOUNT_A, 1_000L, 900L)),
                FIRST_RUN_AT.minusDays(1));

        adapter.replaceForBusinessDate(BUSINESS_DATE, List.of(), FIRST_RUN_AT);

        assertThat(rowsOf(OTHER_BUSINESS_DATE))
                .extracting(LedgerReconciliationMismatchJpaEntity::getAccountId)
                .containsExactly(ACCOUNT_A);
    }

    @Test
    @DisplayName("가장 최근 영업일을 찾는다")
    void findLatestBusinessDate_returnsMaxDate() {
        adapter.replaceForBusinessDate(
                OTHER_BUSINESS_DATE,
                List.of(new LedgerReconciliationMismatch(ACCOUNT_A, 1_000L, 900L)),
                FIRST_RUN_AT.minusDays(1));
        adapter.replaceForBusinessDate(
                BUSINESS_DATE, List.of(new LedgerReconciliationMismatch(ACCOUNT_B, 0L, 50L)), FIRST_RUN_AT);
        flushAndClear();

        assertThat(adapter.findLatestBusinessDate()).contains(BUSINESS_DATE);
    }

    @Test
    @DisplayName("영업일 목록을 계좌 ID 오름차순 페이지로 돌려주고 건수를 센다")
    void findByBusinessDate_pagesByAccountId() {
        adapter.replaceForBusinessDate(
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
        adapter.replaceForBusinessDate(
                BUSINESS_DATE,
                List.of(
                        new LedgerReconciliationMismatch(ACCOUNT_C, 300L, 0L),
                        new LedgerReconciliationMismatch(ACCOUNT_A, 1_000L, 900L),
                        new LedgerReconciliationMismatch(ACCOUNT_B, 0L, 50L)),
                FIRST_RUN_AT);
        adapter.replaceForBusinessDate(
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
