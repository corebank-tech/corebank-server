package com.shinhan.corebank.transfer.application.port.in;

import java.util.List;

/** 타행이체 수취 은행 목록. 당행(088)은 없다. */
public interface InterbankBankQueryUseCase {

    List<InterbankBankItem> findAll();

    record InterbankBankItem(String bankCode, String bankName) {}
}
