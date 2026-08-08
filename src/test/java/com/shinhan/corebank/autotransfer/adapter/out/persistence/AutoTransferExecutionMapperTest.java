package com.shinhan.corebank.autotransfer.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.autotransfer.domain.AutoTransferExecution;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AutoTransferExecutionMapperTest {

    @Test
    @DisplayName("JPA 엔티티를 도메인 객체로 변환하면 모든 필드가 보존된다")
    void toDomain_preservesAllFields() {
        AutoTransferExecutionJpaEntity entity = AutoTransferExecutionJpaEntity.builder()
                .executionId(1L)
                .executionDate(LocalDate.of(2025, 6, 15))
                .amount(10000L)
                .status(ProcessResultStatus.SUCCESS)
                .transactionNumber("TXN0001")
                .failureReason(null)
                .executedAt(LocalDateTime.of(2025, 6, 15, 9, 0))
                .build();

        AutoTransferExecution domain = AutoTransferExecutionMapper.toDomain(entity);

        assertThat(domain.getExecutionId()).isEqualTo(entity.getExecutionId());
        assertThat(domain.getExecutionDate()).isEqualTo(entity.getExecutionDate());
        assertThat(domain.getAmount()).isEqualTo(entity.getAmount());
        assertThat(domain.getStatus()).isEqualTo(entity.getStatus());
        assertThat(domain.getTransactionNumber()).isEqualTo(entity.getTransactionNumber());
        assertThat(domain.getFailureReason()).isEqualTo(entity.getFailureReason());
        assertThat(domain.getExecutedAt()).isEqualTo(entity.getExecutedAt());
    }

    @Test
    @DisplayName("도메인 객체를 JPA 엔티티로 변환하면 모든 필드와 부모 엔티티가 보존된다")
    void toEntity_preservesAllFieldsAndParent() {
        AutoTransferExecution domain = AutoTransferExecution.processing(
                LocalDate.of(2025, 6, 15), 10000L, LocalDateTime.of(2025, 6, 15, 9, 0));
        domain.markSuccess("TXN0001");

        AutoTransferJpaEntity parent = AutoTransferJpaEntity.builder()
                .autoTransferId(99L)
                .build();

        AutoTransferExecutionJpaEntity entity = AutoTransferExecutionMapper.toEntity(domain, parent);

        assertThat(entity.getExecutionId()).isEqualTo(domain.getExecutionId());
        assertThat(entity.getAutoTransfer()).isSameAs(parent);
        assertThat(entity.getExecutionDate()).isEqualTo(domain.getExecutionDate());
        assertThat(entity.getAmount()).isEqualTo(domain.getAmount());
        assertThat(entity.getStatus()).isEqualTo(domain.getStatus());
        assertThat(entity.getTransactionNumber()).isEqualTo(domain.getTransactionNumber());
        assertThat(entity.getFailureReason()).isEqualTo(domain.getFailureReason());
        assertThat(entity.getExecutedAt()).isEqualTo(domain.getExecutedAt());
    }
}