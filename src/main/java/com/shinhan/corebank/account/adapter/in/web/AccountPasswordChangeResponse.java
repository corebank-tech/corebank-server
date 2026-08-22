package com.shinhan.corebank.account.adapter.in.web;

import com.shinhan.corebank.account.application.port.in.ChangeAccountPasswordResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

// 계좌비밀번호 변경 완료 계좌와 변경 일시를 응답한다.
public record AccountPasswordChangeResponse(
        @Schema(description = "계좌 내부 식별자", example = "101")
        Long accountId,

        @Schema(
                description = "계좌비밀번호 변경 일시",
                example = "2026-08-22T13:30:00+09:00"
        )
        OffsetDateTime updatedAt
) {

    // application 결과를 계좌비밀번호 변경 API 응답으로 변환한다.
    public static AccountPasswordChangeResponse from(
            ChangeAccountPasswordResult result
    ) {
        return new AccountPasswordChangeResponse(
                result.accountId(),
                result.updatedAt()
        );
    }
}
