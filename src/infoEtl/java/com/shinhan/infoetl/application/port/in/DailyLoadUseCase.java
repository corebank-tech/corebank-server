package com.shinhan.infoetl.application.port.in;

import com.shinhan.infoetl.domain.LoadResult;
import java.time.LocalDate;

public interface DailyLoadUseCase {

    LoadResult load(LocalDate baseDate);
}
