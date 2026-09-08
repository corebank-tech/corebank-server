package com.shinhan.corebank.auth.application.port.out;

import java.time.LocalDate;
import java.util.List;

// Auth가 성명과 생년월일로 고객 후보를 조회하는 출력 포트를 정의한다.
public interface FindIdCustomerPort {

    List<FindIdCustomerCandidate> findAllByIdentity(
            String customerName,
            LocalDate birthDate
    );
}
