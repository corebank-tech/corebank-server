package com.shinhan.corebank.account.adapter.in.cob;

import com.shinhan.corebank.account.application.port.in.AccountMaturityUseCase;
import com.shinhan.corebank.batch.api.CobStep;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccountMaturityCobStep implements CobStep {

    private static final String NAME = "account-maturity";
    private static final int ORDER = 3;

    private final AccountMaturityUseCase accountMaturityUseCase;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public int order() {
        return ORDER;
    }

    // 전달받은 businessDate는 다음 영업일이 아닌 COB_DATE다.
    // 다음 영업일 계산은 AccountMaturityService에서 수행한다.
    @Override
    public void run(LocalDate businessDate) {
        accountMaturityUseCase.runInCob(businessDate);
    }
}
