package com.shinhan.corebank.account.application.port.in;

// 인증이 완료된 계좌의 비밀번호를 변경하는 유스케이스다.
public interface ChangeAccountPasswordUseCase {

    ChangeAccountPasswordResult change(ChangeAccountPasswordCommand command);
}
