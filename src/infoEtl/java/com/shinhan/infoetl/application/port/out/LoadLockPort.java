package com.shinhan.infoetl.application.port.out;

import java.time.LocalDate;

public interface LoadLockPort {

    // 기준일 하나를 한 프로세스만 적재하게 하는 락. 이미 잡혀 있으면 LoadAlreadyRunningException을 던진다.
    LoadLock acquire(LocalDate baseDate);
}
