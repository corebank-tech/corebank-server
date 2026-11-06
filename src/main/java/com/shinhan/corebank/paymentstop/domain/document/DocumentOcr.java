package com.shinhan.corebank.paymentstop.domain.document;

import com.shinhan.corebank.paymentstop.domain.agent.Pseudonymizer;
import java.time.LocalDateTime;
import java.util.List;

/** 증빙 OCR 결과. 텍스트는 저장 전에 서버가 가명 처리한다. OCR 텍스트는 주장이고 해제 근거가 아니다(D9). */
public record DocumentOcr(
        long documentId, OcrStatus status, List<OcrPage> pages, String engine, LocalDateTime updatedAt) {

    public DocumentOcr {
        pages = pages == null ? List.of() : List.copyOf(pages);
    }

    /** 워커가 보낸 원문 결과를 가명 처리해 완료(또는 실패)로 만든다. */
    public static DocumentOcr fromWorker(
            long documentId, OcrStatus status, List<OcrPage> rawPages, String engine, LocalDateTime at) {
        List<OcrPage> masked = rawPages == null
                ? List.of()
                : rawPages.stream()
                        .map(page -> new OcrPage(
                                page.page(),
                                page.width(),
                                page.height(),
                                page.regions().stream()
                                        .map(region -> new OcrPage.OcrRegion(
                                                region.index(),
                                                Pseudonymizer.mask(region.text()),
                                                region.bbox(),
                                                region.confidence()))
                                        .toList()))
                        .toList();
        return new DocumentOcr(documentId, status, masked, engine, at);
    }
}
