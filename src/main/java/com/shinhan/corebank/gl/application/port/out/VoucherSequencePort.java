package com.shinhan.corebank.gl.application.port.out;

import com.shinhan.corebank.gl.api.GlTxType;
import com.shinhan.corebank.gl.domain.VoucherNumber;
import java.time.LocalDate;

public interface VoucherSequencePort {

    /**
     * (거래일, 유형)마다 1부터 증가하는 전표번호를 발급한다. 호출자 트랜잭션과 별도로 커밋되므로
     * 호출자가 롤백되면 그 번호는 결번으로 남는다.
     */
    VoucherNumber nextVoucherNumber(LocalDate tradeDate, GlTxType txType);
}
