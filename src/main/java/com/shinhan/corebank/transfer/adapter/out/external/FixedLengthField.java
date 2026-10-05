package com.shinhan.corebank.transfer.adapter.out.external;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.util.Arrays;

/**
 * 고정길이 전문의 칸 하나를 바이트로 쓰고 읽는다 (docs/phase2/fixed_length_message_spec.md §4).
 *
 * 숫자(N)는 오른쪽 정렬·왼쪽 0 채움, 문자(AN)는 왼쪽 정렬·오른쪽 공백 채움이다. 길이는 글자 수가 아니라
 * MS949 바이트로 센다. 규격에 맞지 않으면 IllegalArgumentException을 던진다.
 */
final class FixedLengthField {

    static final Charset CHARSET = Charset.forName("MS949");

    private static final byte ZERO = '0';
    private static final byte NINE = '9';
    private static final byte SPACE = ' ';

    private FixedLengthField() {}

    static byte[] numeric(long value, int length) {
        if (value < 0) {
            throw new IllegalArgumentException("숫자 칸에 음수를 담을 수 없다");
        }
        byte[] digits = Long.toString(value).getBytes(CHARSET);
        if (digits.length > length) {
            throw new IllegalArgumentException("숫자가 " + length + "바이트 칸을 넘는다");
        }
        byte[] field = new byte[length];
        Arrays.fill(field, ZERO);
        System.arraycopy(digits, 0, field, length - digits.length, digits.length);
        return field;
    }

    /** 거래코드·일련번호·계좌번호처럼 잘리면 다른 값이 되는 칸. 넘치면 거부한다. null이면 공백으로 채운다. */
    static byte[] identifier(String value, int length) {
        byte[] encoded = encode(value);
        if (encoded.length > length) {
            throw new IllegalArgumentException("식별자가 " + length + "바이트 칸을 넘는다");
        }
        return padRight(encoded, encoded.length, length);
    }

    /** 예금주·적요처럼 화면 표시용 칸. 넘치면 한글 한 글자를 쪼개지 않는 바이트 경계에서 자른다. */
    static byte[] display(String value, int length) {
        byte[] encoded = encode(value);
        int kept = encoded.length <= length ? encoded.length : charBoundary(value, length);
        return padRight(encoded, kept, length);
    }

    static long readNumeric(byte[] message, int offset, int length) {
        long value = 0;
        for (int i = offset; i < offset + length; i++) {
            byte b = message[i];
            if (b < ZERO || b > NINE) {
                throw new IllegalArgumentException("숫자 칸(" + offset + "번째 바이트부터)에 숫자가 아닌 바이트가 있다");
            }
            value = value * 10 + (b - ZERO);
        }
        return value;
    }

    /** 오른쪽 공백만 걷어낸다. 왼쪽 공백은 값으로 본다. */
    static String readText(byte[] message, int offset, int length) {
        int end = offset + length;
        while (end > offset && message[end - 1] == SPACE) {
            end--;
        }
        try {
            return CHARSET.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(message, offset, end - offset))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("문자 칸(" + offset + "번째 바이트부터)을 MS949로 읽을 수 없다", e);
        }
    }

    // ?로 바꿔 보내면 다른 값이 상대에게 기록되므로, 표현할 수 없는 문자(이모지 등)는 거부한다.
    private static byte[] encode(String value) {
        if (value == null) {
            return new byte[0];
        }
        try {
            ByteBuffer buffer = CHARSET.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(value));
            byte[] encoded = new byte[buffer.remaining()];
            buffer.get(encoded);
            return encoded;
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("MS949로 표현할 수 없는 문자가 있다", e);
        }
    }

    private static int charBoundary(String value, int length) {
        int kept = 0;
        for (int i = 0; i < value.length(); i++) {
            int size = String.valueOf(value.charAt(i)).getBytes(CHARSET).length;
            if (kept + size > length) {
                break;
            }
            kept += size;
        }
        return kept;
    }

    private static byte[] padRight(byte[] encoded, int kept, int length) {
        byte[] field = new byte[length];
        Arrays.fill(field, SPACE);
        System.arraycopy(encoded, 0, field, 0, kept);
        return field;
    }
}
