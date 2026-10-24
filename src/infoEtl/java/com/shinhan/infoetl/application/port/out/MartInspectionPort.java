package com.shinhan.infoetl.application.port.out;

import com.shinhan.infoetl.domain.MartColumn;
import java.util.List;
import java.util.Map;

public interface MartInspectionPort {

    // 마트 스키마의 mart_ 테이블별 컬럼(순서대로)
    Map<String, List<MartColumn>> readColumns();

    long countMatching(String table, String column, String regex);

    long countNotMatching(String table, String column, String regex);
}
