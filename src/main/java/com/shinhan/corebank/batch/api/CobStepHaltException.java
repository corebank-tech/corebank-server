package com.shinhan.corebank.batch.api;

// 스텝이 이 예외를 던지면 러너가 남은 스텝을 생략하고 COB를 즉시 중단한다 - 재실행 수단이 없어 #556 전까지 실사용 보류.
public class CobStepHaltException extends RuntimeException {
    public CobStepHaltException(String message) {
        super(message);
    }

    public CobStepHaltException(String message, Throwable cause) {
        super(message, cause);
    }
}
