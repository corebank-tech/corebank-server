package com.shinhan.corebank.auth.application.port.out;

// 평문 비밀번호와 저장된 비밀번호 해시의 일치 여부를 검증하는 Port
public interface PasswordHashVerifierPort {

    boolean matches(String rawPassword, String passwordHash);
}
