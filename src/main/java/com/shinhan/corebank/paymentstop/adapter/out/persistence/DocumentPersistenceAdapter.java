package com.shinhan.corebank.paymentstop.adapter.out.persistence;

import com.shinhan.corebank.paymentstop.application.port.out.ObjectionDocumentPort;
import com.shinhan.corebank.paymentstop.domain.document.DocumentKind;
import com.shinhan.corebank.paymentstop.domain.document.DocumentOcr;
import com.shinhan.corebank.paymentstop.domain.document.ObjectionDocument;
import com.shinhan.corebank.paymentstop.domain.document.OcrPage;
import com.shinhan.corebank.paymentstop.domain.document.OcrStatus;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DocumentPersistenceAdapter implements ObjectionDocumentPort {

    private final DocumentJpaRepository documentRepository;
    private final DocumentOcrJpaRepository ocrRepository;

    @Override
    public ObjectionDocument saveDocument(ObjectionDocument document) {
        DocumentJpaEntity saved = documentRepository.saveAndFlush(DocumentJpaEntity.of(document));
        saved.assignStorageKey(ObjectionDocument.storageKey(saved.getObjectionId(), saved.getDocumentId()));
        OcrStatus ocrStatus = null;
        // 신분증 사본은 OCR 하지 않는다(D37)
        if (document.kind() == DocumentKind.EVIDENCE) {
            ocrRepository.save(DocumentOcrJpaEntity.pending(saved.getDocumentId(), document.uploadedAt()));
            ocrStatus = OcrStatus.PENDING;
        }
        return saved.toDomain(ocrStatus);
    }

    @Override
    public List<ObjectionDocument> findDocuments(long objectionId) {
        List<DocumentJpaEntity> documents = documentRepository.findByObjectionIdOrderByDocumentIdAsc(objectionId);
        Map<Long, OcrStatus> statuses = documents.isEmpty()
                ? Map.of()
                : ocrRepository
                        .findByDocumentIdIn(documents.stream()
                                .map(DocumentJpaEntity::getDocumentId)
                                .toList())
                        .stream()
                        .collect(
                                Collectors.toMap(DocumentOcrJpaEntity::getDocumentId, DocumentOcrJpaEntity::getStatus));
        return documents.stream()
                .map(entity -> entity.toDomain(statuses.get(entity.getDocumentId())))
                .toList();
    }

    @Override
    public Optional<ObjectionDocument> findDocument(long objectionId, long documentId) {
        return documentRepository
                .findByDocumentIdAndObjectionId(documentId, objectionId)
                .map(entity -> entity.toDomain(ocrRepository
                        .findById(entity.getDocumentId())
                        .map(DocumentOcrJpaEntity::getStatus)
                        .orElse(null)));
    }

    @Override
    public Optional<DocumentOcr> findOcr(long documentId) {
        return ocrRepository.findById(documentId).map(DocumentPersistenceAdapter::toOcr);
    }

    @Override
    public Map<Long, DocumentOcr> findOcrs(Collection<Long> documentIds) {
        if (documentIds.isEmpty()) {
            return Map.of();
        }
        return ocrRepository.findByDocumentIdIn(documentIds).stream()
                .map(DocumentPersistenceAdapter::toOcr)
                .collect(Collectors.toMap(DocumentOcr::documentId, Function.identity()));
    }

    @Override
    public void saveOcr(DocumentOcr ocr) {
        ocrRepository
                .findById(ocr.documentId())
                .orElseThrow(() -> new IllegalStateException("OCR 행이 없습니다. documentId=" + ocr.documentId()))
                .update(ocr.status(), toJson(ocr.pages()), ocr.engine(), ocr.updatedAt());
    }

    private static List<Map<String, Object>> toJson(List<OcrPage> pages) {
        List<Map<String, Object>> json = new ArrayList<>();
        for (OcrPage page : pages) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("page", page.page());
            item.put("width", page.width());
            item.put("height", page.height());
            List<Map<String, Object>> regions = new ArrayList<>();
            for (OcrPage.OcrRegion region : page.regions()) {
                Map<String, Object> regionItem = new LinkedHashMap<>();
                regionItem.put("index", region.index());
                regionItem.put("text", region.text());
                regionItem.put("bbox", region.bbox());
                regionItem.put("confidence", region.confidence());
                regions.add(regionItem);
            }
            item.put("regions", regions);
            json.add(item);
        }
        return json;
    }

    private static DocumentOcr toOcr(DocumentOcrJpaEntity entity) {
        List<OcrPage> pages = entity.getPages() == null
                ? List.of()
                : entity.getPages().stream()
                        .map(DocumentPersistenceAdapter::toPage)
                        .toList();
        return new DocumentOcr(
                entity.getDocumentId(), entity.getStatus(), pages, entity.getEngine(), entity.getUpdatedAt());
    }

    @SuppressWarnings("unchecked")
    private static OcrPage toPage(Map<String, Object> json) {
        List<OcrPage.OcrRegion> regions = new ArrayList<>();
        Object rawRegions = json.get("regions");
        if (rawRegions instanceof List<?> list) {
            for (Object raw : list) {
                Map<String, Object> region = (Map<String, Object>) raw;
                List<Double> bbox = region.get("bbox") instanceof List<?> values
                        ? values.stream().map(v -> ((Number) v).doubleValue()).toList()
                        : List.of();
                regions.add(new OcrPage.OcrRegion(
                        intValue(region.get("index")),
                        (String) region.get("text"),
                        bbox,
                        region.get("confidence") instanceof Number n ? n.doubleValue() : null));
            }
        }
        return new OcrPage(
                intValue(json.get("page")),
                json.get("width") instanceof Number w ? w.intValue() : null,
                json.get("height") instanceof Number h ? h.intValue() : null,
                regions);
    }

    private static int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }
}
