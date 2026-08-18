package com.shinhan.corebank.transfer.adapter.out.persistence;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AccountLockJpaEntity 잔액 연산 오버플로우 단위 테스트")
class AccountLockJpaEntityTest {

    @Test
    @DisplayName("credit 시 잔액이 Long 범위를 초과하면 ArithmeticException을 던진다(조용한 오버플로우 방지)")
    void credit_throwsArithmeticException_onOverflow() {
        AccountLockJpaEntity entity = AccountLockJpaEntity.builder()
                .accountId(1L)
                .balance(Long.MAX_VALUE)
                .status("ACTIVE")
                .build();

        assertThatThrownBy(() -> entity.credit(1L, LocalDateTime.now()))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    @DisplayName("debit 시 잔액이 Long 범위를 초과하면 ArithmeticException을 던진다(조용한 언더플로우 방지)")
    void debit_throwsArithmeticException_onUnderflow() {
        AccountLockJpaEntity entity = AccountLockJpaEntity.builder()
                .accountId(1L)
                .balance(Long.MIN_VALUE)
                .status("ACTIVE")
                .build();

        assertThatThrownBy(() -> entity.debit(1L, LocalDateTime.now()))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    @DisplayName("credit 시 executedAt이 null이면 NullPointerException을 던진다(#150)")
    void credit_throwsNullPointerException_whenExecutedAtIsNull() {
        AccountLockJpaEntity entity = AccountLockJpaEntity.builder()
                .accountId(1L)
                .balance(0L)
                .status("ACTIVE")
                .build();

        assertThatThrownBy(() -> entity.credit(1L, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("debit 시 executedAt이 null이면 NullPointerException을 던진다(#150)")
    void debit_throwsNullPointerException_whenExecutedAtIsNull() {
        AccountLockJpaEntity entity = AccountLockJpaEntity.builder()
                .accountId(1L)
                .balance(100L)
                .status("ACTIVE")
                .build();

        assertThatThrownBy(() -> entity.debit(1L, null))
                .isInstanceOf(NullPointerException.class);
    }
}
