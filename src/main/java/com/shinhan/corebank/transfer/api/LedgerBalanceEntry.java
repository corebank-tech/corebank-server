package com.shinhan.corebank.transfer.api;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record LedgerBalanceEntry(LocalDate date, LocalDateTime occurredAt, long ledgerEntryId, long balanceAfter) {}
