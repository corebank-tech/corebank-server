package com.shinhan.corebank.auth.adapter.out.account;

import com.shinhan.corebank.account.api.AccountIdentityVerification;
import com.shinhan.corebank.account.api.AccountIdentityVerifier;
import com.shinhan.corebank.auth.application.port.out.FindIdAccountVerificationPort;
import com.shinhan.corebank.auth.application.port.out.FindIdAccountVerificationResult;
import com.shinhan.corebank.auth.application.port.out.FindIdAccountVerificationStatus;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// Auth의 계좌 본인확인 요청을 Account 공개 API로 연결한다.
@Component
@RequiredArgsConstructor
public class FindIdAccountVerificationAdapter implements FindIdAccountVerificationPort {

    private final AccountIdentityVerifier accountIdentityVerifier;

    @Override
    public FindIdAccountVerificationResult verify(
            Set<Long> candidateCustomerIds, String accountNumber, String accountPassword) {
        var result = accountIdentityVerifier.verify(
                new AccountIdentityVerification(candidateCustomerIds, accountNumber, accountPassword));

        return new FindIdAccountVerificationResult(
                FindIdAccountVerificationStatus.valueOf(result.status().name()), result.customerId());
    }
}
