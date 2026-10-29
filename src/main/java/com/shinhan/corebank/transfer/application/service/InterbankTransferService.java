package com.shinhan.corebank.transfer.application.service;

import com.shinhan.corebank.business.api.BusinessDateGate;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.gl.api.JournalPostingUseCase;
import com.shinhan.corebank.transfer.api.TransferPreCheck;
import com.shinhan.corebank.transfer.api.TransferPreCheckContext;
import com.shinhan.corebank.transfer.application.port.in.InterbankTransferCommand;
import com.shinhan.corebank.transfer.application.port.in.InterbankTransferResult;
import com.shinhan.corebank.transfer.application.port.in.InterbankTransferUseCase;
import com.shinhan.corebank.transfer.application.port.out.AccountLockPort;
import com.shinhan.corebank.transfer.application.port.out.InterbankBankPort;
import com.shinhan.corebank.transfer.application.port.out.InterbankGatewayPort;
import com.shinhan.corebank.transfer.application.port.out.InterbankReply;
import com.shinhan.corebank.transfer.application.port.out.InterbankRequest;
import com.shinhan.corebank.transfer.application.port.out.LedgerSavePort;
import com.shinhan.corebank.transfer.application.port.out.LockedAccount;
import com.shinhan.corebank.transfer.application.port.out.LockedAccountStatus;
import com.shinhan.corebank.transfer.application.port.out.TransferAuthTokenVerificationPort;
import com.shinhan.corebank.transfer.application.port.out.TransferLimitPort;
import com.shinhan.corebank.transfer.application.port.out.TransferOtpVerificationPort;
import com.shinhan.corebank.transfer.application.port.out.TransferSavePort;
import com.shinhan.corebank.transfer.application.port.out.TransferSequencePort;
import com.shinhan.corebank.transfer.domain.LedgerEntry;
import com.shinhan.corebank.transfer.domain.Transfer;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

// 타행이체(PH-33-②). [사전검증 → 1단계 출금·TIMEOUT 커밋 → 송신 → 2단계 확정]
// 응답을 기다리는 동안 락·커넥션을 쥐지 않으려고 두 번 끊는다. 송신 전후에 죽어도 TIMEOUT이 남아 조회거래가 확정한다.
@Slf4j
@Service
public class InterbankTransferService implements InterbankTransferUseCase {

    private static final long FEE = 0L; // 타행 수수료는 범위 밖이라 0 고정 (tasks.md PH-33-②)
    private static final TransferChannel CHANNEL = TransferChannel.WB;

    // 채번 뒤 출금 전까지 들고 다니는 값. 거래일은 gate를 쥔 트랜잭션 안에서 읽어야 해서 Transfer는 거기서 만든다.
    private record Draft(String transactionNumber, LocalDateTime requestedAt) {}

    private final AccountLockPort accountLockPort;
    private final InterbankBankPort interbankBankPort;
    private final InterbankGatewayPort interbankGatewayPort;
    private final InterbankSettlementService interbankSettlementService;
    private final TransferLimitPort transferLimitPort;
    private final TransferAuthTokenVerificationPort transferAuthTokenVerificationPort;
    private final TransferOtpVerificationPort transferOtpVerificationPort;
    private final TransferSequencePort transferSequencePort;
    private final TransferSavePort transferSavePort;
    private final LedgerSavePort ledgerSavePort;
    private final JournalPostingUseCase journalPostingUseCase;
    private final List<TransferPreCheck> transferPreChecks;
    private final BusinessDateGate businessDateGate;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final TransactionTemplate requiresNewTransactionTemplate;

    public InterbankTransferService(
            AccountLockPort accountLockPort,
            InterbankBankPort interbankBankPort,
            InterbankGatewayPort interbankGatewayPort,
            InterbankSettlementService interbankSettlementService,
            TransferLimitPort transferLimitPort,
            TransferAuthTokenVerificationPort transferAuthTokenVerificationPort,
            TransferOtpVerificationPort transferOtpVerificationPort,
            TransferSequencePort transferSequencePort,
            TransferSavePort transferSavePort,
            LedgerSavePort ledgerSavePort,
            JournalPostingUseCase journalPostingUseCase,
            List<TransferPreCheck> transferPreChecks,
            BusinessDateGate businessDateGate,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.accountLockPort = accountLockPort;
        this.interbankBankPort = interbankBankPort;
        this.interbankGatewayPort = interbankGatewayPort;
        this.interbankSettlementService = interbankSettlementService;
        this.transferLimitPort = transferLimitPort;
        this.transferAuthTokenVerificationPort = transferAuthTokenVerificationPort;
        this.transferOtpVerificationPort = transferOtpVerificationPort;
        this.transferSequencePort = transferSequencePort;
        this.transferSavePort = transferSavePort;
        this.ledgerSavePort = ledgerSavePort;
        this.journalPostingUseCase = journalPostingUseCase;
        this.transferPreChecks = transferPreChecks.stream()
                .sorted(Comparator.comparingInt(TransferPreCheck::order))
                .toList();
        this.businessDateGate = businessDateGate;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public InterbankTransferResult execute(InterbankTransferCommand command) {
        // 요청 단계 거부(400)라 결과로 바꾸지 않고 던진다(interbank_transfer_fe_handoff.md §6-2).
        if (!interbankBankPort.isSupported(command.payeeBankCode())) {
            throw new BusinessException(TransferErrorCode.UNSUPPORTED_BANK);
        }
        LocalDateTime requestedAt = LocalDateTime.now(clock);
        Draft draft = null;
        Transfer pending;
        try {
            verifyBeforeNumbering(command);

            String transactionNumber = transferSequencePort.nextTransactionNumber(requestedAt.toLocalDate(), CHANNEL);
            draft = new Draft(transactionNumber, requestedAt);

            preCheckWithoutLock(command);
            runTransferPreChecks(command);
            transferOtpVerificationPort.verifyAndConsumeInterbank(
                    command.otpAuthToken(),
                    command.customerId(),
                    command.withdrawalAccountId(),
                    command.payeeBankCode(),
                    command.depositAccountNumber(),
                    command.amount());

            pending = debitAndHold(command, draft);
        } catch (BusinessException e) {
            if (draft == null) {
                return InterbankTransferResult.builder()
                        .status(ProcessResultStatus.ERROR)
                        .payeeBankCode(command.payeeBankCode())
                        .errorCode(e.getErrorCode().getCode())
                        .errorMessage(e.getMessage())
                        .build();
            }
            return toResult(failBeforeSend(command, draft, e.getErrorCode().getCode(), e.getMessage()));
        } catch (RuntimeException e) {
            recordInternalError(command, draft, e);
            throw e;
        }

        return toResult(sendAndSettle(pending));
    }

    // transfer 행이 생기기 전 검증. 여기서 거부하면 남길 행이 없다.
    private void verifyBeforeNumbering(InterbankTransferCommand command) {
        accountLockPort
                .findWithdrawalAccountDetail(command.withdrawalAccountId())
                .filter(detail -> detail.customerId().equals(command.customerId()) && detail.withdrawalRegistered())
                .orElseThrow(() -> new BusinessException(TransferErrorCode.WITHDRAWAL_ACCOUNT_NOT_REGISTERED));
        // 상대 은행 장애 중이면 출금·환급 없이 바로 거부한다. 당행 이체는 이 검사를 지나지 않는다.
        if (!interbankGatewayPort.isAvailable()) {
            throw new BusinessException(TransferErrorCode.INTERBANK_NOT_PROCESSED);
        }
        transferAuthTokenVerificationPort.verifyAndConsume(
                command.authToken(), command.customerId(), command.withdrawalAccountId());
    }

    // 락 없는 사전 체크. 통과 못 할 요청이 OTP를 태우지 않게 한다. 최종 판정은 1단계의 락 이후 재검증이다.
    private void preCheckWithoutLock(InterbankTransferCommand command) {
        accountLockPort
                .findWithdrawalAccountPreCheckSnapshot(command.withdrawalAccountId())
                .ifPresent(snapshot -> {
                    if (snapshot.status() != LockedAccountStatus.ACTIVE) {
                        throw new BusinessException(TransferErrorCode.WITHDRAWAL_ACCOUNT_SUSPENDED);
                    }
                    if (snapshot.balance() < command.amount()) {
                        throw new BusinessException(TransferErrorCode.INSUFFICIENT_BALANCE);
                    }
                });
    }

    // [훅 A] 당행 이체와 같은 사전검증 체인(transfer_seam.md 정책 1).
    private void runTransferPreChecks(InterbankTransferCommand command) {
        TransferPreCheckContext context =
                new TransferPreCheckContext(command.customerId(), command.withdrawalAccountId(), command.amount());
        for (TransferPreCheck preCheck : transferPreChecks) {
            preCheck.check(context);
        }
    }

    // 1단계. 한도 적립 → 출금계좌 락 → 재검증 → 출금 → 원장 1행 → 미결제 전표 → TIMEOUT으로 커밋.
    private Transfer debitAndHold(InterbankTransferCommand command, Draft draft) {
        return requiresNewTransactionTemplate.execute(status -> {
            // 가장 먼저 gate를 잡는다(gate → 한도 → 계좌). 한 건에 한 번만 읽어 transfer에 싣는다 — 원장·전표가 같은 거래일을 갖게(#516).
            Transfer created = newTransfer(command, draft, businessDateGate.acquireSharedAndReadBusinessDate());
            transferLimitPort.checkAndReserve(command.customerId(), command.amount());

            LockedAccount locked = accountLockPort.lockForCredit(command.withdrawalAccountId());
            if (locked.status() != LockedAccountStatus.ACTIVE) {
                throw new BusinessException(TransferErrorCode.WITHDRAWAL_ACCOUNT_SUSPENDED);
            }
            if (locked.balance() < command.amount()) {
                throw new BusinessException(TransferErrorCode.INSUFFICIENT_BALANCE);
            }

            Transfer transfer = transferSavePort.save(created);
            LocalDateTime executedAt = LocalDateTime.now(clock);
            long balanceAfter = accountLockPort.applyDebit(locked, command.amount(), executedAt);
            ledgerSavePort.saveDebit(LedgerEntry.forDebit(
                    transfer.getTransferId(),
                    transfer.getTransactionNumber(),
                    command.withdrawalAccountId(),
                    balanceAfter,
                    command.amount(),
                    InterbankSettlementService.LEDGER_TRANSACTION_TYPE,
                    command.myPassbookMemo(),
                    CHANNEL,
                    executedAt,
                    transfer.getTradeDate()));
            journalPostingUseCase.post(InterbankJournal.requested(
                    transfer.getTransactionNumber(), transfer.getTradeDate(), command.amount()));

            transfer.timeout();
            return transferSavePort.save(transfer);
        });
    }

    // 2단계. 응답을 못 받았거나 확정 중 오류가 나면 TIMEOUT 그대로 두고 조회거래에 맡긴다.
    private Transfer sendAndSettle(Transfer pending) {
        InterbankReply reply = interbankGatewayPort.send(new InterbankRequest(
                pending.getPayeeBankCode(),
                pending.getTransactionNumber(),
                pending.getDepositAccountNumber(),
                pending.getAmount(),
                pending.getRecipientPassbookMemo()));
        try {
            return interbankSettlementService
                    .settle(pending.getTransactionNumber(), reply)
                    .orElse(pending);
        } catch (RuntimeException e) {
            log.error(
                    "타행 송신 후 확정 실패 - 조회거래로 확정한다. transactionNumber={}, outcome={}",
                    pending.getTransactionNumber(),
                    reply.outcome(),
                    e);
            return pending;
        }
    }

    // 출금 전 실패. 1단계가 롤백돼 행이 없으므로 ERROR 행을 새로 남긴다(당행 failTransfer와 같다).
    private Transfer failBeforeSend(
            InterbankTransferCommand command, Draft draft, String errorCode, String errorMessage) {
        return requiresNewTransactionTemplate.execute(status -> {
            // 롤백된 1단계의 거래일을 쓰지 않고, 이 트랜잭션에서 gate로 읽는다.
            Transfer created = newTransfer(command, draft, businessDateGate.acquireSharedAndReadBusinessDate());
            created.fail(errorCode, errorMessage);
            Transfer failed = transferSavePort.save(created);
            TransferSettledFactory.create(
                            failed,
                            command.customerId(),
                            InterbankSettlementService.LEDGER_TRANSACTION_TYPE,
                            LocalDateTime.now(clock))
                    .ifPresent(eventPublisher::publishEvent);
            return failed;
        });
    }

    // 내부 오류도 ERROR 행을 남기되, 남기다 실패한 원인은 원래 예외에 붙여 둘 다 로그에 남긴다.
    private void recordInternalError(InterbankTransferCommand command, Draft draft, RuntimeException cause) {
        if (draft == null) {
            return;
        }
        try {
            failBeforeSend(
                    command,
                    draft,
                    CommonErrorCode.INTERNAL_ERROR.getCode(),
                    CommonErrorCode.INTERNAL_ERROR.getMessage());
        } catch (RuntimeException recordingFailure) {
            cause.addSuppressed(recordingFailure);
        }
    }

    private Transfer newTransfer(InterbankTransferCommand command, Draft draft, LocalDate tradeDate) {
        return Transfer.createInterbank(
                draft.transactionNumber(),
                command.withdrawalAccountId(),
                command.payeeBankCode(),
                command.depositAccountNumber(),
                command.amount(),
                FEE,
                CHANNEL,
                command.myPassbookMemo(),
                command.recipientPassbookMemo(),
                draft.requestedAt(),
                tradeDate);
    }

    private static InterbankTransferResult toResult(Transfer transfer) {
        boolean success = transfer.getStatus() == ProcessResultStatus.SUCCESS;
        return InterbankTransferResult.builder()
                .status(transfer.getStatus())
                .transactionNumber(transfer.getTransactionNumber())
                .transferredAt(success ? transfer.getTransferredAt() : null)
                .payeeBankCode(transfer.getPayeeBankCode())
                .payeeName(success ? transfer.getPayeeName() : null)
                .withdrawalBalanceAfter(success ? transfer.getWithdrawalBalanceAfter() : null)
                .errorCode(transfer.getErrorCode())
                .errorMessage(transfer.getErrorMessage())
                .build();
    }
}
