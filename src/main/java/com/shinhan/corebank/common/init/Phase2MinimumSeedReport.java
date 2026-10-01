package com.shinhan.corebank.common.init;

import java.time.Duration;

// PH-60 적재 결과와 총 소요 시간을 실행 로그에 전달한다.
public record Phase2MinimumSeedReport(
        int customers, int accounts, int ledgerEntries, int autoTransfers, int transfers, Duration elapsed) {}
