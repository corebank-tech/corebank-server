package com.shinhan.corebank.batch.api;

// 스텝이 이 예외를 던지면 러너가 남은 스텝을 건너뛰고 COB를 즉시 중단한다. 그 외 예외는 로그만 남기고 다음 스텝으로 넘어간다.
public class CobStepHaltException extends RuntimeException {
    public CobStepHaltException(String message) {
        super(message);
    }

    public CobStepHaltException(String message, Throwable cause) {
        super(message, cause);
    }
}
