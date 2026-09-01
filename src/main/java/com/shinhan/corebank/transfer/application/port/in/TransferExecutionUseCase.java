package com.shinhan.corebank.transfer.application.port.in;

public interface TransferExecutionUseCase {

    /**
     * 이체 실행 요청 메서드
     * @param command 이체 요청 정보
     * @return 이체 처리 결과
     */
    TransferResult execute(TransferCommand command);
}
