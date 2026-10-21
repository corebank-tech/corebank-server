package com.shinhan.infoetl.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.infoetl.application.port.out.MartInspectionPort;
import com.shinhan.infoetl.domain.MartColumn;
import com.shinhan.infoetl.domain.MartSchemaWhitelist;
import com.shinhan.infoetl.domain.PiiVerificationResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PiiVerificationServiceTest {

    private final FakeInspection inspection = new FakeInspection();

    @Test
    @DisplayName("컬럼이 화이트리스트와 같고 위반 값이 없으면 통과한다")
    void passesWhenSchemaMatchesAndNoViolations() {
        PiiVerificationResult result = new PiiVerificationService(inspection).verify();

        assertThat(result.passed()).isTrue();
        assertThat(result.scannedColumns()).isPositive();
    }

    @Test
    @DisplayName("화이트리스트에 없는 컬럼이 있으면 위반이다")
    void extraColumnIsViolation() {
        inspection.columns.get("mart_daily_account_balance").add(new MartColumn("customer_name", "varchar"));

        PiiVerificationResult result = new PiiVerificationService(inspection).verify();

        assertThat(result.passed()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("컬럼이 설계와 다릅니다") && v.contains("customer_name"));
    }

    @Test
    @DisplayName("설계에 없는 mart_ 테이블이 있으면 위반이다")
    void extraTableIsViolation() {
        inspection.columns.put("mart_customer_dump", List.of(new MartColumn("name", "varchar")));

        assertThat(new PiiVerificationService(inspection).verify().violations())
                .anyMatch(v -> v.contains("설계에 없는 마트 테이블") && v.contains("mart_customer_dump"));
    }

    @Test
    @DisplayName("계좌번호 형식 값이 있으면 위반이다")
    void accountNumberLikeValueIsViolation() {
        inspection.matching.put("mart_product_subscription_event.product_name", 2L);

        assertThat(new PiiVerificationService(inspection).verify().violations())
                .anyMatch(v -> v.contains("계좌번호 형식") && v.contains("product_name") && v.contains("2건"));
    }

    @Test
    @DisplayName("키 컬럼에 64자리 16진수가 아닌 값이 있으면 위반이다")
    void malformedKeyIsViolation() {
        inspection.notMatching.put("mart_daily_account_balance.account_key", 1L);

        assertThat(new PiiVerificationService(inspection).verify().violations())
                .anyMatch(v -> v.contains("64자리 16진수") && v.contains("account_key"));
    }

    @Test
    @DisplayName("키가 아닌 문자열 컬럼에는 16진수 형식을 요구하지 않는다")
    void nonKeyColumnsAreNotCheckedForHexFormat() {
        inspection.notMatching.put("mart_daily_account_balance.account_type", 5L);

        assertThat(new PiiVerificationService(inspection).verify().passed()).isTrue();
    }

    @Test
    @DisplayName("키 컬럼이 문자열이 아닌 타입으로 바뀌면 값 검사를 피하지 못하고 위반이다")
    void nonStringKeyColumnIsViolation() {
        inspection.changeType("mart_daily_account_balance", "account_key", "varbinary");

        assertThat(new PiiVerificationService(inspection).verify().violations())
                .anyMatch(
                        v -> v.contains("키 컬럼이 문자열 타입이 아닙니다") && v.contains("account_key") && v.contains("varbinary"));
    }

    @Test
    @DisplayName("text로 바뀐 컬럼도 값을 검사한다")
    void textColumnIsStillScanned() {
        inspection.changeType("mart_product_subscription_event", "product_name", "text");
        inspection.matching.put("mart_product_subscription_event.product_name", 1L);

        assertThat(new PiiVerificationService(inspection).verify().violations())
                .anyMatch(v -> v.contains("계좌번호 형식") && v.contains("product_name"));
    }

    private static final class FakeInspection implements MartInspectionPort {

        final Map<String, List<MartColumn>> columns = new LinkedHashMap<>();
        final Map<String, Long> matching = new HashMap<>();
        final Map<String, Long> notMatching = new HashMap<>();

        FakeInspection() {
            MartSchemaWhitelist.COLUMNS.forEach((table, names) -> {
                List<MartColumn> list = new ArrayList<>();
                names.forEach(name -> list.add(new MartColumn(name, typeOf(name))));
                columns.put(table, list);
            });
        }

        void changeType(String table, String name, String dataType) {
            List<MartColumn> list = columns.get(table);
            list.replaceAll(column -> column.name().equals(name) ? new MartColumn(name, dataType) : column);
        }

        private static String typeOf(String name) {
            boolean text = name.endsWith("_key")
                    || name.endsWith("_type")
                    || name.endsWith("_group")
                    || name.equals("product_code")
                    || name.equals("product_name")
                    || name.equals("status")
                    || name.equals("failure_reason");
            return text ? "varchar" : "bigint";
        }

        @Override
        public Map<String, List<MartColumn>> readColumns() {
            return columns;
        }

        @Override
        public long countMatching(String table, String column, String regex) {
            return matching.getOrDefault(table + "." + column, 0L);
        }

        @Override
        public long countNotMatching(String table, String column, String regex) {
            return notMatching.getOrDefault(table + "." + column, 0L);
        }
    }
}
