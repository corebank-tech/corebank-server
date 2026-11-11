package com.shinhan.corebank.paymentstop.application.service;

import com.shinhan.corebank.paymentstop.application.port.out.DocumentStoragePort;
import com.shinhan.corebank.paymentstop.application.port.out.ObjectionDocumentPort;
import com.shinhan.corebank.paymentstop.domain.document.DocumentKind;
import com.shinhan.corebank.paymentstop.domain.document.DocumentPolicy;
import com.shinhan.corebank.paymentstop.domain.document.ObjectionDocument;
import com.shinhan.corebank.paymentstop.domain.document.UploadFile;
import com.shinhan.corebank.paymentstop.domain.document.UploaderRole;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 첨부 파일 검사와 저장(D36, D37). 행을 먼저 넣어 ID 로 키를 만든 뒤 파일을 쓴다. 파일 쓰기가 실패하면 트랜잭션이 되돌아가 행이 남지 않는다.
 * 반대로 파일을 쓴 뒤 트랜잭션이 되돌아가면 파일만 남는데, 키가 행 ID 라 다시 쓰이지 않고 조회되지도 않는다.
 */
@Component
@RequiredArgsConstructor
class DocumentStore {

    private final ObjectionDocumentPort documentPort;
    private final DocumentStoragePort storagePort;

    /** 이미 있는 파일과 합쳐 개수와 크기 한도를 본다. 저장 전에 모든 파일을 먼저 검사한다. */
    void validate(Long objectionId, List<UploadFile> files) {
        List<ObjectionDocument> existing = objectionId == null ? List.of() : documentPort.findDocuments(objectionId);
        DocumentPolicy.validate(
                files,
                existing.size(),
                existing.stream().mapToLong(ObjectionDocument::sizeBytes).sum());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<ObjectionDocument> store(
            long objectionId,
            DocumentKind kind,
            List<UploadFile> files,
            int round,
            long uploadedBy,
            UploaderRole role,
            LocalDateTime at) {
        List<ObjectionDocument> saved = new ArrayList<>();
        boolean encrypt = kind == DocumentKind.ID_COPY;
        for (UploadFile file : files) {
            byte[] content = file.content();
            ObjectionDocument document = documentPort.saveDocument(new ObjectionDocument(
                    null,
                    objectionId,
                    kind,
                    DocumentPolicy.fileName(file.originalName()),
                    DocumentPolicy.contentType(content),
                    content.length,
                    DocumentPolicy.sha256(content),
                    null,
                    encrypt,
                    round,
                    uploadedBy,
                    role,
                    at,
                    null));
            storagePort.write(document.storageKey(), content, encrypt);
            saved.add(document);
        }
        return saved;
    }
}
