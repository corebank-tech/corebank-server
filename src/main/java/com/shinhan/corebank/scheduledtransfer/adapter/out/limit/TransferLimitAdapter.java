package com.shinhan.corebank.scheduledtransfer.adapter.out.limit;

import com.shinhan.corebank.limit.application.port.in.LimitQueryUseCase;
import com.shinhan.corebank.scheduledtransfer.application.port.out.TransferLimitPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 빈 이름 명시: autotransfer.adapter.out.limit.TransferLimitAdapter와 클래스 단순이름이 같아
// 기본 빈 이름(transferLimitAdapter)이 충돌한다.
// 다른 도메인(limit) 소유 테이블을 읽기 전용으로 볼 때는 자체 JPA 매핑 대신 소유 도메인의 공개
// UseCase를 경유한다(ADR-0002, team_db_architecture_guide.md §3-①).
@Component("scheduledTransferTransferLimitAdapter")
@RequiredArgsConstructor
public class TransferLimitAdapter implements TransferLimitPort {

    private final LimitQueryUseCase limitQueryUseCase;

    @Override
    public long findOneTimeLimit(Long customerId) {
        return limitQueryUseCase.get(customerId).oneTimeLimit();
    }
}
