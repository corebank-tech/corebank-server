package com.shinhan.corebank.gl.application.port.out;

import com.shinhan.corebank.gl.domain.TrialBalanceLine;
import java.time.LocalDate;
import java.util.List;

public interface TrialBalanceQueryPort {

    /**
     * 거래일이 기간(양 끝 포함) 안인 분개를 계정별로 합산해 계정코드 오름차순으로 돌려준다.
     * 계정과목에 없는 코드도 행으로 남긴다.
     */
    List<TrialBalanceLine> sumByAccount(LocalDate fromDate, LocalDate toDate);
}
