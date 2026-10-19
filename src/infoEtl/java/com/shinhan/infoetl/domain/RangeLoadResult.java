package com.shinhan.infoetl.domain;

import java.util.List;

public record RangeLoadResult(List<LoadResult> loaded, List<FailedDay> failed) {

    public boolean hasFailures() {
        return !failed.isEmpty();
    }
}
