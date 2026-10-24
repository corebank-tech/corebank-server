package com.shinhan.infoetl.application.service;

import com.shinhan.infoetl.application.port.out.ReplicaCutoffPort;
import com.shinhan.infoetl.domain.CutoffMissingException;
import java.time.LocalDate;

// 경계일 이전 기준일은 cutoff 없이, 이후 기준일은 cutoff(CLOSED)가 보여야 시작한다(설계 §3-1). 경계일을 정하지 않으면 확인하지 않는다.
public final class CutoffPolicy {

    private final LocalDate boundary;
    private final ReplicaCutoffPort cutoffPort;

    private CutoffPolicy(LocalDate boundary, ReplicaCutoffPort cutoffPort) {
        this.boundary = boundary;
        this.cutoffPort = cutoffPort;
    }

    public static CutoffPolicy enabled(LocalDate boundary, ReplicaCutoffPort cutoffPort) {
        return new CutoffPolicy(boundary, cutoffPort);
    }

    // 덤프 기간처럼 레플리카에 cutoff 테이블이 없는 환경
    public static CutoffPolicy disabled() {
        return new CutoffPolicy(null, null);
    }

    public void requireStartable(LocalDate baseDate) {
        if (boundary == null || baseDate.isBefore(boundary)) {
            return;
        }
        if (!cutoffPort.isClosed(baseDate)) {
            throw new CutoffMissingException(baseDate, boundary);
        }
    }
}
