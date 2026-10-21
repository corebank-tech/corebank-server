package com.shinhan.infoetl.domain;

import java.time.LocalDate;

// 경계일 이후의 기준일인데 원천에 cutoff(CLOSED)가 없다. COB 영업일 전환이 실패한 것으로 보고 적재를 시작하지 않는다(설계 §3-1).
public final class CutoffMissingException extends RuntimeException {

    private final LocalDate baseDate;

    public CutoffMissingException(LocalDate baseDate, LocalDate boundary) {
        super("경계일(" + boundary + ") 이후 기준일 " + baseDate + "의 cutoff(CLOSED)가 원천에 없습니다. COB 영업일 전환이 끝나지 않았거나 실패했습니다.");
        this.baseDate = baseDate;
    }

    public LocalDate baseDate() {
        return baseDate;
    }
}
