package com.shinhan.corebank.common.init;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// DB와 무관한 PH-60b 생성 규칙을 결정적으로 계산한다.
final class Phase2BulkSeedPlan {

    private static final int MONTHLY_TRANSACTION_COUNT = 1_000_000;
    private static final int PAYDAY_TRANSACTION_COUNT = 200_000;
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
        this.weightedDates = List.of(weightedDates(2026, 7), weightedDates(2026, 8), weightedDates(2026, 9));
        int[] subscriptionMonthlyCounts = new int[3];
        int[] subscriptionPaydayCounts = new int[3];
        for (int index = 0; index < spec.subscriptionCount(); index++) {
            LocalDate date = subscriptionDate(index);
            int monthIndex = date.getMonthValue() - 7;
            subscriptionMonthlyCounts[monthIndex]++;
            if (date.getDayOfMonth() == 25) {
                subscriptionPaydayCounts[monthIndex]++;
            }
        }
        this.transferMonthlyCounts = new int[3];
        this.transferPaydayCounts = new int[3];
        for (int month = 0; month < 3; month++) {
            transferMonthlyCounts[month] = MONTHLY_TRANSACTION_COUNT - subscriptionMonthlyCounts[month];
            transferPaydayCounts[month] = PAYDAY_TRANSACTION_COUNT - subscriptionPaydayCounts[month];
        }
    }

    LocalDate tradeDate(int transferIndex) {
        if (transferIndex < spec.dormantCandidateCount()) {
            return LocalDate.of(2026, 9, 1);
        }
        int distributableIndex = transferIndex - spec.dormantCandidateCount();
        int monthIndex = transferMonthIndex(distributableIndex);
        int monthOrdinal = distributableIndex;
        for (int month = 0; month < monthIndex; month++) {
            monthOrdinal -= distributableMonthlyCount(month);
        }
        if (monthOrdinal < transferPaydayCounts[monthIndex]) {
            return LocalDate.of(2026, monthIndex + 7, 25);
        }
        List<LocalDate> dates = weightedDates.get(monthIndex);
        return dates.get((monthOrdinal - transferPaydayCounts[monthIndex]) % dates.size());
    }

    LocalDateTime occurredAt(int globalTransactionIndex) {
        int seconds = Math.floorMod(globalTransactionIndex / 3, 86_399);
        return LocalDateTime.of(tradeDate(globalTransactionIndex), LocalTime.ofSecondOfDay(seconds + 1L));
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
        return transferIndex < spec.customerCount() ? 12_000_000L : 10_000L + transferIndex % 90 * 1_000L;
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
            return 3;
        }
        return timeDeposit(subscriptionIndex) ? 12 : 12;
    }

    LocalDate subscriptionDate(int subscriptionIndex) {
        if (subscriptionIndex < spec.maturedCount()) {
            return LocalDate.of(2026, 7, 15).plusDays(subscriptionIndex % 17L);
        }
        if (subscriptionIndex < spec.maturedCount() + spec.nearMaturityCount()) {
            return LocalDate.of(2026, 7, 24).plusDays((subscriptionIndex - spec.maturedCount()) % 30L);
        }
        if (timeDeposit(subscriptionIndex)) {
            return LocalDate.of(2026, 8, 1).plusDays(subscriptionIndex % 61L);
        }
        return LocalDate.of(2026, 7, 22).plusDays(subscriptionIndex % 71L);
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
            return spec.dormantCandidateCount()
                    + Math.floorMod(ordinal, Math.min(HOTSPOT_ACCOUNT_COUNT, eligibleCount));
        }
        return spec.dormantCandidateCount()
                + HOTSPOT_ACCOUNT_COUNT
                + Math.floorMod(ordinal, eligibleCount - HOTSPOT_ACCOUNT_COUNT);
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
        return transferMonthlyCounts[monthIndex] - (monthIndex == 2 ? spec.dormantCandidateCount() : 0);
    }

    private List<LocalDate> weightedDates(int year, int month) {
        List<LocalDate> result = new ArrayList<>();
        YearMonth yearMonth = YearMonth.of(year, month);
        for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
            LocalDate date = yearMonth.atDay(day);
            if (day == 25) {
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
