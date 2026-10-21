package com.shinhan.corebank.account.application.port.out;

import com.shinhan.corebank.account.domain.Account;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;

public interface AccountPersistencePort {

    Account save(Account account);

    boolean existsByAccountNumber(String accountNumber);

    // 아이디 찾기에서 입력한 계좌번호로 소유권 검증 대상 계좌를 조회한다.
    Optional<Account> findByAccountNumber(String accountNumber);

    List<Account> findAllByCustomerId(Long customerId);

    Optional<Account> findByAccountIdAndCustomerId(Long accountId, Long customerId);

    Optional<Account> findByAccountIdAndCustomerIdForUpdate(Long accountId, Long customerId);

    Account updatePasswordState(Account account);

    // 다른 도메인의 배치가 잔액만 필요할 때 전체 Account 도메인 객체 조립 없이 조회한다.
    // 존재하지 않는 계좌 ID는 결과 맵에서 생략된다.
    Map<Long, Long> findBalancesByAccountIds(Collection<Long> accountIds);

    // afterAccountId보다 큰 계좌를 account_id 오름차순으로 최대 limit개. 비어 있으면 끝이다.
    SortedMap<Long, Long> findBalancesAfter(long afterAccountId, int limit);

    // 만기 감지용 keyset 페이지. 조건에 맞는 계좌 ID만 조회한다.
    List<Long> findMaturityCandidateIdsAfter(long afterAccountId, LocalDate maturityBaseDate, int limit);

    // 만기 상태 변경 직전에 최신 계좌 상태를 잠금 조회한다.
    // 반드시 트랜잭션 안에서 호출해야 한다.
    Optional<Account> findByAccountIdForUpdate(Long accountId);
}
