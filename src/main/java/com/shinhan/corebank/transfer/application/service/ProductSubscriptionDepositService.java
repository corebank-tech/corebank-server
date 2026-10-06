package com.shinhan.corebank.transfer.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.limit.domain.exception.LmtErrorCode;
import com.shinhan.corebank.transfer.api.LedgerPostingContext;
import com.shinhan.corebank.transfer.api.LedgerPostingHook;
import com.shinhan.corebank.transfer.application.port.in.ProductSubscriptionDepositUseCase;
import com.shinhan.corebank.transfer.application.port.out.AccountLockPort;
import com.shinhan.corebank.transfer.application.port.out.LedgerSavePort;
import com.shinhan.corebank.transfer.application.port.out.LockedAccountStatus;
import com.shinhan.corebank.transfer.application.port.out.LockedAccountsForTransfer;
import com.shinhan.corebank.transfer.application.port.out.TransferBalances;
import com.shinhan.corebank.transfer.application.port.out.TransferSequencePort;
import com.shinhan.corebank.transfer.domain.LedgerPair;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상품가입 초입금 기표 유스케이스 구현체.
 * [채번 → 계좌 락 획득 → 출금계좌 재검증 → 잔액 이동 → 원장 2행 INSERT → 훅 B]을 수행한다.
 * 훅 B(LedgerPostingHook)는 GL 상품가입 전표를 같은 트랜잭션에 세운다(PH-24). 훅 예외는 가입 전체를 롤백한다.
 *
 * <p>{@code @Transactional}은 기본 전파(REQUIRED)다 — 호출자(상품가입 실행)의 트랜잭션에
 * 참여해야 뒤 단계 실패 시 초입금까지 통째로 롤백된다. 이체 실행처럼 REQUIRES_NEW로
 * 독립 커밋하면 안 된다.
 */
@Service
@RequiredArgsConstructor
public class ProductSubscriptionDepositService implements ProductSubscriptionDepositUseCase {

    // 상품가입은 고객이 인터넷뱅킹에서 직접 실행하는 거래만 존재한다(배치 가입 경로 없음).
    private static final TransferChannel CHANNEL = TransferChannel.WB;
    // ledger_entry.transaction_content는 VARCHAR(10)이라 상품명을 그대로 넣을 수 없다.
    private static final String PASSBOOK_MEMO = "상품가입";
    // ledger_entry.transaction_type 의 상품가입 값(LedgerPair.forProductSubscription).
    private static final String LEDGER_TYPE = "PRODUCT_SUBSCRIPTION";

    private final AccountLockPort accountLockPort;
    private final TransferSequencePort transferSequencePort;
    private final LedgerSavePort ledgerSavePort;
    private final List<LedgerPostingHook> ledgerPostingHooks;
    private final Clock clock;

    @Override
    @Transactional
    public ProductSubscriptionDepositResult deposit(ProductSubscriptionDepositCommand command) {
        LocalDateTime requestedAt = LocalDateTime.now(clock);

        // 채번은 SequenceGenerator 안에서 REQUIRES_NEW로 독립 커밋된다 — 이 트랜잭션이 뒤에서
        // 롤백돼도 번호만 비고(gap) 채번 행은 남는다. 영업일자 기준은 TransferExecutionService와
        // 동일하게 맞춘다(같은 transaction_sequence 행을 공유하므로 기준이 갈리면 안 된다).
        String transactionNumber = transferSequencePort.nextTransactionNumber(requestedAt.toLocalDate(), CHANNEL);

        LockedAccountsForTransfer locked =
                accountLockPort.lockForTransfer(command.withdrawalAccountId(), command.depositAccountId());

        // 원장·잔액 시각은 락을 쥔 뒤에 찍어야 같은 계좌의 occurred_at 순서가 잔액 반영 순서와 같아진다(#545).
        LocalDateTime executedAt = LocalDateTime.now(clock);

        // 입금계좌는 같은 트랜잭션에서 방금 개설된 신규 계좌라 상태 재검증 대상이 아니다.
        // 출금계좌는 사전검증(가입 가능 여부 판정) 이후 락을 얻기까지 사이에 정지·해지되거나
        // 다른 이체로 잔액이 빠졌을 수 있으므로, 락으로 얻은 최신 스냅샷으로 다시 본다.
        if (locked.withdrawal().status() != LockedAccountStatus.ACTIVE) {
            throw new BusinessException(TransferErrorCode.WITHDRAWAL_ACCOUNT_SUSPENDED);
        }
        if (locked.withdrawal().balance() < command.amount()) {
            throw new BusinessException(LmtErrorCode.INSUFFICIENT_WITHDRAWABLE_AMOUNT);
        }

        TransferBalances balances = accountLockPort.applyTransfer(locked, command.amount(), executedAt);

        ledgerSavePort.save(LedgerPair.forProductSubscription(
                transactionNumber,
                command.withdrawalAccountId(),
                balances.withdrawalBalanceAfter(),
                command.depositAccountId(),
                balances.depositBalanceAfter(),
                command.amount(),
                PASSBOOK_MEMO,
                PASSBOOK_MEMO,
                CHANNEL,
                executedAt));

        // tradeDate 는 PH-41 전까지 기표 시각의 달력일이다(TransferExecutionService 와 같다).
        LedgerPostingContext context = new LedgerPostingContext(
                transactionNumber,
                LEDGER_TYPE,
                command.amount(),
                command.withdrawalAccountId(),
                command.depositAccountId(),
                executedAt.toLocalDate());
        for (LedgerPostingHook hook : ledgerPostingHooks) {
            hook.afterLedger(context);
        }

        return new ProductSubscriptionDepositResult(
                transactionNumber, balances.withdrawalBalanceAfter(), balances.depositBalanceAfter());
    }
}
