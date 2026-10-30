package com.shinhan.corebank.customer.adapter.in.web.admin;

import com.shinhan.corebank.customer.domain.model.CustomerStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

// 본문의 enum 역직렬화 실패는 공통 처리기가 CMN9999로 바꾸므로, 문자열로 받아 @Pattern으로 CMN0001을 낸다.
public record AdminStatusChangeRequest(
        @Schema(description = "바꿀 계정 상태. ACTIVE(정상) / SUSPENDED(이용정지)", example = "SUSPENDED")
                @NotNull
                @Pattern(regexp = "ACTIVE|SUSPENDED")
                String status) {

    CustomerStatus toStatus() {
        return CustomerStatus.valueOf(status);
    }
}
