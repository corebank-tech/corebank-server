package com.shinhan.corebank.paymentstop.adapter.out.persistence;

import com.shinhan.corebank.paymentstop.domain.document.OcrStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "objection_document_ocr")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentOcrJpaEntity {

    @Id
    @Column(name = "document_id")
    private Long documentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private OcrStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pages")
    private List<Map<String, Object>> pages;

    @Column(name = "engine", length = 40)
    private String engine;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    static DocumentOcrJpaEntity pending(long documentId, LocalDateTime at) {
        DocumentOcrJpaEntity entity = new DocumentOcrJpaEntity();
        entity.documentId = documentId;
        entity.status = OcrStatus.PENDING;
        entity.updatedAt = at;
        return entity;
    }

    void update(OcrStatus status, List<Map<String, Object>> pages, String engine, LocalDateTime at) {
        this.status = status;
        this.pages = pages;
        this.engine = engine;
        this.updatedAt = at;
    }
}
