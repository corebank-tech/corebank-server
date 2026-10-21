package com.shinhan.infoetl.application.service;

import com.shinhan.infoetl.application.port.in.PiiVerifyUseCase;
import com.shinhan.infoetl.application.port.out.MartInspectionPort;
import com.shinhan.infoetl.domain.MartColumn;
import com.shinhan.infoetl.domain.MartSchemaWhitelist;
import com.shinhan.infoetl.domain.PiiVerificationResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public final class PiiVerificationService implements PiiVerifyUseCase {

    // 12자리 숫자는 계좌번호 형식이다. 원천 ID가 마트로 넘어오지 않았는지 본다.
    static final String ACCOUNT_NUMBER_PATTERN = "^[0-9]{12}$";
    static final String KEY_PATTERN = "^[0-9a-f]{64}$";

    private final MartInspectionPort inspection;

    public PiiVerificationService(MartInspectionPort inspection) {
        this.inspection = inspection;
    }

    @Override
    public PiiVerificationResult verify() {
        List<String> violations = new ArrayList<>();
        Map<String, List<MartColumn>> actual = inspection.readColumns();
        checkTables(actual, violations);
        long scanned = 0;
        for (Map.Entry<String, List<String>> expected : MartSchemaWhitelist.COLUMNS.entrySet()) {
            List<MartColumn> columns = actual.getOrDefault(expected.getKey(), List.of());
            checkColumns(expected.getKey(), expected.getValue(), columns, violations);
            scanned += scanValues(expected.getKey(), columns, violations);
        }
        return new PiiVerificationResult(violations, scanned);
    }

    private void checkTables(Map<String, List<MartColumn>> actual, List<String> violations) {
        for (String table : new TreeSet<>(actual.keySet())) {
            if (!MartSchemaWhitelist.COLUMNS.containsKey(table)) {
                violations.add("설계에 없는 마트 테이블이 있습니다: " + table);
            }
        }
        for (String table : new TreeSet<>(MartSchemaWhitelist.COLUMNS.keySet())) {
            if (!actual.containsKey(table)) {
                violations.add("마트 테이블이 없습니다: " + table);
            }
        }
    }

    private void checkColumns(String table, List<String> expected, List<MartColumn> actual, List<String> violations) {
        List<String> actualNames = actual.stream().map(MartColumn::name).toList();
        if (!actualNames.equals(expected)) {
            violations.add("컬럼이 설계와 다릅니다: " + table + " 설계=" + expected + " 실제=" + actualNames);
        }
    }

    private long scanValues(String table, List<MartColumn> columns, List<String> violations) {
        long scanned = 0;
        for (MartColumn column : columns) {
            if (!column.isString()) {
                if (column.isKey()) {
                    violations.add("키 컬럼이 문자열 타입이 아닙니다: " + table + "." + column.name() + " " + column.dataType());
                }
                continue;
            }
            scanned++;
            long accountNumbers = inspection.countMatching(table, column.name(), ACCOUNT_NUMBER_PATTERN);
            if (accountNumbers > 0) {
                violations.add("계좌번호 형식(12자리 숫자) 값이 있습니다: " + table + "." + column.name() + " " + accountNumbers + "건");
            }
            if (column.isKey()) {
                long badKeys = inspection.countNotMatching(table, column.name(), KEY_PATTERN);
                if (badKeys > 0) {
                    violations.add("64자리 16진수가 아닌 키가 있습니다: " + table + "." + column.name() + " " + badKeys + "건");
                }
            }
        }
        return scanned;
    }
}
