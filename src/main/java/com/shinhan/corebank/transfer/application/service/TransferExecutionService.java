package com.shinhan.corebank.transfer.application.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.transfer.application.port.in.TransferCommand;
import com.shinhan.corebank.transfer.application.port.in.TransferExecutionUseCase;
import com.shinhan.corebank.transfer.application.port.in.TransferResult;
import com.shinhan.corebank.transfer.application.port.out.AccountLockPort;
import com.shinhan.corebank.transfer.application.port.out.LedgerSavePort;
import com.shinhan.corebank.transfer.application.port.out.LockedAccountStatus;
import com.shinhan.corebank.transfer.application.port.out.LockedAccountType;
import com.shinhan.corebank.transfer.application.port.out.LockedAccountsForTransfer;
import com.shinhan.corebank.transfer.application.port.out.ResolvedPayee;
import com.shinhan.corebank.transfer.application.port.out.TransferBalances;
import com.shinhan.corebank.transfer.application.port.out.TransferAuthTokenVerificationPort;
import com.shinhan.corebank.transfer.application.port.out.TransferSavePort;
import com.shinhan.corebank.transfer.application.port.out.TransferLimitPort;
import com.shinhan.corebank.transfer.application.port.out.TransferSequencePort;
import com.shinhan.corebank.transfer.domain.LedgerPair;
import com.shinhan.corebank.transfer.domain.Transfer;
import com.shinhan.corebank.transfer.domain.TransferSourceType;
import com.shinhan.corebank.transfer.domain.TransferType;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 원장 기표 엔진의 유스케이스 구현체.
 * [계좌번호→ID 해석 → 채번 → (독립 트랜잭션) 계좌 락 획득 → 계좌번호 재검증 → 잔액검증
 * → 잔액 UPDATE → 원장 2행 INSERT → 이체 완료 기록]을 수행한다.
 *
 * <p>기표(성공)와 ERROR 확정 기록(실패) 모두 {@code requiresNewTransactionTemplate}으로 독립
 * 커밋한다 — execute()를 호출자(P5 자동이체 배치 등)의 트랜잭션 안에서 불러도 결과는 호출자
 * 트랜잭션 성공 여부와 무관하게 남는다(REQUIRED였다면 호출자가 나중에 실패할 때 ERROR 행까지
 * 같이 롤백됨). 대신 SUCCESS 반환 시점에 자금은 이미 확정 이동한 것이므로, 호출자가 이후
 * 자신의 트랜잭션에서 하는 후속 작업(실행 이력 갱신 등)이 실패해도 그 자금 이동은 롤백되지
 * 않는다 — 호출자는 이를 "재시도 가능한 실패"가 아니라 "후속 처리 실패, 수동 정합화 필요"로
 * 다뤄야 한다.
 * @Primary: MockTransferExecutionPort와 test/local 프로필에서 같이 뜨는 동안, 타입 기반으로
 * TransferExecutionUseCase를 주입받는 소비자(AutoTransferBatchItemProcessor 등)가 두 빈 중
 * 무엇을 받을지 모호해지는 것을 막는다. 실제 구현체가 나온 이상 기본 후보는 이쪽이어야 한다.
 */
@Service
@Primary
public class TransferExecutionService implements TransferExecutionUseCase {

    private static final long FEE = 0L; // 당행 이체 수수료 0 고정 (POL-028)

    private final AccountLockPort accountLockPort;
    private final TransferLimitPort transferLimitPort;
    private final TransferAuthTokenVerificationPort transferAuthTokenVerificationPort;
    private final TransferSequencePort transferSequencePort;
    private final TransferSavePort transferSavePort;
    private final LedgerSavePort ledgerSavePort;
    private final Clock clock;
    private final TransactionTemplate requiresNewTransactionTemplate;

    public TransferExecutionService(
            AccountLockPort accountLockPort,
            TransferLimitPort transferLimitPort,
            TransferAuthTokenVerificationPort transferAuthTokenVerificationPort,
            TransferSequencePort transferSequencePort,
            TransferSavePort transferSavePort,
            LedgerSavePort ledgerSavePort,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.accountLockPort = accountLockPort;
        this.transferLimitPort = transferLimitPort;
        this.transferAuthTokenVerificationPort = transferAuthTokenVerificationPort;
        this.transferSequencePort = transferSequencePort;
        this.transferSavePort = transferSavePort;
        this.ledgerSavePort = ledgerSavePort;
        this.clock = clock;
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public TransferResult execute(TransferCommand command) {
        // 계좌 락 획득 전 시각이므로 채번(영업일자)에만 쓴다. 락 대기로 실제 처리가 지연될 수
        // 있어 원장·완료 기록에는 락 획득 이후 새로 캡처하는 executedAt을 쓴다.
        LocalDateTime requestedAt = LocalDateTime.now(clock);

        // 1차 검증(락 이전) ⓪: 인증 완료 토큰. IMMEDIATE만 authToken이 있으므로(TransferCommand
        // 계약) SCHEDULED/AUTO(시스템 트리거, 세션 없음)는 이 검증을 건너뛴다.
        if (command.authToken() != null) {
            transferAuthTokenVerificationPort.verify(
                    command.authToken(), command.withdrawalAccountId(), "TRANSFER_EXECUTE");
        }

        // 1차 검증(락 이전) ①: 출금계좌 소유·등록 여부. 존재하지 않는 계좌도 "내 소유가 아님"과
        // 구분하지 않고 같은 TRF0001로 묶는다 — 계좌ID가 이제 HTTP 요청 바디로 들어오므로
        // 계좌 존재 여부를 스캐닝하는 데 악용되지 않도록 한다.
        accountLockPort.findWithdrawalAccountDetail(command.withdrawalAccountId())
                .filter(detail -> detail.customerId().equals(command.customerId()) && detail.withdrawalRegistered())
                .orElseThrow(() -> new BusinessException(TransferErrorCode.WITHDRAWAL_ACCOUNT_NOT_REGISTERED));

        ResolvedPayee payee = accountLockPort.resolvePayeeByAccountNumber(command.depositAccountNumber())
                .orElseThrow(() -> new BusinessException(TransferErrorCode.PAYEE_NOT_FOUND));

        // 1차 검증(락 이전) ②: 입금계좌 상품유형. 정기예금(TIME_DEPOSIT)은 만기까지 목돈을
        // 묶어두는 상품이라 이체로 추가 입금할 수 없다. 정기적금(INSTALLMENT_SAVINGS)은 매달
        // 나눠 넣는 게 상품 목적이라 허용한다(REQ-TRSF-030).
        if (payee.accountType() == LockedAccountType.TIME_DEPOSIT) {
            throw new BusinessException(TransferErrorCode.UNSUPPORTED_ACCOUNT_TYPE);
        }

        String transactionNumber =
                transferSequencePort.nextTransactionNumber(requestedAt.toLocalDate(), command.channel());

        // 실패해도 ERROR로 남길 수 있도록, DB에 아직 저장되지 않은(transferId=null) 상태로
        // 들고 있는다. 아래 트랜잭션이 실패해 롤백되더라도 이 자바 객체 자체는 영향받지 않는다.
        Transfer created = Transfer.create(
                transactionNumber,
                command.withdrawalAccountId(),
                payee.accountId(),
                command.depositAccountNumber(),
                payee.payeeName(),
                command.amount(),
                FEE,
                command.transferType(),
                command.channel(),
                resolveSourceType(command.transferType()),
                command.sourceId(),
                command.myPassbookMemo(),
                command.recipientPassbookMemo(),
                requestedAt
        );

        try {
            Transfer completed = requiresNewTransactionTemplate.execute(status -> {
                // 한도 락을 계좌 락보다 먼저 획득한다(P1-P4 합의: 한도 → 계좌 순서). 위반 시
                // LMT0002/LMT0003을 던진다 — 여기서 예외가 나면 아래 계좌 락은 아예 시도되지 않는다.
                transferLimitPort.checkAndReserve(command.customerId(), command.amount());

                // 계좌 락을 transfer 행 INSERT보다 먼저 잡는다. INSERT는 withdrawal_account_id·
                // deposit_account_id에 FK가 걸려 있어 대상 계좌 행에 공유락을 요구하는데, 그 순서가
                // lockForTransfer의 오름차순 규칙을 따르지 않는다(선언 순서=출금→입금). INSERT를
                // 먼저 하면 두 스레드가 서로 다른 순서로 공유락을 쥔 채 배타락으로 승격하려다
                // 데드락이 난다 — 락을 먼저 잡아두면 그 시점엔 이미 우리가 배타락을 쥐고 있어
                // FK의 공유락 요구가 자기 자신과 충돌하지 않는다.
                LockedAccountsForTransfer locked =
                        accountLockPort.lockForTransfer(command.withdrawalAccountId(), payee.accountId());

                // 계좌번호 조회(락 없음)와 락 획득 사이에 입금계좌 매핑이 바뀌지 않았는지 재확인한다.
                if (!command.depositAccountNumber().equals(locked.deposit().accountNumber())) {
                    throw new BusinessException(TransferErrorCode.PAYEE_NOT_FOUND);
                }

                // 계좌번호 사전 조회 시점 이후 락을 얻기까지 사이에 계좌가 정지/해지됐을 수 있으므로,
                // 락으로 얻은 최신 상태를 기준으로 재검증한다.
                if (locked.withdrawal().status() != LockedAccountStatus.ACTIVE) {
                    throw new BusinessException(TransferErrorCode.WITHDRAWAL_ACCOUNT_SUSPENDED);
                }
                if (locked.deposit().status() != LockedAccountStatus.ACTIVE) {
                    throw new BusinessException(TransferErrorCode.PAYEE_ACCOUNT_SUSPENDED);
                }

                if (locked.withdrawal().balance() < command.amount()) {
                    throw new BusinessException(TransferErrorCode.INSUFFICIENT_BALANCE);
                }

                Transfer transfer = transferSavePort.save(created);

                TransferBalances balances = accountLockPort.applyTransfer(locked, command.amount());

                LocalDateTime executedAt = LocalDateTime.now(clock);

                LedgerPair pair = LedgerPair.forTransfer(
                        transfer.getTransferId(),
                        transactionNumber,
                        command.withdrawalAccountId(),
                        balances.withdrawalBalanceAfter(),
                        payee.accountId(),
                        balances.depositBalanceAfter(),
                        command.amount(),
                        resolveTransactionType(command.transferType()),
                        command.myPassbookMemo(),
                        command.recipientPassbookMemo(),
                        command.channel(),
                        executedAt
                );
                ledgerSavePort.save(pair);

                transfer.complete(balances.withdrawalBalanceAfter(), executedAt);
                return transferSavePort.save(transfer);
            });

            return TransferResult.builder()
                    .status(completed.getStatus())
                    .transactionNumber(completed.getTransactionNumber())
                    .transferredAt(completed.getTransferredAt())
                    .withdrawalBalanceAfter(completed.getWithdrawalBalanceAfter())
                    .build();
        } catch (BusinessException e) {
            return failTransfer(created, e.getErrorCode().getCode(), e.getMessage(), e);
        } catch (RuntimeException e) {
            failTransfer(created, CommonErrorCode.INTERNAL_ERROR.getCode(), CommonErrorCode.INTERNAL_ERROR.getMessage(), e);
            throw e;
        }
    }

    /**
     * 이체 시도를 ERROR로 확정해 새 행으로 남긴다. 위 트랜잭션이 이미 롤백되어 같은
     * transaction_number를 가진 PROCESSING 행은 존재하지 않으므로 새 INSERT로 처리된다.
     *
     * <p>이 INSERT도 {@code requiresNewTransactionTemplate}(REQUIRES_NEW)으로 독립 커밋한다.
     * execute()가 호출자(P5 자동이체 배치 등)의 트랜잭션 안에서 실행됐다면, 앰비언트 트랜잭션에
     * 그냥 합류시킬 경우 이후 호출자 쪽에서 예외가 나 그 트랜잭션이 롤백될 때 방금 남긴 ERROR
     * 확정 행까지 함께 사라진다 — "실패해도 흔적을 남긴다"는 이 클래스의 핵심 보장이 호출자
     * 트랜잭션의 성공 여부에 좌우돼서는 안 되므로, 성공 경로와 대칭적으로 항상 독립 트랜잭션에서
     * 커밋한다.
     *
     * <p>withdrawal_account_id·deposit_account_id는 transfer 테이블에 FK(NOT NULL)로 걸려
     * 있다. {@code ACCOUNT_LOCK_TARGET_NOT_FOUND}처럼 애초에 존재하지 않는 계좌가 원인인
     * 실패는 이 ERROR 확정 INSERT 자체가 FK 위반으로 실패한다 — 정상 흐름에서는 도달할 수
     * 없는 불변식 위반이므로 원장 기표 없이도 어차피 남길 수 없는 경우다. 이때는 기록 실패
     * 원인(recordingFailure)을 버리지 않고 원래 예외(cause)에 suppressed로 붙여 함께 던져,
     * 어느 쪽 원인도 로그에서 유실되지 않게 한다.
     */
    private TransferResult failTransfer(Transfer created, String errorCode, String errorMessage, RuntimeException cause) {
        created.fail(errorCode, errorMessage);
        try {
            requiresNewTransactionTemplate.executeWithoutResult(status -> transferSavePort.save(created));
        } catch (RuntimeException recordingFailure) {
            if (recordingFailure != cause) {
                cause.addSuppressed(recordingFailure);
            }
            throw cause;
        }

        return TransferResult.builder()
                .status(ProcessResultStatus.ERROR)
                .transactionNumber(created.getTransactionNumber())
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }

    private TransferSourceType resolveSourceType(TransferType transferType) {
        return switch (transferType) {
            case SCHEDULED -> TransferSourceType.SCHEDULED;
            case AUTO -> TransferSourceType.AUTO;
            case IMMEDIATE -> null;
        };
    }

    private String resolveTransactionType(TransferType transferType) {
        return switch (transferType) {
            case IMMEDIATE -> "IMMEDIATE_TRANSFER";
            case SCHEDULED -> "SCHEDULED_TRANSFER";
            case AUTO -> "AUTO_TRANSFER";
        };
    }
}
