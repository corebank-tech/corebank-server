package com.shinhan.corebank.autotransfer.application.port.out;

import com.shinhan.corebank.account.domain.AccountType;
import java.time.LocalDate;

public record DepositAccountInfo(AccountType accountType, LocalDate maturityDate) {}
