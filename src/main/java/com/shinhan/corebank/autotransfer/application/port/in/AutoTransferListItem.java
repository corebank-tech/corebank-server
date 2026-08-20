package com.shinhan.corebank.autotransfer.application.port.in;

import com.shinhan.corebank.autotransfer.domain.AutoTransfer;

// AutoTransfer 필드를 그대로 재사용 - 목록조회는 계좌가 항상 하나뿐이라 별칭만 덧붙이면 된다(#236)
public record AutoTransferListItem(AutoTransfer autoTransfer, String fromAlias) {
}
