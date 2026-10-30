package com.shinhan.corebank.info.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("정보계 조회 오류코드")
class InfoErrorCodeTest {

    @Test
    @DisplayName("연결 실패는 INF9001 · 500 으로 api_conventions.md §4-15 마스터와 같다")
    void connectionFailedFollowsMaster() {
        assertThat(InfoErrorCode.CONNECTION_FAILED.getCode()).isEqualTo("INF9001");
        assertThat(InfoErrorCode.CONNECTION_FAILED.getStatus()).isEqualTo(500);
        assertThat(InfoErrorCode.CONNECTION_FAILED.getMessage()).isEqualTo("정보계 API에 연결하지 못했습니다.");
    }

    @Test
    @DisplayName("INF 접두어 4자리 번호 형식이다")
    void codesFollowFormat() {
        for (InfoErrorCode code : InfoErrorCode.values()) {
            assertThat(code.getCode()).matches("INF\\d{4}");
        }
    }
}
