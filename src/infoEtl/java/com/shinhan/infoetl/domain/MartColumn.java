package com.shinhan.infoetl.domain;

import java.util.Locale;
import java.util.Set;

// information_schema에서 읽은 마트 컬럼 하나. 값 스캔 대상인지 가리려고 문자열 타입 여부를 함께 든다.
public record MartColumn(String name, String dataType) {

    private static final Set<String> STRING_TYPES =
            Set.of("char", "varchar", "tinytext", "text", "mediumtext", "longtext", "enum", "set");

    public boolean isString() {
        return STRING_TYPES.contains(dataType.toLowerCase(Locale.ROOT));
    }

    public boolean isKey() {
        return name.endsWith("_key");
    }
}
