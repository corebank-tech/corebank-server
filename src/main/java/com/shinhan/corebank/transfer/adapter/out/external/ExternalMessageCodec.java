package com.shinhan.corebank.transfer.adapter.out.external;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.function.Supplier;

/**
 * 타행 이체 전문 한 장을 124바이트로 쓰고 읽는다 (docs/phase2/fixed_length_message_spec.md).
 *
 * 규격에 맞지 않는 바이트는 IllegalArgumentException으로 거부한다. 받는 쪽은 이를 형식 오류(1003)로 본다.
 */
public final class ExternalMessageCodec {

    public static final int MESSAGE_LENGTH = 124;

    /** 전문길이 칸의 값. 전문길이 칸 4바이트를 뺀 나머지 바이트 수다. */
    private static final int LENGTH_VALUE = MESSAGE_LENGTH - 4;

    private static final DateTimeFormatter SENT_AT_FORMAT = DateTimeFormatter.ofPattern("uuuuMMddHHmmss");

    /** 규격 §2 필드 배치. 표와 한 줄씩 맞춰 볼 수 있게 시작 위치를 그대로 적는다. */
    private enum Field {
        LENGTH(0, 4),
        KIND(4, 4),
        TRANSACTION_CODE(8, 6),
        SENT_AT(14, 14),
        BANK_CODE(28, 3),
        SERIAL_NUMBER(31, 20),
        RESPONSE_CODE(51, 4),
        ACCOUNT_NUMBER(55, 16),
        AMOUNT(71, 13),
        PAYEE_NAME(84, 20),
        MEMO(104, 20);

        private final int offset;
        private final int length;

        Field(int offset, int length) {
            this.offset = offset;
            this.length = length;
        }
    }

    private ExternalMessageCodec() {}

    public static byte[] encode(ExternalMessage message) {
        byte[] out = new byte[MESSAGE_LENGTH];
        ResponseCode responseCode = message.responseCode();
        write(out, Field.LENGTH, FixedLengthField.numeric(LENGTH_VALUE, Field.LENGTH.length));
        write(out, Field.KIND, FixedLengthField.identifier(message.kind().code(), Field.KIND.length));
        write(
                out,
                Field.TRANSACTION_CODE,
                FixedLengthField.identifier(message.transactionCode().code(), Field.TRANSACTION_CODE.length));
        write(
                out,
                Field.SENT_AT,
                FixedLengthField.identifier(SENT_AT_FORMAT.format(message.sentAt()), Field.SENT_AT.length));
        write(out, Field.BANK_CODE, FixedLengthField.identifier(message.bankCode(), Field.BANK_CODE.length));
        write(
                out,
                Field.SERIAL_NUMBER,
                FixedLengthField.identifier(message.serialNumber(), Field.SERIAL_NUMBER.length));
        write(
                out,
                Field.RESPONSE_CODE,
                FixedLengthField.identifier(
                        responseCode == null ? null : responseCode.code(), Field.RESPONSE_CODE.length));
        write(
                out,
                Field.ACCOUNT_NUMBER,
                FixedLengthField.identifier(message.accountNumber(), Field.ACCOUNT_NUMBER.length));
        write(out, Field.AMOUNT, FixedLengthField.numeric(message.amount(), Field.AMOUNT.length));
        write(out, Field.PAYEE_NAME, FixedLengthField.display(message.payeeName(), Field.PAYEE_NAME.length));
        write(out, Field.MEMO, FixedLengthField.display(message.memo(), Field.MEMO.length));
        return out;
    }

    public static ExternalMessage decode(byte[] message) {
        if (message == null || message.length != MESSAGE_LENGTH) {
            throw new IllegalArgumentException("전문은 " + MESSAGE_LENGTH + "바이트여야 한다");
        }
        if (readNumeric(message, Field.LENGTH) != LENGTH_VALUE) {
            throw new IllegalArgumentException("전문길이 값이 " + LENGTH_VALUE + "가 아니다");
        }
        readNumeric(message, Field.BANK_CODE); // 기관코드는 N 타입이라 숫자 아닌 바이트를 여기서 거부한다
        String responseCode = readText(message, Field.RESPONSE_CODE);
        return new ExternalMessage(
                MessageKind.fromCode(readText(message, Field.KIND)),
                TransactionCode.fromCode(readText(message, Field.TRANSACTION_CODE)),
                readSentAt(message),
                readText(message, Field.BANK_CODE),
                readText(message, Field.SERIAL_NUMBER),
                responseCode.isEmpty() ? null : ResponseCode.fromCode(responseCode),
                readText(message, Field.ACCOUNT_NUMBER),
                readNumeric(message, Field.AMOUNT),
                blankToNull(readText(message, Field.PAYEE_NAME)),
                blankToNull(readText(message, Field.MEMO)));
    }

    /**
     * 형식이 깨진 전문에 돌려줄 1003 응답. 받는 쪽이 읽을 수 있어야 하므로 깨진 칸을 그대로 돌려주지 않는다.
     * 살릴 수 있는 칸(거래코드·기관코드·일련번호·계좌번호·금액)만 건지고 나머지는 규격에 맞는 빈 값으로 채운다.
     */
    public static byte[] malformedResponse(byte[] received, LocalDateTime sentAt) {
        byte[] in = new byte[MESSAGE_LENGTH];
        Arrays.fill(in, (byte) ' ');
        System.arraycopy(received, 0, in, 0, Math.min(received.length, MESSAGE_LENGTH));
        return encode(new ExternalMessage(
                MessageKind.RESPONSE,
                salvage(() -> TransactionCode.fromCode(readText(in, Field.TRANSACTION_CODE)), TransactionCode.TRANSFER),
                sentAt,
                salvage(() -> String.format("%03d", readNumeric(in, Field.BANK_CODE)), "000"),
                salvage(() -> nonBlank(readText(in, Field.SERIAL_NUMBER)), "0".repeat(Field.SERIAL_NUMBER.length)),
                ResponseCode.MALFORMED_MESSAGE,
                salvage(() -> digits(readText(in, Field.ACCOUNT_NUMBER)), "0"),
                salvage(() -> readNumeric(in, Field.AMOUNT), 0L),
                null,
                null));
    }

    private static <T> T salvage(Supplier<T> read, T fallback) {
        try {
            return read.get();
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static String digits(String value) {
        if (value.isEmpty() || !value.chars().allMatch(c -> c >= '0' && c <= '9')) {
            throw new IllegalArgumentException("숫자 칸이 아니다");
        }
        return value;
    }

    private static String nonBlank(String value) {
        if (value.isBlank()) {
            throw new IllegalArgumentException("빈 칸이다");
        }
        return value;
    }

    private static void write(byte[] out, Field field, byte[] bytes) {
        System.arraycopy(bytes, 0, out, field.offset, field.length);
    }

    private static long readNumeric(byte[] message, Field field) {
        return FixedLengthField.readNumeric(message, field.offset, field.length);
    }

    private static String readText(byte[] message, Field field) {
        return FixedLengthField.readText(message, field.offset, field.length);
    }

    private static LocalDateTime readSentAt(byte[] message) {
        try {
            return LocalDateTime.parse(readText(message, Field.SENT_AT), SENT_AT_FORMAT);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("전송일시가 yyyyMMddHHmmss 형식이 아니다", e);
        }
    }

    private static String blankToNull(String value) {
        return value.isEmpty() ? null : value;
    }
}
