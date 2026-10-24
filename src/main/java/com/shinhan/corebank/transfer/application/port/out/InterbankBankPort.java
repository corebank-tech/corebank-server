package com.shinhan.corebank.transfer.application.port.out;

import java.util.List;

/** 타행이체 수취 은행 목록(common_code BANK_CODE). 당행은 없다. */
public interface InterbankBankPort {

    List<InterbankBank> findAll();

    boolean isSupported(String bankCode);

    record InterbankBank(String bankCode, String bankName) {}
}
