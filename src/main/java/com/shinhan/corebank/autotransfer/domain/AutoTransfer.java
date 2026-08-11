package com.shinhan.corebank.autotransfer.domain;

import com.shinhan.corebank.common.exception.BusinessException;
import lombok.AccessLevel;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
public class AutoTransfer {
    private Long autoTransferId;
    private Long customerId;
    private Long withdrawalAccountId;
    private String depositAccountNumber;
    private String payeeName;
    private Long amount;
    private Integer cycleMonths;
    private Integer transferDay;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate nextExecutionDate;
    private String myPassbookMemo;
    private String recipientPassbookMemo;
    private AutoTransferStatus status;
    private LocalDateTime registeredAt;
    private LocalDateTime terminatedAt;
    private LocalDateTime updatedAt;
    // 실행 이력은 추가만 허용한다. 외부에서 remove()/clear()로 지울 수 없도록 불변 뷰로만 노출한다.
    @Getter(AccessLevel.NONE)
    private final List<AutoTransferExecution> executions = new ArrayList<>();

    // 등록
    public static AutoTransfer register(
            Long customerId, Long withdrawalAccountId, String depositAccountNumber, String payeeName,
            Long amount, Integer cycleMonths, Integer transferDay,
            LocalDate startDate, LocalDate endDate,
            String myPassbookMemo, String recipientPassbookMemo, LocalDateTime now) {

        TransferCycle.fromMonths(cycleMonths);

        if (amount <= 0) {
            throw new BusinessException(AutoTransferErrorCode.INVALID_AMOUNT);
        }

        if (transferDay < 1 || transferDay > 31) {
            throw new BusinessException(AutoTransferErrorCode.INVALID_TRANSFER_DAY);
        }
        LocalDate today = now.toLocalDate();
        if (!startDate.isAfter(today)) {
            throw new BusinessException(AutoTransferErrorCode.INVALID_TRANSFER_PERIOD, "이체 시작일은 당일이나 과거일 수 없습니다.");
        }
        if (startDate.isAfter(today.plusDays(365))) {
            throw new BusinessException(AutoTransferErrorCode.INVALID_TRANSFER_PERIOD, "이체 시작일은 익일부터 1년 이내여야 합니다.");
        }
        if (endDate.isBefore(startDate)) {
            throw new BusinessException(AutoTransferErrorCode.INVALID_TRANSFER_PERIOD, "이체 종료일은 시작일 이후여야 합니다.");
        }
        if (endDate.isAfter(startDate.plusMonths(60))) {
            throw new BusinessException(AutoTransferErrorCode.INVALID_TRANSFER_PERIOD, "이체 종료일은 시작일로부터 60개월 이내여야 합니다.");
        }

        // 최초 실행 예정일은 항상 startDate 이후 첫 transferDay(월말 보정)로 서버가 직접 계산
        LocalDate nextExecutionDate = firstExecutionDate(startDate, transferDay);
        if (nextExecutionDate.isAfter(endDate)) {
            throw new BusinessException(AutoTransferErrorCode.NO_EXECUTION_WITHIN_PERIOD);
        }

        AutoTransfer e = new AutoTransfer();
        e.customerId = customerId;
        e.withdrawalAccountId = withdrawalAccountId;
        e.depositAccountNumber = depositAccountNumber;
        e.payeeName = payeeName;
        e.amount = amount;
        e.cycleMonths = cycleMonths;
        e.transferDay = transferDay;
        e.startDate = startDate;
        e.endDate = endDate;
        e.nextExecutionDate = nextExecutionDate;
        e.myPassbookMemo = myPassbookMemo;
        e.recipientPassbookMemo = recipientPassbookMemo;
        e.status = AutoTransferStatus.NORMAL;
        e.registeredAt = now;
        return e;
    }

    // startDate 이후 첫 transferDay(월말 보정) 날짜
    private static LocalDate firstExecutionDate(LocalDate startDate, int transferDay) {
        LocalDate candidate = clampToTransferDay(YearMonth.from(startDate), transferDay);
        if (candidate.isBefore(startDate)) {
            candidate = clampToTransferDay(YearMonth.from(startDate).plusMonths(1), transferDay);
        }
        return candidate;
    }

    // 해당 월에 transferDay가 없으면(29~31일) 말일로 보정
    private static LocalDate clampToTransferDay(YearMonth yearMonth, int transferDay) {
        int day = Math.min(transferDay, yearMonth.lengthOfMonth());
        return yearMonth.atDay(day);
    }

    // 해지
    public void terminate(LocalDateTime now) {
        if (!this.status.isModifiable()) {
            throw new BusinessException(AutoTransferErrorCode.NOT_IN_NORMAL_STATUS);
        }
        if (this.nextExecutionDate.equals(now.toLocalDate())) {
            throw new BusinessException(AutoTransferErrorCode.CANNOT_TERMINATE_ON_EXECUTION_DATE);
        }
        this.status = AutoTransferStatus.TERMINATED;
        this.terminatedAt = now;
    }

    // 이체 종료
    public void expire(LocalDateTime now) {
        this.status = AutoTransferStatus.EXPIRED;
        this.terminatedAt = now;
    }

    // 다음 실행 예정일. transferDay 기준으로 계산해 월말 보정이 누적 X
    public void advanceNextExecutionDate() {
        YearMonth targetMonth = YearMonth.from(this.nextExecutionDate).plusMonths(this.cycleMonths);
        this.nextExecutionDate = clampToTransferDay(targetMonth, this.transferDay);
    }

    // 회차 리스트에 새 회차를 추가. 외부에는 불변 뷰로만 노출한다.
    public void addExecution(AutoTransferExecution execution) {
        this.executions.add(execution);
    }

    public List<AutoTransferExecution> getExecutions() {
        return Collections.unmodifiableList(executions);
    }

    // 금액, 주기, 종료일, 통장 표시 내용 변경 (null인 필드는 기존 값 유지)
    public void change(Long amount, Integer cycleMonths, LocalDate endDate,
                       String myPassbookMemo, String recipientPassbookMemo) {
        if (!this.status.isModifiable()) {
            throw new BusinessException(AutoTransferErrorCode.NOT_IN_NORMAL_STATUS);
        }

        Long newAmount = amount != null ? amount : this.amount;
        Integer newCycleMonths = cycleMonths != null ? cycleMonths : this.cycleMonths;
        LocalDate newEndDate = endDate != null ? endDate : this.endDate;

        if (newAmount <= 0) {
            throw new BusinessException(AutoTransferErrorCode.INVALID_AMOUNT);
        }
        TransferCycle.fromMonths(newCycleMonths);
        if (newEndDate.isBefore(this.startDate)) {
            throw new BusinessException(AutoTransferErrorCode.INVALID_TRANSFER_PERIOD, "이체 종료일은 시작일 이후여야 합니다.");
        }
        if (newEndDate.isAfter(this.startDate.plusMonths(60))) {
            throw new BusinessException(AutoTransferErrorCode.INVALID_TRANSFER_PERIOD, "이체 종료일은 시작일로부터 60개월 이내여야 합니다.");
        }

        LocalDate newNextExecutionDate = this.nextExecutionDate;
        if (!newCycleMonths.equals(this.cycleMonths)) {
            // 직전 실행 예정일 기준 재산출: 현재 nextExecutionDate를 옛 주기만큼 되돌린 지점에 새 주기 더하는 방식
            YearMonth cycleBaseMonth = YearMonth.from(this.nextExecutionDate).minusMonths(this.cycleMonths);
            YearMonth targetMonth = cycleBaseMonth.plusMonths(newCycleMonths);
            newNextExecutionDate = clampToTransferDay(targetMonth, this.transferDay);
        }
        if (newNextExecutionDate.isAfter(newEndDate)) {
            throw new BusinessException(AutoTransferErrorCode.NO_EXECUTION_WITHIN_PERIOD);
        }

        // 모든 검증을 통과한 뒤에만 필드에 반영한다 — 검증 도중 실패하면 엔티티는 변경 전 상태를 유지해야 한다
        this.cycleMonths = newCycleMonths;
        this.nextExecutionDate = newNextExecutionDate;
        this.amount = newAmount;
        this.endDate = newEndDate;
        this.myPassbookMemo = myPassbookMemo != null ? myPassbookMemo : this.myPassbookMemo;
        this.recipientPassbookMemo = recipientPassbookMemo != null ? recipientPassbookMemo : this.recipientPassbookMemo;
    }

    public static AutoTransfer reconstitute(
            Long autoTransferId, Long customerId, Long withdrawalAccountId, String depositAccountNumber, String payeeName,
            Long amount, Integer cycleMonths, Integer transferDay, LocalDate startDate, LocalDate endDate, LocalDate nextExecutionDate,
            String myPassbookMemo, String recipientPassbookMemo, AutoTransferStatus status, LocalDateTime registeredAt,
            LocalDateTime terminatedAt, LocalDateTime updatedAt) {
        AutoTransfer e = new AutoTransfer();
        e.autoTransferId = autoTransferId;
        e.customerId = customerId;
        e.withdrawalAccountId = withdrawalAccountId;
        e.depositAccountNumber = depositAccountNumber;
        e.payeeName = payeeName;
        e.amount = amount;
        e.cycleMonths = cycleMonths;
        e.transferDay = transferDay;
        e.startDate = startDate;
        e.endDate = endDate;
        e.nextExecutionDate = nextExecutionDate;
        e.myPassbookMemo = myPassbookMemo;
        e.recipientPassbookMemo = recipientPassbookMemo;
        e.status = status;
        e.registeredAt = registeredAt;
        e.terminatedAt = terminatedAt;
        e.updatedAt = updatedAt;
        return e;
    }

}
