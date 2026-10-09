package com.shinhan.corebank.product.adapter.out.persistence;

import com.shinhan.corebank.product.application.port.out.TermsView;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface TermsViewHistoryJpaRepository
        extends JpaRepository<TermsViewHistoryJpaEntity, TermsViewHistoryJpaEntityId> {

    // 고객×약관 PK 하나라 "있으면 갱신, 없으면 추가"를 한 문장으로 끝낸다.
    @Modifying
    @Transactional
    @Query(
            value =
                    """
            INSERT INTO terms_view_history (customer_id, terms_id, viewed_at, expires_at)
            VALUES (:customerId, :termsId, :viewedAt, :expiresAt) AS new
            ON DUPLICATE KEY UPDATE viewed_at = new.viewed_at, expires_at = new.expires_at
            """,
            nativeQuery = true)
    int upsert(
            @Param("customerId") Long customerId,
            @Param("termsId") Long termsId,
            @Param("viewedAt") LocalDateTime viewedAt,
            @Param("expiresAt") LocalDateTime expiresAt);

    // 엔티티가 아니라 값으로 읽어 upsert 이전 엔티티가 영속성 컨텍스트에 남아 있어도 영향받지 않는다.
    @Query(
            """
        SELECT new com.shinhan.corebank.product.application.port.out.TermsView(t.viewedAt, t.expiresAt)
          FROM TermsViewHistoryJpaEntity t
         WHERE t.id.customerId = :customerId AND t.id.termsId = :termsId AND t.expiresAt > :now
        """)
    Optional<TermsView> findUsable(
            @Param("customerId") Long customerId, @Param("termsId") Long termsId, @Param("now") LocalDateTime now);

    // 한 번에 다 지우면 긴 트랜잭션이 되어 열람 upsert를 오래 막으므로 나눠서 지운다.
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM terms_view_history WHERE expires_at < :now LIMIT :limit", nativeQuery = true)
    int deleteExpiredBatch(@Param("now") LocalDateTime now, @Param("limit") int limit);
}
