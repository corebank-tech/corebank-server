package com.shinhan.corebank.auth.application.port.in;

// 고객·계좌 본인확인으로 로그인 아이디를 찾는 유스케이스를 정의한다.
public interface FindIdUseCase {

    FindIdResult findId(FindIdCommand command);
}
