package com.shinhan.corebank.paymentstop.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "review_draft")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewDraftJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_draft_id")
    private Long reviewDraftId;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "objection_id", nullable = false)
    private Long objectionId;

    @Column(name = "branch", nullable = false, length = 16)
    private String branch;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content", nullable = false)
    private Map<String, Object> content;

    @Column(name = "submitted_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime submittedAt;

    static ReviewDraftJpaEntity of(
            long runId, long objectionId, String branch, Map<String, Object> content, LocalDateTime submittedAt) {
        ReviewDraftJpaEntity entity = new ReviewDraftJpaEntity();
        entity.runId = runId;
        entity.objectionId = objectionId;
        entity.branch = branch;
        entity.content = content;
        entity.submittedAt = submittedAt;
        return entity;
    }
}
