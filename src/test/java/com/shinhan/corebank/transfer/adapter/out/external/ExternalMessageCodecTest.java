package com.shinhan.corebank.transfer.adapter.out.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("ExternalMessageCodec 전문 직렬화·역직렬화 단위 테스트")
class ExternalMessageCodecTest {

    // 규격 §5 예시를 그대로 옮기고 ·만 공백으로 바꾼다. 문서와 코드가 어긋나면 이 값이 깨진다.
    private static final String SPEC_REQUEST = spec(
            "01200200TRF0012026102310301500420261023WB0000000123····1234567890123···0000000050000····················홍길동··············");
    private static final String SPEC_RESPONSE = spec(
            "01200210TRF0012026102310301600420261023WB000000012300001234567890123···0000000050000김민수··············홍길동··············");

    private static final ExternalMessage REQUEST = ExternalMessage.request(
            TransactionCode.TRANSFER,
            LocalDateTime.of(2026, 10, 23, 10, 30, 15),
            "004",
            "20261023WB0000000123",
            "1234567890123",
            50_000L,
            "홍길동");

    private static String spec(String example) {
        return example.replace('·', ' ');
    }

    private static byte[] bytes(String text) {
        return text.getBytes(FixedLengthField.CHARSET);
    }

    @Nested
    @DisplayName("규격 §5 예시")
    class SpecExampleTest {

        @Test
        @DisplayName("요청 전문을 만들면 규격 예시와 바이트까지 같다")
        void encodesRequestLikeSpec() {
            byte[] encoded = ExternalMessageCodec.encode(REQUEST);

            assertThat(encoded).hasSize(ExternalMessageCodec.MESSAGE_LENGTH);
            assertThat(encoded).isEqualTo(bytes(SPEC_REQUEST));
        }

        @Test
        @DisplayName("응답 전문을 만들면 규격 예시와 바이트까지 같다")
        void encodesResponseLikeSpec() {
            ExternalMessage response =
                    REQUEST.toResponse(ResponseCode.APPROVED, "김민수", LocalDateTime.of(2026, 10, 23, 10, 30, 16));

            assertThat(ExternalMessageCodec.encode(response)).isEqualTo(bytes(SPEC_RESPONSE));
        }

        @Test
        @DisplayName("규격 예시를 읽으면 같은 값이 나온다")
        void decodesSpecExamples() {
            assertThat(ExternalMessageCodec.decode(bytes(SPEC_REQUEST))).isEqualTo(REQUEST);

            ExternalMessage response = ExternalMessageCodec.decode(bytes(SPEC_RESPONSE));
            assertThat(response.kind()).isEqualTo(MessageKind.RESPONSE);
            assertThat(response.responseCode()).isEqualTo(ResponseCode.APPROVED);
            assertThat(response.payeeName()).isEqualTo("김민수");
            assertThat(response.memo()).isEqualTo("홍길동");
            assertThat(response.serialNumber()).isEqualTo("20261023WB0000000123");
        }
    }

    @Nested
    @DisplayName("쓰기")
    class EncodeTest {

        @Test
        @DisplayName("조회거래 요청은 원거래와 같은 본문을 싣고 거래코드만 INQ001이다")
        void encodesInquiry() {
            ExternalMessage inquiry = ExternalMessage.request(
                    TransactionCode.INQUIRY,
                    REQUEST.sentAt(),
                    REQUEST.bankCode(),
                    REQUEST.serialNumber(),
                    REQUEST.accountNumber(),
                    REQUEST.amount(),
                    REQUEST.memo());

            String encoded = new String(ExternalMessageCodec.encode(inquiry), FixedLengthField.CHARSET);

            assertThat(encoded).isEqualTo(SPEC_REQUEST.replace("TRF001", "INQ001"));
        }

        @Test
        @DisplayName("적요가 20바이트를 넘으면 잘라서 싣는다")
        void truncatesLongMemo() {
            ExternalMessage longMemo = ExternalMessage.request(
                    TransactionCode.TRANSFER,
                    REQUEST.sentAt(),
                    "004",
                    "20261023WB0000000123",
                    "1234567890123",
                    50_000L,
                    "가나다라마바사아자차카");

            ExternalMessage decoded = ExternalMessageCodec.decode(ExternalMessageCodec.encode(longMemo));

            assertThat(decoded.memo()).isEqualTo("가나다라마바사아자차");
        }

        @Test
        @DisplayName("계좌번호가 16바이트를 넘으면 전문을 만들지 않는다")
        void rejectsLongAccountNumber() {
            ExternalMessage longAccount = ExternalMessage.request(
                    TransactionCode.TRANSFER,
                    REQUEST.sentAt(),
                    "004",
                    "20261023WB0000000123",
                    "12345678901234567",
                    50_000L,
                    null);

            assertThatThrownBy(() -> ExternalMessageCodec.encode(longAccount))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("읽기 거부 — 받는 쪽은 형식 오류(1003)로 본다")
    class DecodeRejectTest {

        @Test
        @DisplayName("전체 길이가 124바이트가 아니면 거부한다")
        void rejectsWrongLength() {
            assertThatThrownBy(() -> ExternalMessageCodec.decode(bytes(SPEC_REQUEST + " ")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @ParameterizedTest(name = "{1}")
        @CsvSource({
            "0,0121,전문길이 값이 0120이 아님",
            "4,0300,알 수 없는 전문종별",
            "8,TRF999,알 수 없는 거래코드",
            "14,20261332103015,없는 날짜",
            "28,0A4,기관코드에 숫자 아닌 바이트",
            "51,0001,알 수 없는 응답코드",
            "55,123-456-789    ,계좌번호에 하이픈",
            "71,00000000500 0,금액에 숫자 아닌 바이트"
        })
        @DisplayName("칸 하나가 규격을 어기면 거부한다")
        void rejectsBrokenField(int offset, String broken, String reason) {
            byte[] message = bytes(SPEC_RESPONSE);
            byte[] patch = bytes(broken);
            System.arraycopy(patch, 0, message, offset, patch.length);

            assertThatThrownBy(() -> ExternalMessageCodec.decode(message))
                    .as(reason)
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("응답 전문에 응답코드가 비어 있으면 거부한다")
        void rejectsResponseWithoutCode() {
            byte[] message = bytes(SPEC_RESPONSE);
            System.arraycopy(bytes("    "), 0, message, 51, 4);

            assertThatThrownBy(() -> ExternalMessageCodec.decode(message)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("응답 만들기")
    class ToResponseTest {

        @Test
        @DisplayName("응답 전문에 다시 응답할 수 없다")
        void rejectsResponseToResponse() {
            ExternalMessage response = REQUEST.toResponse(ResponseCode.APPROVED, "김민수", REQUEST.sentAt());

            assertThatThrownBy(() -> response.toResponse(ResponseCode.APPROVED, "김민수", REQUEST.sentAt()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
