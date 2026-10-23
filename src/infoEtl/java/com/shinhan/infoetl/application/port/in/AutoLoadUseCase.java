package com.shinhan.infoetl.application.port.in;

import com.shinhan.infoetl.domain.RangeLoadResult;

public interface AutoLoadUseCase {

    // 경계일부터 가장 최근 cutoff까지의 영업일을 오래된 날짜부터 적재한다. 이미 COMPLETED인 날은 건너뛴다.
    RangeLoadResult loadPending();
}
