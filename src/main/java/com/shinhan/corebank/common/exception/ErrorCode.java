package com.shinhan.corebank.common.exception;

public interface ErrorCode {

    String getCode();

    int getStatus(); // HttpStatus에 의존하지 않는 순수 int

    String getMessage();
}
