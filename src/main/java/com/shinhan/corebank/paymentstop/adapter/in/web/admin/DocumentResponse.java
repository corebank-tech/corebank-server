package com.shinhan.corebank.paymentstop.adapter.in.web.admin;

import com.shinhan.corebank.paymentstop.domain.document.ObjectionDocument;
import java.time.LocalDateTime;

/** 첨부 서류 목록 행. ocrStatus 는 증빙만 갖고 신분증 사본은 null 이다(OCR 하지 않음, D37). */
public record DocumentResponse(
        long documentId,
        String kind,
        String fileName,
        String contentType,
        long size,
        String sha256,
        int round,
        String uploaderRole,
        LocalDateTime uploadedAt,
        String ocrStatus) {

    public static DocumentResponse from(ObjectionDocument document) {
        return new DocumentResponse(
                document.documentId(),
                document.kind().name(),
                document.fileName(),
                document.contentType(),
                document.sizeBytes(),
                document.sha256(),
                document.round(),
                document.uploaderRole().name(),
                document.uploadedAt(),
                document.ocrStatus() == null ? null : document.ocrStatus().name());
    }
}
