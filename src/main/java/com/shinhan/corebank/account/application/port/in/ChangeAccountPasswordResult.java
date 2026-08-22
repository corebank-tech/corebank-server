package com.shinhan.corebank.account.application.port.in;

import java.time.OffsetDateTime;

// 계좌비밀번호 변경 완료 계좌와 변경 시각을 반환한다.
public record ChangeAccountPasswordResult(
        Long accountId,
        OffsetDateTime updatedAt
) {
}
