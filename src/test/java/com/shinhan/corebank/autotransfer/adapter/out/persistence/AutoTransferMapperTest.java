package com.shinhan.corebank.autotransfer.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.autotransfer.domain.AutoTransfer;
import com.shinhan.corebank.autotransfer.domain.AutoTransferStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AutoTransferMapperTest {

    @Test
    @DisplayName("JPA 엔티티를 도메인 객체로 변환하면 모든 필드가 보존된다")
    void toDomain_preservesAllFields() {
        AutoTransferJpaEntity entity = AutoTransferJpaEntity.builder()
                .autoTransferId(1L)
                .customerId(2L)
                .withdrawalAccountId(3L)
                .depositAccountNumber("110987654321")
                .payeeName("홍길동")
                .amount(10000L)
                .cycleMonths(1)
                .transferDay(15)
                .startDate(LocalDate.of(2025, 6, 2))
                .endDate(LocalDate.of(2027, 6, 2))
                .nextExecutionDate(LocalDate.of(2025, 6, 15))
                .myPassbookMemo("내메모")
                .recipientPassbookMemo("받는메모")
                .status(AutoTransferStatus.NORMAL)
                .registeredAt(LocalDateTime.of(2025, 6, 1, 0, 0))
                .updatedAt(LocalDateTime.of(2025, 6, 1, 0, 0))
                .build();

        AutoTransfer domain = AutoTransferMapper.toDomain(entity);

        assertThat(domain.getAutoTransferId()).isEqualTo(entity.getAutoTransferId());
        assertThat(domain.getCustomerId()).isEqualTo(entity.getCustomerId());
        assertThat(domain.getWithdrawalAccountId()).isEqualTo(entity.getWithdrawalAccountId());
        assertThat(domain.getDepositAccountNumber()).isEqualTo(entity.getDepositAccountNumber());
        assertThat(domain.getPayeeName()).isEqualTo(entity.getPayeeName());
        assertThat(domain.getAmount()).isEqualTo(entity.getAmount());
        assertThat(domain.getCycleMonths()).isEqualTo(entity.getCycleMonths());
        assertThat(domain.getTransferDay()).isEqualTo(entity.getTransferDay());
        assertThat(domain.getStartDate()).isEqualTo(entity.getStartDate());
        assertThat(domain.getEndDate()).isEqualTo(entity.getEndDate());
        assertThat(domain.getNextExecutionDate()).isEqualTo(entity.getNextExecutionDate());
        assertThat(domain.getMyPassbookMemo()).isEqualTo(entity.getMyPassbookMemo());
        assertThat(domain.getRecipientPassbookMemo()).isEqualTo(entity.getRecipientPassbookMemo());
        assertThat(domain.getStatus()).isEqualTo(entity.getStatus());
        assertThat(domain.getRegisteredAt()).isEqualTo(entity.getRegisteredAt());
        assertThat(domain.getTerminatedAt()).isEqualTo(entity.getTerminatedAt());
        assertThat(domain.getUpdatedAt()).isEqualTo(entity.getUpdatedAt());
    }

    @Test
    @DisplayName("도메인 객체를 JPA 엔티티로 변환하면 모든 필드가 보존된다")
    void toEntity_preservesAllFields() {
        AutoTransfer domain = AutoTransfer.register(
                2L,
                3L,
                "110987654321",
                "홍길동",
                10000L,
                1,
                15,
                LocalDate.of(2025, 6, 2),
                LocalDate.of(2027, 6, 2),
                "내메모",
                "받는메모",
                LocalDateTime.of(2025, 6, 1, 0, 0));

        AutoTransferJpaEntity entity = AutoTransferMapper.toEntity(domain);

        assertThat(entity.getAutoTransferId()).isEqualTo(domain.getAutoTransferId());
        assertThat(entity.getCustomerId()).isEqualTo(domain.getCustomerId());
        assertThat(entity.getWithdrawalAccountId()).isEqualTo(domain.getWithdrawalAccountId());
        assertThat(entity.getDepositAccountNumber()).isEqualTo(domain.getDepositAccountNumber());
        assertThat(entity.getPayeeName()).isEqualTo(domain.getPayeeName());
        assertThat(entity.getAmount()).isEqualTo(domain.getAmount());
        assertThat(entity.getCycleMonths()).isEqualTo(domain.getCycleMonths());
        assertThat(entity.getTransferDay()).isEqualTo(domain.getTransferDay());
        assertThat(entity.getStartDate()).isEqualTo(domain.getStartDate());
        assertThat(entity.getEndDate()).isEqualTo(domain.getEndDate());
        assertThat(entity.getNextExecutionDate()).isEqualTo(domain.getNextExecutionDate());
        assertThat(entity.getMyPassbookMemo()).isEqualTo(domain.getMyPassbookMemo());
        assertThat(entity.getRecipientPassbookMemo()).isEqualTo(domain.getRecipientPassbookMemo());
        assertThat(entity.getStatus()).isEqualTo(domain.getStatus());
        assertThat(entity.getRegisteredAt()).isEqualTo(domain.getRegisteredAt());
        assertThat(entity.getTerminatedAt()).isEqualTo(domain.getTerminatedAt());
        // register() 직후엔 updatedAt이 없다 — 실제 저장 시점에 @LastModifiedDate가 채워준다
        assertThat(entity.getUpdatedAt()).isEqualTo(domain.getUpdatedAt());
    }
}
