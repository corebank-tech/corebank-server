package com.shinhan.corebank.paymentstop.domain.document;

import java.time.LocalDateTime;

/**
 * 이의제기 첨부 서류. 파일 본문은 저장소에 두고 여기에는 키와 해시, 종류, 크기만 둔다(D36).
 *
 * @param documentId 저장 전이면 null
 * @param round 0 접수, 1~2 보완 회차
 * @param ocrStatus 증빙의 OCR 상태. 신분증 사본은 OCR 하지 않아 null
 */
public record ObjectionDocument(
        Long documentId,
        long objectionId,
        DocumentKind kind,
        String fileName,
        String contentType,
        long sizeBytes,
        String sha256,
        String storageKey,
        boolean encrypted,
        int round,
        long uploadedBy,
        UploaderRole uploaderRole,
        LocalDateTime uploadedAt,
        OcrStatus ocrStatus) {

    public static String storageKey(long objectionId, long documentId) {
        return objectionId + "/" + documentId;
    }
}
