package com.shinhan.corebank.limit.application.port.in;

import com.shinhan.corebank.limit.application.port.in.dto.LimitCommand;

/**
 * 이체한도 상태 변경. 가입 시 기본값 부여(REQ-TRSF-029)와 고객의 한도 변경
 * (REQ-TRSF-025)이 모두 여기 속한다.
 */
public interface LimitCommandUseCase {

    void create(Long customerId);

    void update(Long customerId, LimitCommand command);
}
