package com.shinhan.corebank.customer.application.port.out;

// 관리자 비밀번호 초기화에 쓸 임시 비밀번호를 만든다.
public interface TemporaryPasswordGeneratorPort {
    String generate();
}
