package com.shinhan.corebank.common.init;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// DB와 무관한 PH-60b 생성 규칙을 결정적으로 계산한다.
final class Phase2BulkSeedPlan {

    // 데모 기준일(10/5)까지 300만 건을 압축하고 9/25 급여일 집중을 유지한다.
    private static final int[] PERIOD_TRANSACTION_COUNTS = {2_000_000, 1_000_000};
    private static final int SEPTEMBER_PAYDAY_TRANSACTION_COUNT = 400_000;
    private static final int HOTSPOT_PERCENT = 30;
    private static final int HOTSPOT_ACCOUNT_COUNT = 100;
    private static final long PH60_DEMAND_ACCOUNT_START = 60_000_001L;
    private static final int PH60_CUSTOMER_COUNT = 10_000;

    private final Phase2BulkSeedSpec spec;
    private final List<List<LocalDate>> weightedDates;
    private final int[] transferMonthlyCounts;
    private final int[] transferPaydayCounts;

    Phase2BulkSeedPlan(Phase2BulkSeedSpec spec) {
        this.spec = spec;
        this.weightedDates = List.of(
                weightedDates(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)),
                weightedDates(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5)));
        int[] subscriptionMonthlyCounts = new int[2];
        int[] subscriptionPaydayCounts = new int[2];
        for (int index = 0; index < spec.subscriptionCount(); index++) {
            LocalDate date = subscriptionDate(index);
            int monthIndex = date.getMonthValue() - 9;
            subscriptionMonthlyCounts[monthIndex]++;
            if (date.getDayOfMonth() == 25) {
                subscriptionPaydayCounts[monthIndex]++;
            }
        }
        this.transferMonthlyCounts = new int[2];
        this.transferPaydayCounts = new int[2];
        for (int month = 0; month < 2; month++) {
            transferMonthlyCounts[month] = PERIOD_TRANSACTION_COUNTS[month] - subscriptionMonthlyCounts[month];
            transferPaydayCounts[month] =
                    (month == 0 ? SEPTEMBER_PAYDAY_TRANSACTION_COUNT : 0) - subscriptionPaydayCounts[month];
        }
    }

    LocalDate tradeDate(int transferIndex) {
        // 최초 5만 건의 자금 공급 거래일을 PH-60 개시일인 9월 1일로 고정한다.
        if (transferIndex < spec.customerCount()) {
            return LocalDate.of(2026, 9, 1);
        }
        int distributableIndex = transferIndex - spec.customerCount();
        int monthIndex = transferMonthIndex(distributableIndex);
        int monthOrdinal = distributableIndex;
        for (int month = 0; month < monthIndex; month++) {
            monthOrdinal -= distributableMonthlyCount(month);
        }
        if (monthOrdinal < transferPaydayCounts[monthIndex]) {
            return LocalDate.of(2026, 9, 25);
        }
        List<LocalDate> dates = weightedDates.get(monthIndex);
        return dates.get((monthOrdinal - transferPaydayCounts[monthIndex]) % dates.size());
    }

    LocalDateTime occurredAt(int globalTransactionIndex) {
        if (globalTransactionIndex < spec.customerCount()) {
            // 공급 5만 건을 개시 직후 1마이크로초 간격으로 두어 PH-60의 첫 이체보다 먼저 발생시킨다.
            return LocalDateTime.of(2026, 9, 1, 0, 0).plusNanos((globalTransactionIndex + 1L) * 1_000L);
        }
        LocalDate date = tradeDate(globalTransactionIndex);
        if (date.equals(LocalDate.of(2026, 9, 1))) {
            return date.atTime(3, 0).plusSeconds(Math.floorMod(globalTransactionIndex, 75_600));
        }
        int seconds = Math.floorMod(globalTransactionIndex / 3, 86_399);
        return LocalDateTime.of(date, LocalTime.ofSecondOfDay(seconds + 1L));
    }

    boolean hotspot(int transferIndex) {
        return Math.floorMod(transferIndex, 100) < HOTSPOT_PERCENT;
    }

    long withdrawalAccountId(int transferIndex) {
        if (transferIndex < spec.customerCount()) {
            return PH60_DEMAND_ACCOUNT_START + (transferIndex % PH60_CUSTOMER_COUNT) * 3L;
        }
        return demandAccountId(transferCustomerIndex(transferIndex));
    }

    long depositAccountId(int transferIndex) {
        if (transferIndex < spec.customerCount()) {
            return demandAccountId(transferIndex);
        }
        int source = transferCustomerIndex(transferIndex);
        int eligibleCount = spec.customerCount() - spec.dormantCandidateCount();
        int hotspotSize = Math.min(HOTSPOT_ACCOUNT_COUNT, eligibleCount);
        int eligibleSource = source - spec.dormantCandidateCount();
        int eligibleDestination = hotspot(transferIndex) || eligibleCount <= HOTSPOT_ACCOUNT_COUNT
                ? (eligibleSource + 1) % hotspotSize
                : HOTSPOT_ACCOUNT_COUNT
                        + Math.floorMod(
                                eligibleSource - HOTSPOT_ACCOUNT_COUNT + 1, eligibleCount - HOTSPOT_ACCOUNT_COUNT);
        int destination = spec.dormantCandidateCount() + eligibleDestination;
        return demandAccountId(destination);
    }

    long transferAmount(int transferIndex) {
        // PH-60의 9월 1일 출금과 합산해도 5,000만 원 일 한도 안에 들도록 계좌당 공급 합계를 4,750만 원으로 제한한다.
        return transferIndex < spec.customerCount() ? 9_500_000L : 10_000L + transferIndex % 90 * 1_000L;
    }

    int subscriptionCustomerIndex(int subscriptionIndex) {
        return subscriptionIndex % spec.customerCount();
    }

    boolean timeDeposit(int subscriptionIndex) {
        return subscriptionIndex < spec.customerCount();
    }

    String productCode(int subscriptionIndex) {
        if (!timeDeposit(subscriptionIndex)) {
            return "PRD_REGULAR_SAVE";
        }
        return subscriptionIndex < spec.maturedCount() + spec.nearMaturityCount() ? "PRD_SHORT_DEP" : "PRD_BASIC_DEP";
    }

    int termMonths(int subscriptionIndex) {
        if (subscriptionIndex < spec.maturedCount()) {
            return 1;
        }
        if (subscriptionIndex < spec.maturedCount() + spec.nearMaturityCount()) {
            return nearMaturityOffset(subscriptionIndex) < 7 ? 1 : 2;
        }
        return 12;
    }

    LocalDate subscriptionDate(int subscriptionIndex) {
        if (subscriptionIndex < spec.maturedCount()) {
            if (subscriptionIndex < spec.dormantCandidateCount()) {
                return LocalDate.of(2026, 9, 1);
            }
            return LocalDate.of(2026, 9, 1).plusDays(subscriptionIndex % 5L);
        }
        if (subscriptionIndex < spec.maturedCount() + spec.nearMaturityCount()) {
            // 1·2개월 기간을 조합해 가입은 10/5 이전, 만기는 10/24~11/22로 둔다.
            int offset = nearMaturityOffset(subscriptionIndex);
            return offset < 7
                    ? LocalDate.of(2026, 9, 24).plusDays(offset)
                    : LocalDate.of(2026, 9, 1).plusDays(offset - 7L);
        }
        if (timeDeposit(subscriptionIndex)) {
            return LocalDate.of(2026, 9, 1).plusDays(subscriptionIndex % 35L);
        }
        if (subscriptionCustomerIndex(subscriptionIndex) < spec.dormantCandidateCount()) {
            return LocalDate.of(2026, 9, 1);
        }
        return LocalDate.of(2026, 9, 1).plusDays(subscriptionIndex % 35L);
    }

    LocalDate maturityDate(int subscriptionIndex) {
        return subscriptionDate(subscriptionIndex).plusMonths(termMonths(subscriptionIndex));
    }

    String accountStatus(int subscriptionIndex) {
        return subscriptionIndex < spec.maturedCount() ? "MATURED" : "ACTIVE";
    }

    long subscriptionAmount(int subscriptionIndex) {
        return switch (productCode(subscriptionIndex)) {
            case "PRD_SHORT_DEP" -> 500_000L;
            case "PRD_BASIC_DEP" -> 1_000_000L;
            default -> 50_000L;
        };
    }

    long demandAccountId(int customerIndex) {
        return spec.accountIdStart() + customerIndex * 3L;
    }

    long timeDepositAccountId(int customerIndex) {
        return demandAccountId(customerIndex) + 1;
    }

    long savingsAccountId(int customerIndex) {
        return demandAccountId(customerIndex) + 2;
    }

    long subscriptionAccountId(int subscriptionIndex) {
        int customerIndex = subscriptionCustomerIndex(subscriptionIndex);
        return timeDeposit(subscriptionIndex) ? timeDepositAccountId(customerIndex) : savingsAccountId(customerIndex);
    }

    String accountNumber(int accountIndex) {
        int customerIndex = accountIndex / 3;
        int kind = accountIndex % 3;
        String prefix;
        int sequence;
        if (kind == 0) {
            prefix = "10";
            sequence = customerIndex + 6_000_001;
        } else if (kind == 2) {
            prefix = "32";
            sequence = customerIndex + 6_000_001;
        } else if (customerIndex < spec.maturedCount() + spec.nearMaturityCount()) {
            prefix = "23";
            sequence = customerIndex + 6_000_001;
        } else {
            prefix = "20";
            sequence = customerIndex - spec.maturedCount() - spec.nearMaturityCount() + 6_000_001;
        }
        return "088" + prefix + String.format(Locale.ROOT, "%07d", sequence);
    }

    String customerUserId(int customerIndex) {
        return String.format(Locale.ROOT, "ph60b%05d", customerIndex + 1);
    }

    String transactionNumber(int globalTransactionIndex) {
        return transactionNumber(tradeDate(globalTransactionIndex), globalTransactionIndex);
    }

    String transactionNumber(LocalDate tradeDate, int globalTransactionIndex) {
        return tradeDate.toString().replace("-", "")
                + "WB"
                + String.format(Locale.ROOT, "%010d", 100_000_001L + globalTransactionIndex);
    }

    int transferMonthlyCount(int monthIndex) {
        return transferMonthlyCounts[monthIndex];
    }

    int transferPaydayCount(int monthIndex) {
        return transferPaydayCounts[monthIndex];
    }

    private int transferCustomerIndex(int transferIndex) {
        int ordinal = transferIndex - spec.customerCount();
        int eligibleCount = spec.customerCount() - spec.dormantCandidateCount();
        if (hotspot(transferIndex) || eligibleCount <= HOTSPOT_ACCOUNT_COUNT) {
            // 100건 중 30건의 실제 핫스팟 순번으로 압축해 100계좌를 고르게 순환한다.
            int hotspotOrdinal = Math.floorDiv(ordinal, 100) * HOTSPOT_PERCENT + Math.floorMod(ordinal, 100);
            return spec.dormantCandidateCount()
                    + Math.floorMod(hotspotOrdinal, Math.min(HOTSPOT_ACCOUNT_COUNT, eligibleCount));
        }
        int regularOrdinal =
                Math.floorDiv(ordinal, 100) * (100 - HOTSPOT_PERCENT) + Math.floorMod(ordinal, 100) - HOTSPOT_PERCENT;
        return spec.dormantCandidateCount()
                + HOTSPOT_ACCOUNT_COUNT
                + Math.floorMod(regularOrdinal, eligibleCount - HOTSPOT_ACCOUNT_COUNT);
    }

    private int nearMaturityOffset(int subscriptionIndex) {
        return Math.floorMod(subscriptionIndex - spec.maturedCount(), 29);
    }

    private int transferMonthIndex(int transferIndex) {
        int boundary = 0;
        for (int month = 0; month < transferMonthlyCounts.length; month++) {
            boundary += distributableMonthlyCount(month);
            if (transferIndex < boundary) {
                return month;
            }
        }
        throw new IllegalArgumentException("PH-60b 이체 인덱스가 목표 건수를 벗어났습니다: " + transferIndex);
    }

    private int distributableMonthlyCount(int monthIndex) {
        return transferMonthlyCounts[monthIndex] - (monthIndex == 0 ? spec.customerCount() : 0);
    }

    private List<LocalDate> weightedDates(LocalDate start, LocalDate end) {
        List<LocalDate> result = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            if (date.equals(LocalDate.of(2026, 9, 25))) {
                continue;
            }
            int weight = isWeekend(date) ? 1 : 5;
            for (int index = 0; index < weight; index++) {
                result.add(date);
            }
        }
        return List.copyOf(result);
    }

    private boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }
}
