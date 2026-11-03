package com.shinhan.corebank.transfer.adapter.out.persistence;

import com.shinhan.corebank.transfer.api.TransferHoldReleaseMode;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferHoldStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 상태 전이는 TransferHoldJpaRepository의 조건부 UPDATE로만 한다. 그래서 setter와 @Version이 없다.
@Entity
@Table(name = "transfer_hold")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class TransferHoldJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transfer_hold_id")
    private Long transferHoldId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "withdrawal_account_id", nullable = false)
    private Long withdrawalAccountId;

    @Column(name = "deposit_account_number", nullable = false, length = 16)
    private String depositAccountNumber;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "my_passbook_memo", length = 10)
    private String myPassbookMemo;

    @Column(name = "recipient_passbook_memo", length = 10)
    private String recipientPassbookMemo;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, columnDefinition = "CHAR(2)")
    private TransferChannel channel;

    @Column(name = "flow_id", length = 64)
    private String flowId;

    @Column(name = "policy_code", nullable = false, length = 30)
    private String policyCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "release_mode", nullable = false, length = 12)
    private TransferHoldReleaseMode releaseMode;

    @Column(name = "release_at")
    private LocalDateTime releaseAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private TransferHoldStatus status;

    @Column(name = "authenticated_at", nullable = false)
    private LocalDateTime authenticatedAt;

    @Column(name = "held_at", nullable = false)
    private LocalDateTime heldAt;

    @Column(name = "transfer_id")
    private Long transferId;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "reason_code", length = 30)
    private String reasonCode;

    @Column(name = "executing_at")
    private LocalDateTime executingAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
