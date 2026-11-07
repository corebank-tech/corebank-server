package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.account.api.PartialStopResult;
import com.shinhan.corebank.account.api.PaymentStopUseCase;
import com.shinhan.corebank.account.application.port.out.AccountPaymentStopPort;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.application.port.out.AccountStatusHistoryPort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.AccountPaymentStop;
import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.account.domain.exception.AccountErrorCode;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계좌 지급정지 계약 구현.
 *
 * <p>항상 계좌 행을 먼저 잠그고 지급정지 기록을 읽는다. 같은 계좌에 대한 적용, 전환, 해제가 이 락으로 줄을 선다.
 */
@Service
@RequiredArgsConstructor
public class PaymentStopService implements PaymentStopUseCase {

    private final AccountPersistencePort accountPersistencePort;
    private final AccountPaymentStopPort paymentStopPort;
    private final AccountStatusHistoryPort accountStatusHistoryPort;
    private final Clock clock;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void applyFullStop(long accountId, long caseId) {
        Account account = lockAccount(accountId);

        var existing = paymentStopPort.findByAccountIdAndCaseId(accountId, caseId);
        if (existing.isPresent()) {
            AccountPaymentStop stop = existing.get();
            if (stop.isActive() && stop.isFull()) {
                return;
            }
            throw new BusinessException(AccountErrorCode.PAYMENT_STOP_STATE_CONFLICT);
        }

        List<AccountPaymentStop> activeStops = paymentStopPort.findActiveByAccountId(accountId);

        // 첫 지급정지일 때만 지금 상태를 정지 전 상태로 남긴다. 이미 걸려 있으면 그 기록의 정지 전 상태를 이어 쓴다.
        AccountStatus statusBefore;
        if (activeStops.isEmpty()) {
            account.validatePaymentStopAllowed();
            statusBefore = account.getStatus();
        } else {
            statusBefore = activeStops.getFirst().getStatusBefore();
        }

        AccountPaymentStop saved =
                paymentStopPort.save(AccountPaymentStop.full(accountId, caseId, statusBefore, now()));

        List<AccountPaymentStop> nextActive = new ArrayList<>(activeStops);
        nextActive.add(saved);
        recalculate(account, nextActive, statusBefore);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public PartialStopResult convertToPartial(long accountId, long caseId, long requestedAmount) {
        if (requestedAmount <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        Account account = lockAccount(accountId);

        AccountPaymentStop stop = paymentStopPort
                .findByAccountIdAndCaseId(accountId, caseId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.PAYMENT_STOP_NOT_FOUND));

        if (!stop.isActive()) {
            throw new BusinessException(AccountErrorCode.PAYMENT_STOP_STATE_CONFLICT);
        }
        if (!stop.isFull()) {
            if (stop.getRequestedAmount() == requestedAmount) {
                return new PartialStopResult(stop.getRequestedAmount(), stop.getAppliedAmount());
            }
            throw new BusinessException(AccountErrorCode.PAYMENT_STOP_STATE_CONFLICT);
        }

        List<AccountPaymentStop> activeStops = paymentStopPort.findActiveByAccountId(accountId);

        // 다른 사건이 이미 묶은 보류액을 뺀 잔액이 이 사건이 묶을 수 있는 상한이다.
        long otherHold = activeStops.stream()
                .filter(other -> other.getCaseId() != caseId && !other.isFull())
                .mapToLong(AccountPaymentStop::getAppliedAmount)
                .sum();
        long appliedAmount = Math.min(requestedAmount, Math.max(0L, account.getBalance() - otherHold));

        stop.convertToPartial(requestedAmount, appliedAmount);
        AccountPaymentStop saved = paymentStopPort.save(stop);

        List<AccountPaymentStop> nextActive = activeStops.stream()
                .map(other -> other.getCaseId() == caseId ? saved : other)
                .toList();
        recalculate(account, nextActive, stop.getStatusBefore());

        return new PartialStopResult(requestedAmount, appliedAmount);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void release(long accountId, long caseId) {
        Account account = lockAccount(accountId);

        AccountPaymentStop stop = paymentStopPort
                .findByAccountIdAndCaseId(accountId, caseId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.PAYMENT_STOP_NOT_FOUND));

        if (!stop.isActive()) {
            return;
        }

        stop.release(now());
        paymentStopPort.save(stop);

        List<AccountPaymentStop> nextActive = paymentStopPort.findActiveByAccountId(accountId).stream()
                .filter(other -> other.getCaseId() != caseId)
                .toList();
        recalculate(account, nextActive, stop.getStatusBefore());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasOtherStopHistory(long accountId, long excludingCaseId) {
        return paymentStopPort.existsOtherCase(accountId, excludingCaseId);
    }

    private Account lockAccount(long accountId) {
        return accountPersistencePort
                .findByAccountIdForUpdate(accountId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND_OR_FORBIDDEN));
    }

    private void recalculate(Account account, List<AccountPaymentStop> activeStops, AccountStatus statusBefore) {
        AccountStatus previousStatus = account.getStatus();

        boolean fullStopActive = activeStops.stream().anyMatch(AccountPaymentStop::isFull);
        long partialHoldAmount = activeStops.stream()
                .filter(stop -> !stop.isFull())
                .mapToLong(AccountPaymentStop::getAppliedAmount)
                .sum();

        account.applyPaymentStops(statusBefore, fullStopActive, partialHoldAmount);
        accountPersistencePort.save(account);

        if (account.getStatus() != previousStatus) {
            accountStatusHistoryPort.recordPaymentStopTransition(
                    account.getAccountId(), previousStatus, account.getStatus(), now());
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
