package com.shinhan.corebank.transfer.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.transfer.application.port.in.InterbankPayeeInquiryResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InterbankPayeeResponseTest {

    @Test
    @DisplayName("상대 은행이 돌려준 예금주는 타행이체 응답과 같은 규칙으로 가운데를 마스킹한다")
    void from_masksPayeeName() {
        assertThat(InterbankPayeeResponse.from(new InterbankPayeeInquiryResult("김민수"))
                        .payeeName())
                .isEqualTo("김*수");
    }
}
