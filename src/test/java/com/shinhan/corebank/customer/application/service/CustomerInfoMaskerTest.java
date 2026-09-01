package com.shinhan.corebank.customer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CustomerInfoMaskerTest {

    private CustomerInfoMasker masker;

    @BeforeEach
    void setUp() {
        masker = new CustomerInfoMasker();
    }

    @Test
    @DisplayName("고객정보를 확정된 마스킹 규칙으로 변환한다")
    void masksCustomerInformation() {
        assertThat(masker.maskUserName("홍길동")).isEqualTo("홍*동");
        assertThat(masker.maskUserId("honggildong")).isEqualTo("hong*******");
        assertThat(masker.maskBirthDate(LocalDate.of(1995, 3, 10))).isEqualTo("1995-**-**");
        assertThat(masker.maskPhoneNumber("01012345678")).isEqualTo("010****5678");
        assertThat(masker.maskEmail("newmail@corebank.com")).isEqualTo("newm***@corebank.com");
    }

    @Test
    @DisplayName("로컬파트가 4자 이하인 이메일도 최소 한 자를 마스킹한다")
    void masksAtLeastOneEmailCharacter() {
        assertThat(masker.maskEmail("a@corebank.com")).isEqualTo("*@corebank.com");
        assertThat(masker.maskEmail("ab@corebank.com")).isEqualTo("a*@corebank.com");
        assertThat(masker.maskEmail("abcd@corebank.com")).isEqualTo("abc*@corebank.com");
    }

    @Test
    @DisplayName("마스킹할 고객정보 형식이 잘못되면 예외를 던진다")
    void rejectsInvalidCustomerInformation() {
        assertThatThrownBy(() -> masker.maskUserId("abcd")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> masker.maskBirthDate(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> masker.maskPhoneNumber("0101234")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> masker.maskEmail("invalid-email")).isInstanceOf(IllegalArgumentException.class);
    }
}
