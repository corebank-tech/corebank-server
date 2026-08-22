package com.shinhan.corebank.limit.application.port.in;

import com.shinhan.corebank.limit.application.port.in.dto.LimitCommand;
import com.shinhan.corebank.limit.application.port.in.dto.LimitResult;

/**
 * 고객이 직접 일으키는 이체한도 상태 변경(REQ-TRSF-025).
 *
 * <p>가입 시 기본값 부여(REQ-TRSF-029)는 여기 두지 않는다. 호출자가 웹이 아니라 signup 모듈이라
 * limit/api 의 TransferLimitRegistration 이 그 입구를 맡는다.
 */
public interface LimitCommandUseCase {

    /** 변경된 한도와 당일 사용 현황을 함께 돌려준다. 클라이언트가 재조회하지 않아도 된다. */
    LimitResult update(Long customerId, LimitCommand command);
}
