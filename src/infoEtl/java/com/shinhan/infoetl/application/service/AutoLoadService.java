package com.shinhan.infoetl.application.service;

import com.shinhan.infoetl.application.port.in.AutoLoadUseCase;
import com.shinhan.infoetl.application.port.in.RangeLoadUseCase;
import com.shinhan.infoetl.application.port.out.ReplicaCutoffPort;
import com.shinhan.infoetl.domain.RangeLoadResult;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public final class AutoLoadService implements AutoLoadUseCase {

    private static final System.Logger LOG = System.getLogger(AutoLoadService.class.getName());

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
        if (latest.isEmpty()) {
            LOG.log(System.Logger.Level.WARNING, "적재할 날짜가 없습니다 - 원천에 cutoff가 없습니다(복제 지연 또는 경계일 전). 경계일={0}", boundary);
            return new RangeLoadResult(List.of(), List.of(), List.of());
        }
        if (latest.get().isBefore(boundary)) {
            LOG.log(
                    System.Logger.Level.WARNING,
                    "적재할 날짜가 없습니다 - 가장 최근 cutoff={0}이 경계일={1}보다 앞섭니다. 경계일 설정을 확인하세요.",
                    latest.get(),
                    boundary);
            return new RangeLoadResult(List.of(), List.of(), List.of());
        }
        return rangeLoad.loadRange(boundary, latest.get());
    }
}
