package com.shinhan.corebank.paymentstop.domain.document;

import java.util.List;

/** OCR 한 페이지. 좌표는 원본 이미지 픽셀 기준이다. */
public record OcrPage(int page, Integer width, Integer height, List<OcrRegion> regions) {

    public OcrPage {
        regions = regions == null ? List.of() : List.copyOf(regions);
    }

    /**
     * 글자 영역 하나.
     *
     * @param bbox [x, y, w, h]
     * @param confidence OCR 엔진의 글자 인식 확률(0~1). 0.85 미만은 화면에서 담당자 확인 표시(D40)
     */
    public record OcrRegion(int index, String text, List<Double> bbox, Double confidence) {

        public OcrRegion {
            bbox = bbox == null ? List.of() : List.copyOf(bbox);
        }
    }
}
