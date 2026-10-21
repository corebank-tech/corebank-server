package com.shinhan.infoetl.application.port.in;

import com.shinhan.infoetl.domain.RangeLoadResult;
import java.time.LocalDate;

public interface RangeLoadUseCase {

    // from~to(양끝 포함)의 영업일을 오래된 날짜부터 적재한다. 하루가 실패해도 다음 날로 넘어간다.
    RangeLoadResult loadRange(LocalDate from, LocalDate to);
}
