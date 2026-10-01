package com.shinhan.corebank.gl.application.port.out;

import com.shinhan.corebank.gl.domain.Voucher;

public interface VoucherSavePort {

    /** 전표와 분개 줄을 함께 저장한다. 전표가 먼저 들어간다(분개의 복합 FK). */
    void save(Voucher voucher);
}
