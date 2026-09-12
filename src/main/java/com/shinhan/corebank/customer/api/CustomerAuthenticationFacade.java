package com.shinhan.corebank.customer.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

// 다른 모듈에 고객 인증정보 조회 및 로그인 상태 변경 기능을 공개하는 인터페이스
public interface CustomerAuthenticationFacade {

    // 로그인 아이디로 고객 인증정보 조회
    Optional<CustomerAuthenticationData> findByUserId(String userId);

    // 성명과 생년월일이 일치하는 아이디 찾기 후보 고객을 조회한다.
    List<CustomerIdentityData> findAllByIdentity(String customerName, LocalDate birthDate);

    // 로그인 실패 결과를 고객 상태에 반영하도록 요청
    LoginFailureState updateLoginFailureState(RecordLoginFailureCommand command);

    // 로그인 성공 결과를 고객 상태에 반영하도록 요청
    LoginSuccessState updateLoginSuccessState(RecordLoginSuccessCommand command);
}
