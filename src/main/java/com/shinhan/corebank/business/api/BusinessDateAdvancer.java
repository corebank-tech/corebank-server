package com.shinhan.corebank.business.api;

import java.time.LocalDate;

// COB 첫 스텝(PH-43, ADR-0004)이 영업일을 다음 영업일로 넘길 때 부른다
public interface BusinessDateAdvancer {

    // businessDate 를 처리한 COB 가 넘긴다. 이미 넘어가 있으면 그대로 돌려준다(재실행 안전)
    LocalDate advanceFrom(LocalDate businessDate);
}
