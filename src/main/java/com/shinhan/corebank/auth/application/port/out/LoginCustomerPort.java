package com.shinhan.corebank.auth.application.port.out;

import com.shinhan.corebank.auth.domain.model.LoginCustomer;

import java.time.LocalDateTime;
import java.util.Optional;

// 로그인 고객 인증정보 조회 및 상태 저장을 위한 출력 포트
public interface LoginCustomerPort {

    // 로그인 아이디로 고객 인증정보 조회
    Optional<LoginCustomer> findByUserId(String userId);

    // 고객의 로그인 실패를 1회 기록
    void recordLoginFailure(Long customerId);

    // 로그인 성공 상태와 최근 접속 정보 저장
    void recordLoginSuccess(
            Long customerId,
            LocalDateTime loginAt,
            String loginIp
    );
}
