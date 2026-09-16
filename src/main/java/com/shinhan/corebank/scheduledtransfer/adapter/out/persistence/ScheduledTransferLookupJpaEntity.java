package com.shinhan.corebank.scheduledtransfer.adapter.out.persistence;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "transfer")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// transfer 테이블 겨냥한 scheduledtransfer 도메인 전용 경량 매핑 (조회 전용).
// autotransfer의 TransferLookupJpaEntity와 클래스명이 같으면 JPQL 엔티티명이 충돌해서
// ScheduledTransferLookupJpaEntity로 이름을 구분한다.
public class ScheduledTransferLookupJpaEntity {

    @Id
    @Column(name = "transfer_id")
    private Long transferId;

    @Column(name = "transaction_number", nullable = false, length = 20, insertable = false, updatable = false)
    private String transactionNumber;

    @Column(name = "source_type", length = 12, insertable = false, updatable = false)
    private String sourceType;

    @Column(name = "source_id", insertable = false, updatable = false)
    private Long sourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12, insertable = false, updatable = false)
    private ProcessResultStatus status;

    @Column(name = "error_message", length = 200, insertable = false, updatable = false)
    private String errorMessage;

    @Column(name = "transferred_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime transferredAt;
}
