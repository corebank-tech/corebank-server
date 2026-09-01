package com.shinhan.corebank.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApiResponseTest {

    @Test
    @DisplayName("success(data) -> code 0000, 기본 메시지, data 포함")
    void success_withData() {
        ApiResponse<String> response = ApiResponse.success("hello");

        assertThat(response.code()).isEqualTo("0000");
        assertThat(response.message()).isEqualTo("정상 처리되었습니다.");
        assertThat(response.data()).isEqualTo("hello");
    }

    @Test
    @DisplayName("success(data, message) -> 커스텀 메시지가 그대로 반영")
    void success_withCustomMessage() {
        ApiResponse<String> response = ApiResponse.success("hello", "자동이체가 등록되었습니다.");

        assertThat(response.code()).isEqualTo("0000");
        assertThat(response.message()).isEqualTo("자동이체가 등록되었습니다.");
        assertThat(response.data()).isEqualTo("hello");
    }

    @Test
    @DisplayName("success() -> data 는 null, code/message 는 기본값")
    void success_withoutData() {
        ApiResponse<Void> response = ApiResponse.success();

        assertThat(response.code()).isEqualTo("0000");
        assertThat(response.message()).isEqualTo("정상 처리되었습니다.");
        assertThat(response.data()).isNull();
    }
}
