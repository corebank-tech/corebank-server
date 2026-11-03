package com.shinhan.corebank.transfer.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "delayed_transfer_setting")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class DelayedTransferSettingJpaEntity {

    @Id
    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "threshold_amount", nullable = false)
    private Long thresholdAmount;

    @Column(name = "delay_hours", nullable = false)
    private Integer delayHours;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    void change(boolean enabled, long thresholdAmount, int delayHours, LocalDateTime now) {
        this.enabled = enabled;
        this.thresholdAmount = thresholdAmount;
        this.delayHours = delayHours;
        this.updatedAt = now;
    }
}
