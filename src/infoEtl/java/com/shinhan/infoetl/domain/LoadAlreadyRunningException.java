package com.shinhan.infoetl.domain;

import java.time.LocalDate;

// 같은 기준일을 다른 프로세스가 적재 중이다.
public class LoadAlreadyRunningException extends RuntimeException {

    public LoadAlreadyRunningException(LocalDate baseDate) {
        super("같은 기준일을 다른 프로세스가 적재 중입니다: " + baseDate);
    }
}
