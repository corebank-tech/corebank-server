package com.shinhan.corebank.paymentstop.config;

import com.shinhan.corebank.paymentstop.domain.gate.GateSettings;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 해제 게이트 설정값(D10). 애플리케이션 설정에만 두고 Agent와 화면은 바꿀 수 없다. 기본값은 PLAN 게이트 규칙 절의 초기값이고 모두
 * 내부 기준이다(D33).
 */
@ConfigurationProperties(prefix = "app.payment-stop.gate")
public record GateSettingsProperties(
        @DefaultValue("24") int fraudWindowHours,
        @DefaultValue("3") int fraudMinSenders,
        @DefaultValue("10000") long fraudMinDeposit,
        @DefaultValue("80") int fraudOutflowPercent,
        @DefaultValue("60") int fraudOutflowMinutes,
        @DefaultValue("180") int relationLookbackDays,
        @DefaultValue("3") int relationMinCount,
        @DefaultValue("30") int relationMinSpanDays,
        @DefaultValue("30") int relationMinLeadDays,
        @DefaultValue("5") int relationMaxAmountMultiple) {

    // 범위 검사는 GateSettings 생성자가 한다. 잘못된 값이면 기동에서 실패한다
    public GateSettings toSettings() {
        return new GateSettings(
                fraudWindowHours,
                fraudMinSenders,
                fraudMinDeposit,
                fraudOutflowPercent,
                fraudOutflowMinutes,
                relationLookbackDays,
                relationMinCount,
                relationMinSpanDays,
                relationMinLeadDays,
                relationMaxAmountMultiple);
    }
}
