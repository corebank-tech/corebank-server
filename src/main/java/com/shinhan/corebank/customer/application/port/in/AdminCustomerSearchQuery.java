package com.shinhan.corebank.customer.application.port.in;

import com.shinhan.corebank.customer.domain.model.CustomerStatus;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;

// 관리자 고객 검색 입력. 빈 문자열은 조건 없음으로 보고, 조건이 하나도 없으면 거부한다.
public record AdminCustomerSearchQuery(
        String userId, String userName, String email, Boolean accountLocked, CustomerStatus status) {

    // 조회 감사에 남길, 실제로 지정된 조건의 이름. accountLocked=false도 지정한 조건이라 넣는다.
    public List<String> usedConditionNames() {
        List<String> names = new ArrayList<>();
        if (StringUtils.hasText(userId)) {
            names.add("userId");
        }
        if (StringUtils.hasText(userName)) {
            names.add("userName");
        }
        if (StringUtils.hasText(email)) {
            names.add("email");
        }
        if (accountLocked != null) {
            names.add("accountLocked");
        }
        if (status != null) {
            names.add("status");
        }
        return names;
    }
}
