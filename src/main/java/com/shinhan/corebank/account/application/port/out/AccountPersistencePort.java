package com.shinhan.corebank.account.application.port.out;

import com.shinhan.corebank.account.domain.Account;
import java.util.List;
import java.util.Optional;

public interface AccountPersistencePort {

    Account save(Account account);

    boolean existsByAccountNumber(String accountNumber);

    // 아이디 찾기에서 입력한 계좌번호로 소유권 검증 대상 계좌를 조회한다.
    Optional<Account> findByAccountNumber(String accountNumber);

    List<Account> findAllByCustomerId(Long customerId);

    Optional<Account> findByAccountIdAndCustomerId(Long accountId, Long customerId);

    Optional<Account> findByAccountIdAndCustomerIdForUpdate(Long accountId, Long customerId);

    Account updatePasswordState(Account account);
}
