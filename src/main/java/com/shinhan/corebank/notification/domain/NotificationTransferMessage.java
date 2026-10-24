package com.shinhan.corebank.notification.domain;

import java.util.Locale;
import java.util.Map;

/**
 * 이체 결과 알림의 제목과 본문. 실패 사유는 고객이 직접 조치할 수 있는 것만 문구로 바꾸고, 나머지 코드는
 * 본문에 드러내지 않는다. 상대방 이름은 발행 때 마스킹된 값이며, 타행이체는 상대 은행이 성공 응답을
 * 주기 전에는 이름을 모르므로 없을 수 있다.
 */
public record NotificationTransferMessage(String title, String content) {

    private static final Map<String, String> CUSTOMER_REASON_BY_ERROR_CODE = Map.of(
            "TRF0303", "출금계좌 잔액이 부족합니다.",
            "TRF0304", "출금계좌가 거래정지 또는 해지 상태입니다.",
            "TRF0301", "입금계좌가 거래정지 또는 해지 상태입니다.",
            "TRF0308", "상대 은행에서 처리하지 못했습니다.");

    public static NotificationTransferMessage succeeded(long amount, String counterpartyName) {
        return new NotificationTransferMessage("이체 완료", recipientPhrase(amount, counterpartyName) + "을 보냈습니다.");
    }

    public static NotificationTransferMessage failed(long amount, String counterpartyName, String errorCode) {
        String content = recipientPhrase(amount, counterpartyName) + "을 보내지 못했습니다.";
        String customerReason = errorCode == null ? null : CUSTOMER_REASON_BY_ERROR_CODE.get(errorCode);
        if (customerReason != null) {
            content = content + " " + customerReason;
        }
        return new NotificationTransferMessage("이체 실패", content);
    }

    private static String recipientPhrase(long amount, String counterpartyName) {
        String groupedAmount = String.format(Locale.ROOT, "%,d원", amount);
        return counterpartyName == null ? groupedAmount : counterpartyName + "님에게 " + groupedAmount;
    }
}
