package com.shinhan.corebank.account.application.port.in;

import java.time.LocalDate;

public interface AccountMaturityUseCase {

    // cobDate는 영업일 전환 전의 마감 대상 영업일이다.
    void runInCob(LocalDate cobDate);
}
