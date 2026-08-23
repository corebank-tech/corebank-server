package com.shinhan.corebank.limit.application.port.in;

import com.shinhan.corebank.limit.application.port.in.dto.TransferLimitResult;

/** 이체한도 조회(REQ-TRSF-024). */
public interface TransferLimitQueryUseCase {

    TransferLimitResult get(Long customerId);
}
