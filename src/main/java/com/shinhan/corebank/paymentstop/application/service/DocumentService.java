package com.shinhan.corebank.paymentstop.application.service;

import com.shinhan.corebank.common.audit.AccessReason;
import com.shinhan.corebank.common.audit.AuditLogService;
import com.shinhan.corebank.common.audit.PersonalDataAccessEventType;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.paymentstop.application.port.in.AdminActor;
import com.shinhan.corebank.paymentstop.application.port.in.DocumentUseCase;
import com.shinhan.corebank.paymentstop.application.port.out.DocumentStoragePort;
import com.shinhan.corebank.paymentstop.application.port.out.ObjectionDocumentPort;
import com.shinhan.corebank.paymentstop.application.port.out.PaymentStopRecordPort;
import com.shinhan.corebank.paymentstop.domain.Objection;
import com.shinhan.corebank.paymentstop.domain.document.DocumentKind;
import com.shinhan.corebank.paymentstop.domain.document.DocumentOcr;
import com.shinhan.corebank.paymentstop.domain.document.ObjectionDocument;
import com.shinhan.corebank.paymentstop.domain.document.UploadFile;
import com.shinhan.corebank.paymentstop.domain.document.UploaderRole;
import com.shinhan.corebank.paymentstop.domain.exception.PaymentStopErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 서류 업로드와 조회. 영업점 대리 접수도 고객 접수와 같은 저장소를 쓴다(D34). */
@Service
@RequiredArgsConstructor
public class DocumentService implements DocumentUseCase {

    private final PaymentStopRecordPort recordPort;
    private final ObjectionDocumentPort documentPort;
    private final DocumentStoragePort storagePort;
    private final DocumentStore documentStore;
    private final AuditLogService auditLogService;
    private final Clock clock;

    @Override
    @Transactional
    public List<ObjectionDocument> upload(
            long objectionId, DocumentKind kind, List<UploadFile> files, AdminActor actor) {
        // 같은 이의제기의 동시 업로드가 한도를 함께 넘지 않게 이의제기 행을 잠근다
        Objection objection = recordPort
                .findObjectionForUpdate(objectionId)
                .orElseThrow(() -> new BusinessException(PaymentStopErrorCode.OBJECTION_NOT_FOUND));
        objection.requireNotDecided();
        documentStore.validate(objectionId, files);
        return documentStore.store(
                objectionId,
                kind,
                files,
                objection.getSupplementRound(),
                actor.adminCustomerId(),
                UploaderRole.ADMIN,
                LocalDateTime.now(clock));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ObjectionDocument> list(long objectionId) {
        requireObjection(objectionId);
        return documentPort.findDocuments(objectionId);
    }

    // 조회 감사 행을 같은 트랜잭션에 쓴다
    @Override
    @Transactional
    public DocumentContent content(long objectionId, long documentId, AdminActor actor, AccessReason accessReason) {
        ObjectionDocument document = document(objectionId, documentId);
        auditLogService.recordPersonalDataAccess(
                actor.adminCustomerId(),
                PersonalDataAccessEventType.OBJECTION_DOCUMENT_VIEW,
                actor.requestIp(),
                true,
                Map.of(
                        "objectionId",
                        objectionId,
                        "documentId",
                        documentId,
                        "kind",
                        document.kind().name()),
                accessReason);
        return new DocumentContent(document, storagePort.read(document.storageKey(), document.encrypted()));
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentOcr ocr(long objectionId, long documentId) {
        ObjectionDocument document = document(objectionId, documentId);
        // 신분증 사본은 OCR 하지 않는다(D37)
        return documentPort
                .findOcr(document.documentId())
                .orElseThrow(() -> new BusinessException(PaymentStopErrorCode.DOCUMENT_NOT_FOUND));
    }

    private ObjectionDocument document(long objectionId, long documentId) {
        requireObjection(objectionId);
        return documentPort
                .findDocument(objectionId, documentId)
                .orElseThrow(() -> new BusinessException(PaymentStopErrorCode.DOCUMENT_NOT_FOUND));
    }

    private void requireObjection(long objectionId) {
        if (recordPort.findObjection(objectionId).isEmpty()) {
            throw new BusinessException(PaymentStopErrorCode.OBJECTION_NOT_FOUND);
        }
    }
}
