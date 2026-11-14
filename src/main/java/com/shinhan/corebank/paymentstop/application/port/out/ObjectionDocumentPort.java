package com.shinhan.corebank.paymentstop.application.port.out;

import com.shinhan.corebank.paymentstop.domain.document.DocumentOcr;
import com.shinhan.corebank.paymentstop.domain.document.ObjectionDocument;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// 첨부 서류 메타데이터와 OCR 결과 저장소. 파일 본문은 DocumentStoragePort 가 맡는다.
public interface ObjectionDocumentPort {

    /** 문서 행을 넣고 ID 와 저장소 키를 채워 돌려준다. 증빙이면 OCR 대기 행도 넣는다. */
    ObjectionDocument saveDocument(ObjectionDocument document);

    List<ObjectionDocument> findDocuments(long objectionId);

    int countDocuments(long objectionId);

    Optional<ObjectionDocument> findDocument(long objectionId, long documentId);

    Optional<DocumentOcr> findOcr(long documentId);

    Map<Long, DocumentOcr> findOcrs(Collection<Long> documentIds);

    void saveOcr(DocumentOcr ocr);
}
