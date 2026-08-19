package com.shinhan.corebank.limit.application.port.out;

import com.shinhan.corebank.limit.domain.TransferLimit;

/** 한도 상태를 바꾸는 오퍼레이션. */
public interface TransferLimitCommandPort {

    TransferLimit save(TransferLimit limit);
}
