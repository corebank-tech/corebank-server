package com.shinhan.corebank.paymentstop.domain.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PseudonymizerTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
            delimiter = '|',
            value = {
                "주민번호 900101-1234567 입니다|주민번호 [주민등록번호] 입니다",
                "연락처 010-1234-5678|연락처 [연락처]",
                "연락처 01012345678 로 주세요|연락처 [연락처] 로 주세요",
                "사무실 02-1234-5678 로 주세요|사무실 [연락처] 로 주세요",
                "사무실 031-123-4567 입니다|사무실 [연락처] 입니다",
                "인터넷 전화 070-1234-5678|인터넷 전화 [연락처]",
                "메일 hong.gildong@example.com 확인|메일 [이메일] 확인",
                "계좌 110-123-456789 로 입금|계좌 [계좌번호] 로 입금",
                "계좌 110123456789 로 입금|계좌 [계좌번호] 로 입금",
                "계좌 010123456789 로 입금|계좌 [계좌번호] 로 입금"
            })
    @DisplayName("주민등록번호, 연락처, 이메일, 계좌번호 형태를 가린다")
    void masksPersonalData(String input, String expected) {
        assertThat(Pseudonymizer.mask(input)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} 은 그대로")
    @CsvSource({"거래 20991231WB0000123456 확인", "2026-11-05 에 입금", "300000원을 받았습니다", "판매 대금으로 받은 돈입니다"})
    @DisplayName("거래번호, 날짜, 금액, 일반 문장은 가리지 않는다")
    void keepsNonPersonalValues(String input) {
        assertThat(Pseudonymizer.mask(input)).isEqualTo(input);
    }

    @Test
    @DisplayName("상세의 문자열 값은 깊이와 상관없이 가리고 키와 숫자는 그대로 둔다")
    void masksNestedValues() {
        Map<String, Object> detail = Map.of(
                "claims", List.of(Map.of("text", "010-1234-5678 로 연락", "amount", 300_000)), "phone", "01098765432");

        Map<String, Object> masked = Pseudonymizer.maskMap(detail);

        assertThat(masked.get("phone")).isEqualTo("[연락처]");
        @SuppressWarnings("unchecked")
        Map<String, Object> claim = (Map<String, Object>) ((List<?>) masked.get("claims")).getFirst();
        assertThat(claim).containsEntry("text", "[연락처] 로 연락").containsEntry("amount", 300_000);
    }
}
