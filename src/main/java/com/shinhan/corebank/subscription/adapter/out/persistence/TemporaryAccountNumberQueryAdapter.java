package com.shinhan.corebank.subscription.adapter.out.persistence;

import com.shinhan.corebank.subscription.application.port.out.AccountNumberQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * account 도메인(#60/#49)이 아직 없어 JdbcTemplate으로 직접 조회하는 임시 구현.
 * account 도메인 완료 후 정식 AccountQueryPort 기반 어댑터로 교체할 것.
 */
@Repository
@RequiredArgsConstructor
public class TemporaryAccountNumberQueryAdapter implements AccountNumberQueryPort {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Optional<String> findAccountNumberById(Long accountId) {
        try {
            String accountNumber = jdbcTemplate.queryForObject(
                    "SELECT account_number FROM account WHERE account_id = ?", String.class, accountId);
            return Optional.ofNullable(accountNumber);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
