package com.shinhan.corebank.customer.application.port.in;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AdminCustomerSearchQueryTest {

    @Test
    @DisplayName("공백뿐인 문자열 조건은 빼고, accountLocked=false는 지정한 조건으로 넣는다")
    void usedConditionNames_skipsBlankAndKeepsExplicitFalse() {
        AdminCustomerSearchQuery query = new AdminCustomerSearchQuery(" ", "홍길", "", false, null);

        assertThat(query.usedConditionNames()).containsExactly("userName", "accountLocked");
    }
}
