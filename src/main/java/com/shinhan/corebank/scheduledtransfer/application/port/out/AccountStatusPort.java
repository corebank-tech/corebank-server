package com.shinhan.corebank.scheduledtransfer.application.port.out;

import com.shinhan.corebank.account.domain.AccountType;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

// 정상계좌인지, 입금받는 계좌가 있는지 확인하는 인터페이스
public interface AccountStatusPort {
    boolean isActiveAccount(Long accountId);

    Optional<AccountType> findAccountTypeByNumber(String accountNumber);

    boolean belongsToCustomer(Long accountId, Long customerId);

    boolean isWithdrawalRegistered(Long accountId);

    Optional<String> findAccountNumberById(Long accountId);

    Map<Long, String> findAccountNumbersByIds(Collection<Long> accountIds);

    // 별칭 미설정 계좌는 결과 Map에서 제외됨(값이 null이 아니라 키 자체가 없음) - 호출부는 getOrDefault/get으로 null 허용 처리
    Map<Long, String> findAccountAliasesByIds(Collection<Long> accountIds);
}
