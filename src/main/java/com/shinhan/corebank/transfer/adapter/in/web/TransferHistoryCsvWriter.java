package com.shinhan.corebank.transfer.adapter.in.web;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

// Excel에서 한글이 깨지지 않도록 UTF-8 BOM을 파일 맨 앞에 붙인다. CRLF는 CSV 표준(RFC 4180) 줄바꿈이다.
public class TransferHistoryCsvWriter {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final String HEADER_ROW = "거래번호,처리상태,처리일시,입금계좌번호,예금주명,금액,이체구분,채널,오류코드,실패사유";
    // OffsetDateTime#toString()은 초가 0이면 생략해 열 형식이 행마다 달라진다 — 고정 패턴으로 항상 초까지 남긴다
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx");
    // Excel 등에서 =,+,-,@로 시작하는 셀 값을 수식으로 실행하는 CSV Injection(OWASP)을 막기 위한 트리거 문자
    private static final String FORMULA_TRIGGER_CHARS = "=+-@";

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
                escape(item.executedAt().format(TIMESTAMP_FORMAT)),
                escape(item.accountNumber()),
                escape(item.payeeName()),
                String.valueOf(item.amount()),
                escape(item.transferType().name()),
                escape(item.channel().name()),
                escape(item.errorCode()),
                escape(item.failureReason()));
    }

    // 값에 쉼표·큰따옴표·줄바꿈이 있으면 RFC 4180대로 큰따옴표로 감싸고, 내부 큰따옴표는 두 번 반복해 이스케이핑한다.
    // 그 전에 =,+,-,@로 시작하면 앞에 작은따옴표를 붙여 Excel이 수식으로 해석하지 못하게 막는다.
    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = startsWithFormulaTrigger(value) ? "'" + value : value;
        if (sanitized.contains(",")
                || sanitized.contains("\"")
                || sanitized.contains("\n")
                || sanitized.contains("\r")) {
            return "\"" + sanitized.replace("\"", "\"\"") + "\"";
        }
        return sanitized;
    }

    private static boolean startsWithFormulaTrigger(String value) {
        return !value.isEmpty() && FORMULA_TRIGGER_CHARS.indexOf(value.charAt(0)) >= 0;
    }

    private TransferHistoryCsvWriter() {} // new 만드는거 방지
}
