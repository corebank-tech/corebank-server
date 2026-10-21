package com.shinhan.infoetl.application.service;

import com.shinhan.infoetl.application.port.in.AutoLoadUseCase;
import com.shinhan.infoetl.application.port.in.RangeLoadUseCase;
import com.shinhan.infoetl.application.port.out.ReplicaCutoffPort;
import com.shinhan.infoetl.domain.RangeLoadResult;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public final class AutoLoadService implements AutoLoadUseCase {

    private final RangeLoadUseCase rangeLoad;
    private final ReplicaCutoffPort cutoffPort;
    private final LocalDate boundary;

    public AutoLoadService(RangeLoadUseCase rangeLoad, ReplicaCutoffPort cutoffPort, LocalDate boundary) {
        this.rangeLoad = rangeLoad;
        this.cutoffPort = cutoffPort;
        this.boundary = boundary;
    }

    // 범위의 끝은 가장 최근 cutoff다. 그 뒤 날짜는 아직 마감 전이다. 중간에 cutoff가 없는 영업일은 기간 적재가 실패로 모은다.
    @Override
    public RangeLoadResult loadPending() {
        Optional<LocalDate> latest = cutoffPort.latestClosedDate();
        if (latest.isEmpty() || latest.get().isBefore(boundary)) {
            return new RangeLoadResult(List.of(), List.of(), List.of());
        }
        return rangeLoad.loadRange(boundary, latest.get());
    }
}
