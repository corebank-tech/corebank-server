package com.shinhan.corebank.transfer.api;

import java.time.LocalDate;

/** 예약·자동이체 회차 하나를 가리키는 열쇠. transfer 의 uk_transfer_source_execution_date 에서 출처 구분을 뺀 값이다. */
public record SourceExecutionKey(Long sourceId, LocalDate executionDate) {}
