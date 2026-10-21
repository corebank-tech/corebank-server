package com.shinhan.infoetl.domain;

// information_schema에서 읽은 마트 컬럼 하나. 값 스캔 대상인지 가리려고 문자열 타입 여부를 함께 든다.
public record MartColumn(String name, String dataType) {

    public boolean isString() {
        return dataType.equalsIgnoreCase("varchar") || dataType.equalsIgnoreCase("char");
    }

    public boolean isKey() {
        return name.endsWith("_key");
    }
}
