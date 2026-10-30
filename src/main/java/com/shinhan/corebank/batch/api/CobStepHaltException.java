package com.shinhan.corebank.batch.api;

// 스텝이 이 예외를 던지면 러너가 남은 스텝을 생략하고 COB를 중단한다 - 다음 run()이 중단된 스텝부터 같은 COB_DATE로 이어서 실행한다.
public class CobStepHaltException extends RuntimeException {
    public CobStepHaltException(String message) {
        super(message);
    }

    public CobStepHaltException(String message, Throwable cause) {
        super(message, cause);
    }
}
