package com.shinhan.corebank.customer.application.port.in;

// 로그인 고객의 휴대폰 번호와 이메일 변경 유스케이스를 정의한다.
public interface UpdateCustomerInfoUseCase {

    UpdateCustomerInfoResult update(UpdateCustomerInfoCommand command);
}
