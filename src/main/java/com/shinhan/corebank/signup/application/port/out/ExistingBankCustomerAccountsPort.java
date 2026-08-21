package com.shinhan.corebank.signup.application.port.out;

import com.shinhan.corebank.signup.domain.model.ExistingBankAccountSnapshot;
import java.util.List;

// 기존 은행 고객이 보유한 전체 계좌를 원장에서 조회한다.
public interface ExistingBankCustomerAccountsPort {

    List<ExistingBankAccountSnapshot> findAllByCustomerId(
            String existingBankCustomerId
    );
}
