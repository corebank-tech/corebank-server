package com.shinhan.corebank.paymentstop.domain.gate;

/**
 * 해제 게이트 설정값. 애플리케이션 설정에만 두고 Agent와 화면은 바꿀 수 없다(D10).
 * 모두 공식 기준이 아닌 내부 기준이다(D33).
 *
 * @param fraudWindowHours 사기 패턴: 다툼 거래 전후 시간 창
 * @param fraudMinSenders 사기 패턴: 창 안 서로 다른 송금인 수 하한
 * @param fraudMinDeposit 사기 패턴: 셀 입금액 하한(원)
 * @param fraudOutflowPercent 사기 패턴: 입금 후 바로 빠져나간 비율 하한(%)
 * @param fraudOutflowMinutes 사기 패턴: 입금 후 출금을 셀 시간(분)
 * @param relationLookbackDays 관계 인정: 다툼 거래 이전 조회 기간(일)
 * @param relationMinCount 관계 인정: 관계 거래 횟수 하한
 * @param relationMinSpanDays 관계 인정: 첫 거래와 마지막 거래 간격 하한(일)
 * @param relationMinLeadDays 관계 인정: 첫 관계 거래가 다툼 거래보다 앞선 기간 하한(일)
 * @param relationMaxAmountMultiple 관계 인정: 다툼 금액 합 ≤ 과거 관계 거래 최대액 × 이 배수
 * @param partialSmallAmount 일부 지급정지 요건: 다툼 입금액 합 상한(원)
 * @param partialLivelihoodDays 일부 지급정지 요건: 생활 거래를 보는 다툼 이전 기간(일)
 * @param partialLivelihoodMonths 일부 지급정지 요건: 같은 상대와 정기 거래가 있어야 하는 달 수 하한
 */
public record GateSettings(
        int fraudWindowHours,
        int fraudMinSenders,
        long fraudMinDeposit,
        int fraudOutflowPercent,
        int fraudOutflowMinutes,
        int relationLookbackDays,
        int relationMinCount,
        int relationMinSpanDays,
        int relationMinLeadDays,
        int relationMaxAmountMultiple,
        long partialSmallAmount,
        int partialLivelihoodDays,
        int partialLivelihoodMonths) {

    // 게이트 규칙 절의 초기값
    public static GateSettings defaults() {
        return new GateSettings(24, 3, 10_000L, 80, 60, 180, 3, 30, 30, 5, 3_000_000L, 90, 2);
    }

    public GateSettings {
        if (fraudWindowHours <= 0
                || fraudMinSenders <= 0
                || fraudMinDeposit < 0
                || fraudOutflowPercent <= 0
                || fraudOutflowPercent > 100
                || fraudOutflowMinutes <= 0
                || relationLookbackDays <= 0
                || relationMinCount <= 0
                || relationMinSpanDays < 0
                || relationMinLeadDays < 0
                || relationMaxAmountMultiple <= 0
                || partialSmallAmount <= 0
                || partialLivelihoodDays <= 0
                || partialLivelihoodMonths <= 0) {
            throw new IllegalArgumentException("게이트 설정값이 올바르지 않습니다.");
        }
    }
}
