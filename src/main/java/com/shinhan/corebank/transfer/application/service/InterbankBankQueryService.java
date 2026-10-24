package com.shinhan.corebank.transfer.application.service;

import com.shinhan.corebank.transfer.application.port.in.InterbankBankQueryUseCase;
import com.shinhan.corebank.transfer.application.port.out.InterbankBankPort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InterbankBankQueryService implements InterbankBankQueryUseCase {

    private final InterbankBankPort interbankBankPort;

    @Override
    public List<InterbankBankItem> findAll() {
        return interbankBankPort.findAll().stream()
                .map(bank -> new InterbankBankItem(bank.bankCode(), bank.bankName()))
                .toList();
    }
}
