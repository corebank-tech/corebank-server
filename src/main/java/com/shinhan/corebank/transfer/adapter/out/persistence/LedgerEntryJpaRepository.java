package com.shinhan.corebank.transfer.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryJpaRepository extends JpaRepository<LedgerEntryJpaEntity, LedgerEntryId> {

    List<LedgerEntryJpaEntity> findByTransactionNumber(String transactionNumber);

    /**
     * 지정 기간(fromInclusive~toExclusive)에 원장 기표가 있었던 계좌 ID를 중복 없이 반환한다.
     * 대사 배치가 전 계좌를 매일 전수 대조하지 않고, 전일 기표가 발생한 계좌만 증분 대상으로
     * 삼기 위한 선별 쿼리다.
     */
    @Query(
            """
        SELECT DISTINCT l.accountId
        FROM LedgerEntryJpaEntity l
        WHERE l.occurredAt >= :fromInclusive AND l.occurredAt < :toExclusive
        """)
    List<Long> findDistinctAccountIdsPostedBetween(
            @Param("fromInclusive") LocalDateTime fromInclusive, @Param("toExclusive") LocalDateTime toExclusive);

    /**
     * ledgerEntryId 단건 조회. reversal_id가 가리키는 원거래 원장 행을 occurredAt 없이 조회할 때 사용한다.
     *
     * 주의: ledger_entry의 실제 PK는 (ledger_entry_id, occurred_at) 복합키이고,
     * ledger_entry는 파티션 테이블이라 파티션 키(occurred_at)가 모든 UNIQUE 인덱스에 포함돼야
     * 하는 MySQL 제약 때문에 ledger_entry_id 단독 UNIQUE 인덱스를 DB에 걸 수 없다(다른 파티션에
     * 동일 ledger_entry_id가 들어가는 걸 DB가 막아주지 않는다). 이 메서드가 항상 단건만 반환한다는
     * 전제는 {@link LedgerEntryIdGenerator}로만 ledgerEntryId를 채번한다는 애플리케이션 규율에
     * 의존하며, DB 제약으로 강제되지 않는다. 이 규율이 깨지면 둘 이상의 행이 매칭돼
     * {@code IncorrectResultSizeDataAccessException}이 발생한다.
     */
    Optional<LedgerEntryJpaEntity> findByLedgerEntryId(Long ledgerEntryId);

    /**
     * 계좌별 원장 누적 합계("원장상 잔액")를 계산한다 — 입금은 더하고 출금은 뺀다.
     * account.balance는 조회 성능용 캐시이고 이 값이 진실의 원천이다(#378 대사 배치).
     * 취소(반대기표) 여부와 무관하게 원장 전체를 더한다 — 반대기표도 자신의 금액만큼
     * 신규 행으로 반대 방향 기표되므로, 원거래+반대기표를 함께 더하면 순효과가 0이 되어
     * account.balance와 자연히 일치한다(둘 다 제외하는 것과 결과가 같다).
     */
    @Query(
            """
        SELECT l.accountId AS accountId,
               SUM(CASE WHEN l.direction = com.shinhan.corebank.transfer.domain.LedgerDirection.DEPOSIT
                        THEN l.amount ELSE -l.amount END) AS signedSum
        FROM LedgerEntryJpaEntity l
        WHERE l.accountId IN :accountIds
        GROUP BY l.accountId
        """)
    List<AccountLedgerSumProjection> sumSignedAmountByAccountIds(@Param("accountIds") Collection<Long> accountIds);

    interface AccountLedgerSumProjection {
        Long getAccountId();

        Long getSignedSum();
    }
}
