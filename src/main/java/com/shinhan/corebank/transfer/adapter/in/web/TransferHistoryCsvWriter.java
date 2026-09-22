package com.shinhan.corebank.transfer.adapter.in.web;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

// Excel에서 한글이 깨지지 않도록 UTF-8 BOM을 파일 맨 앞에 붙인다. CRLF는 CSV 표준(RFC 4180) 줄바꿈이다.
public class TransferHistoryCsvWriter {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final String HEADER_ROW = "거래번호,처리상태,처리일시,입금계좌번호,예금주명,금액,이체구분,채널,오류코드,실패사유";

    public static byte[] write(List<TransferHistoryItemResponse> items) {
        StringBuilder body = new StringBuilder();
        body.append(HEADER_ROW).append("\r\n");
        for (TransferHistoryItemResponse item : items) {
            body.append(toRow(item)).append("\r\n");
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(UTF8_BOM);
        out.writeBytes(body.toString().getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private static String toRow(TransferHistoryItemResponse item) {
        return String.join(
                ",",
                escape(item.transactionNumber()),
                escape(item.status().name()),
                escape(item.executedAt().toString()),
                escape(item.accountNumber()),
                escape(item.payeeName()),
                String.valueOf(item.amount()),
                escape(item.transferType().name()),
                escape(item.channel().name()),
                escape(item.errorCode()),
                escape(item.failureReason()));
    }

    // 값에 쉼표·큰따옴표·줄바꿈이 있으면 RFC 4180대로 큰따옴표로 감싸고, 내부 큰따옴표는 두 번 반복해 이스케이핑한다.
    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private TransferHistoryCsvWriter() {} // new 만드는거 방지
}
