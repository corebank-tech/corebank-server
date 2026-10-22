package com.shinhan.corebank.gl.application.port.in;

import com.shinhan.corebank.gl.domain.GlIntegrityReport;
import java.time.LocalDate;

// 관리자 GL 정합성 점검(PH-28b). 요청 시에만 계산하고 결과를 저장하지 않는다.
public interface GlIntegrityCheckUseCase {

    GlIntegrityReport check(LocalDate tradeDate);
}
