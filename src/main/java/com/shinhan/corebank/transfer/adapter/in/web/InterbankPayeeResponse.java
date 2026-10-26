package com.shinhan.corebank.transfer.adapter.in.web;

import com.shinhan.corebank.common.util.MaskingUtil;
import com.shinhan.corebank.transfer.application.port.in.InterbankPayeeInquiryResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record InterbankPayeeResponse(
        @Schema(description = "상대 은행이 확인한 예금주명 (마스킹, 예: 김*수)", example = "김*수") String payeeName) {

    public static InterbankPayeeResponse from(InterbankPayeeInquiryResult result) {
        return new InterbankPayeeResponse(MaskingUtil.maskName(result.payeeName()));
    }
}
