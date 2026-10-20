package com.shinhan.corebank.transfer.adapter.out.persistence;

import com.shinhan.corebank.common.code.CommonCodeJpaRepository;
import com.shinhan.corebank.transfer.application.port.out.InterbankBankPort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InterbankBankPersistenceAdapter implements InterbankBankPort {

    private static final String BANK_CODE_GROUP = "BANK_CODE";
    private static final String IN_USE = "Y";

    private final CommonCodeJpaRepository commonCodeJpaRepository;

    @Override
    public List<InterbankBank> findAll() {
        return commonCodeJpaRepository.findByCodeGroupAndUseYnOrderBySortOrderAsc(BANK_CODE_GROUP, IN_USE).stream()
                .map(code -> new InterbankBank(code.getCode(), code.getCodeName()))
                .toList();
    }

    @Override
    public boolean isSupported(String bankCode) {
        return findAll().stream().anyMatch(bank -> bank.bankCode().equals(bankCode));
    }
}
