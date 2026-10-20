package com.shinhan.corebank.transfer.application.port.in;

/** 타행이체 실행 (PH-33-②). 당행 이체 엔진(TransferExecutionUseCase)과 흐름이 달라 따로 둔다. */
public interface InterbankTransferUseCase {

    /** 결과와 상관없이 예외를 던지지 않고 결과를 돌려준다. TIMEOUT은 실패가 아니라 처리 불명이다. */
    InterbankTransferResult execute(InterbankTransferCommand command);
}
