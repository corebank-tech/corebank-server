package com.shinhan.corebank.transfer.adapter.out.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("FixedLengthField 전문 칸 패딩 단위 테스트")
class FixedLengthFieldTest {

    private static String text(byte[] field) {
        return new String(field, FixedLengthField.CHARSET);
    }

    @Nested
    @DisplayName("숫자 칸(N)")
    class NumericTest {

        @Test
        @DisplayName("오른쪽 정렬하고 왼쪽을 0으로 채운다")
        void padsLeftWithZeros() {
            assertThat(text(FixedLengthField.numeric(50_000L, 13))).isEqualTo("0000000050000");
            assertThat(text(FixedLengthField.numeric(0L, 4))).isEqualTo("0000");
        }

        @Test
        @DisplayName("칸을 넘는 숫자와 음수는 거부한다")
        void rejectsOverflowAndNegative() {
            assertThatThrownBy(() -> FixedLengthField.numeric(10_000L, 4)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> FixedLengthField.numeric(-1L, 4)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("읽을 때 숫자가 아닌 바이트가 있으면 거부한다")
        void readRejectsNonDigit() {
            byte[] message = "00 5".getBytes(FixedLengthField.CHARSET);

            assertThatThrownBy(() -> FixedLengthField.readNumeric(message, 0, 4))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("쓴 값을 그대로 읽는다")
        void roundTrips() {
            byte[] field = FixedLengthField.numeric(9_999_999_999_999L, 13);

            assertThat(FixedLengthField.readNumeric(field, 0, 13)).isEqualTo(9_999_999_999_999L);
        }
    }

    @Nested
    @DisplayName("식별자 칸(AN) — 거래코드·일련번호·계좌번호")
    class IdentifierTest {

        @Test
        @DisplayName("왼쪽 정렬하고 오른쪽을 공백으로 채운다. null은 전부 공백이다")
        void padsRightWithSpaces() {
            assertThat(text(FixedLengthField.identifier("1234567890123", 16))).isEqualTo("1234567890123   ");
            assertThat(text(FixedLengthField.identifier(null, 4))).isEqualTo("    ");
        }

        @Test
        @DisplayName("칸을 넘으면 자르지 않고 거부한다")
        void rejectsOverflow() {
            assertThatThrownBy(() -> FixedLengthField.identifier("12345678901234567", 16))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("표시용 칸(AN) — 예금주·적요")
    class DisplayTest {

        @Test
        @DisplayName("한글은 1자에 2바이트를 차지한다")
        void countsKoreanAsTwoBytes() {
            byte[] field = FixedLengthField.display("홍길동", 20);

            assertThat(field).hasSize(20);
            assertThat(text(field)).isEqualTo("홍길동" + " ".repeat(14));
        }

        @Test
        @DisplayName("넘치면 바이트 경계에서 자른다")
        void truncatesAtByteBoundary() {
            byte[] field = FixedLengthField.display("가나다라마바사아자차카", 20);

            assertThat(text(field)).isEqualTo("가나다라마바사아자차");
        }

        @Test
        @DisplayName("마지막 한글이 1바이트만 들어갈 자리면 그 글자를 빼고 공백으로 채운다")
        void doesNotSplitKoreanCharacter() {
            byte[] field = FixedLengthField.display("A가나다라마바사아자차", 20);

            assertThat(field).hasSize(20);
            assertThat(text(field)).isEqualTo("A가나다라마바사아자 ");
        }

        @Test
        @DisplayName("순수 EUC-KR에 없는 한글(똠)도 MS949라 담긴다")
        void acceptsMs949ExtendedHangul() {
            byte[] field = FixedLengthField.display("똠", 20);

            assertThat(FixedLengthField.readText(field, 0, 20)).isEqualTo("똠");
        }

        @Test
        @DisplayName("MS949로 표현할 수 없는 문자(이모지)는 ?로 바꾸지 않고 거부한다")
        void rejectsUnmappableCharacter() {
            assertThatThrownBy(() -> FixedLengthField.display("홍길동😀", 20))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> FixedLengthField.identifier("😀", 16))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("문자 칸 읽기")
    class ReadTextTest {

        @Test
        @DisplayName("오른쪽 공백만 걷어내고 왼쪽 공백은 값으로 남긴다")
        void stripsTrailingSpacesOnly() {
            byte[] message = " 김민수   ".getBytes(FixedLengthField.CHARSET);

            assertThat(FixedLengthField.readText(message, 0, message.length)).isEqualTo(" 김민수");
        }

        @Test
        @DisplayName("전부 공백이면 빈 문자열이다")
        void blankIsEmpty() {
            byte[] message = "    ".getBytes(FixedLengthField.CHARSET);

            assertThat(FixedLengthField.readText(message, 0, 4)).isEmpty();
        }
    }
}
