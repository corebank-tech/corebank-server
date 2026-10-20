package com.shinhan.infoetl.application.port.in;

import com.shinhan.infoetl.domain.LoadResult;
import java.time.LocalDate;

public interface DailyLoadUseCase {

    // 이미 COMPLETED인 기준일은 rebuild가 true일 때만 다시 만든다(P3 설계 §3-1).
    LoadResult load(LocalDate baseDate, boolean rebuild);

    default LoadResult load(LocalDate baseDate) {
        return load(baseDate, false);
    }
}
