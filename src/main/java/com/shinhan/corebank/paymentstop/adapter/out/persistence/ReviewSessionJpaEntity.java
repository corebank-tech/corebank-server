package com.shinhan.corebank.paymentstop.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "objection_review_session")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewSessionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_session_id")
    private Long reviewSessionId;

    @Column(name = "objection_id", nullable = false)
    private Long objectionId;

    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Column(name = "started_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime startedAt;

    static ReviewSessionJpaEntity of(long objectionId, long adminId, LocalDateTime startedAt) {
        ReviewSessionJpaEntity entity = new ReviewSessionJpaEntity();
        entity.objectionId = objectionId;
        entity.adminId = adminId;
        entity.startedAt = startedAt;
        return entity;
    }
}
