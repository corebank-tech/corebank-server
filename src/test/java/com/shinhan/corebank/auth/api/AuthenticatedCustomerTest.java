package com.shinhan.corebank.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AuthenticatedCustomerTest {

    @Test
    void exposesStableIdentityWithoutLeakingProfileInLogs() {
        var customer = new AuthenticatedCustomer(638001L, "session-user", "홍길동");

        assertThat(customer.customerId()).isEqualTo(638001L);
        assertThat(customer.getName()).isEqualTo("638001");
        assertThat(customer.userId()).isEqualTo("session-user");
        assertThat(customer.userName()).isEqualTo("홍길동");
        assertThat(customer.toString()).contains("638001").doesNotContain("session-user", "홍길동");
    }

    @Test
    void comparesAllSerializedIdentityFields() {
        var customer = new AuthenticatedCustomer(638001L, "session-user", "홍길동");
        var same = new AuthenticatedCustomer(638001L, "session-user", "홍길동");

        // 재역직렬화된 동일 인증 객체와 필드별 변경 객체를 구분한다.
        assertThat(customer).isEqualTo(customer).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(customer).isNotEqualTo(null).isNotEqualTo("638001");
        assertThat(customer).isNotEqualTo(new AuthenticatedCustomer(638002L, "session-user", "홍길동"));
        assertThat(customer).isNotEqualTo(new AuthenticatedCustomer(638001L, "other-user", "홍길동"));
        assertThat(customer).isNotEqualTo(new AuthenticatedCustomer(638001L, "session-user", "다른이름"));
    }

    @Test
    void requiresCustomerIdForSessionIndex() {
        assertThatThrownBy(() -> new AuthenticatedCustomer(null, "session-user", "홍길동"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("customerId must not be null");
    }
}
