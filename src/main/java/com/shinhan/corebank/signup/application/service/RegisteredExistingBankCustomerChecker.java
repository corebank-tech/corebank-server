package com.shinhan.corebank.signup.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.signup.application.port.out.ExistingBankCustomerAccountsPort;
import com.shinhan.corebank.signup.application.port.out.SignupCustomerAvailabilityPort;
import com.shinhan.corebank.signup.application.port.out.SignupRegisteredAccountPort;
import com.shinhan.corebank.signup.domain.exception.SignupErrorCode;
import com.shinhan.corebank.signup.domain.model.ExistingBankAccountSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 원장 고객이 이미 인터넷뱅킹에 가입했는지 판정한다. A-03 선검증과 회원가입
// 완료가 같은 기준으로 판정하도록 한 곳에 모은다.
@Component
@RequiredArgsConstructor
public class RegisteredExistingBankCustomerChecker {

    private final SignupCustomerAvailabilityPort availabilityPort;
    private final ExistingBankCustomerAccountsPort accountsPort;
    private final SignupRegisteredAccountPort registeredAccountPort;

    public void rejectIfRegistered(String existingBankCustomerId) {
        if (isRegistered(existingBankCustomerId)) {
            throw new BusinessException(
                    SignupErrorCode.DUPLICATE_EXISTING_BANK_CUSTOMER
            );
        }
    }

    // 판정 경로가 둘인 이유는 existing_bank_customer_id 가 뒤늦게 생긴 컬럼이라서다.
    // 컬럼이 생기기 전에 가입한 고객은 값이 NULL 이라 1번으로는 걸리지 않는다.
    // 그 행을 백필하려면 계좌번호로 원장을 조회해야 하는데 원장이 외부라 SQL 로는
    // 불가능하므로, 그 사람의 계좌가 이미 등록돼 있는지로 대신 판정한다.
    private boolean isRegistered(String existingBankCustomerId) {
        if (availabilityPort.isExistingBankCustomerRegistered(
                existingBankCustomerId
        )) {
            return true;
        }

        return accountsPort.findAllByCustomerId(existingBankCustomerId)
                .stream()
                .map(ExistingBankAccountSnapshot::accountNumber)
                .anyMatch(registeredAccountPort::isRegistered);
    }
}
