package com.shinhan.corebank.gl.application.port.in;

import com.shinhan.corebank.gl.domain.TrialBalance;

// 관리자 시산표 조회(PH-28, ADM-02).
public interface TrialBalanceQueryUseCase {

    TrialBalance query(TrialBalanceQuery query);
}
