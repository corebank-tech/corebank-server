package com.shinhan.corebank.paymentstop.adapter.out.persistence;

import com.shinhan.corebank.paymentstop.domain.document.DocumentKind;
import com.shinhan.corebank.paymentstop.domain.document.ObjectionDocument;
import com.shinhan.corebank.paymentstop.domain.document.OcrStatus;
import com.shinhan.corebank.paymentstop.domain.document.UploaderRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "objection_document")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "document_id")
    private Long documentId;

    @Column(name = "objection_id", nullable = false)
    private Long objectionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 10)
    private DocumentKind kind;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 30)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "sha256", nullable = false, columnDefinition = "CHAR(64)")
    private String sha256;

    @Column(name = "storage_key", nullable = false, length = 100)
    private String storageKey;

    @Column(name = "encrypted", nullable = false)
    private boolean encrypted;

    @Column(name = "round", nullable = false, columnDefinition = "TINYINT")
    private Byte round;

    @Column(name = "uploaded_by", nullable = false)
    private Long uploadedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "uploader_role", nullable = false, length = 10)
    private UploaderRole uploaderRole;

    @Column(name = "uploaded_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime uploadedAt;

    // 키는 행 ID 로 만들므로 먼저 넣고 바로 채운다
    static DocumentJpaEntity of(ObjectionDocument document) {
        DocumentJpaEntity entity = new DocumentJpaEntity();
        entity.objectionId = document.objectionId();
        entity.kind = document.kind();
        entity.fileName = document.fileName();
        entity.contentType = document.contentType();
        entity.sizeBytes = document.sizeBytes();
        entity.sha256 = document.sha256();
        entity.storageKey = "pending";
        entity.encrypted = document.encrypted();
        entity.round = (byte) document.round();
        entity.uploadedBy = document.uploadedBy();
        entity.uploaderRole = document.uploaderRole();
        entity.uploadedAt = document.uploadedAt();
        return entity;
    }

    void assignStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    ObjectionDocument toDomain(OcrStatus ocrStatus) {
        return new ObjectionDocument(
                documentId,
                objectionId,
                kind,
                fileName,
                contentType,
                sizeBytes,
                sha256,
                storageKey,
                encrypted,
                round,
                uploadedBy,
                uploaderRole,
                uploadedAt,
                ocrStatus);
    }
}
