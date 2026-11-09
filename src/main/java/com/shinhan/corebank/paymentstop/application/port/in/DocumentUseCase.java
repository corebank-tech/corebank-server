package com.shinhan.corebank.paymentstop.application.port.in;

import com.shinhan.corebank.common.audit.AccessReason;
import com.shinhan.corebank.paymentstop.domain.document.DocumentKind;
import com.shinhan.corebank.paymentstop.domain.document.DocumentOcr;
import com.shinhan.corebank.paymentstop.domain.document.ObjectionDocument;
import com.shinhan.corebank.paymentstop.domain.document.UploadFile;
import java.util.List;

// 관리자 서류 업로드(영업점 대리 접수)와 조회(PA-12, PA-20).
public interface DocumentUseCase {

    List<ObjectionDocument> upload(long objectionId, DocumentKind kind, List<UploadFile> files, AdminActor actor);

    List<ObjectionDocument> list(long objectionId);

    /** 파일 본문. 신분증 사본이 있을 수 있어 조회 사유를 받고 조회 감사를 남긴다. */
    DocumentContent content(long objectionId, long documentId, AdminActor actor, AccessReason accessReason);

    DocumentOcr ocr(long objectionId, long documentId);

    record DocumentContent(ObjectionDocument document, byte[] content) {}
}
