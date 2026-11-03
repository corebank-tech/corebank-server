package com.shinhan.infoapi.domain;

import java.time.LocalDate;

public record DepositBalancePoint(LocalDate baseDate, long totalBalance) {}
