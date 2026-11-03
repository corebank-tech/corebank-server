package com.shinhan.corebank.account.adapter.out.persistence;

import com.shinhan.corebank.account.application.port.out.AccountTransferLifecycleLockPort;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.account.domain.exception.AccountErrorCode;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AccountTransferLifecycleLockPersistenceAdapter implements AccountTransferLifecycleLockPort {

    private static final String ACTIVE = "ACTIVE";

    private final JdbcTemplate jdbcTemplate;

    private record LockedAccount(
            Long accountId,
            String accountNumber,
            Long customerId,
            AccountType accountType,
            String status,
            boolean withdrawalRegistered) {}

    @Override
    public void lockForRegistration(Long customerId, Long withdrawalAccountId, String depositAccountNumber) {

        requireNotNull(customerId, withdrawalAccountId, depositAccountNumber);

        // 계좌번호는 불변 식별자다. ID만 먼저 조회한다.
        // 실제 상태 검증은 아래 FOR UPDATE 잠금 이후에 수행한다.
        List<Long> payeeIds = jdbcTemplate.query(
                """
                SELECT account_id
                FROM account
                WHERE account_number = ?
                """,
                (rs, rowNum) -> rs.getLong("account_id"),
                depositAccountNumber);

        if (payeeIds.isEmpty()) {
            throw new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND_OR_FORBIDDEN);
        }

        Long depositAccountId = payeeIds.getFirst();

        // 양쪽 계좌를 항상 ID 오름차순으로 잠근다.
        Map<Long, LockedAccount> locked = lockBoth(withdrawalAccountId, depositAccountId);

        LockedAccount withdrawal = locked.get(withdrawalAccountId);
        LockedAccount deposit = locked.get(depositAccountId);

        if (withdrawal == null
                || deposit == null
                || !customerId.equals(withdrawal.customerId())
                || !ACTIVE.equals(withdrawal.status())
                || !withdrawal.withdrawalRegistered()
                || withdrawal.accountType() != AccountType.DEMAND_DEPOSIT
                || !ACTIVE.equals(deposit.status())
                || !depositAccountNumber.equals(deposit.accountNumber())
                || deposit.accountType() == AccountType.TIME_DEPOSIT) {

            throw new BusinessException(AccountErrorCode.INVALID_ACCOUNT_STATUS);
        }
    }

    @Override
    public void lockForEarlyTermination(Long customerId, Long productAccountId, Long destinationAccountId) {

        requireNotNull(customerId, productAccountId, destinationAccountId);

        if (productAccountId.equals(destinationAccountId)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        Map<Long, LockedAccount> locked = lockBoth(productAccountId, destinationAccountId);

        LockedAccount product = locked.get(productAccountId);
        LockedAccount destination = locked.get(destinationAccountId);

        if (product == null
                || destination == null
                || !customerId.equals(product.customerId())
                || !customerId.equals(destination.customerId())
                || !ACTIVE.equals(product.status())
                || !ACTIVE.equals(destination.status())
                || !product.accountType().isMaturityEligible()
                || destination.accountType() != AccountType.DEMAND_DEPOSIT
                || !destination.withdrawalRegistered()) {

            throw new BusinessException(AccountErrorCode.EARLY_TERMINATION_NOT_ALLOWED);
        }
    }

    private Map<Long, LockedAccount> lockBoth(Long firstId, Long secondId) {
        Map<Long, LockedAccount> locked = new HashMap<>();

        List<Long> orderedIds = Stream.of(firstId, secondId).distinct().sorted().toList();

        // ORDER BY가 있는 IN절 한 번이 아니라,
        // PK별로 FOR UPDATE를 실행해 실제 잠금 획득 순서를 고정한다.
        for (Long accountId : orderedIds) {
            LockedAccount row = lockOne(accountId);

            if (row == null) {
                throw new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND_OR_FORBIDDEN);
            }

            locked.put(accountId, row);
        }

        return locked;
    }

    private LockedAccount lockOne(Long accountId) {
        List<LockedAccount> rows = jdbcTemplate.query(
                """
                SELECT account_id,
                       account_number,
                       customer_id,
                       account_type,
                       status,
                       withdrawal_registered
                FROM account
                WHERE account_id = ?
                FOR UPDATE
                """,
                (rs, rowNum) -> new LockedAccount(
                        rs.getLong("account_id"),
                        rs.getString("account_number"),
                        rs.getLong("customer_id"),
                        AccountType.valueOf(rs.getString("account_type")),
                        rs.getString("status"),
                        rs.getBoolean("withdrawal_registered")),
                accountId);

        return rows.isEmpty() ? null : rows.getFirst();
    }

    private static void requireNotNull(Object... values) {
        for (Object value : values) {
            if (value == null) {
                throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
            }
        }
    }
}
