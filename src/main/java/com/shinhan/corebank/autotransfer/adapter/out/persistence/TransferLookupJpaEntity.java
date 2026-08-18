package com.shinhan.corebank.autotransfer.adapter.out.persistence;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "transfer")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// transfer 테이블 거냥한 autotransfer 도메인 전용 경량 매핑 ( 조회 전용 )
public class TransferLookupJpaEntity {

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
