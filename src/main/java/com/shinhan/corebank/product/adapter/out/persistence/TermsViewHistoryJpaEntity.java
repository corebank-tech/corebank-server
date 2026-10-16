package com.shinhan.corebank.product.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 상품 약관 열람 이력 (#580). 쓰기는 TermsViewHistoryJpaRepository의 upsert만 쓴다.
@Entity
@Table(name = "terms_view_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TermsViewHistoryJpaEntity {

    @EmbeddedId
    private TermsViewHistoryJpaEntityId id;

    @Column(name = "viewed_at", nullable = false)
    private LocalDateTime viewedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}
