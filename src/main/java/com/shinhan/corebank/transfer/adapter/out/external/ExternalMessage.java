package com.shinhan.corebank.transfer.adapter.out.external;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * 타행 이체 전문 한 장 (docs/phase2/fixed_length_message_spec.md §2). 바이트 변환은 ExternalMessageCodec이 한다.
 *
 * @param bankCode 상대 기관(수신 은행) 코드 3자리. 요청·응답 모두 같은 값이다
 * @param serialNumber 원거래의 transfer.transaction_number. 조회거래도 원거래 번호를 싣는다
 * @param responseCode 요청이면 null
 * @param payeeName 상대 기관이 확인한 수취인 이름. 요청이면 null
 * @param memo 받는 분 통장 표시. 없으면 null
 */
public record ExternalMessage(
        MessageKind kind,
        TransactionCode transactionCode,
        LocalDateTime sentAt,
        String bankCode,
        String serialNumber,
        ResponseCode responseCode,
        String accountNumber,
        long amount,
        String payeeName,
        String memo) {

    private static final Pattern BANK_CODE = Pattern.compile("\\d{3}");
    private static final Pattern ACCOUNT_NUMBER = Pattern.compile("\\d+");

    public ExternalMessage {
        if (kind == null || transactionCode == null || sentAt == null) {
            throw new IllegalArgumentException("전문종별·거래코드·전송일시는 비울 수 없다");
        }
        if (bankCode == null || !BANK_CODE.matcher(bankCode).matches()) {
            throw new IllegalArgumentException("기관코드는 숫자 3자리다");
        }
        if (serialNumber == null || serialNumber.isBlank()) {
            throw new IllegalArgumentException("일련번호는 비울 수 없다");
        }
        if (accountNumber == null || !ACCOUNT_NUMBER.matcher(accountNumber).matches()) {
            throw new IllegalArgumentException("계좌번호는 하이픈 없이 숫자만 쓴다");
        }
        if ((kind == MessageKind.REQUEST) != (responseCode == null)) {
            throw new IllegalArgumentException("응답코드는 응답 전문에만 있다");
        }
    }

    public static ExternalMessage request(
            TransactionCode transactionCode,
            LocalDateTime sentAt,
            String bankCode,
            String serialNumber,
            String accountNumber,
            long amount,
            String memo) {
        return new ExternalMessage(
                MessageKind.REQUEST,
                transactionCode,
                sentAt,
                bankCode,
                serialNumber,
                null,
                accountNumber,
                amount,
                null,
                memo);
    }

    /** 요청의 헤더·본문을 그대로 돌려주고 응답코드·예금주·전송일시만 채운다. */
    public ExternalMessage toResponse(ResponseCode responseCode, String payeeName, LocalDateTime sentAt) {
        if (kind != MessageKind.REQUEST) {
            throw new IllegalArgumentException("요청 전문에만 응답할 수 있다");
        }
        return new ExternalMessage(
                MessageKind.RESPONSE,
                transactionCode,
                sentAt,
                bankCode,
                serialNumber,
                responseCode,
                accountNumber,
                amount,
                payeeName,
                memo);
    }
}
