package com.shinhan.corebank.transfer.adapter.out.persistence;

import com.shinhan.corebank.transfer.domain.TransferHold;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TransferHoldMapper {

    // 실행 주체·사유·전이 시각은 조건부 UPDATE만 채운다. 새 보류를 저장할 때는 비어 있다.
    public static TransferHoldJpaEntity toEntity(TransferHold domain) {
        return TransferHoldJpaEntity.builder()
                .transferHoldId(domain.getTransferHoldId())
                .customerId(domain.getCustomerId())
                .withdrawalAccountId(domain.getWithdrawalAccountId())
                .depositAccountNumber(domain.getDepositAccountNumber())
                .amount(domain.getAmount())
                .myPassbookMemo(domain.getMyPassbookMemo())
                .recipientPassbookMemo(domain.getRecipientPassbookMemo())
                .channel(domain.getChannel())
                .flowId(domain.getFlowId())
                .policyCode(domain.getPolicyCode())
                .releaseMode(domain.getReleaseMode())
                .releaseAt(domain.getReleaseAt())
                .status(domain.getStatus())
                .authenticatedAt(domain.getAuthenticatedAt())
                .heldAt(domain.getHeldAt())
                .transferId(domain.getTransferId())
                .build();
    }

    public static TransferHold toDomain(TransferHoldJpaEntity entity) {
        return TransferHold.of(
                entity.getTransferHoldId(),
                entity.getCustomerId(),
                entity.getWithdrawalAccountId(),
                entity.getDepositAccountNumber(),
                entity.getAmount(),
                entity.getMyPassbookMemo(),
                entity.getRecipientPassbookMemo(),
                entity.getChannel(),
                entity.getFlowId(),
                entity.getPolicyCode(),
                entity.getReleaseMode(),
                entity.getReleaseAt(),
                entity.getStatus(),
                entity.getAuthenticatedAt(),
                entity.getHeldAt(),
                entity.getTransferId());
    }
}
