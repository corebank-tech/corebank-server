package com.shinhan.infoetl.domain;

import java.time.LocalDate;
import java.util.List;

// skipped는 이미 COMPLETED라 다시 적재하지 않은 영업일이다.
public record RangeLoadResult(List<LoadResult> loaded, List<LocalDate> skipped, List<FailedDay> failed) {

    public boolean hasFailures() {
        return !failed.isEmpty();
    }
}
